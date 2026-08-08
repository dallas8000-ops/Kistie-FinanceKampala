package com.financeapp.uganda.accounting;

import com.financeapp.uganda.database.FinanceDao;
import com.financeapp.uganda.models.Expense;

public class ExpenseAccountingHelper {

    private final FinanceDao dao;
    private final AccountingService accounting;

    public ExpenseAccountingHelper(FinanceDao dao) {
        this.dao = dao;
        this.accounting = new AccountingService(dao);
    }

    public void saveNew(Expense expense) {
        long id = dao.insertExpense(expense);
        expense.setId((int) id);
        accounting.postExpense(expense);
        dao.updateExpense(expense);
    }

    public void update(Expense expense) {
        dao.updateExpense(expense);
        if (expense.getJournalEntryId() > 0) {
            accounting.updateExpenseAccounting(expense);
        } else {
            accounting.postExpense(expense);
            dao.updateExpense(expense);
        }
    }

    public void delete(Expense expense) {
        accounting.deleteExpenseAccounting(expense);
        dao.deleteExpense(expense);
    }
}
