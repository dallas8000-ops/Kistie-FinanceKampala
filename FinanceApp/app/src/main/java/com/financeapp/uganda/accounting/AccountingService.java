package com.financeapp.uganda.accounting;

import com.financeapp.uganda.database.FinanceDao;
import com.financeapp.uganda.models.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class AccountingService {

    private final FinanceDao dao;

    public AccountingService(FinanceDao dao) {
        this.dao = dao;
    }

    public int paymentAccountId(String paymentMethod) {
        if (paymentMethod == null) return AccountCodes.CASH;
        switch (paymentMethod) {
            case "Mobile Money": return AccountCodes.MOBILE_MONEY;
            case "Bank Transfer":
            case "Cheque": return AccountCodes.BANK;
            case "Credit": return AccountCodes.ACCOUNTS_RECEIVABLE;
            default: return AccountCodes.CASH;
        }
    }

    public boolean isCreditPayment(String paymentMethod) {
        return "Credit".equals(paymentMethod);
    }

    public boolean isPayablePayment(String paymentMethod) {
        return "Credit".equals(paymentMethod);
    }

    public boolean hasInitialCapital() {
        return dao.getCapitalEntry() != null;
    }

    /** Credit balance of Initial Capital (positive number). */
    public double getInitialCapitalBalance() {
        double net = dao.getAccountNetBalance(AccountCodes.INITIAL_CAPITAL);
        return net <= 0 ? -net : net;
    }

    /** Opening entry: Dr Cash/Bank/Mobile Money, Cr Initial Capital */
    public void postInitialCapital(double amount, int assetAccountId, String paymentLabel) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (dao.getCapitalEntry() != null) {
            throw new IllegalStateException("Initial capital already recorded");
        }
        JournalEntry entry = createEntry(new Date(),
                "Opening capital — owner investment",
                "CAP-OPEN",
                "CAPITAL", 1);
        List<JournalLine> lines = new ArrayList<>();
        lines.add(new JournalLine(entry.getId(), assetAccountId, amount, 0, paymentLabel));
        lines.add(new JournalLine(entry.getId(), AccountCodes.INITIAL_CAPITAL, 0, amount, "Owner investment"));
        insertBalancedLines(entry, lines);
    }

    public void updateInitialCapital(double amount, int assetAccountId, String paymentLabel) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        JournalEntry existing = dao.getCapitalEntry();
        if (existing == null) {
            postInitialCapital(amount, assetAccountId, paymentLabel);
            return;
        }
        dao.deleteLinesForEntry(existing.getId());
        existing.setDescription("Opening capital — owner investment");
        existing.setDate(new Date());
        dao.updateJournalEntry(existing);
        List<JournalLine> lines = new ArrayList<>();
        lines.add(new JournalLine(existing.getId(), assetAccountId, amount, 0, paymentLabel));
        lines.add(new JournalLine(existing.getId(), AccountCodes.INITIAL_CAPITAL, 0, amount, "Owner investment"));
        insertBalancedLines(existing, lines);
    }

    /** Post expense: Dr Operating Expense, Cr Cash/AP */
    public void postExpense(Expense expense) {
        int creditAccount = isPayablePayment(expense.getPaymentMethod())
                ? AccountCodes.ACCOUNTS_PAYABLE : paymentAccountId(expense.getPaymentMethod());

        JournalEntry entry = createEntry(expense.getDate(),
                expense.getDescription(),
                "EXP-" + expense.getId(),
                "EXPENSE", expense.getId());

        List<JournalLine> lines = new ArrayList<>();
        lines.add(new JournalLine((int) entry.getId(), AccountCodes.OPERATING_EXPENSE, expense.getAmount(), 0,
                expense.getSubCategory()));
        lines.add(new JournalLine((int) entry.getId(), creditAccount, 0, expense.getAmount(),
                expense.getPaymentMethod()));
        insertBalancedLines(entry, lines);

        expense.setJournalEntryId((int) entry.getId());

        if (isPayablePayment(expense.getPaymentMethod())) {
            AccountPayable ap = new AccountPayable();
            ap.setVendorName(expense.getSupplierName() != null && !expense.getSupplierName().isEmpty()
                    ? expense.getSupplierName() : "Vendor");
            ap.setDescription(expense.getDescription());
            ap.setAmount(expense.getAmount());
            ap.setAmountPaid(0);
            ap.setBillDate(expense.getDate());
            ap.setDueDate(addDays(expense.getDate(), 30));
            ap.setStatus("OPEN");
            ap.setJournalEntryId((int) entry.getId());
            ap.setExpenseId(expense.getId());
            long apId = dao.insertPayable(ap);
            expense.setPayableId((int) apId);
        }
        dao.updateExpense(expense);
    }

    public void updateExpenseAccounting(Expense expense) {
        if (expense.getJournalEntryId() > 0) {
            reverseAndDeletePayable(expense);
            dao.deleteLinesForEntry(expense.getJournalEntryId());
            JournalEntry entry = dao.getJournalEntryById(expense.getJournalEntryId());
            if (entry != null) {
                entry.setDate(expense.getDate());
                entry.setDescription(expense.getDescription());
                dao.updateJournalEntry(entry);
                repostExpenseLines(expense, entry);
            }
        } else {
            postExpense(expense);
        }
    }

    public void deleteExpenseAccounting(Expense expense) {
        reverseAndDeletePayable(expense);
        if (expense.getJournalEntryId() > 0) {
            dao.deleteLinesForEntry(expense.getJournalEntryId());
            JournalEntry entry = dao.getJournalEntryById(expense.getJournalEntryId());
            if (entry != null) dao.deleteJournalEntry(entry);
        }
    }

    private void reverseAndDeletePayable(Expense expense) {
        if (expense.getPayableId() > 0) {
            AccountPayable ap = dao.getPayableById(expense.getPayableId());
            if (ap != null) dao.deletePayable(ap);
            expense.setPayableId(0);
        }
    }

    private void repostExpenseLines(Expense expense, JournalEntry entry) {
        int creditAccount = isPayablePayment(expense.getPaymentMethod())
                ? AccountCodes.ACCOUNTS_PAYABLE : paymentAccountId(expense.getPaymentMethod());
        List<JournalLine> lines = new ArrayList<>();
        lines.add(new JournalLine(entry.getId(), AccountCodes.OPERATING_EXPENSE, expense.getAmount(), 0,
                expense.getSubCategory()));
        lines.add(new JournalLine(entry.getId(), creditAccount, 0, expense.getAmount(),
                expense.getPaymentMethod()));
        insertBalancedLines(entry, lines);

        if (isPayablePayment(expense.getPaymentMethod())) {
            AccountPayable ap = new AccountPayable();
            ap.setVendorName(expense.getSupplierName() != null ? expense.getSupplierName() : "Vendor");
            ap.setDescription(expense.getDescription());
            ap.setAmount(expense.getAmount());
            ap.setAmountPaid(0);
            ap.setBillDate(expense.getDate());
            ap.setDueDate(addDays(expense.getDate(), 30));
            ap.setStatus("OPEN");
            ap.setJournalEntryId(entry.getId());
            ap.setExpenseId(expense.getId());
            long apId = dao.insertPayable(ap);
            expense.setPayableId((int) apId);
            dao.updateExpense(expense);
        }
    }

    /** Post sale with COGS: Dr Cash/AR + Dr COGS, Cr Revenue + Cr Inventory */
    public void postSale(Sale sale, InventoryItem item, boolean onCredit) {
        double cogs = item.getUnitCost() * sale.getQuantitySold();
        sale.setCostOfGoodsSold(cogs);

        int debitAccount = onCredit ? AccountCodes.ACCOUNTS_RECEIVABLE : paymentAccountId(sale.getPaymentMethod());

        JournalEntry entry = createEntry(sale.getSaleDate(),
                "Sale: " + sale.getItemName(),
                "SALE-" + sale.getId(),
                "SALE", sale.getId());

        List<JournalLine> lines = new ArrayList<>();
        lines.add(new JournalLine((int) entry.getId(), debitAccount, sale.getTotalAmount(), 0, "Sale receipt"));
        lines.add(new JournalLine((int) entry.getId(), AccountCodes.SALES_REVENUE, 0, sale.getTotalAmount(), "Revenue"));
        if (cogs > 0) {
            lines.add(new JournalLine((int) entry.getId(), AccountCodes.COGS, cogs, 0, "COGS"));
            lines.add(new JournalLine((int) entry.getId(), AccountCodes.INVENTORY, 0, cogs, "Inventory reduction"));
        }
        insertBalancedLines(entry, lines);
        sale.setJournalEntryId((int) entry.getId());

        if (onCredit) {
            AccountReceivable ar = new AccountReceivable();
            ar.setCustomerName(sale.getCustomerName() != null ? sale.getCustomerName() : "Customer");
            ar.setCustomerPhone(sale.getCustomerPhone());
            ar.setDescription("Sale: " + sale.getItemName());
            ar.setAmount(sale.getTotalAmount());
            ar.setAmountPaid(0);
            ar.setInvoiceDate(sale.getSaleDate());
            ar.setDueDate(addDays(sale.getSaleDate(), 30));
            ar.setStatus("OPEN");
            ar.setJournalEntryId((int) entry.getId());
            ar.setSaleId(sale.getId());
            long arId = dao.insertReceivable(ar);
            sale.setReceivableId((int) arId);
        }
        dao.updateSale(sale);
    }

    public void recordReceivablePayment(AccountReceivable ar, double amount, String paymentMethod) {
        JournalEntry entry = createEntry(new Date(),
                "Payment from " + ar.getCustomerName(),
                "AR-PAY-" + ar.getId(),
                "PAYMENT", ar.getId());

        int debitAccount = paymentAccountId(paymentMethod);
        List<JournalLine> lines = new ArrayList<>();
        lines.add(new JournalLine((int) entry.getId(), debitAccount, amount, 0, "AR payment"));
        lines.add(new JournalLine((int) entry.getId(), AccountCodes.ACCOUNTS_RECEIVABLE, 0, amount, "AR cleared"));
        insertBalancedLines(entry, lines);

        ar.setAmountPaid(ar.getAmountPaid() + amount);
        ar.setStatus(ar.getAmountPaid() >= ar.getAmount() ? "PAID" : "PARTIAL");
        dao.updateReceivable(ar);
    }

    public void recordPayablePayment(AccountPayable ap, double amount, String paymentMethod) {
        JournalEntry entry = createEntry(new Date(),
                "Payment to " + ap.getVendorName(),
                "AP-PAY-" + ap.getId(),
                "PAYMENT", ap.getId());

        int creditAccount = paymentAccountId(paymentMethod);
        List<JournalLine> lines = new ArrayList<>();
        lines.add(new JournalLine((int) entry.getId(), AccountCodes.ACCOUNTS_PAYABLE, amount, 0, "AP payment"));
        lines.add(new JournalLine((int) entry.getId(), creditAccount, 0, amount, "Cash out"));
        insertBalancedLines(entry, lines);

        ap.setAmountPaid(ap.getAmountPaid() + amount);
        ap.setStatus(ap.getAmountPaid() >= ap.getAmount() ? "PAID" : "PARTIAL");
        dao.updatePayable(ap);
    }

    public void updateReceivable(AccountReceivable ar) {
        dao.updateReceivable(ar);
        if (ar.getJournalEntryId() > 0) {
            dao.deleteLinesForEntry(ar.getJournalEntryId());
            JournalEntry entry = dao.getJournalEntryById(ar.getJournalEntryId());
            if (entry != null) {
                entry.setDescription(ar.getDescription());
                entry.setDate(ar.getInvoiceDate());
                dao.updateJournalEntry(entry);
                List<JournalLine> lines = new ArrayList<>();
                lines.add(new JournalLine(entry.getId(), AccountCodes.ACCOUNTS_RECEIVABLE, ar.getAmount(), 0, "AR"));
                lines.add(new JournalLine(entry.getId(), AccountCodes.SALES_REVENUE, 0, ar.getAmount(), "Revenue"));
                insertBalancedLines(entry, lines);
            }
        }
    }

    public void updatePayable(AccountPayable ap) {
        dao.updatePayable(ap);
        if (ap.getJournalEntryId() > 0) {
            dao.deleteLinesForEntry(ap.getJournalEntryId());
            JournalEntry entry = dao.getJournalEntryById(ap.getJournalEntryId());
            if (entry != null) {
                entry.setDescription(ap.getDescription());
                entry.setDate(ap.getBillDate());
                dao.updateJournalEntry(entry);
                List<JournalLine> lines = new ArrayList<>();
                lines.add(new JournalLine(entry.getId(), AccountCodes.OPERATING_EXPENSE, ap.getAmount(), 0, "Expense"));
                lines.add(new JournalLine(entry.getId(), AccountCodes.ACCOUNTS_PAYABLE, 0, ap.getAmount(), "AP"));
                insertBalancedLines(entry, lines);
            }
        }
    }

    /** Manual journal entry - must balance */
    public long postManualEntry(Date date, String description, List<JournalLine> lines) {
        JournalEntry entry = createEntry(date, description, "JE-MANUAL", "MANUAL", 0);
        for (JournalLine line : lines) {
            line.setJournalEntryId((int) entry.getId());
        }
        insertBalancedLines(entry, lines);
        return entry.getId();
    }

    public void updateJournalEntry(JournalEntry entry, List<JournalLine> lines) {
        dao.deleteLinesForEntry(entry.getId());
        dao.updateJournalEntry(entry);
        for (JournalLine line : lines) {
            line.setJournalEntryId(entry.getId());
            line.setId(0);
        }
        insertBalancedLines(entry, lines);
    }

    public boolean isBalanced(List<JournalLine> lines) {
        double debits = 0, credits = 0;
        for (JournalLine line : lines) {
            debits += line.getDebit();
            credits += line.getCredit();
        }
        return Math.abs(debits - credits) < 0.01 && debits > 0;
    }

    public List<TrialBalanceLine> getTrialBalance() {
        List<TrialBalanceLine> result = new ArrayList<>();
        double totalDebit = 0, totalCredit = 0;
        for (Account account : dao.getAllAccounts()) {
            double net = dao.getAccountNetBalance(account.getId());
            double debit = 0, credit = 0;
            String type = account.getType();
            if ("ASSET".equals(type) || "EXPENSE".equals(type)) {
                if (net >= 0) debit = net; else credit = -net;
            } else {
                if (net <= 0) credit = -net; else debit = net;
            }
            if (debit > 0.005 || credit > 0.005) {
                result.add(new TrialBalanceLine(account.getId(), account.getCode(), account.getName(), type, debit, credit));
                totalDebit += debit;
                totalCredit += credit;
            }
        }
        result.add(new TrialBalanceLine(-1, "", "TOTAL", "", totalDebit, totalCredit));
        return result;
    }

    public boolean isBooksBalanced() {
        List<TrialBalanceLine> tb = getTrialBalance();
        if (tb.isEmpty()) return true;
        TrialBalanceLine total = tb.get(tb.size() - 1);
        return Math.abs(total.debit - total.credit) < 0.01;
    }

    public String getFinancialSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Trial Balance Summary (UGX):\n");
        for (TrialBalanceLine line : getTrialBalance()) {
            if (line.accountId == -1) {
                sb.append(String.format("TOTALS: Debit %,.0f | Credit %,.0f | Balanced: %s\n",
                        line.debit, line.credit, Math.abs(line.debit - line.credit) < 0.01 ? "Yes" : "No"));
            } else if (line.debit > 0 || line.credit > 0) {
                sb.append(String.format("%s %s: Dr %,.0f Cr %,.0f\n", line.code, line.name, line.debit, line.credit));
            }
        }
        sb.append(String.format("Open Receivables: %,.0f UGX\n", dao.getTotalReceivableBalance()));
        sb.append(String.format("Open Payables: %,.0f UGX\n", dao.getTotalPayableBalance()));
        sb.append(String.format("Total Sales: %,.0f UGX\n", dao.getTotalSales()));
        return sb.toString();
    }

    private JournalEntry createEntry(Date date, String description, String reference, String sourceType, int sourceId) {
        JournalEntry entry = new JournalEntry();
        entry.setDate(date != null ? date : new Date());
        entry.setDescription(description);
        entry.setReference(reference);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setCreatedAt(System.currentTimeMillis());
        long id = dao.insertJournalEntry(entry);
        entry.setId((int) id);
        return entry;
    }

    private void insertBalancedLines(JournalEntry entry, List<JournalLine> lines) {
        double debits = 0, credits = 0;
        for (JournalLine line : lines) {
            line.setJournalEntryId(entry.getId());
            debits += line.getDebit();
            credits += line.getCredit();
        }
        if (Math.abs(debits - credits) > 0.01) {
            throw new IllegalStateException("Journal entry must balance. Debits=" + debits + " Credits=" + credits);
        }
        dao.insertJournalLines(lines);
    }

    private Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }
}
