package com.financeapp.uganda.backup;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.financeapp.uganda.database.AppDatabase;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class FinanceBackupManager {
    private static final byte[] MAGIC = {'K', 'F', 'B', 'K'};
    private static final byte[] PAYLOAD_MAGIC = {'K', 'F', 'D', 'B'};
    private static final int FORMAT_VERSION = 1;
    private static final int PAYLOAD_VERSION = 1;
    private static final String DATABASE_NAME = "finance_database";
    private static final String CANDIDATE_DATABASE_NAME = "finance_restore_candidate";
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int MIN_ITERATIONS = 100_000;
    private static final int MAX_ITERATIONS = 1_000_000;
    private static final int HEADER_BYTES = MAGIC.length + 1 + Integer.BYTES + SALT_BYTES + NONCE_BYTES;
    private static final int MAX_ARCHIVE_BYTES = 512 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private FinanceBackupManager() {}

    public static byte[] encryptArchivePayload(byte[] payload, char[] passphrase) throws IOException {
        if (payload == null || passphrase == null || passphrase.length == 0) {
            throw new IllegalArgumentException("Backup payload and passphrase are required.");
        }
        if (payload.length > MAX_ARCHIVE_BYTES) throw new IOException("Backup is too large.");

        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(deriveKey(passphrase, salt, PBKDF2_ITERATIONS), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(payload);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_BYTES + ciphertext.length);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.write(MAGIC);
                output.writeByte(FORMAT_VERSION);
                output.writeInt(PBKDF2_ITERATIONS);
                output.write(salt);
                output.write(nonce);
                output.write(ciphertext);
            }
            return bytes.toByteArray();
        } catch (GeneralSecurityException e) {
            throw new IOException("Unable to encrypt backup.", e);
        }
    }

    public static byte[] decryptArchivePayload(byte[] archive, char[] passphrase) throws IOException {
        if (archive == null || passphrase == null || passphrase.length == 0) {
            throw new IllegalArgumentException("Backup archive and passphrase are required.");
        }
        if (archive.length < HEADER_BYTES + TAG_BITS / 8 || archive.length > MAX_ARCHIVE_BYTES) {
            throw new IOException("Backup archive has an invalid size.");
        }

        ByteBuffer input = ByteBuffer.wrap(archive);
        for (byte expected : MAGIC) {
            if (input.get() != expected) throw new IOException("Backup archive header is invalid.");
        }
        int version = Byte.toUnsignedInt(input.get());
        if (version != FORMAT_VERSION) throw new IOException("Backup format version is not supported.");
        int iterations = input.getInt();
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
            throw new IOException("Backup key-derivation parameters are invalid.");
        }
        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        input.get(salt);
        input.get(nonce);
        byte[] ciphertext = new byte[input.remaining()];
        input.get(ciphertext);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(deriveKey(passphrase, salt, iterations), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IOException("Backup could not be authenticated or decrypted.", e);
        }
    }

    public static void exportDatabase(Context context, Uri destination, char[] passphrase) throws IOException {
        Context app = context.getApplicationContext();
        if (destination == null) throw new IllegalArgumentException("Choose a backup destination.");
        checkpointAndClose(app);
        try {
            File databaseFile = app.getDatabasePath(DATABASE_NAME);
            byte[] databaseBytes = readFile(databaseFile, MAX_ARCHIVE_BYTES);
            byte[] payload = encodePayload(databaseBytes, AppDatabase.CURRENT_SCHEMA_VERSION, System.currentTimeMillis());
            byte[] archive = encryptArchivePayload(payload, passphrase);
            ContentResolver resolver = app.getContentResolver();
            try (OutputStream output = resolver.openOutputStream(destination, "wt")) {
                if (output == null) throw new IOException("Unable to open the selected backup file.");
                output.write(archive);
                output.flush();
            }
        } finally {
            reopenDatabase(app);
        }
    }

    public static void restoreDatabase(Context context, Uri source, char[] passphrase) throws IOException {
        Context app = context.getApplicationContext();
        if (source == null) throw new IllegalArgumentException("Choose a backup file.");
        byte[] archive;
        try (InputStream input = app.getContentResolver().openInputStream(source)) {
            if (input == null) throw new IOException("Unable to open the selected backup file.");
            archive = readStream(input, MAX_ARCHIVE_BYTES);
        }
        byte[] databaseBytes = decodePayload(decryptArchivePayload(archive, passphrase));
        File candidate = new File(app.getCacheDir(), CANDIDATE_DATABASE_NAME + ".db");
        writeFile(candidate, databaseBytes);
        File candidateDatabase = app.getDatabasePath(CANDIDATE_DATABASE_NAME);
        ensureParent(candidateDatabase);
        copyFile(candidate, candidateDatabase);
        File candidateWal = new File(candidateDatabase.getPath() + "-wal");
        File candidateShm = new File(candidateDatabase.getPath() + "-shm");
        boolean mainDatabaseClosed = false;
        try {
            validateRoomDatabase(app, candidateDatabase);
            checkpointNamedDatabase(app, CANDIDATE_DATABASE_NAME);
            checkpointAndClose(app);
            mainDatabaseClosed = true;
            File liveDatabase = app.getDatabasePath(DATABASE_NAME);
            File rollbackDirectory = new File(app.getCacheDir(), "finance-restore-rollback");
            if (!rollbackDirectory.mkdirs() && !rollbackDirectory.isDirectory()) {
                throw new IOException("Unable to create the pre-restore safety copy.");
            }
            File rollbackDatabase = new File(rollbackDirectory, DATABASE_NAME);
            File rollbackWal = new File(rollbackDirectory, DATABASE_NAME + "-wal");
            File rollbackShm = new File(rollbackDirectory, DATABASE_NAME + "-shm");
            copyIfExists(liveDatabase, rollbackDatabase);
            copyIfExists(new File(liveDatabase.getPath() + "-wal"), rollbackWal);
            copyIfExists(new File(liveDatabase.getPath() + "-shm"), rollbackShm);
            try {
                deleteIfExists(new File(liveDatabase.getPath() + "-wal"));
                deleteIfExists(new File(liveDatabase.getPath() + "-shm"));
                copyFile(candidateDatabase, liveDatabase);
                AppDatabase restored = AppDatabase.getInstance(app);
                restored.financeDao().getAllInventory();
                mainDatabaseClosed = false;
                deleteRecursively(rollbackDirectory);
            } catch (Exception restoreFailure) {
                AppDatabase.closeInstance();
                restoreFiles(rollbackDatabase, rollbackWal, rollbackShm, liveDatabase);
                reopenDatabase(app);
                mainDatabaseClosed = false;
                throw new IOException("Restore failed; the pre-restore database was recovered.", restoreFailure);
            }
        } finally {
            if (mainDatabaseClosed) reopenDatabase(app);
            deleteIfExists(candidate);
            app.deleteDatabase(CANDIDATE_DATABASE_NAME);
            deleteIfExists(candidateWal);
            deleteIfExists(candidateShm);
        }
    }

    private static byte[] encodePayload(byte[] databaseBytes, int schemaVersion, long createdAt) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(databaseBytes.length + 32);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(PAYLOAD_MAGIC);
            output.writeByte(PAYLOAD_VERSION);
            output.writeInt(schemaVersion);
            output.writeLong(createdAt);
            output.writeInt(databaseBytes.length);
            output.write(databaseBytes);
        }
        return bytes.toByteArray();
    }

    private static byte[] decodePayload(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            for (byte expected : PAYLOAD_MAGIC) {
                if (input.readByte() != expected) throw new IOException("Backup manifest is invalid.");
            }
            if (input.readUnsignedByte() != PAYLOAD_VERSION) throw new IOException("Backup manifest version is not supported.");
            int schemaVersion = input.readInt();
            if (schemaVersion < 3 || schemaVersion > AppDatabase.CURRENT_SCHEMA_VERSION) {
                throw new IOException("Backup database version is not supported by this app.");
            }
            input.readLong();
            int databaseLength = input.readInt();
            if (databaseLength <= 0 || databaseLength > MAX_ARCHIVE_BYTES || databaseLength != input.available()) {
                throw new IOException("Backup database payload is invalid.");
            }
            byte[] databaseBytes = new byte[databaseLength];
            input.readFully(databaseBytes);
            return databaseBytes;
        }
    }

    private static void validateRoomDatabase(Context context, File databaseFile) throws IOException {
        try (SQLiteDatabase database = SQLiteDatabase.openDatabase(
                databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            if (database.getVersion() < 3 || database.getVersion() > AppDatabase.CURRENT_SCHEMA_VERSION) {
                throw new IOException("Database schema version is not supported.");
            }
            try (Cursor cursor = database.rawQuery("PRAGMA integrity_check", null)) {
                if (!cursor.moveToFirst() || !"ok".equalsIgnoreCase(cursor.getString(0))) {
                    throw new IOException("Backup database integrity check failed.");
                }
            }
        } catch (android.database.sqlite.SQLiteException e) {
            throw new IOException("Backup does not contain a valid SQLite database.", e);
        }
        AppDatabase candidate = Room.databaseBuilder(context, AppDatabase.class, CANDIDATE_DATABASE_NAME)
                .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
                .allowMainThreadQueries()
                .build();
        try {
            candidate.getOpenHelper().getWritableDatabase();
            candidate.financeDao().getAllInventory();
        } catch (RuntimeException e) {
            throw new IOException("Backup database schema could not be opened by this app.", e);
        } finally {
            candidate.close();
        }
    }

    private static void checkpointAndClose(Context context) throws IOException {
        AppDatabase database = AppDatabase.getInstance(context);
        checkpoint(database.getOpenHelper().getWritableDatabase());
        AppDatabase.closeInstance();
    }

    private static void checkpointNamedDatabase(Context context, String name) throws IOException {
        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, name)
                .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
                .allowMainThreadQueries().build();
        try {
            checkpoint(database.getOpenHelper().getWritableDatabase());
        } finally {
            database.close();
        }
    }

    private static void checkpoint(SupportSQLiteDatabase database) throws IOException {
        try (Cursor cursor = database.query("PRAGMA wal_checkpoint(FULL)")) {
            if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                throw new IOException("Database checkpoint is busy; retry the backup/restore.");
            }
        }
    }

    private static void reopenDatabase(Context context) {
        AppDatabase.getInstance(context);
    }

    private static byte[] readFile(File file, int maximumBytes) throws IOException {
        if (!file.isFile() || file.length() <= 0 || file.length() > maximumBytes) {
            throw new IOException("Database file is missing or too large to back up.");
        }
        try (InputStream input = new FileInputStream(file)) {
            return readStream(input, maximumBytes);
        }
    }

    private static byte[] readStream(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > maximumBytes) throw new IOException("Backup file exceeds the size limit.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void writeFile(File file, byte[] contents) throws IOException {
        ensureParent(file);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(contents);
            output.getFD().sync();
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        ensureParent(target);
        try (InputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.getFD().sync();
        }
    }

    private static void copyIfExists(File source, File target) throws IOException {
        if (source.isFile()) copyFile(source, target);
    }

    private static void restoreFiles(File backupDatabase, File backupWal, File backupShm,
                                     File liveDatabase) throws IOException {
        if (backupDatabase.isFile()) copyFile(backupDatabase, liveDatabase);
        if (backupWal.isFile()) copyFile(backupWal, new File(liveDatabase.getPath() + "-wal"));
        if (backupShm.isFile()) copyFile(backupShm, new File(liveDatabase.getPath() + "-shm"));
    }

    private static void ensureParent(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create backup working directory.");
        }
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file.exists() && !file.delete()) throw new IOException("Unable to remove temporary database file.");
    }

    private static void deleteRecursively(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        deleteIfExists(file);
    }

    private static byte[] deriveKey(char[] passphrase, byte[] salt, int iterations)
            throws GeneralSecurityException {
        byte[] password = new String(passphrase).getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(password, "HmacSHA256"));
        byte[] firstBlockInput = ByteBuffer.allocate(salt.length + Integer.BYTES)
                .put(salt).putInt(1).array();
        byte[] u = mac.doFinal(firstBlockInput);
        byte[] result = u.clone();
        for (int iteration = 1; iteration < iterations; iteration++) {
            u = mac.doFinal(u);
            for (int index = 0; index < result.length; index++) result[index] ^= u[index];
        }
        java.util.Arrays.fill(password, (byte) 0);
        java.util.Arrays.fill(u, (byte) 0);
        return result;
    }
}