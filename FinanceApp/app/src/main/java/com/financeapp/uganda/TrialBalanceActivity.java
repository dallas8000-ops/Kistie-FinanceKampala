package com.financeapp.uganda;

import android.os.Bundle;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.accounting.AccountingService;
import com.financeapp.uganda.accounting.TrialBalanceLine;
import com.financeapp.uganda.adapters.TrialBalanceAdapter;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.utils.CurrencyFormatter;
import java.util.List;

public class TrialBalanceActivity extends BaseToolbarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trial_balance);
        setupToolbar("Trial Balance");

        AppDatabase db = AppDatabase.getInstance(this);
        AccountingService accounting = new AccountingService(db.financeDao());
        RecyclerView rv = findViewById(R.id.recyclerView);
        TextView tvStatus = findViewById(R.id.tvBalanceStatus);
        TextView tvLegacyWarning = findViewById(R.id.tvLegacyWarning);

        TrialBalanceAdapter adapter = new TrialBalanceAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        List<TrialBalanceLine> lines = accounting.getTrialBalance();
        adapter.setLines(lines);

        int legacySales = db.financeDao().getLegacySaleCount();
        int legacyExpenses = db.financeDao().getLegacyExpenseCount();
        if (legacySales > 0 || legacyExpenses > 0) {
            tvLegacyWarning.setVisibility(android.view.View.VISIBLE);
            tvLegacyWarning.setText("Needs review: " + legacySales + " sale(s) and " + legacyExpenses
                + " expense(s) have no linked journal entry. No historical entries were added automatically.");
        }

        if (!lines.isEmpty()) {
            TrialBalanceLine total = lines.get(lines.size() - 1);
            boolean ok = Math.abs(total.debit - total.credit) < 0.01;
            tvStatus.setText(ok
                    ? "Balanced: " + CurrencyFormatter.formatUGX(total.debit)
                    : "Out of balance by " + CurrencyFormatter.formatUGX(Math.abs(total.debit - total.credit)));
            tvStatus.setTextColor(getColor(ok ? R.color.success : R.color.danger));
        }
    }
}
