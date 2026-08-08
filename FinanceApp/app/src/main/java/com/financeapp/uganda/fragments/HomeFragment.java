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
import com.financeapp.uganda.AddSaleActivity;
import com.financeapp.uganda.CapitalSetupActivity;
import com.financeapp.uganda.R;
import com.financeapp.uganda.accounting.AccountingService;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.utils.CurrencyFormatter;
import java.util.Calendar;
import java.util.Date;

public class HomeFragment extends Fragment {

    private AppDatabase db;
    private AccountingService accounting;
    private TextView tvTotalSales, tvTotalProfit, tvTotalExpenses, tvInventoryValue, tvCapitalBalance;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = AppDatabase.getInstance(requireContext());
        accounting = new AccountingService(db.financeDao());
        tvTotalSales = view.findViewById(R.id.tvTotalSales);
        tvTotalProfit = view.findViewById(R.id.tvTotalProfit);
        tvTotalExpenses = view.findViewById(R.id.tvTotalExpenses);
        tvInventoryValue = view.findViewById(R.id.tvInventoryValue);
        tvCapitalBalance = view.findViewById(R.id.tvCapitalBalance);
        View cardCapital = view.findViewById(R.id.cardCapital);
        cardCapital.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CapitalSetupActivity.class)));
        view.findViewById(R.id.btnAddSale).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddSaleActivity.class)));
        refreshData();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isAdded()) refreshData();
    }

    private void refreshData() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startOfMonth = cal.getTime();
        Date now = new Date();

        tvTotalSales.setText(CurrencyFormatter.formatUGX(db.financeDao().getTotalSalesBetweenDates(startOfMonth, now)));
        tvTotalProfit.setText(CurrencyFormatter.formatUGX(db.financeDao().getTotalProfitBetweenDates(startOfMonth, now)));
        tvTotalExpenses.setText(CurrencyFormatter.formatUGX(db.financeDao().getTotalExpensesBetweenDates(startOfMonth, now)));
        tvInventoryValue.setText(CurrencyFormatter.formatUGX(db.financeDao().getTotalInventoryValue()));

        if (accounting.hasInitialCapital()) {
            tvCapitalBalance.setText(CurrencyFormatter.formatUGX(accounting.getInitialCapitalBalance()) + " invested");
        } else {
            tvCapitalBalance.setText("Not set — tap to configure");
        }
    }
}
