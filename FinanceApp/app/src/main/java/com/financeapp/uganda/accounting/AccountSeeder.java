package com.financeapp.uganda.accounting;

import com.financeapp.uganda.database.FinanceDao;
import com.financeapp.uganda.models.Account;
import java.util.Arrays;
import java.util.List;

public final class AccountSeeder {

    private AccountSeeder() {}

    public static void seedIfNeeded(FinanceDao dao) {
        if (dao.getAccountCount() == 0) {
            dao.insertAccounts(Arrays.asList(
                    new Account(AccountCodes.CASH, "1000", "Cash", "ASSET", true),
                    new Account(AccountCodes.MOBILE_MONEY, "1010", "Mobile Money", "ASSET", true),
                    new Account(AccountCodes.BANK, "1020", "Bank Account", "ASSET", true),
                    new Account(AccountCodes.ACCOUNTS_RECEIVABLE, "1100", "Accounts Receivable", "ASSET", true),
                    new Account(AccountCodes.INVENTORY, "1200", "Inventory", "ASSET", true),
                    new Account(AccountCodes.ACCOUNTS_PAYABLE, "2000", "Accounts Payable", "LIABILITY", true),
                    new Account(AccountCodes.OWNER_EQUITY, "3000", "Owner's Equity", "EQUITY", true),
                    new Account(AccountCodes.INITIAL_CAPITAL, "3010", "Initial Capital", "EQUITY", true),
                    new Account(AccountCodes.SALES_REVENUE, "4000", "Sales Revenue", "REVENUE", true),
                    new Account(AccountCodes.COGS, "5000", "Cost of Goods Sold", "EXPENSE", true),
                    new Account(AccountCodes.OPERATING_EXPENSE, "5100", "Operating Expenses", "EXPENSE", true)
            ));
        } else if (dao.getAccountById(AccountCodes.INITIAL_CAPITAL) == null) {
            dao.insertAccount(new Account(AccountCodes.INITIAL_CAPITAL, "3010", "Initial Capital", "EQUITY", true));
        }
    }
}
