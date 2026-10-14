package com.financeapp.uganda.database;

import androidx.room.*;
import com.financeapp.uganda.models.*;
import java.util.Date;
import java.util.List;

@Dao
public interface FinanceDao {

    // --- Expenses ---
    @Insert long insertExpense(Expense expense);
    @Update void updateExpense(Expense expense);
    @Delete void deleteExpense(Expense expense);
    @Query("SELECT * FROM expenses ORDER BY date DESC") List<Expense> getAllExpenses();
    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY date DESC") List<Expense> getExpensesByCategory(String category);
    @Query("SELECT * FROM expenses WHERE shipmentStatus = :status ORDER BY date DESC") List<Expense> getExpensesByShipmentStatus(String status);
    @Query("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE category = :category AND status = 'POSTED'") double getTotalByCategory(String category);
    @Query("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE date BETWEEN :startDate AND :endDate AND status = 'POSTED'") double getTotalExpensesBetweenDates(Date startDate, Date endDate);
    @Query("SELECT * FROM expenses WHERE importSource = :source ORDER BY date DESC") List<Expense> getExpensesByImportSource(String source);
    @Query("SELECT * FROM expenses WHERE id = :id") Expense getExpenseById(int id);

    // --- Inventory ---
    @Insert long insertInventoryItem(InventoryItem item);
    @Update void updateInventoryItem(InventoryItem item);
    @Delete void deleteInventoryItem(InventoryItem item);
    @Query("SELECT * FROM inventory ORDER BY dateAdded DESC") List<InventoryItem> getAllInventory();
    @Query("SELECT * FROM inventory WHERE id = :id") InventoryItem getInventoryById(int id);
    @Query("SELECT * FROM inventory WHERE quantity <= minStockLevel") List<InventoryItem> getLowStockItems();
    @Query("SELECT * FROM inventory WHERE category = :category") List<InventoryItem> getInventoryByCategory(String category);
    @Query("SELECT * FROM inventory WHERE importSource = :source") List<InventoryItem> getInventoryBySource(String source);
    @Query("SELECT COALESCE(SUM(quantity * (unitCost + shippingCost + importDuty + clearingCost)),0) FROM inventory") double getTotalInventoryValue();
    @Query("SELECT SUM(quantity) FROM inventory") int getTotalStockCount();

    // --- Shipments ---
    @Insert long insertShipment(Shipment shipment);
    @Update void updateShipment(Shipment shipment);
    @Delete void deleteShipment(Shipment shipment);
    @Query("SELECT * FROM shipments ORDER BY orderDate DESC") List<Shipment> getAllShipments();
    @Query("SELECT * FROM shipments WHERE status = :status") List<Shipment> getShipmentsByStatus(String status);
    @Query("SELECT * FROM shipments WHERE sourceCountry = :country") List<Shipment> getShipmentsBySource(String country);
    @Query("SELECT * FROM shipments WHERE batchId = :batchId") Shipment getShipmentByBatchId(String batchId);
    @Query("SELECT * FROM shipments WHERE id = :id") Shipment getShipmentById(int id);
    @Insert long insertShipmentLine(ShipmentLine line);
    @Update void updateShipmentLine(ShipmentLine line);
    @Query("SELECT * FROM shipment_lines WHERE id = :id") ShipmentLine getShipmentLineById(int id);
    @Query("SELECT * FROM shipment_lines WHERE shipmentId = :shipmentId ORDER BY id") List<ShipmentLine> getShipmentLines(int shipmentId);
    @Insert long insertShipmentReceipt(ShipmentReceipt receipt);
    @Insert void insertShipmentReceiptLines(List<ShipmentReceiptLine> lines);
    @Query("SELECT * FROM shipment_receipts WHERE id = :id") ShipmentReceipt getShipmentReceiptById(int id);
    @Query("SELECT * FROM shipment_receipts WHERE reference = :reference LIMIT 1") ShipmentReceipt getShipmentReceiptByReference(String reference);
    @Query("SELECT * FROM shipment_receipts WHERE shipmentId = :shipmentId ORDER BY receivedAt, id") List<ShipmentReceipt> getShipmentReceipts(int shipmentId);
    @Query("SELECT * FROM shipment_receipt_lines WHERE receiptId = :receiptId ORDER BY id") List<ShipmentReceiptLine> getShipmentReceiptLines(int receiptId);
        @Query("SELECT receipt_lines.* FROM shipment_receipt_lines AS receipt_lines "
            + "INNER JOIN shipment_receipts AS receipts ON receipts.id = receipt_lines.receiptId "
            + "WHERE receipt_lines.shipmentLineId = :shipmentLineId ORDER BY receipts.receivedAt, receipt_lines.id")
        List<ShipmentReceiptLine> getShipmentReceiptLinesForShipmentLine(int shipmentLineId);

    // --- Sales ---
    @Insert long insertSale(Sale sale);
    @Update void updateSale(Sale sale);
    @Delete void deleteSale(Sale sale);
    @Query("SELECT * FROM sales ORDER BY saleDate DESC") List<Sale> getAllSales();
    @Query("SELECT * FROM sales WHERE id = :id") Sale getSaleById(int id);
    @Query("SELECT * FROM sales WHERE saleDate BETWEEN :startDate AND :endDate AND status = 'POSTED'") List<Sale> getSalesBetweenDates(Date startDate, Date endDate);
    @Query("SELECT COALESCE(SUM(totalAmount),0) FROM sales WHERE saleDate BETWEEN :startDate AND :endDate AND status = 'POSTED'") double getTotalSalesBetweenDates(Date startDate, Date endDate);
    @Query("SELECT COALESCE(SUM(profit),0) FROM sales WHERE saleDate BETWEEN :startDate AND :endDate AND status = 'POSTED'") double getTotalProfitBetweenDates(Date startDate, Date endDate);
    @Query("SELECT COALESCE(SUM(totalAmount),0) FROM sales WHERE status = 'POSTED'") double getTotalSales();
    @Query("SELECT COALESCE(SUM(profit),0) FROM sales WHERE status = 'POSTED'") double getTotalProfit();
        @Query("SELECT COUNT(*) FROM sales AS sale WHERE sale.status = 'POSTED' AND "
            + "(sale.journalEntryId = 0 OR NOT EXISTS "
            + "(SELECT 1 FROM journal_entries AS journal WHERE journal.id = sale.journalEntryId))")
        int getLegacySaleCount();
        @Query("SELECT COUNT(*) FROM expenses AS expense WHERE expense.status = 'POSTED' AND "
            + "(expense.journalEntryId = 0 OR NOT EXISTS "
            + "(SELECT 1 FROM journal_entries AS journal WHERE journal.id = expense.journalEntryId))")
        int getLegacyExpenseCount();

    // --- Chart of Accounts ---
    @Insert void insertAccount(Account account);
    @Insert void insertAccounts(List<Account> accounts);
    @Update void updateAccount(Account account);
    @Query("SELECT * FROM accounts ORDER BY code") List<Account> getAllAccounts();
    @Query("SELECT * FROM accounts WHERE id = :id") Account getAccountById(int id);
    @Query("SELECT COUNT(*) FROM accounts") int getAccountCount();

    // --- Journal ---
    @Insert long insertJournalEntry(JournalEntry entry);
    @Update void updateJournalEntry(JournalEntry entry);
    @Delete void deleteJournalEntry(JournalEntry entry);
    @Query("SELECT * FROM journal_entries ORDER BY date DESC, id DESC") List<JournalEntry> getAllJournalEntries();
    @Query("SELECT * FROM journal_entries WHERE id = :id") JournalEntry getJournalEntryById(int id);
    @Query("SELECT * FROM journal_entries WHERE sourceType = :type AND sourceId = :sourceId LIMIT 1") JournalEntry getJournalBySource(String type, int sourceId);
    @Query("SELECT * FROM journal_entries WHERE sourceType = 'CAPITAL' LIMIT 1") JournalEntry getCapitalEntry();

    @Insert long insertJournalLine(JournalLine line);
    @Insert void insertJournalLines(List<JournalLine> lines);
    @Update void updateJournalLine(JournalLine line);
    @Delete void deleteJournalLine(JournalLine line);
    @Query("DELETE FROM journal_lines WHERE journalEntryId = :entryId") void deleteLinesForEntry(int entryId);
    @Query("SELECT * FROM journal_lines WHERE journalEntryId = :entryId") List<JournalLine> getLinesForEntry(int entryId);
    @Query("SELECT * FROM journal_lines WHERE accountId = :accountId ORDER BY id DESC") List<JournalLine> getLinesForAccount(int accountId);
    @Query("SELECT COALESCE(SUM(debit),0) FROM journal_lines WHERE journalEntryId = :entryId") double getTotalDebitForEntry(int entryId);
    @Query("SELECT COALESCE(SUM(credit),0) FROM journal_lines WHERE journalEntryId = :entryId") double getTotalCreditForEntry(int entryId);
    @Query("SELECT COALESCE(SUM(debit),0) - COALESCE(SUM(credit),0) FROM journal_lines WHERE accountId = :accountId") double getAccountNetBalance(int accountId);

    // --- Accounts Receivable ---
    @Insert long insertReceivable(AccountReceivable ar);
    @Update void updateReceivable(AccountReceivable ar);
    @Delete void deleteReceivable(AccountReceivable ar);
    @Query("SELECT * FROM accounts_receivable ORDER BY invoiceDate DESC") List<AccountReceivable> getAllReceivables();
    @Query("SELECT * FROM accounts_receivable WHERE status IN ('OPEN','PARTIAL') ORDER BY dueDate") List<AccountReceivable> getOpenReceivables();
    @Query("SELECT * FROM accounts_receivable WHERE id = :id") AccountReceivable getReceivableById(int id);
    @Query("SELECT COALESCE(SUM(amount - amountPaid),0) FROM accounts_receivable WHERE status IN ('OPEN','PARTIAL')") double getTotalReceivableBalance();
    @Insert long insertReceivablePayment(ReceivablePayment payment);
    @Update void updateReceivablePayment(ReceivablePayment payment);
    @Query("SELECT * FROM receivable_payments WHERE receivableId = :receivableId ORDER BY paidAt, id") List<ReceivablePayment> getReceivablePayments(int receivableId);

    // --- Accounts Payable ---
    @Insert long insertPayable(AccountPayable ap);
    @Update void updatePayable(AccountPayable ap);
    @Delete void deletePayable(AccountPayable ap);
    @Query("SELECT * FROM accounts_payable ORDER BY billDate DESC") List<AccountPayable> getAllPayables();
    @Query("SELECT * FROM accounts_payable WHERE status IN ('OPEN','PARTIAL') ORDER BY dueDate") List<AccountPayable> getOpenPayables();
    @Query("SELECT * FROM accounts_payable WHERE id = :id") AccountPayable getPayableById(int id);
    @Query("SELECT COALESCE(SUM(amount - amountPaid),0) FROM accounts_payable WHERE status IN ('OPEN','PARTIAL')") double getTotalPayableBalance();
    @Insert long insertPayablePayment(PayablePayment payment);
    @Update void updatePayablePayment(PayablePayment payment);
    @Query("SELECT * FROM payable_payments WHERE payableId = :payableId ORDER BY paidAt, id") List<PayablePayment> getPayablePayments(int payableId);
}
