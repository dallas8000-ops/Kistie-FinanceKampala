package com.financeapp.uganda.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.financeapp.uganda.R;
import com.financeapp.uganda.ReportsActivity;
import com.financeapp.uganda.TrialBalanceActivity;
import com.financeapp.uganda.utils.DashboardMenuHelper;

public class ReportsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reports, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        DashboardMenuHelper.bind(view, R.id.btnReports, R.drawable.ic_chart,
                "Reports & Analytics", "Charts and summaries", ReportsActivity.class);
        DashboardMenuHelper.bind(view, R.id.btnTrialBalance, R.drawable.ic_ledger,
                "Trial Balance", "Verify books are balanced", TrialBalanceActivity.class);
    }
}
