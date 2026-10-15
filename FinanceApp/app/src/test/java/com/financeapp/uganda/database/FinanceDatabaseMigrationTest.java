package com.financeapp.uganda.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import com.financeapp.uganda.accounting.AccountCodes;
import com.financeapp.uganda.models.Expense;
import com.financeapp.uganda.models.InventoryItem;
import com.financeapp.uganda.models.Sale;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.json.JSONArray;
import org.json.JSONObject;

@RunWith(RobolectricTestRunner.class)
public class FinanceDatabaseMigrationTest {

    private static final String DATABASE_NAME = "finance-migration-test";

    @Test
    public void currentSchemaConstantMatchesRoomDatabaseVersion() {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5,
                        AppDatabase.MIGRATION_5_6)
                .allowMainThreadQueries()
                .build();
        try {
            try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query("PRAGMA user_version")) {
                cursor.moveToFirst();
                assertEquals(AppDatabase.CURRENT_SCHEMA_VERSION, cursor.getInt(0));
            }
        } finally {
            database.close();
        }
    }

    @Test
    public void versionThreeMigrationPreservesExistingFinancialRows() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SQLiteOpenHelper legacyHelper = new SQLiteOpenHelper(context, DATABASE_NAME, null, 3) {
            @Override
            public void onCreate(SQLiteDatabase database) {
                createSchema(database, 3);
            }

            @Override
            public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {}
        };
        SQLiteDatabase oldDatabase = legacyHelper.getWritableDatabase();
        oldDatabase.execSQL("INSERT INTO accounts (id, code, name, type, isSystem) "
                + "VALUES (1, '1000', 'Cash', 'ASSET', 1)");
        oldDatabase.execSQL("INSERT INTO inventory (id, name, quantity, unitCost, sellingPrice, "
                + "minStockLevel, importDuty, shippingCost, clearingCost) "
                + "VALUES (4, 'Preserved item', 7, 500, 1200, 2, 30, 40, 10)");
        oldDatabase.execSQL("INSERT INTO sales (id, inventoryItemId, itemName, quantitySold, "
                + "unitPrice, totalAmount, discount, saleDate, profit, journalEntryId, "
                + "receivableId, costOfGoodsSold) "
                + "VALUES (8, 4, 'Preserved item', 1, 1200, 1200, 0, 1000, 620, 0, 0, 580)");
        oldDatabase.execSQL("INSERT INTO expenses (id, category, subCategory, amount, description, "
                + "date, isRecurring, journalEntryId, payableId) "
                + "VALUES (9, 'business_operational', 'Supplies', 250, 'Preserved expense', "
                + "1000, 0, 0, 0)");
        oldDatabase.close();
        legacyHelper.close();

        AppDatabase migratedDatabase = Room.databaseBuilder(context, AppDatabase.class, DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
                .allowMainThreadQueries()
                .build();
        try {
            FinanceDao dao = migratedDatabase.financeDao();
            InventoryItem item = dao.getInventoryById(4);
            Sale sale = dao.getSaleById(8);
            Expense expense = dao.getExpenseById(9);

            assertEquals("Preserved item", item.getName());
            assertEquals(7, item.getQuantity());
            assertEquals(500d, item.getUnitCost(), 0.001d);
            assertEquals(1200d, item.getSellingPrice(), 0.001d);
            assertEquals(1200d, sale.getTotalAmount(), 0.001d);
            assertEquals(0d, sale.getAmountPaid(), 0.001d);
            assertEquals("POSTED", sale.getStatus());
            assertEquals(250d, expense.getAmount(), 0.001d);
            assertEquals("POSTED", expense.getStatus());
            assertNotNull(dao.getAccountById(AccountCodes.OWNER_DRAWINGS));
            try (Cursor cursor = migratedDatabase.getOpenHelper().getReadableDatabase()
                    .query("SELECT COUNT(*) FROM receivable_payments")) {
                cursor.moveToFirst();
                assertEquals(0, cursor.getInt(0));
            }
        } finally {
            migratedDatabase.close();
            context.deleteDatabase(DATABASE_NAME);
        }
    }

    @Test
    public void versionFourMigrationPreservesShipmentAndInventoryRows() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String databaseName = "finance-migration-v4-test";
        SQLiteOpenHelper legacyHelper = new SQLiteOpenHelper(context, databaseName, null, 4) {
            @Override
            public void onCreate(SQLiteDatabase database) {
                createSchema(database, 4);
            }

            @Override
            public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {}
        };
        SQLiteDatabase oldDatabase = legacyHelper.getWritableDatabase();
        oldDatabase.execSQL("INSERT INTO inventory (id, name, quantity, unitCost, sellingPrice, "
                + "minStockLevel, importDuty, shippingCost, clearingCost) "
                + "VALUES (14, 'Existing stock', 5, 500, 1200, 2, 30, 40, 10)");
        oldDatabase.execSQL("INSERT INTO shipments (id, batchId, sourceCountry, supplierName, "
                + "orderDate, status, totalValue, shippingCost, insuranceCost, importDuty, vat, "
                + "clearingCharges, transportToStore, isDelivered) "
                + "VALUES (6, 'BATCH-6', 'China', 'Supplier', 1000, 'in_transit', 2500, "
                + "300, 50, 200, 0, 100, 75, 0)");
        oldDatabase.close();
        legacyHelper.close();

        AppDatabase migratedDatabase = Room.databaseBuilder(context, AppDatabase.class, databaseName)
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
                .allowMainThreadQueries()
                .build();
        try {
            SupportSQLiteDatabase database = migratedDatabase.getOpenHelper().getWritableDatabase();
            try (Cursor cursor = database.query("SELECT COUNT(*) FROM shipment_lines")) {
                cursor.moveToFirst();
                assertEquals(0, cursor.getInt(0));
            }
            assertEquals("Existing stock", migratedDatabase.financeDao().getInventoryById(14).getName());
            try (Cursor cursor = database.query("SELECT batchId FROM shipments WHERE id = 6")) {
                cursor.moveToFirst();
                assertEquals("BATCH-6", cursor.getString(0));
            }
        } finally {
            migratedDatabase.close();
            context.deleteDatabase(databaseName);
        }
    }

    @Test
    public void versionFiveMigrationCreatesEmptyOutboxAndPreservesBusinessRows() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String databaseName = "finance-migration-v5-test";
        SQLiteOpenHelper legacyHelper = new SQLiteOpenHelper(context, databaseName, null, 5) {
            @Override
            public void onCreate(SQLiteDatabase database) {
                createSchema(database, 5);
            }

            @Override
            public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {}
        };
        SQLiteDatabase oldDatabase = legacyHelper.getWritableDatabase();
        oldDatabase.execSQL("INSERT INTO inventory (id, name, quantity, unitCost, sellingPrice, "
                + "minStockLevel, importDuty, shippingCost, clearingCost) "
                + "VALUES (24, 'Synced stock', 3, 700, 1200, 1, 0, 0, 0)");
        oldDatabase.execSQL("INSERT INTO shipments (id, batchId, sourceCountry, status, totalValue, "
                + "shippingCost, insuranceCost, importDuty, vat, clearingCharges, transportToStore, "
                + "isDelivered, payableId, journalEntryId) "
                + "VALUES (26, 'SYNC-BATCH', 'China', 'partially_received', 2000, 100, 0, 50, 0, 0, 0, 0, 0, 0)");
        oldDatabase.close();
        legacyHelper.close();

        AppDatabase migratedDatabase = Room.databaseBuilder(context, AppDatabase.class, databaseName)
                .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5,
                        AppDatabase.MIGRATION_5_6)
                .allowMainThreadQueries()
                .build();
        try {
            assertEquals("Synced stock", migratedDatabase.financeDao().getInventoryById(24).getName());
            assertEquals("SYNC-BATCH", migratedDatabase.financeDao().getShipmentById(26).getBatchId());
            assertEquals(0, migratedDatabase.financeDao().getPendingSyncCount());
        } finally {
            migratedDatabase.close();
            context.deleteDatabase(databaseName);
        }
    }

    private void createSchema(SQLiteDatabase database, int version) {
        try (InputStream schemaStream = getClass().getClassLoader().getResourceAsStream(
                "com.financeapp.uganda.database.AppDatabase/" + version + ".json")) {
            assertNotNull("Room schema version " + version + " must be available to tests", schemaStream);
            StringBuilder schemaText = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(schemaStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) schemaText.append(line);
            }
            JSONArray entities = new JSONObject(schemaText.toString())
                    .getJSONObject("database").getJSONArray("entities");
            for (int index = 0; index < entities.length(); index++) {
                JSONObject entity = entities.getJSONObject(index);
                String createSql = entity.getString("createSql").replace(
                        "${TABLE_NAME}", entity.getString("tableName"));
                database.execSQL(createSql);
                JSONArray indices = entity.getJSONArray("indices");
                for (int indexNumber = 0; indexNumber < indices.length(); indexNumber++) {
                    String indexSql = indices.getJSONObject(indexNumber).getString("createSql")
                            .replace("${TABLE_NAME}", entity.getString("tableName"));
                    database.execSQL(indexSql);
                }
            }
            JSONObject databaseSchema = new JSONObject(schemaText.toString()).getJSONObject("database");
            database.execSQL("CREATE TABLE IF NOT EXISTS room_master_table "
                    + "(id INTEGER PRIMARY KEY, identity_hash TEXT)");
            database.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) "
                    + "VALUES (42, '" + databaseSchema.getString("identityHash") + "')");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create the version 3 Room schema", e);
        }
    }
}