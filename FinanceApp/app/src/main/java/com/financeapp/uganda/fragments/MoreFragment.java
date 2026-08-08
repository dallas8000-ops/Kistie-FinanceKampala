package com.financeapp.uganda.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.financeapp.uganda.AiAssistantActivity;
import com.financeapp.uganda.CapitalSetupActivity;
import com.financeapp.uganda.R;
import com.financeapp.uganda.SettingsActivity;
import com.financeapp.uganda.utils.DashboardMenuHelper;

public class MoreFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_more, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        DashboardMenuHelper.bind(view, R.id.btnAiAssistant, R.drawable.ic_ai,
                "AI Finance Assistant", "Ask questions about your books", AiAssistantActivity.class);
        DashboardMenuHelper.bind(view, R.id.btnSettings, R.drawable.ic_settings,
                "Settings", "OpenAI API key & preferences", SettingsActivity.class);
        DashboardMenuHelper.bind(view, R.id.btnInitialCapital, R.drawable.ic_capital,
                "Initial Capital", "Edit opening investment amount", CapitalSetupActivity.class);
    }
}
