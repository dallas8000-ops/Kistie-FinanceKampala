package com.financeapp.uganda.accounting;

import android.content.Context;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.Expense;

public class ExpenseAccountingHelper {

    private final FinanceTransactionService transactions;

    public ExpenseAccountingHelper(AppDatabase database) {
        this(null, database);
    }

    public ExpenseAccountingHelper(Context context, AppDatabase database) {
        this.transactions = new FinanceTransactionService(context, database);
    }

    public void saveNew(Expense expense) {
        transactions.recordExpense(expense);
    }

    public void update(Expense expense) {
        transactions.replaceExpense(expense.getId(), expense, "Expense edited", false);
    }

    public void delete(Expense expense) {
        transactions.voidExpense(expense.getId(), "Voided from expense history", true);
    }
}
