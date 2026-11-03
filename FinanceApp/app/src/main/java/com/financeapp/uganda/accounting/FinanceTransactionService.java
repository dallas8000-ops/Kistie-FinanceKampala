package com.financeapp.uganda.accounting;

import android.content.Context;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.database.FinanceDao;
import com.financeapp.uganda.models.Expense;
import com.financeapp.uganda.models.InventoryItem;
import com.financeapp.uganda.models.AccountReceivable;
import com.financeapp.uganda.models.AccountPayable;
import com.financeapp.uganda.models.JournalEntry;
import com.financeapp.uganda.models.JournalLine;
import com.financeapp.uganda.models.ReceivablePayment;
import com.financeapp.uganda.models.PayablePayment;
import com.financeapp.uganda.models.Sale;
import com.financeapp.uganda.models.Shipment;
import com.financeapp.uganda.models.ShipmentLine;
import com.financeapp.uganda.models.ShipmentReceipt;
import com.financeapp.uganda.models.ShipmentReceiptLine;
import com.financeapp.uganda.models.SyncOutboxEntry;
import com.financeapp.uganda.sync.FinanceSyncScheduler;
import com.financeapp.uganda.utils.PaymentMethods;
import com.google.gson.Gson;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class FinanceTransactionService {

    private final AppDatabase database;
    private final FinanceDao dao;
    private final AccountingService accounting;
    private final Context appContext;

    public FinanceTransactionService(AppDatabase database) {
        this(null, database);
    }

    public FinanceTransactionService(Context context, AppDatabase database) {
        this.appContext = context == null ? null : context.getApplicationContext();
        this.database = database;
        this.dao = database.financeDao();
        this.accounting = new AccountingService(dao);
    }

    public long recordInventoryItem(InventoryItem item) {
        if (item == null) throw new IllegalArgumentException("Inventory item is required.");
        long[] itemId = new long[1];
        database.runInTransaction(() -> {
            itemId[0] = dao.insertInventoryItem(item);
            item.setId((int) itemId[0]);
            enqueueSyncOperation("INVENTORY_ITEM_POSTED", item.getSyncId(), item);
        });
        return itemId[0];
    }

    public void deleteInventoryItem(int inventoryItemId) {
        database.runInTransaction(() -> {
            InventoryItem item = dao.getInventoryById(inventoryItemId);
            if (item == null) throw new IllegalArgumentException("Inventory item does not exist.");
            dao.deleteInventoryItem(item);
            enqueueSyncOperation("INVENTORY_ITEM_DELETED", item.getSyncId(), item);
        });
    }

    public long recordSale(Sale sale) {
        long[] saleId = new long[1];
        database.runInTransaction(() -> saleId[0] = recordSaleWithinTransaction(sale));
        return saleId[0];
    }

    public long replaceSale(int saleId, Sale replacement, boolean refundConfirmed) {
        long[] replacementId = new long[1];
        database.runInTransaction(() -> {
            voidSaleWithinTransaction(saleId, "Sale edited", refundConfirmed);
            replacement.setId(0);
            replacement.setSyncId(UUID.randomUUID().toString());
            replacement.setJournalEntryId(0);
            replacement.setReceivableId(0);
            replacement.setCostOfGoodsSold(0);
            replacement.setStatus("POSTED");
            replacement.setVoidReason(null);
            replacement.setVoidedAt(null);
            replacementId[0] = recordSaleWithinTransaction(replacement);
        });
        return replacementId[0];
    }

    public long recordExpense(Expense expense) {
        long[] expenseId = new long[1];
        database.runInTransaction(() -> {
            expenseId[0] = recordExpenseWithinTransaction(expense);
        });
        return expenseId[0];
    }

    private long recordSaleWithinTransaction(Sale sale) {
        InventoryItem item = dao.getInventoryById(sale.getInventoryItemId());
        validateSale(sale, item);

        sale.setItemName(item.getName());
        sale.setTotalAmount(sale.getUnitPrice() * sale.getQuantitySold() - sale.getDiscount());
        validatePayment(sale);
        sale.setProfit(sale.getTotalAmount() - item.getTotalCost() * sale.getQuantitySold());
        long saleId = dao.insertSale(sale);
        sale.setId((int) saleId);

        accounting.postSale(sale, item, sale.getAmountPaid());

        item.setQuantity(item.getQuantity() - sale.getQuantitySold());
        dao.updateInventoryItem(item);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sale", sale);
        payload.put("inventoryItem", item);
        payload.put("journalEntry", dao.getJournalEntryById(sale.getJournalEntryId()));
        payload.put("journalLines", dao.getLinesForEntry(sale.getJournalEntryId()));
        if (sale.getReceivableId() > 0) {
            AccountReceivable receivable = dao.getReceivableById(sale.getReceivableId());
            payload.put("receivable", receivable);
            payload.put("receivablePayments", dao.getReceivablePayments(sale.getReceivableId()));
        }
        enqueueSyncOperation("SALE_POSTED", String.valueOf(saleId), payload);
        return saleId;
    }

    public void voidExpense(int expenseId, String reason, boolean refundConfirmed) {
        database.runInTransaction(() -> voidExpenseWithinTransaction(expenseId, reason, refundConfirmed));
    }

    public long replaceExpense(int expenseId, Expense replacement, String reason, boolean refundConfirmed) {
        long[] replacementId = new long[1];
        database.runInTransaction(() -> {
            voidExpenseWithinTransaction(expenseId, reason, refundConfirmed);
            replacement.setId(0);
            replacement.setSyncId(UUID.randomUUID().toString());
            replacement.setJournalEntryId(0);
            replacement.setPayableId(0);
            replacement.setStatus("POSTED");
            replacement.setVoidReason(null);
            replacement.setVoidedAt(null);
            replacementId[0] = recordExpenseWithinTransaction(replacement);
        });
        return replacementId[0];
    }

    public long recordReceivablePayment(int receivableId, double amount, String paymentMethod,
                                        Date paidAt, String reference) {
        long[] journalId = new long[1];
        database.runInTransaction(() -> {
            AccountReceivable receivable = dao.getReceivableById(receivableId);
            if (receivable == null || "VOID".equals(receivable.getStatus())) {
                throw new IllegalArgumentException("Receivable is not available for payment.");
            }
            validatePaymentAmount(amount, receivable.getBalanceDue());
            validatePaymentMethod(paymentMethod);
            validateProviderReference(paymentMethod, reference);
            journalId[0] = accounting.recordReceivablePayment(
                    receivable, amount, paymentMethod, paidAt, reference);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("receivable", dao.getReceivableById(receivableId));
                payload.put("payments", dao.getReceivablePayments(receivableId));
                payload.put("journalEntry", dao.getJournalEntryById((int) journalId[0]));
                payload.put("journalLines", dao.getLinesForEntry((int) journalId[0]));
                enqueueSyncOperation("RECEIVABLE_PAYMENT", receivableId + ":" + journalId[0], payload);
        });
        return journalId[0];
    }

    public long recordPayablePayment(int payableId, double amount, String paymentMethod,
                                     Date paidAt, String reference) {
        long[] journalId = new long[1];
        database.runInTransaction(() -> {
            AccountPayable payable = dao.getPayableById(payableId);
            if (payable == null || "VOID".equals(payable.getStatus())) {
                throw new IllegalArgumentException("Payable is not available for payment.");
            }
            validatePaymentAmount(amount, payable.getBalanceDue());
            validatePaymentMethod(paymentMethod);
            validateProviderReference(paymentMethod, reference);
            journalId[0] = accounting.recordPayablePayment(payable, amount, paymentMethod, paidAt, reference);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("payable", dao.getPayableById(payableId));
            payload.put("payments", dao.getPayablePayments(payableId));
            payload.put("journalEntry", dao.getJournalEntryById((int) journalId[0]));
            payload.put("journalLines", dao.getLinesForEntry((int) journalId[0]));
            enqueueSyncOperation("PAYABLE_PAYMENT", payableId + ":" + journalId[0], payload);
        });
        return journalId[0];
    }

    public long recordManualReceivable(AccountReceivable receivable) {
        long[] receivableId = new long[1];
        database.runInTransaction(() -> {
            receivableId[0] = recordManualReceivableWithinTransaction(receivable);
        });
        return receivableId[0];
    }

    public long addShipmentLine(ShipmentLine line) {
        long[] lineId = new long[1];
        database.runInTransaction(() -> {
            Shipment shipment = dao.getShipmentById(line.getShipmentId());
            if (shipment == null || shipment.isDelivered() || shipment.getStockReceivedAt() != null
                    || !dao.getShipmentReceipts(shipment.getId()).isEmpty()) {
                throw new IllegalArgumentException("Shipment is not open for line changes.");
            }
            if (dao.getInventoryById(line.getInventoryItemId()) == null) {
                throw new IllegalArgumentException("Select an existing inventory item.");
            }
            if (line.getOrderedQuantity() <= 0 || line.getReceivedQuantity() != 0
                    || line.getQuantityStocked() != 0) {
                throw new IllegalArgumentException("Ordered quantity must be positive; new lines cannot be pre-received.");
            }
            if (!Double.isFinite(line.getUnitCost()) || line.getUnitCost() < 0) {
                throw new IllegalArgumentException("Unit cost must be a valid non-negative amount.");
            }
            String currency = line.getCurrencyCode() == null ? "" : line.getCurrencyCode().trim().toUpperCase(Locale.ROOT);
            if (!("UGX".equals(currency) || "USD".equals(currency)
                    || "TRY".equals(currency) || "CNY".equals(currency))) {
                throw new IllegalArgumentException("Currency must be UGX, USD, TRY, or CNY.");
            }
            if (!Double.isFinite(line.getExchangeRate()) || line.getExchangeRate() <= 0
                    || ("UGX".equals(currency) && line.getExchangeRate() != 1d)) {
                throw new IllegalArgumentException("Enter a valid UGX exchange rate; UGX items use rate 1.");
            }
            line.setCurrencyCode(currency);
            lineId[0] = dao.insertShipmentLine(line);
            line.setId((int) lineId[0]);
            double goodsValueUgx = 0;
            for (ShipmentLine existingLine : dao.getShipmentLines(shipment.getId())) {
                goodsValueUgx += existingLine.getOrderedQuantity()
                        * existingLine.getUnitCost() * existingLine.getExchangeRate();
            }
            shipment.setTotalValue(goodsValueUgx);
            dao.updateShipment(shipment);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("shipment", shipment);
            payload.put("line", line);
            payload.put("inventoryItem", dao.getInventoryById(line.getInventoryItemId()));
            enqueueSyncOperation("SHIPMENT_LINE_ADDED", String.valueOf(lineId[0]), payload);
        });
        return lineId[0];
    }

    public long receiveShipment(int shipmentId, Map<Integer, Integer> receivedQuantities,
                                Date receivedAt, String reference) {
        long[] receiptId = new long[1];
        database.runInTransaction(() -> {
            if (reference == null || reference.trim().isEmpty()) {
                throw new IllegalArgumentException("Receipt reference is required for safe retries.");
            }
            String receiptReference = reference.trim();
            ShipmentReceipt priorReceipt = dao.getShipmentReceiptByReference(receiptReference);
            if (priorReceipt != null) {
                if (priorReceipt.getShipmentId() != shipmentId) {
                    throw new IllegalArgumentException("Receipt reference is already used by another shipment.");
                }
                receiptId[0] = priorReceipt.getId();
                return;
            }
            Shipment shipment = dao.getShipmentById(shipmentId);
            if (shipment == null || shipment.isDelivered()) {
                throw new IllegalArgumentException("Shipment is not open for receiving.");
            }
            List<ShipmentLine> lines = dao.getShipmentLines(shipmentId);
            if (lines.isEmpty() || receivedQuantities == null || receivedQuantities.isEmpty()) {
                throw new IllegalArgumentException("Add shipment lines and received quantities first.");
            }

            double totalOrderValueUgx = 0;
            for (ShipmentLine line : lines) {
                totalOrderValueUgx += line.getOrderedQuantity() * line.getUnitCost() * line.getExchangeRate();
            }
            if (totalOrderValueUgx <= 0) {
                throw new IllegalArgumentException("Shipment lines must have a positive invoice value.");
            }

            double receivedGoodsValueUgx = 0;
            for (Map.Entry<Integer, Integer> requested : receivedQuantities.entrySet()) {
                ShipmentLine line = dao.getShipmentLineById(requested.getKey());
                int quantity = requested.getValue() == null ? 0 : requested.getValue();
                if (line == null || line.getShipmentId() != shipmentId) {
                    throw new IllegalArgumentException("A received line does not belong to this shipment.");
                }
                if (quantity <= 0 || quantity > line.getOrderedQuantity() - line.getQuantityStocked()) {
                    throw new IllegalArgumentException("Received quantity must be positive and no more than the remaining order.");
                }
                receivedGoodsValueUgx += quantity * line.getUnitCost() * line.getExchangeRate();
            }
            if (receivedGoodsValueUgx <= 0) {
                throw new IllegalArgumentException("Received goods must have a positive value.");
            }

            double totalShipmentCharges = shipment.getShippingCost() + shipment.getInsuranceCost()
                    + shipment.getImportDuty() + shipment.getVat() + shipment.getClearingCharges()
                    + shipment.getTransportToStore();
            double allocatedCharges = totalShipmentCharges * receivedGoodsValueUgx / totalOrderValueUgx;
            Date receiptDate = receivedAt != null ? receivedAt : new Date();
            ShipmentReceipt receipt = new ShipmentReceipt();
            receipt.setShipmentId(shipmentId);
            receipt.setReceivedAt(receiptDate);
            receipt.setGoodsValueUgx(receivedGoodsValueUgx);
            receipt.setAllocatedChargesUgx(allocatedCharges);
            receipt.setTotalLandedCostUgx(receivedGoodsValueUgx + allocatedCharges);
            receipt.setReference(receiptReference);

            double freightAndTransport = shipment.getShippingCost() + shipment.getInsuranceCost()
                    + shipment.getTransportToStore();
            double dutyAndVat = shipment.getImportDuty() + shipment.getVat();
            List<ShipmentReceiptLine> receiptLines = new java.util.ArrayList<>();
            List<JournalLine> journalLines = new java.util.ArrayList<>();
            for (Map.Entry<Integer, Integer> requested : receivedQuantities.entrySet()) {
                ShipmentLine line = dao.getShipmentLineById(requested.getKey());
                int quantity = requested.getValue();
                double goodsValue = quantity * line.getUnitCost() * line.getExchangeRate();
                double lineShare = goodsValue / totalOrderValueUgx;
                double shippingAllocation = freightAndTransport * lineShare;
                double dutyAllocation = dutyAndVat * lineShare;
                double clearingAllocation = shipment.getClearingCharges() * lineShare;
                double landedPerUnit = (goodsValue + shippingAllocation + dutyAllocation + clearingAllocation) / quantity;

                InventoryItem item = dao.getInventoryById(line.getInventoryItemId());
                if (item == null) throw new IllegalStateException("A shipment product was removed from inventory.");
                int oldQuantity = item.getQuantity();
                int newQuantity = oldQuantity + quantity;
                blendInventoryCost(item, oldQuantity, newQuantity, quantity,
                        line.getUnitCost() * line.getExchangeRate(), shippingAllocation,
                        dutyAllocation, clearingAllocation);
                item.setQuantity(newQuantity);
                item.setShipmentBatchId(shipment.getBatchId());
                item.setArrivalDate(receiptDate);
                dao.updateInventoryItem(item);

                line.setReceivedQuantity(line.getReceivedQuantity() + quantity);
                line.setQuantityStocked(line.getQuantityStocked() + quantity);
                dao.updateShipmentLine(line);

                ShipmentReceiptLine receiptLine = new ShipmentReceiptLine();
                receiptLine.setShipmentLineId(line.getId());
                receiptLine.setQuantity(quantity);
                receiptLine.setGoodsValueUgx(goodsValue);
                receiptLine.setAllocatedShippingUgx(shippingAllocation);
                receiptLine.setAllocatedDutyVatUgx(dutyAllocation);
                receiptLine.setAllocatedClearingUgx(clearingAllocation);
                receiptLine.setLandedUnitCostUgx(landedPerUnit);
                receiptLines.add(receiptLine);
            }

            AccountPayable payable = new AccountPayable();
            payable.setVendorName(shipment.getSupplierName());
            payable.setDescription("Shipment receipt " + shipment.getBatchId());
            payable.setAmount(receipt.getTotalLandedCostUgx());
            payable.setAmountPaid(0);
            payable.setBillDate(receiptDate);
            payable.setDueDate(addDays(receiptDate, 30));
            payable.setStatus("OPEN");
            int payableId = (int) dao.insertPayable(payable);
            payable.setId(payableId);
            JournalEntry entry = new JournalEntry();
            entry.setDate(receiptDate);
            entry.setDescription("Inventory received: " + shipment.getBatchId());
            entry.setReference("SHIP-" + receiptReference);
            entry.setSourceType("SHIPMENT_RECEIPT");
            entry.setSourceId(shipmentId);
            entry.setCreatedAt(System.currentTimeMillis());
            int journalId = (int) dao.insertJournalEntry(entry);
            entry.setId(journalId);
            payable.setJournalEntryId(journalId);
            dao.updatePayable(payable);
            journalLines.add(new JournalLine(journalId, AccountCodes.INVENTORY,
                    receipt.getTotalLandedCostUgx(), 0, "Received inventory"));
            journalLines.add(new JournalLine(journalId, AccountCodes.ACCOUNTS_PAYABLE,
                    0, receipt.getTotalLandedCostUgx(), "Supplier payable"));
            insertBalancedLines(entry, journalLines);

            receipt.setPayableId(payableId);
            receipt.setJournalEntryId(journalId);
            receiptId[0] = dao.insertShipmentReceipt(receipt);
            for (ShipmentReceiptLine receiptLine : receiptLines) receiptLine.setReceiptId((int) receiptId[0]);
            dao.insertShipmentReceiptLines(receiptLines);

            shipment.setPayableId(payableId);
            shipment.setJournalEntryId(journalId);
            boolean fullyReceived = true;
            for (ShipmentLine line : dao.getShipmentLines(shipmentId)) {
                if (line.getQuantityStocked() < line.getOrderedQuantity()) fullyReceived = false;
            }
            if (fullyReceived) {
                shipment.setStockReceivedAt(receiptDate);
                shipment.setDeliveryDate(receiptDate);
                shipment.setDelivered(true);
                shipment.setStatus("delivered");
            } else {
                shipment.setStatus("partially_received");
            }
            dao.updateShipment(shipment);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("shipment", shipment);
            payload.put("shipmentLines", dao.getShipmentLines(shipmentId));
            payload.put("receipt", dao.getShipmentReceiptById((int) receiptId[0]));
            payload.put("receiptLines", dao.getShipmentReceiptLines((int) receiptId[0]));
            payload.put("payable", dao.getPayableById(payableId));
            payload.put("journalEntry", dao.getJournalEntryById(journalId));
            payload.put("journalLines", dao.getLinesForEntry(journalId));
            enqueueSyncOperation("SHIPMENT_RECEIVED", receiptReference, payload);
        });
        return receiptId[0];
    }

    public long replaceManualReceivable(int receivableId, AccountReceivable replacement) {
        long[] replacementId = new long[1];
        database.runInTransaction(() -> {
            AccountReceivable existing = dao.getReceivableById(receivableId);
            if (existing == null || existing.getSaleId() > 0 || existing.getAmountPaid() > 0
                    || "VOID".equals(existing.getStatus())) {
                throw new IllegalArgumentException("Only unpaid manual receivables can be edited.");
            }
            if (!dao.getReceivablePayments(existing.getId()).isEmpty()) {
                throw new IllegalStateException("Receivables with payment history cannot be edited.");
            }
            validateManualReceivable(replacement);
            accounting.reverseJournalEntry(existing.getJournalEntryId(), "Receivable edited");
            existing.setStatus("VOID");
            existing.setVoidReason("Receivable edited");
            existing.setVoidedAt(new Date());
            dao.updateReceivable(existing);
            enqueueSyncOperation("RECEIVABLE_VOIDED", String.valueOf(existing.getId()), existing);

            replacement.setId(0);
            replacement.setSyncId(UUID.randomUUID().toString());
            replacement.setAmountPaid(0);
            replacement.setStatus("OPEN");
            replacement.setJournalEntryId(0);
            replacement.setSaleId(0);
            replacement.setVoidReason(null);
            replacement.setVoidedAt(null);
            replacementId[0] = recordManualReceivableWithinTransaction(replacement);
        });
        return replacementId[0];
    }

    public void voidReceivable(int receivableId, String reason, boolean refundConfirmed) {
        AccountReceivable initial = dao.getReceivableById(receivableId);
        if (initial != null && initial.getSaleId() > 0) {
            voidSale(initial.getSaleId(), reason, refundConfirmed);
            return;
        }
        database.runInTransaction(() -> {
            AccountReceivable receivable = dao.getReceivableById(receivableId);
            if (receivable == null || "VOID".equals(receivable.getStatus())) {
                throw new IllegalArgumentException("Receivable is not available to void.");
            }
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("A reason is required to void a receivable.");
            }
            List<ReceivablePayment> payments = dao.getReceivablePayments(receivableId);
            if (receivable.getAmountPaid() > 0 && payments.isEmpty()) {
                throw new IllegalStateException("This receivable has legacy payments that must be reconciled before voiding.");
            }
            boolean hasCollectedFunds = false;
            for (ReceivablePayment payment : payments) {
                if ("POSTED".equals(payment.getStatus())) hasCollectedFunds = true;
            }
            if (hasCollectedFunds && !refundConfirmed) {
                throw new IllegalArgumentException("Confirm that customer payments have been refunded.");
            }
            for (ReceivablePayment payment : payments) {
                if (!"POSTED".equals(payment.getStatus())) continue;
                int reversalId = (int) accounting.reverseJournalEntry(payment.getJournalEntryId(), reason.trim());
                payment.setStatus("REVERSED");
                payment.setReversalJournalEntryId(reversalId);
                dao.updateReceivablePayment(payment);
            }
            if (receivable.getJournalEntryId() > 0) {
                accounting.reverseJournalEntry(receivable.getJournalEntryId(), reason.trim());
            }
            receivable.setStatus("VOID");
            receivable.setVoidReason(reason.trim());
            receivable.setVoidedAt(new Date());
            dao.updateReceivable(receivable);
            enqueueSyncOperation("RECEIVABLE_VOIDED", String.valueOf(receivableId), receivable);
        });
    }

    public long recordManualPayable(AccountPayable payable) {
        long[] payableId = new long[1];
        database.runInTransaction(() -> {
            validateOpenBalance(payable.getAmount(), "Payable");
            if (payable.getVendorName() == null || payable.getVendorName().trim().isEmpty()) {
                throw new IllegalArgumentException("Vendor name is required.");
            }
            payable.setAmountPaid(0);
            payable.setStatus("OPEN");
            payableId[0] = dao.insertPayable(payable);
            payable.setId((int) payableId[0]);
            JournalEntry entry = createManualJournal(payable.getDescription(), "PAYABLE", payable.getId(),
                    AccountCodes.OPERATING_EXPENSE, AccountCodes.ACCOUNTS_PAYABLE, payable.getAmount());
            payable.setJournalEntryId(entry.getId());
            dao.updatePayable(payable);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("payable", payable);
            payload.put("journalEntry", entry);
            payload.put("journalLines", dao.getLinesForEntry(entry.getId()));
            enqueueSyncOperation("PAYABLE_POSTED", String.valueOf(payableId[0]), payload);
        });
        return payableId[0];
    }

    public long replaceManualPayable(int payableId, AccountPayable replacement) {
        long[] replacementId = new long[1];
        database.runInTransaction(() -> {
            AccountPayable existing = dao.getPayableById(payableId);
            if (existing == null || existing.getAmountPaid() > 0 || "VOID".equals(existing.getStatus())) {
                throw new IllegalArgumentException("Only unpaid payables can be edited.");
            }
            if (!dao.getPayablePayments(existing.getId()).isEmpty()) {
                throw new IllegalStateException("Payables with payment history cannot be edited.");
            }
            validateOpenBalance(replacement.getAmount(), "Payable");
            if (replacement.getVendorName() == null || replacement.getVendorName().trim().isEmpty()) {
                throw new IllegalArgumentException("Vendor name is required.");
            }
            accounting.reverseJournalEntry(existing.getJournalEntryId(), "Payable edited");
            existing.setStatus("VOID");
            existing.setVoidReason("Payable edited");
            existing.setVoidedAt(new Date());
            dao.updatePayable(existing);
            enqueueSyncOperation("PAYABLE_VOIDED", String.valueOf(existing.getId()), existing);

            replacement.setId(0);
            replacement.setSyncId(UUID.randomUUID().toString());
            replacement.setAmountPaid(0);
            replacement.setStatus("OPEN");
            replacement.setJournalEntryId(0);
            replacement.setExpenseId(0);
            replacement.setVoidReason(null);
            replacement.setVoidedAt(null);
            replacementId[0] = dao.insertPayable(replacement);
            replacement.setId((int) replacementId[0]);
            JournalEntry entry = createManualJournal(replacement.getDescription(), "PAYABLE",
                    replacement.getId(), AccountCodes.OPERATING_EXPENSE,
                    AccountCodes.ACCOUNTS_PAYABLE, replacement.getAmount());
            replacement.setJournalEntryId(entry.getId());
            dao.updatePayable(replacement);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("payable", replacement);
            payload.put("journalEntry", entry);
            payload.put("journalLines", dao.getLinesForEntry(entry.getId()));
            enqueueSyncOperation("PAYABLE_POSTED", String.valueOf(replacementId[0]), payload);
        });
        return replacementId[0];
    }

    public void voidPayable(int payableId, String reason, boolean refundConfirmed) {
        database.runInTransaction(() -> {
            AccountPayable payable = dao.getPayableById(payableId);
            if (payable == null || "VOID".equals(payable.getStatus())) {
                throw new IllegalArgumentException("Payable is not available to void.");
            }
            if (payable.getExpenseId() > 0) {
                throw new IllegalArgumentException("Void the linked expense to reverse this payable.");
            }
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("A reason is required to void a payable.");
            }
            List<PayablePayment> payments = dao.getPayablePayments(payableId);
            if (payable.getAmountPaid() > 0 && payments.isEmpty()) {
                throw new IllegalStateException("This payable has legacy payments that must be reconciled before voiding.");
            }
            boolean hasPaidFunds = false;
            for (PayablePayment payment : payments) {
                if ("POSTED".equals(payment.getStatus())) hasPaidFunds = true;
            }
            if (hasPaidFunds && !refundConfirmed) {
                throw new IllegalArgumentException("Confirm that vendor payments have been refunded.");
            }
            for (PayablePayment payment : payments) {
                if (!"POSTED".equals(payment.getStatus())) continue;
                int reversalId = (int) accounting.reverseJournalEntry(payment.getJournalEntryId(), reason.trim());
                payment.setStatus("REVERSED");
                payment.setReversalJournalEntryId(reversalId);
                dao.updatePayablePayment(payment);
            }
            if (payable.getJournalEntryId() > 0) {
                accounting.reverseJournalEntry(payable.getJournalEntryId(), reason.trim());
            }
            payable.setStatus("VOID");
            payable.setVoidReason(reason.trim());
            payable.setVoidedAt(new Date());
            dao.updatePayable(payable);
            enqueueSyncOperation("PAYABLE_VOIDED", String.valueOf(payableId), payable);
        });
    }

    public void voidSale(int saleId, String reason, boolean refundConfirmed) {
        database.runInTransaction(() -> voidSaleWithinTransaction(saleId, reason, refundConfirmed));
    }

    private void voidSaleWithinTransaction(int saleId, String reason, boolean refundConfirmed) {
            Sale sale = dao.getSaleById(saleId);
            if (sale == null || !"POSTED".equals(sale.getStatus())) {
                throw new IllegalArgumentException("Sale is not available to void.");
            }
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("A reason is required to void a sale.");
            }

            AccountReceivable receivable = sale.getReceivableId() > 0
                    ? dao.getReceivableById(sale.getReceivableId()) : null;
            List<ReceivablePayment> payments = receivable != null
                    ? dao.getReceivablePayments(receivable.getId()) : new java.util.ArrayList<>();
            boolean hasCollectedFunds = sale.getAmountPaid() > 0;
            for (ReceivablePayment payment : payments) {
                if ("POSTED".equals(payment.getStatus())
                        && payment.getJournalEntryId() != sale.getJournalEntryId()) {
                    hasCollectedFunds = true;
                }
            }
            if (hasCollectedFunds && !refundConfirmed) {
                throw new IllegalArgumentException("Confirm that collected payments have been refunded.");
            }

            long saleReversalId = 0;
            if (sale.getJournalEntryId() > 0) {
                saleReversalId = accounting.reverseJournalEntry(sale.getJournalEntryId(), reason.trim());
            }
            for (ReceivablePayment payment : payments) {
                if (!"POSTED".equals(payment.getStatus())) continue;
                if (payment.getJournalEntryId() != sale.getJournalEntryId()) {
                    int reversalId = (int) accounting.reverseJournalEntry(
                            payment.getJournalEntryId(), reason.trim());
                    payment.setReversalJournalEntryId(reversalId);
                } else {
                    payment.setReversalJournalEntryId((int) saleReversalId);
                }
                payment.setStatus("REVERSED");
                dao.updateReceivablePayment(payment);
            }
            if (receivable != null) {
                receivable.setStatus("VOID");
                receivable.setVoidReason(reason.trim());
                receivable.setVoidedAt(new Date());
                dao.updateReceivable(receivable);
            }

            InventoryItem item = dao.getInventoryById(sale.getInventoryItemId());
            if (item == null) throw new IllegalStateException("Sale inventory item no longer exists.");
            item.setQuantity(item.getQuantity() + sale.getQuantitySold());
            dao.updateInventoryItem(item);

            sale.setStatus("VOID");
            sale.setVoidReason(reason.trim());
            sale.setVoidedAt(new Date());
            dao.updateSale(sale);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sale", sale);
            payload.put("inventoryItem", item);
            if (receivable != null) {
                payload.put("receivable", receivable);
                payload.put("payments", dao.getReceivablePayments(receivable.getId()));
            }
            enqueueSyncOperation("SALE_VOIDED", String.valueOf(saleId), payload);
    }

    private void validateSale(Sale sale, InventoryItem item) {
        if (item == null) throw new IllegalArgumentException("Select an inventory item.");
        if (sale.getQuantitySold() <= 0) throw new IllegalArgumentException("Quantity must be greater than zero.");
        if (sale.getQuantitySold() > item.getQuantity()) {
            throw new IllegalArgumentException("Not enough stock. Available: " + item.getQuantity());
        }
        if (!Double.isFinite(sale.getUnitPrice()) || sale.getUnitPrice() <= 0) {
            throw new IllegalArgumentException("Unit price must be greater than zero.");
        }
        double gross = sale.getUnitPrice() * sale.getQuantitySold();
        if (!Double.isFinite(sale.getDiscount()) || sale.getDiscount() < 0 || sale.getDiscount() > gross) {
            throw new IllegalArgumentException("Discount must be between zero and the sale total.");
        }
        if (sale.getPaymentMethod() == null || sale.getPaymentMethod().trim().isEmpty()) {
            throw new IllegalArgumentException("Select a payment method.");
        }
        if (gross - sale.getDiscount() <= 0) {
            throw new IllegalArgumentException("Sale total must be greater than zero.");
        }
    }

    private void validatePayment(Sale sale) {
        double amountPaid = sale.getAmountPaid();
        if (!Double.isFinite(amountPaid) || amountPaid < 0 || amountPaid > sale.getTotalAmount()) {
            throw new IllegalArgumentException("Amount received must be between zero and the sale total.");
        }
        if (amountPaid > 0) validateProviderReference(sale.getPaymentMethod(), sale.getTransactionId());
        double balanceDue = sale.getTotalAmount() - amountPaid;
        if (balanceDue > 0 && (sale.getCustomerName() == null || sale.getCustomerName().trim().isEmpty())) {
            throw new IllegalArgumentException("Customer name is required when a balance is due.");
        }
        if (amountPaid == 0 && !PaymentMethods.CREDIT.equals(sale.getPaymentMethod())) {
            throw new IllegalArgumentException("Select Credit when no payment is received.");
        }
        if (amountPaid > 0 && PaymentMethods.CREDIT.equals(sale.getPaymentMethod())) {
            throw new IllegalArgumentException("Choose the method used for the deposit.");
        }
    }

    private void validatePaymentAmount(double amount, double balanceDue) {
        if (!Double.isFinite(amount) || amount <= 0 || amount > balanceDue) {
            throw new IllegalArgumentException("Payment must be greater than zero and no more than the balance due.");
        }
    }

    private void validatePaymentMethod(String paymentMethod) {
        if (!PaymentMethods.isRecordedMethod(paymentMethod)) {
            throw new IllegalArgumentException("Select a supported payment method.");
        }
    }

    private void validateProviderReference(String paymentMethod, String reference) {
        if (PaymentMethods.isMobileMoney(paymentMethod)
                && (reference == null || reference.trim().isEmpty())) {
            throw new IllegalArgumentException("Enter the MTN/Airtel transaction reference.");
        }
    }

    private void validateExpense(Expense expense) {
        if (expense == null) throw new IllegalArgumentException("Expense is required.");
        if (!Double.isFinite(expense.getAmount()) || expense.getAmount() <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than zero.");
        }
        if (expense.getDescription() == null || expense.getDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Expense description is required.");
        }
        if (expense.getCategory() == null || expense.getCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Expense category is required.");
        }
        if (expense.getPaymentMethod() == null || expense.getPaymentMethod().trim().isEmpty()) {
            throw new IllegalArgumentException("Select a payment method.");
        }
        validateProviderReference(expense.getPaymentMethod(), expense.getReceiptNumber());
    }

    private long recordExpenseWithinTransaction(Expense expense) {
        validateExpense(expense);
        long expenseId = dao.insertExpense(expense);
        expense.setId((int) expenseId);
        accounting.postExpense(expense);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expense", expense);
        payload.put("journalEntry", dao.getJournalEntryById(expense.getJournalEntryId()));
        payload.put("journalLines", dao.getLinesForEntry(expense.getJournalEntryId()));
        if (expense.getPayableId() > 0) payload.put("payable", dao.getPayableById(expense.getPayableId()));
        enqueueSyncOperation("EXPENSE_POSTED", String.valueOf(expenseId), payload);
        return expenseId;
    }

    private void enqueueSyncOperation(String operationType, String aggregateId, Object payload) {
        SyncOutboxEntry entry = new SyncOutboxEntry();
        entry.setOperationId(UUID.randomUUID().toString());
        entry.setOperationType(operationType);
        entry.setAggregateId(aggregateId);
        entry.setPayloadJson(new Gson().toJson(payload));
        entry.setPayloadVersion(1);
        entry.setCreatedAt(System.currentTimeMillis());
        entry.setNextAttemptAt(0);
        entry.setStatus("PENDING");
        dao.insertSyncOutboxEntry(entry);
        if (appContext != null) FinanceSyncScheduler.schedule(appContext);
    }

    private long recordManualReceivableWithinTransaction(AccountReceivable receivable) {
        validateManualReceivable(receivable);
        receivable.setAmountPaid(0);
        receivable.setStatus("OPEN");
        long id = dao.insertReceivable(receivable);
        receivable.setId((int) id);
        JournalEntry entry = createManualJournal(receivable.getDescription(), "RECEIVABLE", receivable.getId(),
                AccountCodes.ACCOUNTS_RECEIVABLE, AccountCodes.SALES_REVENUE, receivable.getAmount());
        receivable.setJournalEntryId(entry.getId());
        dao.updateReceivable(receivable);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receivable", receivable);
        payload.put("journalEntry", entry);
        payload.put("journalLines", dao.getLinesForEntry(entry.getId()));
        enqueueSyncOperation("RECEIVABLE_POSTED", String.valueOf(id), payload);
        return id;
    }

    private void validateManualReceivable(AccountReceivable receivable) {
        validateOpenBalance(receivable.getAmount(), "Receivable");
        if (receivable.getCustomerName() == null || receivable.getCustomerName().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required.");
        }
    }

    private void validateOpenBalance(double amount, String label) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException(label + " amount must be greater than zero.");
        }
    }

    private void blendInventoryCost(InventoryItem item, int oldQuantity, int newQuantity, int receivedQuantity,
                                    double goodsPerUnit, double shippingTotal,
                                    double dutyVatTotal, double clearingTotal) {
        item.setUnitCost(blend(item.getUnitCost(), oldQuantity, goodsPerUnit, receivedQuantity, newQuantity));
        item.setShippingCost(blend(item.getShippingCost(), oldQuantity,
                shippingTotal / receivedQuantity, receivedQuantity, newQuantity));
        item.setImportDuty(blend(item.getImportDuty(), oldQuantity,
                dutyVatTotal / receivedQuantity, receivedQuantity, newQuantity));
        item.setClearingCost(blend(item.getClearingCost(), oldQuantity,
                clearingTotal / receivedQuantity, receivedQuantity, newQuantity));
    }

    private double blend(double existingUnitCost, int existingQuantity, double receivedUnitCost,
                         int receivedQuantity, int combinedQuantity) {
        return (existingUnitCost * existingQuantity + receivedUnitCost * receivedQuantity) / combinedQuantity;
    }

    private int insertBalancedLines(JournalEntry entry, List<JournalLine> lines) {
        double debits = 0;
        double credits = 0;
        for (JournalLine line : lines) {
            line.setJournalEntryId(entry.getId());
            debits += line.getDebit();
            credits += line.getCredit();
        }
        if (Math.abs(debits - credits) > 0.01 || debits <= 0) {
            throw new IllegalStateException("Shipment journal must balance.");
        }
        dao.insertJournalLines(lines);
        return entry.getId();
    }

    private Date addDays(Date date, int days) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(java.util.Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    private JournalEntry createManualJournal(String description, String sourceType, int sourceId,
                                             int debitAccount, int creditAccount, double amount) {
        JournalEntry entry = new JournalEntry();
        entry.setDate(new Date());
        entry.setDescription(description);
        entry.setReference(sourceType + "-" + sourceId);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setCreatedAt(System.currentTimeMillis());
        entry.setId((int) dao.insertJournalEntry(entry));
        dao.insertJournalLines(java.util.Arrays.asList(
                new com.financeapp.uganda.models.JournalLine(entry.getId(), debitAccount, amount, 0, sourceType),
                new com.financeapp.uganda.models.JournalLine(entry.getId(), creditAccount, 0, amount, sourceType)));
        return entry;
    }

    private void voidExpenseWithinTransaction(int expenseId, String reason, boolean refundConfirmed) {
        Expense expense = dao.getExpenseById(expenseId);
        if (expense == null || !"POSTED".equals(expense.getStatus())) {
            throw new IllegalArgumentException("Expense is not available to void.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("A reason is required to void an expense.");
        }

        AccountPayable payable = expense.getPayableId() > 0
                ? dao.getPayableById(expense.getPayableId()) : null;
        List<PayablePayment> payments = payable != null
                ? dao.getPayablePayments(payable.getId()) : new java.util.ArrayList<>();
        if (payable != null && payable.getAmountPaid() > 0 && payments.isEmpty()) {
            throw new IllegalStateException("This payable has legacy payments that must be reconciled before voiding.");
        }
        boolean hasPaidFunds = payable != null && payable.getAmountPaid() > 0;
        for (PayablePayment payment : payments) {
            if ("POSTED".equals(payment.getStatus())) hasPaidFunds = true;
        }
        if (hasPaidFunds && !refundConfirmed) {
            throw new IllegalArgumentException("Confirm that vendor payments have been refunded.");
        }

        for (PayablePayment payment : payments) {
            if (!"POSTED".equals(payment.getStatus())) continue;
            int reversalId = (int) accounting.reverseJournalEntry(
                    payment.getJournalEntryId(), reason.trim());
            payment.setStatus("REVERSED");
            payment.setReversalJournalEntryId(reversalId);
            dao.updatePayablePayment(payment);
        }
        if (expense.getJournalEntryId() > 0) {
            accounting.reverseJournalEntry(expense.getJournalEntryId(), reason.trim());
        }
        if (payable != null) {
            payable.setStatus("VOID");
            payable.setVoidReason(reason.trim());
            payable.setVoidedAt(new Date());
            dao.updatePayable(payable);
        }
        expense.setStatus("VOID");
        expense.setVoidReason(reason.trim());
        expense.setVoidedAt(new Date());
        dao.updateExpense(expense);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("expense", expense);
        payload.put("payable", payable);
        if (expense.getJournalEntryId() > 0) {
            payload.put("journalEntry", dao.getJournalEntryById(expense.getJournalEntryId()));
            payload.put("journalLines", dao.getLinesForEntry(expense.getJournalEntryId()));
        }
        enqueueSyncOperation("EXPENSE_VOIDED", String.valueOf(expenseId), payload);
    }
}