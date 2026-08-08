package com.financeapp.uganda.adapters;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.R;
import com.financeapp.uganda.accounting.TrialBalanceLine;
import com.financeapp.uganda.utils.CurrencyFormatter;
import java.util.ArrayList;
import java.util.List;

public class TrialBalanceAdapter extends RecyclerView.Adapter<TrialBalanceAdapter.Holder> {
    private List<TrialBalanceLine> lines = new ArrayList<>();

    public void setLines(List<TrialBalanceLine> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trial_balance, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        TrialBalanceLine line = lines.get(position);
        boolean isTotal = line.accountId == -1;
        h.tvAccount.setText(isTotal ? "TOTAL" : line.code + "  " + line.name);
        h.tvDebit.setText(line.debit > 0 ? CurrencyFormatter.formatUGX(line.debit) : "—");
        h.tvCredit.setText(line.credit > 0 ? CurrencyFormatter.formatUGX(line.credit) : "—");
        if (isTotal) {
            h.tvAccount.setTypeface(null, Typeface.BOLD);
            h.tvDebit.setTypeface(null, Typeface.BOLD);
            h.tvCredit.setTypeface(null, Typeface.BOLD);
        }
    }

    @Override
    public int getItemCount() { return lines.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvAccount, tvDebit, tvCredit;
        Holder(View v) {
            super(v);
            tvAccount = v.findViewById(R.id.tvAccount);
            tvDebit = v.findViewById(R.id.tvDebit);
            tvCredit = v.findViewById(R.id.tvCredit);
        }
    }
}
