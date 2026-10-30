package com.financeapp.uganda.sync;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

public final class SupabaseSessionStore {
    private static final String PREFERENCES = "supabase_session";
    private static final String CIPHERTEXT = "session_ciphertext";
    private static final String KEY_ALIAS = "kistie_supabase_session_v1";
    private static final int NONCE_BYTES = 12;
    private final SharedPreferences preferences;
    private final SecretKey encryptionKey;

    public SupabaseSessionStore(Context context) {
        this(context, loadOrCreateKey());
    }

    SupabaseSessionStore(Context context, SecretKey encryptionKey) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        this.encryptionKey = encryptionKey;
    }

    public void saveSession(String accessToken, String refreshToken, long expiresAtMillis) {
        try {
            JSONObject session = new JSONObject();
            session.put("access", accessToken);
            session.put("refresh", refreshToken);
            session.put("expiresAt", expiresAtMillis);
            byte[] nonce = new byte[NONCE_BYTES];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(session.toString().getBytes(StandardCharsets.UTF_8));
            byte[] stored = ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array();
            if (!preferences.edit().putString(CIPHERTEXT, Base64.encodeToString(stored, Base64.NO_WRAP))
                    .commit()) {
                throw new IllegalStateException("Could not save the encrypted sync session.");
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("Could not encrypt the sync session.", e);
        }
    }

    public Session getSession() {
        String encoded = preferences.getString(CIPHERTEXT, null);
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            byte[] stored = Base64.decode(encoded, Base64.NO_WRAP);
            if (stored.length <= NONCE_BYTES) throw new IllegalStateException("Invalid session data.");
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] encrypted = new byte[stored.length - NONCE_BYTES];
            System.arraycopy(stored, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(stored, NONCE_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            JSONObject session = new JSONObject(new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8));
            return new Session(session.getString("access"), session.getString("refresh"),
                    session.getLong("expiresAt"));
        } catch (Exception e) {
            clear();
            return null;
        }
    }

    @SuppressLint("ApplySharedPref") // session removal must hit disk before logout completes
    public void clear() {
        preferences.edit().remove(CIPHERTEXT).commit();
    }

    private static SecretKey loadOrCreateKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            }
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return generator.generateKey();
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize secure sync storage.", e);
        }
    }

    public static final class Session {
        private final String accessToken;
        private final String refreshToken;
        private final long expiresAtMillis;

        Session(String accessToken, String refreshToken, long expiresAtMillis) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAtMillis = expiresAtMillis;
        }

        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public long getExpiresAtMillis() { return expiresAtMillis; }
    }
}
