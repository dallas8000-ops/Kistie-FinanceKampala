package com.financeapp.uganda.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.financeapp.uganda.CapitalSetupActivity;
import com.financeapp.uganda.LedgerActivity;
import com.financeapp.uganda.PayablesActivity;
import com.financeapp.uganda.R;
import com.financeapp.uganda.ReceivablesActivity;
import com.financeapp.uganda.SalesActivity;
import com.financeapp.uganda.TrialBalanceActivity;
import com.financeapp.uganda.accounting.AccountingService;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.financeapp.uganda.utils.DashboardMenuHelper;

public class AccountingFragment extends Fragment {

    private AccountingService accounting;
    private TextView tvCapitalBalance;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_accounting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        accounting = new AccountingService(AppDatabase.getInstance(requireContext()).financeDao());
        tvCapitalBalance = view.findViewById(R.id.tvCapitalBalance);
        view.findViewById(R.id.cardCapital).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CapitalSetupActivity.class)));

        DashboardMenuHelper.bind(view, R.id.btnInitialCapital, R.drawable.ic_capital,
                "Initial Capital", "Set or edit opening investment", CapitalSetupActivity.class);
        DashboardMenuHelper.bind(view, R.id.btnLedger, R.drawable.ic_ledger,
                "General Ledger", "Double-entry journal — view & edit", LedgerActivity.class);
        DashboardMenuHelper.bind(view, R.id.btnTrialBalance, R.drawable.ic_chart,
                "Trial Balance", "All accounts linked to capital", TrialBalanceActivity.class);
        DashboardMenuHelper.bind(view, R.id.btnReceivables, R.drawable.ic_profit,
                "Accounts Receivable", "Money customers owe you", ReceivablesActivity.class);
        DashboardMenuHelper.bind(view, R.id.btnPayables, R.drawable.ic_expense,
                "Accounts Payable", "Bills & supplier debts", PayablesActivity.class);
        DashboardMenuHelper.bind(view, R.id.btnSalesHistory, R.drawable.ic_sales,
                "Sales History", "Revenue posted to ledger", SalesActivity.class);
        refreshCapital();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isAdded()) refreshCapital();
    }

    private void refreshCapital() {
        if (accounting.hasInitialCapital()) {
            tvCapitalBalance.setText(CurrencyFormatter.formatUGX(accounting.getInitialCapitalBalance()) + " invested");
        } else {
            tvCapitalBalance.setText("Not set — tap to configure");
        }
    }
}
