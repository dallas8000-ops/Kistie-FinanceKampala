package com.financeapp.uganda.database;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import android.content.Context;
import com.financeapp.uganda.accounting.AccountSeeder;
import com.financeapp.uganda.models.*;
import com.financeapp.uganda.utils.DateConverter;

@Database(entities = {
        Expense.class, InventoryItem.class, Sale.class, Shipment.class,
        Account.class, JournalEntry.class, JournalLine.class,
        AccountReceivable.class, AccountPayable.class,
        ReceivablePayment.class, PayablePayment.class, SyncOutboxEntry.class,
        ShipmentLine.class, ShipmentReceipt.class, ShipmentReceiptLine.class
    }, version = 7, exportSchema = true)
@TypeConverters(DateConverter.class)
public abstract class AppDatabase extends RoomDatabase {
    public static final int CURRENT_SCHEMA_VERSION = 7;
        public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE sales ADD COLUMN amountPaid REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE sales ADD COLUMN status TEXT NOT NULL DEFAULT 'POSTED'");
            database.execSQL("ALTER TABLE sales ADD COLUMN voidReason TEXT");
            database.execSQL("ALTER TABLE sales ADD COLUMN voidedAt INTEGER");
            database.execSQL("ALTER TABLE expenses ADD COLUMN status TEXT NOT NULL DEFAULT 'POSTED'");
            database.execSQL("ALTER TABLE expenses ADD COLUMN voidReason TEXT");
            database.execSQL("ALTER TABLE expenses ADD COLUMN voidedAt INTEGER");
            database.execSQL("ALTER TABLE accounts_receivable ADD COLUMN voidReason TEXT");
            database.execSQL("ALTER TABLE accounts_receivable ADD COLUMN voidedAt INTEGER");
            database.execSQL("ALTER TABLE accounts_payable ADD COLUMN voidReason TEXT");
            database.execSQL("ALTER TABLE accounts_payable ADD COLUMN voidedAt INTEGER");
            database.execSQL("CREATE TABLE IF NOT EXISTS receivable_payments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + "receivableId INTEGER NOT NULL, amount REAL NOT NULL, paymentMethod TEXT, "
                + "paidAt INTEGER, reference TEXT, journalEntryId INTEGER NOT NULL, "
                + "status TEXT NOT NULL DEFAULT 'POSTED', "
                + "reversalJournalEntryId INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("CREATE TABLE IF NOT EXISTS payable_payments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + "payableId INTEGER NOT NULL, amount REAL NOT NULL, paymentMethod TEXT, "
                + "paidAt INTEGER, reference TEXT, journalEntryId INTEGER NOT NULL, "
                + "status TEXT NOT NULL DEFAULT 'POSTED', "
                + "reversalJournalEntryId INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("INSERT OR IGNORE INTO accounts (id, code, name, type, isSystem) "
                + "VALUES (12, '3020', 'Owner Drawings', 'EQUITY', 1)");
        }
        };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS sync_outbox ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "operationId TEXT NOT NULL, operationType TEXT NOT NULL, aggregateId TEXT NOT NULL, "
                    + "payloadJson TEXT NOT NULL, payloadVersion INTEGER NOT NULL DEFAULT 1, "
                    + "createdAt INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, "
                    + "nextAttemptAt INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'PENDING', "
                    + "lastError TEXT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_operationId "
                    + "ON sync_outbox (operationId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_status_nextAttemptAt "
                    + "ON sync_outbox (status, nextAttemptAt)");
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            String[] tables = {
                    "inventory", "sales", "expenses", "accounts_receivable", "accounts_payable",
                    "receivable_payments", "payable_payments", "shipments", "shipment_lines",
                    "shipment_receipts", "shipment_receipt_lines", "journal_entries", "journal_lines"
            };
            for (String table : tables) {
                database.execSQL("ALTER TABLE " + table
                        + " ADD COLUMN syncId TEXT NOT NULL DEFAULT ''");
                database.execSQL("UPDATE " + table
                        + " SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''");
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_" + table
                        + "_syncId ON " + table + " (syncId)");
            }
        }
    };

        public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE shipments ADD COLUMN stockReceivedAt INTEGER");
            database.execSQL("ALTER TABLE shipments ADD COLUMN payableId INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE shipments ADD COLUMN journalEntryId INTEGER NOT NULL DEFAULT 0");
            database.execSQL("CREATE TABLE IF NOT EXISTS shipment_lines ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shipmentId INTEGER NOT NULL, "
                + "inventoryItemId INTEGER NOT NULL, orderedQuantity INTEGER NOT NULL, "
                + "receivedQuantity INTEGER NOT NULL DEFAULT 0, quantityStocked INTEGER NOT NULL DEFAULT 0, "
                + "unitCost REAL NOT NULL, currencyCode TEXT NOT NULL, exchangeRate REAL NOT NULL DEFAULT 1)");
            database.execSQL("CREATE TABLE IF NOT EXISTS shipment_receipts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shipmentId INTEGER NOT NULL, "
                + "receivedAt INTEGER, goodsValueUgx REAL NOT NULL, allocatedChargesUgx REAL NOT NULL, "
                + "totalLandedCostUgx REAL NOT NULL, payableId INTEGER NOT NULL, "
                    + "journalEntryId INTEGER NOT NULL, reference TEXT NOT NULL)");
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_shipment_receipts_reference "
                    + "ON shipment_receipts (reference)");
            database.execSQL("CREATE TABLE IF NOT EXISTS shipment_receipt_lines ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, receiptId INTEGER NOT NULL, "
                + "shipmentLineId INTEGER NOT NULL, quantity INTEGER NOT NULL, goodsValueUgx REAL NOT NULL, "
                + "allocatedShippingUgx REAL NOT NULL, allocatedDutyVatUgx REAL NOT NULL, "
                + "allocatedClearingUgx REAL NOT NULL, landedUnitCostUgx REAL NOT NULL)");
        }
        };

    private static AppDatabase instance;

    public abstract FinanceDao financeDao();

    public static synchronized void closeInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    "finance_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .allowMainThreadQueries()
                .build();
            AccountSeeder.seedIfNeeded(instance.financeDao());
        }
        return instance;
    }
}
