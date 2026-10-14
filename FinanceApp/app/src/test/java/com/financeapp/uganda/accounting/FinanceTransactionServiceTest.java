package com.financeapp.uganda.accounting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.widget.EditText;
import android.widget.TextView;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.financeapp.uganda.AddSaleActivity;
import com.financeapp.uganda.R;
import com.financeapp.uganda.SalesActivity;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.database.FinanceDao;
import com.financeapp.uganda.models.Expense;
import com.financeapp.uganda.models.InventoryItem;
import com.financeapp.uganda.models.JournalEntry;
import com.financeapp.uganda.models.AccountReceivable;
import com.financeapp.uganda.models.AccountPayable;
import com.financeapp.uganda.models.ReceivablePayment;
import com.financeapp.uganda.models.Sale;
import com.financeapp.uganda.models.Shipment;
import com.financeapp.uganda.models.ShipmentLine;
import com.financeapp.uganda.models.ShipmentReceipt;
import com.financeapp.uganda.models.ShipmentReceiptLine;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
public class FinanceTransactionServiceTest {

    private AppDatabase database;
    private Field databaseInstance;
    private ActivityController<AddSaleActivity> saleActivityController;
    private ActivityController<SalesActivity> salesActivityController;

    @Before
    public void setUp() throws Exception {
        Application application = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        databaseInstance = AppDatabase.class.getDeclaredField("instance");
        databaseInstance.setAccessible(true);
        databaseInstance.set(null, database);
        AccountSeeder.seedIfNeeded(database.financeDao());
    }

    @After
    public void tearDown() throws Exception {
        if (saleActivityController != null) {
            saleActivityController.pause().stop().destroy();
        }
        if (salesActivityController != null) {
            salesActivityController.pause().stop().destroy();
        }
        if (databaseInstance != null) {
            databaseInstance.set(null, null);
        }
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void saleSubmissionCreatesBalancedJournalAndDecrementsStock() {
        FinanceDao dao = database.financeDao();
        InventoryItem item = newInventoryItem();
        long itemId = dao.insertInventoryItem(item);

        saleActivityController = Robolectric.buildActivity(AddSaleActivity.class).setup();
        AddSaleActivity activity = saleActivityController.get();
        ((EditText) activity.findViewById(R.id.etQuantity)).setText("1");
        activity.findViewById(R.id.btnSaveSale).performClick();

        InventoryItem updatedItem = dao.getInventoryById((int) itemId);
        Sale sale = dao.getAllSales().get(0);
        assertEquals(3, updatedItem.getQuantity());
        assertEquals(1200d, sale.getTotalAmount(), 0.001d);
        assertEquals(500d, sale.getCostOfGoodsSold(), 0.001d);

        JournalEntry journal = dao.getJournalBySource("SALE", sale.getId());
        assertNotNull("sale must post its journal entry", journal);
        assertEquals(1700d, dao.getTotalDebitForEntry(journal.getId()), 0.001d);
        assertEquals(1700d, dao.getTotalCreditForEntry(journal.getId()), 0.001d);
    }

    @Test
    public void saleFormRecordsDepositAndRemainingCustomerBalance() {
        FinanceDao dao = database.financeDao();
        InventoryItem item = newInventoryItem();
        dao.insertInventoryItem(item);

        saleActivityController = Robolectric.buildActivity(AddSaleActivity.class).setup();
        AddSaleActivity activity = saleActivityController.get();
        ((EditText) activity.findViewById(R.id.etQuantity)).setText("1");
        ((EditText) activity.findViewById(R.id.etAmountPaid)).setText("400");
        ((EditText) activity.findViewById(R.id.etCustomerName)).setText("Amina Namusoke");
        activity.findViewById(R.id.btnSaveSale).performClick();

        Sale sale = dao.getAllSales().get(0);
        AccountReceivable receivable = dao.getReceivableById(sale.getReceivableId());
        assertEquals(400d, sale.getAmountPaid(), 0.001d);
        assertEquals(800d, receivable.getBalanceDue(), 0.001d);
        assertEquals(1, dao.getReceivablePayments(receivable.getId()).size());
    }

    @Test
    public void selectingMtnSalePaymentShowsItsConfiguredDestinationAndReference() {
        FinanceDao dao = database.financeDao();
        dao.insertInventoryItem(newInventoryItem());
        com.financeapp.uganda.utils.PrefsHelper.saveMtnMobileMoneyNumber(
                ApplicationProvider.getApplicationContext(), "+256700111222");
        com.financeapp.uganda.utils.PrefsHelper.saveAirtelMoneyNumber(
            ApplicationProvider.getApplicationContext(), "+256750333444");

        saleActivityController = Robolectric.buildActivity(AddSaleActivity.class).setup();
        AddSaleActivity activity = saleActivityController.get();
        ((android.widget.Spinner) activity.findViewById(R.id.spinnerPayment)).setSelection(1);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        TextView destination = activity.findViewById(R.id.tvReceivingNumber);
        android.view.View reference = activity.findViewById(R.id.tilTransactionReference);
        assertEquals("Customer sends to MTN MoMo: +256700111222", destination.getText().toString());
        assertEquals(android.view.View.VISIBLE, reference.getVisibility());

        ((android.widget.Spinner) activity.findViewById(R.id.spinnerPayment)).setSelection(2);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals("Customer sends to Airtel Money: +256750333444", destination.getText().toString());
        assertEquals(android.view.View.VISIBLE, reference.getVisibility());
    }

    @Test
    public void invalidSaleDoesNotChangeStockOrCreateRecords() {
        FinanceDao dao = database.financeDao();
        InventoryItem item = newInventoryItem();
        long itemId = dao.insertInventoryItem(item);
        Sale sale = new Sale();
        sale.setInventoryItemId((int) itemId);
        sale.setQuantitySold(0);
        sale.setUnitPrice(1200d);
        sale.setAmountPaid(1200d);
        sale.setPaymentMethod("Cash");
        sale.setSaleDate(new Date());

        try {
            new FinanceTransactionService(database).recordSale(sale);
            fail("zero quantity must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Quantity"));
        }

        assertTrue(dao.getAllSales().isEmpty());
        assertTrue(dao.getAllJournalEntries().isEmpty());
        assertEquals(4, dao.getInventoryById((int) itemId).getQuantity());
    }

    @Test
    public void saleJournalFailureRollsBackSaleAndInventoryChange() {
        FinanceDao dao = database.financeDao();
        InventoryItem item = newInventoryItem();
        long itemId = dao.insertInventoryItem(item);
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_sale_journal BEFORE INSERT ON journal_lines "
                        + "BEGIN SELECT RAISE(ABORT, 'injected sale journal failure'); END");
        Sale sale = new Sale();
        sale.setInventoryItemId((int) itemId);
        sale.setQuantitySold(1);
        sale.setUnitPrice(1200d);
        sale.setAmountPaid(1200d);
        sale.setPaymentMethod("Cash");
        sale.setSaleDate(new Date());

        try {
            new FinanceTransactionService(database).recordSale(sale);
            fail("injected journal failure should abort sale");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage() != null);
        }

        assertTrue(dao.getAllSales().isEmpty());
        assertTrue(dao.getAllJournalEntries().isEmpty());
        assertEquals(4, dao.getInventoryById((int) itemId).getQuantity());
    }

    @Test
    public void saleEditReversesOriginalAndPostsReplacementAtomically() {
        FinanceDao dao = database.financeDao();
        InventoryItem item = newInventoryItem();
        long itemId = dao.insertInventoryItem(item);
        FinanceTransactionService transactions = new FinanceTransactionService(database);
        Sale original = new Sale();
        original.setInventoryItemId((int) itemId);
        original.setQuantitySold(1);
        original.setUnitPrice(1200d);
        original.setAmountPaid(1200d);
        original.setPaymentMethod("Cash");
        original.setSaleDate(new Date());
        long originalId = transactions.recordSale(original);

        Sale replacement = new Sale();
        replacement.setInventoryItemId((int) itemId);
        replacement.setQuantitySold(1);
        replacement.setUnitPrice(1100d);
        replacement.setAmountPaid(1100d);
        replacement.setPaymentMethod("Cash");
        replacement.setSaleDate(new Date());
        long replacementId = transactions.replaceSale((int) originalId, replacement, true);

        assertEquals("VOID", dao.getSaleById((int) originalId).getStatus());
        assertEquals("POSTED", dao.getSaleById((int) replacementId).getStatus());
        assertEquals(3, dao.getInventoryById((int) itemId).getQuantity());
        assertEquals(3, dao.getAllJournalEntries().size());
        assertEquals(1100d, dao.getTotalSales(), 0.001d);
        assertTrue(new AccountingService(dao).isBooksBalanced());
    }

    @Test
    public void legacySaleAndExpenseWithoutJournalsAreCountedForReview() {
        FinanceDao dao = database.financeDao();
        Sale legacySale = new Sale();
        legacySale.setItemName("Legacy item");
        legacySale.setQuantitySold(1);
        legacySale.setUnitPrice(1200d);
        legacySale.setTotalAmount(1200d);
        legacySale.setSaleDate(new Date());
        dao.insertSale(legacySale);
        dao.insertExpense(newExpense("business_operational", 250d, "Legacy expense"));

        assertEquals(1, dao.getLegacySaleCount());
        assertEquals(1, dao.getLegacyExpenseCount());
    }

    @Test
    public void householdExpensePostsToOwnerDrawingsNotOperatingExpenses() {
        FinanceDao dao = database.financeDao();
        Expense expense = newExpense("household", 250d, "Groceries");

        new FinanceTransactionService(database).recordExpense(expense);

        JournalEntry journal = dao.getJournalBySource("EXPENSE", expense.getId());
        assertNotNull(journal);
        assertEquals(250d, dao.getAccountNetBalance(AccountCodes.OWNER_DRAWINGS), 0.001d);
        assertEquals(0d, dao.getAccountNetBalance(AccountCodes.OPERATING_EXPENSE), 0.001d);
        assertTrue(new AccountingService(dao).isBooksBalanced());
    }

    @Test
    public void manualReceivableCreationPostsLinkedBalancedEntry() {
        FinanceDao dao = database.financeDao();
        AccountReceivable receivable = new AccountReceivable();
        receivable.setCustomerName("Amina Namusoke");
        receivable.setDescription("Custom order deposit balance");
        receivable.setAmount(800d);
        receivable.setInvoiceDate(new Date());
        receivable.setDueDate(new Date());

        new FinanceTransactionService(database).recordManualReceivable(receivable);

        AccountReceivable saved = dao.getReceivableById(receivable.getId());
        assertEquals("OPEN", saved.getStatus());
        assertEquals(800d, dao.getAccountNetBalance(AccountCodes.ACCOUNTS_RECEIVABLE), 0.001d);
        assertEquals(-800d, dao.getAccountNetBalance(AccountCodes.SALES_REVENUE), 0.001d);
        JournalEntry journal = dao.getJournalEntryById(saved.getJournalEntryId());
        assertEquals(800d, dao.getTotalDebitForEntry(journal.getId()), 0.001d);
        assertEquals(800d, dao.getTotalCreditForEntry(journal.getId()), 0.001d);
    }

    @Test
    public void manualPayableCreationPostsLinkedBalancedEntry() {
        FinanceDao dao = database.financeDao();
        AccountPayable payable = new AccountPayable();
        payable.setVendorName("Local supplier");
        payable.setDescription("Shop packaging");
        payable.setAmount(500d);
        payable.setBillDate(new Date());
        payable.setDueDate(new Date());

        new FinanceTransactionService(database).recordManualPayable(payable);

        AccountPayable saved = dao.getPayableById(payable.getId());
        assertEquals("OPEN", saved.getStatus());
        assertEquals(500d, dao.getAccountNetBalance(AccountCodes.OPERATING_EXPENSE), 0.001d);
        assertEquals(-500d, dao.getAccountNetBalance(AccountCodes.ACCOUNTS_PAYABLE), 0.001d);
        JournalEntry journal = dao.getJournalEntryById(saved.getJournalEntryId());
        assertEquals(500d, dao.getTotalDebitForEntry(journal.getId()), 0.001d);
        assertEquals(500d, dao.getTotalCreditForEntry(journal.getId()), 0.001d);
    }

    @Test
    public void manualReceivableVoidReversesJournalAndKeepsRecord() {
        FinanceDao dao = database.financeDao();
        AccountReceivable receivable = new AccountReceivable();
        receivable.setCustomerName("Amina Namusoke");
        receivable.setDescription("Custom order");
        receivable.setAmount(800d);
        receivable.setInvoiceDate(new Date());
        receivable.setDueDate(new Date());
        FinanceTransactionService transactions = new FinanceTransactionService(database);
        long receivableId = transactions.recordManualReceivable(receivable);

        transactions.voidReceivable((int) receivableId, "Order cancelled", false);

        assertEquals("VOID", dao.getReceivableById((int) receivableId).getStatus());
        assertEquals(2, dao.getAllJournalEntries().size());
        assertEquals(0d, dao.getTotalReceivableBalance(), 0.001d);
        assertTrue(new AccountingService(dao).isBooksBalanced());
    }

    @Test
    public void manualReceivableEditCreatesReplacementAndRetainsOriginal() {
        FinanceDao dao = database.financeDao();
        AccountReceivable receivable = new AccountReceivable();
        receivable.setCustomerName("Amina Namusoke");
        receivable.setDescription("Custom order");
        receivable.setAmount(800d);
        receivable.setInvoiceDate(new Date());
        receivable.setDueDate(new Date());
        FinanceTransactionService transactions = new FinanceTransactionService(database);
        long originalId = transactions.recordManualReceivable(receivable);
        AccountReceivable replacement = new AccountReceivable();
        replacement.setCustomerName("Amina Namusoke");
        replacement.setDescription("Updated custom order");
        replacement.setAmount(950d);
        replacement.setInvoiceDate(new Date());
        replacement.setDueDate(new Date());

        long replacementId = transactions.replaceManualReceivable((int) originalId, replacement);

        assertEquals("VOID", dao.getReceivableById((int) originalId).getStatus());
        assertEquals("OPEN", dao.getReceivableById((int) replacementId).getStatus());
        assertEquals(950d, dao.getReceivableById((int) replacementId).getAmount(), 0.001d);
        assertEquals(3, dao.getAllJournalEntries().size());
        assertTrue(new AccountingService(dao).isBooksBalanced());
    }

    @Test
    public void manualPayableVoidReversesJournalAndKeepsRecord() {
        FinanceDao dao = database.financeDao();
        AccountPayable payable = new AccountPayable();
        payable.setVendorName("Local supplier");
        payable.setDescription("Packaging");
        payable.setAmount(500d);
        payable.setBillDate(new Date());
        payable.setDueDate(new Date());
        FinanceTransactionService transactions = new FinanceTransactionService(database);
        long payableId = transactions.recordManualPayable(payable);

        transactions.voidPayable((int) payableId, "Bill cancelled", false);

        assertEquals("VOID", dao.getPayableById((int) payableId).getStatus());
        assertEquals(2, dao.getAllJournalEntries().size());
        assertEquals(0d, dao.getTotalPayableBalance(), 0.001d);
        assertTrue(new AccountingService(dao).isBooksBalanced());
    }

    @Test
    public void manualPayableEditCreatesReplacementAndRetainsOriginal() {
        FinanceDao dao = database.financeDao();
        AccountPayable payable = new AccountPayable();
        payable.setVendorName("Local supplier");
        payable.setDescription("Packaging");
        payable.setAmount(500d);
        payable.setBillDate(new Date());
        payable.setDueDate(new Date());
        FinanceTransactionService transactions = new FinanceTransactionService(database);
        long originalId = transactions.recordManualPayable(payable);
        AccountPayable replacement = new AccountPayable();
        replacement.setVendorName("Local supplier");
        replacement.setDescription("Updated packaging");
        replacement.setAmount(750d);
        replacement.setBillDate(new Date());
        replacement.setDueDate(new Date());

        long replacementId = transactions.replaceManualPayable((int) originalId, replacement);

        assertEquals("VOID", dao.getPayableById((int) originalId).getStatus());
        assertEquals("OPEN", dao.getPayableById((int) replacementId).getStatus());
        assertEquals(750d, dao.getPayableById((int) replacementId).getAmount(), 0.001d);
        assertEquals(3, dao.getAllJournalEntries().size());
        assertTrue(new AccountingService(dao).isBooksBalanced());
    }

    @Test
    public void receivablePaymentJournalFailureRollsBackPaymentAndBalance() {
        FinanceDao dao = database.financeDao();
        AccountReceivable receivable = new AccountReceivable();
        receivable.setCustomerName("Amina Namusoke");
        receivable.setDescription("Balance");
        receivable.setAmount(1000d);
        receivable.setInvoiceDate(new Date());
        receivable.setDueDate(new Date());
        receivable.setStatus("OPEN");
        long receivableId = dao.insertReceivable(receivable);
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_payment_journal BEFORE INSERT ON journal_lines "
                        + "BEGIN SELECT RAISE(ABORT, 'injected payment journal failure'); END");

        try {
            new FinanceTransactionService(database).recordReceivablePayment(
                    (int) receivableId, 300d, "Cash", new Date(), null);
            fail("injected journal failure should abort payment");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage() != null);
        }

        assertEquals(0d, dao.getReceivableById((int) receivableId).getAmountPaid(), 0.001d);
        assertTrue(dao.getReceivablePayments((int) receivableId).isEmpty());
        assertTrue(dao.getAllJournalEntries().isEmpty());
    }

    @Test
    public void payablePaymentCreatesHistoryAndBalancedJournal() {
        FinanceDao dao = database.financeDao();
        AccountPayable payable = new AccountPayable();
        payable.setVendorName("Local supplier");
        payable.setDescription("Packaging");
        payable.setAmount(500d);
        payable.setBillDate(new Date());
        payable.setDueDate(new Date());
        payable.setStatus("OPEN");
        long payableId = dao.insertPayable(payable);

        long journalId = new FinanceTransactionService(database)
                .recordPayablePayment((int) payableId, 200d, "Mobile Money", new Date(), "MM-001");

        assertEquals(1, dao.getPayablePayments((int) payableId).size());
        assertEquals(300d, dao.getPayableById((int) payableId).getBalanceDue(), 0.001d);
        assertEquals(200d, dao.getTotalDebitForEntry((int) journalId), 0.001d);
        assertEquals(200d, dao.getTotalCreditForEntry((int) journalId), 0.001d);
    }

    @Test
    public void mtnAndAirtelMethodsMapToMobileMoneyAccount() {
        AccountingService accounting = new AccountingService(database.financeDao());

        assertEquals(AccountCodes.MOBILE_MONEY, accounting.paymentAccountId("MTN MoMo"));
        assertEquals(AccountCodes.MOBILE_MONEY, accounting.paymentAccountId("Airtel Money"));
    }

    @Test
    public void mobileMoneySaleRequiresProviderTransactionReference() {
        FinanceDao dao = database.financeDao();
        InventoryItem item = newInventoryItem();
        long itemId = dao.insertInventoryItem(item);
        Sale sale = new Sale();
        sale.setInventoryItemId((int) itemId);
        sale.setQuantitySold(1);
        sale.setUnitPrice(1200d);
        sale.setAmountPaid(1200d);
        sale.setPaymentMethod("MTN MoMo");
        sale.setSaleDate(new Date());

        try {
            new FinanceTransactionService(database).recordSale(sale);
            fail("mobile money sale without a provider reference must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("reference"));
        }

        assertTrue(dao.getAllSales().isEmpty());
        assertEquals(4, dao.getInventoryById((int) itemId).getQuantity());
    }

    @Test
    public void mobileMoneyReceivablePaymentRequiresProviderTransactionReference() {
        FinanceDao dao = database.financeDao();
        AccountReceivable receivable = new AccountReceivable();
        receivable.setCustomerName("Amina Namusoke");
        receivable.setAmount(1000d);
        receivable.setInvoiceDate(new Date());
        receivable.setDueDate(new Date());
        receivable.setStatus("OPEN");
        long receivableId = dao.insertReceivable(receivable);

        try {
            new FinanceTransactionService(database).recordReceivablePayment(
                    (int) receivableId, 300d, "Airtel Money", new Date(), null);
            fail("mobile money payment without a provider reference must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("reference"));
        }

        assertEquals(0d, dao.getReceivableById((int) receivableId).getAmountPaid(), 0.001d);
        assertTrue(dao.getReceivablePayments((int) receivableId).isEmpty());
    }

    @Test
    public void partialShipmentReceiptAllocatesLandedCostAndRetryIsIdempotent() {
        FinanceDao dao = database.financeDao();
        Shipment shipment = new Shipment();
        shipment.setBatchId("BATCH-RECEIVE-1");
        shipment.setSourceCountry("China");
        shipment.setSupplierName("Import supplier");
        shipment.setStatus("in_transit");
        shipment.setOrderDate(new Date());
        shipment.setTotalValue(100000d);
        shipment.setShippingCost(10000d);
        shipment.setInsuranceCost(1000d);
        shipment.setImportDuty(5000d);
        shipment.setVat(2000d);
        shipment.setClearingCharges(1000d);
        shipment.setTransportToStore(1000d);
        long shipmentId = dao.insertShipment(shipment);

        InventoryItem firstItem = newInventoryItem();
        firstItem.setName("Existing product");
        firstItem.setQuantity(2);
        firstItem.setUnitCost(400d);
        firstItem.setSellingPrice(15000d);
        long firstItemId = dao.insertInventoryItem(firstItem);
        InventoryItem secondItem = newInventoryItem();
        secondItem.setName("New product");
        secondItem.setQuantity(0);
        secondItem.setUnitCost(0d);
        secondItem.setSellingPrice(40000d);
        long secondItemId = dao.insertInventoryItem(secondItem);

        ShipmentLine firstLine = shipmentLine((int) shipmentId, (int) firstItemId, 4, 100d, "USD", 100d);
        ShipmentLine secondLine = shipmentLine((int) shipmentId, (int) secondItemId, 2, 300d, "USD", 100d);
        long firstLineId = new FinanceTransactionService(database).addShipmentLine(firstLine);
        long secondLineId = new FinanceTransactionService(database).addShipmentLine(secondLine);
        assertEquals(100000d, dao.getShipmentById((int) shipmentId).getTotalValue(), 0.001d);
        Map<Integer, Integer> receiptQuantities = new HashMap<>();
        receiptQuantities.put((int) firstLineId, 2);
        receiptQuantities.put((int) secondLineId, 1);

        FinanceTransactionService transactions = new FinanceTransactionService(database);
        long receiptId = transactions.receiveShipment((int) shipmentId, receiptQuantities,
                new Date(), "RECEIPT-001");

        InventoryItem receivedFirst = dao.getInventoryById((int) firstItemId);
        InventoryItem receivedSecond = dao.getInventoryById((int) secondItemId);
        assertEquals(4, receivedFirst.getQuantity());
        assertEquals(5200d, receivedFirst.getUnitCost(), 0.001d);
        assertEquals(600d, receivedFirst.getShippingCost(), 0.001d);
        assertEquals(350d, receivedFirst.getImportDuty(), 0.001d);
        assertEquals(50d, receivedFirst.getClearingCost(), 0.001d);
        assertEquals(1, receivedSecond.getQuantity());
        assertEquals(30000d, receivedSecond.getUnitCost(), 0.001d);
        assertEquals(3600d, receivedSecond.getShippingCost(), 0.001d);
        assertEquals(2100d, receivedSecond.getImportDuty(), 0.001d);
        assertEquals(300d, receivedSecond.getClearingCost(), 0.001d);
        assertEquals(60800d, dao.getTotalInventoryValue(), 0.001d);

        ShipmentReceipt receipt = dao.getShipmentReceiptById((int) receiptId);
        assertEquals(50000d, receipt.getGoodsValueUgx(), 0.001d);
        assertEquals(10000d, receipt.getAllocatedChargesUgx(), 0.001d);
        assertEquals(60000d, receipt.getTotalLandedCostUgx(), 0.001d);
        assertEquals(2, dao.getShipmentReceiptLines((int) receiptId).size());
        assertEquals("partially_received", dao.getShipmentById((int) shipmentId).getStatus());
        AccountPayable payable = dao.getPayableById(receipt.getPayableId());
        assertEquals(60000d, payable.getAmount(), 0.001d);
        assertEquals(60000d, dao.getTotalDebitForEntry(receipt.getJournalEntryId()), 0.001d);
        assertEquals(60000d, dao.getTotalCreditForEntry(receipt.getJournalEntryId()), 0.001d);

        long retryReceiptId = transactions.receiveShipment((int) shipmentId, receiptQuantities,
                new Date(), "RECEIPT-001");
        assertEquals(receiptId, retryReceiptId);
        assertEquals(4, dao.getInventoryById((int) firstItemId).getQuantity());
        assertEquals(1, dao.getInventoryById((int) secondItemId).getQuantity());
        assertEquals(1, dao.getShipmentReceipts((int) shipmentId).size());
    }

    @Test
    public void shipmentJournalFailureRollsBackReceiptStockAndPayable() {
        FinanceDao dao = database.financeDao();
        Shipment shipment = new Shipment();
        shipment.setBatchId("BATCH-ROLLBACK");
        shipment.setSourceCountry("Turkey");
        shipment.setSupplierName("Test supplier");
        shipment.setStatus("in_transit");
        shipment.setTotalValue(1000d);
        long shipmentId = dao.insertShipment(shipment);
        InventoryItem item = newInventoryItem();
        item.setQuantity(0);
        item.setUnitCost(0);
        long itemId = dao.insertInventoryItem(item);
        ShipmentLine line = shipmentLine((int) shipmentId, (int) itemId, 1, 10d, "USD", 100d);
        long lineId = new FinanceTransactionService(database).addShipmentLine(line);
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_shipment_journal BEFORE INSERT ON journal_lines "
                        + "BEGIN SELECT RAISE(ABORT, 'injected shipment journal failure'); END");
        Map<Integer, Integer> quantities = new HashMap<>();
        quantities.put((int) lineId, 1);

        try {
            new FinanceTransactionService(database).receiveShipment(
                    (int) shipmentId, quantities, new Date(), "RECEIPT-ROLLBACK");
            fail("injected journal failure should abort receiving");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage() != null);
        }

        assertEquals(0, dao.getInventoryById((int) itemId).getQuantity());
        assertEquals(0, dao.getShipmentLineById((int) lineId).getQuantityStocked());
        assertTrue(dao.getShipmentReceipts((int) shipmentId).isEmpty());
        assertTrue(dao.getAllPayables().isEmpty());
        assertTrue(dao.getAllJournalEntries().isEmpty());
    }

    @Test
    public void shipmentLineRejectsInvalidExchangeRateWithoutChangingShipment() {
        FinanceDao dao = database.financeDao();
        Shipment shipment = new Shipment();
        shipment.setBatchId("BATCH-FX");
        shipment.setStatus("ordered");
        long shipmentId = dao.insertShipment(shipment);
        InventoryItem item = newInventoryItem();
        long itemId = dao.insertInventoryItem(item);
        ShipmentLine line = shipmentLine((int) shipmentId, (int) itemId, 2, 10d, "USD", 0d);

        try {
            new FinanceTransactionService(database).addShipmentLine(line);
            fail("zero exchange rate must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("exchange rate"));
        }

        assertTrue(dao.getShipmentLines((int) shipmentId).isEmpty());
        assertEquals(0d, dao.getShipmentById((int) shipmentId).getTotalValue(), 0.001d);
    }

    @Test
    public void shipmentCompositionCannotChangeAfterFirstReceipt() {
        FinanceDao dao = database.financeDao();
        Shipment shipment = new Shipment();
        shipment.setBatchId("BATCH-LOCK");
        shipment.setStatus("in_transit");
        long shipmentId = dao.insertShipment(shipment);
        InventoryItem firstItem = newInventoryItem();
        firstItem.setQuantity(0);
        firstItem.setUnitCost(0);
        long firstItemId = dao.insertInventoryItem(firstItem);
        InventoryItem secondItem = newInventoryItem();
        secondItem.setQuantity(0);
        secondItem.setUnitCost(0);
        long secondItemId = dao.insertInventoryItem(secondItem);
        FinanceTransactionService transactions = new FinanceTransactionService(database);
        ShipmentLine firstLine = shipmentLine((int) shipmentId, (int) firstItemId, 2, 10d, "UGX", 1d);
        long firstLineId = transactions.addShipmentLine(firstLine);
        Map<Integer, Integer> quantities = new HashMap<>();
        quantities.put((int) firstLineId, 1);
        transactions.receiveShipment((int) shipmentId, quantities, new Date(), "BATCH-LOCK-RCV1");

        try {
            transactions.addShipmentLine(
                    shipmentLine((int) shipmentId, (int) secondItemId, 1, 20d, "UGX", 1d));
            fail("shipment composition must be locked after receiving begins");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("not open"));
        }

        assertEquals(1, dao.getShipmentLines((int) shipmentId).size());
        assertEquals(1, dao.getShipmentReceipts((int) shipmentId).size());
    }

    @Test
    public void failedExpensePostingRollsBackExpenseAndJournal() {
        FinanceDao dao = database.financeDao();
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_test_journal_lines BEFORE INSERT ON journal_lines "
                        + "BEGIN SELECT RAISE(ABORT, 'injected journal failure'); END");

        Expense expense = new Expense();
        expense.setCategory("business_operational");
        expense.setSubCategory("Shop Supplies");
        expense.setDescription("Test supplies");
        expense.setAmount(250d);
        expense.setCurrency("UGX");
        expense.setDate(new Date());
        expense.setPaymentMethod("Cash");

        try {
            new ExpenseAccountingHelper(database).saveNew(expense);
            fail("injected journal failure should abort expense posting");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage() != null);
        }

        assertTrue("failed posting must not leave an expense", dao.getAllExpenses().isEmpty());
        assertTrue("failed posting must not leave a journal", dao.getAllJournalEntries().isEmpty());
    }

    @Test
    public void depositSaleSplitsCashAndReceivableAndRecordsDeposit() {
        FinanceDao dao = database.financeDao();
        InventoryItem item = newInventoryItem();
        long itemId = dao.insertInventoryItem(item);
        Sale sale = new Sale();
        sale.setInventoryItemId((int) itemId);
        sale.setQuantitySold(1);
        sale.setUnitPrice(1200d);
        sale.setPaymentMethod("Cash");
        sale.setAmountPaid(400d);
        sale.setCustomerName("Amina Namusoke");
        sale.setCustomerPhone("0770000000");
        sale.setSaleDate(new Date());

        new FinanceTransactionService(database).recordSale(sale);

        Sale savedSale = dao.getSaleById(sale.getId());
        AccountReceivable receivable = dao.getReceivableById(savedSale.getReceivableId());
        assertNotNull("a partial deposit must create a linked receivable", receivable);
        assertEquals(1200d, receivable.getAmount(), 0.001d);
        assertEquals(400d, receivable.getAmountPaid(), 0.001d);
        assertEquals(800d, receivable.getBalanceDue(), 0.001d);
        assertEquals(1, dao.getReceivablePayments(receivable.getId()).size());

        JournalEntry journal = dao.getJournalEntryById(savedSale.getJournalEntryId());
        assertEquals(1700d, dao.getTotalDebitForEntry(journal.getId()), 0.001d);
        assertEquals(1700d, dao.getTotalCreditForEntry(journal.getId()), 0.001d);
    }

    @Test
    public void receivablePaymentStoresMethodAmountAndBalancedJournalLink() {
        FinanceDao dao = database.financeDao();
        AccountReceivable receivable = new AccountReceivable();
        receivable.setCustomerName("Amina Namusoke");
        receivable.setDescription("Item balance");
        receivable.setAmount(1000d);
        receivable.setAmountPaid(200d);
        receivable.setInvoiceDate(new Date());
        receivable.setDueDate(new Date());
        receivable.setStatus("PARTIAL");
        long receivableId = dao.insertReceivable(receivable);
        receivable.setId((int) receivableId);

        long journalId = new AccountingService(dao)
                .recordReceivablePayment(receivable, 300d, "Mobile Money");

        assertEquals(1, dao.getReceivablePayments((int) receivableId).size());
        ReceivablePayment payment = dao.getReceivablePayments((int) receivableId).get(0);
        assertEquals(300d, payment.getAmount(), 0.001d);
        assertEquals("Mobile Money", payment.getPaymentMethod());
        assertEquals(journalId, payment.getJournalEntryId());
        assertEquals(500d, dao.getReceivableById((int) receivableId).getBalanceDue(), 0.001d);
        assertEquals(300d, dao.getTotalDebitForEntry((int) journalId), 0.001d);
        assertEquals(300d, dao.getTotalCreditForEntry((int) journalId), 0.001d);
    }

    @Test
    public void deletingSaleFromHistoryRestoresStockAndKeepsSaleForAudit() throws Exception {
        FinanceDao dao = database.financeDao();
        InventoryItem item = newInventoryItem();
        long itemId = dao.insertInventoryItem(item);
        Sale sale = new Sale();
        sale.setInventoryItemId((int) itemId);
        sale.setQuantitySold(1);
        sale.setUnitPrice(1200d);
        sale.setAmountPaid(1200d);
        sale.setPaymentMethod("Cash");
        sale.setSaleDate(new Date());
        long saleId = new FinanceTransactionService(database).recordSale(sale);
        assertEquals(3, dao.getInventoryById((int) itemId).getQuantity());

        salesActivityController = Robolectric.buildActivity(SalesActivity.class).setup();
        SalesActivity activity = salesActivityController.get();
        java.lang.reflect.Method showSaleDetail = SalesActivity.class
                .getDeclaredMethod("showSaleDetail", Sale.class);
        showSaleDetail.setAccessible(true);
        showSaleDetail.invoke(activity, dao.getSaleById((int) saleId));
            new FinanceTransactionService(database).voidSale(
                (int) saleId, "Customer cancellation", true);

        assertEquals(4, dao.getInventoryById((int) itemId).getQuantity());
        assertEquals("VOID", dao.getSaleById((int) saleId).getStatus());
        assertEquals(2, dao.getAllJournalEntries().size());
        assertEquals(0d, dao.getTotalSales(), 0.001d);
        assertEquals(0d, dao.getTotalProfit(), 0.001d);
    }

    @Test
    public void deletingExpenseVoidsItAndRetainsBalancedJournalHistory() {
        FinanceDao dao = database.financeDao();
        Expense expense = newExpense("household", 250d, "Groceries");
        ExpenseAccountingHelper helper = new ExpenseAccountingHelper(database);
        helper.saveNew(expense);
        int originalJournalId = expense.getJournalEntryId();

        helper.delete(expense);

        Expense storedExpense = dao.getExpenseById(expense.getId());
        assertNotNull("voided expense must remain available for audit", storedExpense);
        assertEquals("VOID", storedExpense.getStatus());
        assertEquals(2, dao.getAllJournalEntries().size());
        assertEquals(250d, dao.getTotalDebitForEntry(originalJournalId), 0.001d);
        assertEquals(250d, dao.getTotalCreditForEntry(originalJournalId), 0.001d);
        assertEquals(0d, dao.getTotalByCategory("household"), 0.001d);
        assertTrue(new AccountingService(dao).isBooksBalanced());
    }

    @Test
    public void editingExpenseCreatesReplacementAndPreservesOriginal() {
        FinanceDao dao = database.financeDao();
        Expense expense = newExpense("business_operational", 250d, "Shop supplies");
        ExpenseAccountingHelper helper = new ExpenseAccountingHelper(database);
        helper.saveNew(expense);
        int originalId = expense.getId();
        int originalJournalId = expense.getJournalEntryId();
        expense.setAmount(400d);
        expense.setDescription("Updated shop supplies");

        helper.update(expense);

        Expense original = dao.getExpenseById(originalId);
        assertEquals("VOID", original.getStatus());
        assertEquals(250d, original.getAmount(), 0.001d);
        assertEquals(2, dao.getAllExpenses().size());
        Expense replacement = dao.getAllExpenses().stream()
                .filter(value -> value.getId() != originalId)
                .filter(value -> "POSTED".equals(value.getStatus()))
                .findFirst().orElse(null);
        assertNotNull(replacement);
        assertEquals(400d, replacement.getAmount(), 0.001d);
        assertEquals("Updated shop supplies", replacement.getDescription());
        assertNotNull(dao.getJournalBySource("REVERSAL", originalJournalId));
        assertTrue(new AccountingService(dao).isBooksBalanced());
    }

    private InventoryItem newInventoryItem() {
        InventoryItem item = new InventoryItem();
        item.setName("Test item");
        item.setCategory("clothing");
        item.setQuantity(4);
        item.setUnitCost(500d);
        item.setSellingPrice(1200d);
        item.setMinStockLevel(0);
        item.setDateAdded(new Date());
        return item;
    }

    private Expense newExpense(String category, double amount, String description) {
        Expense expense = new Expense();
        expense.setCategory(category);
        expense.setSubCategory("General");
        expense.setDescription(description);
        expense.setAmount(amount);
        expense.setCurrency("UGX");
        expense.setDate(new Date());
        expense.setPaymentMethod("Cash");
        return expense;
    }

    private ShipmentLine shipmentLine(int shipmentId, int inventoryItemId, int quantity,
                                      double unitCost, String currency, double exchangeRate) {
        ShipmentLine line = new ShipmentLine();
        line.setShipmentId(shipmentId);
        line.setInventoryItemId(inventoryItemId);
        line.setOrderedQuantity(quantity);
        line.setUnitCost(unitCost);
        line.setCurrencyCode(currency);
        line.setExchangeRate(exchangeRate);
        return line;
    }
}