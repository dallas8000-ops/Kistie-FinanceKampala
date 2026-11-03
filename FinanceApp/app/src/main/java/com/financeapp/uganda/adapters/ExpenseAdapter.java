package com.financeapp.uganda.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.R;
import com.financeapp.uganda.models.Expense;
import com.financeapp.uganda.utils.CurrencyFormatter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder> {
    private List<Expense> expenses = new ArrayList<>();
    private OnExpenseClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public interface OnExpenseClickListener {
        void onExpenseClick(Expense expense);
        void onExpenseLongClick(Expense expense);
    }

    public void setOnExpenseClickListener(OnExpenseClickListener listener) {
        this.listener = listener;
    }

    public void setExpenses(List<Expense> expenses) {
        this.expenses = expenses != null ? expenses : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ExpenseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_expense, parent, false);
        return new ExpenseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExpenseViewHolder holder, int position) {
        Expense expense = expenses.get(position);
        holder.tvDescription.setText(expense.getDescription());
        holder.tvAmount.setText(CurrencyFormatter.formatUGX(expense.getAmount()));
        holder.tvDate.setText(expense.getDate() != null ? dateFormat.format(expense.getDate()) : "");
        holder.tvCategory.setText(expense.getCategory() != null ? expense.getCategory().replace("_", " ").toUpperCase(Locale.ROOT) : "");

        if ("VOID".equals(expense.getStatus())) {
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText("VOID");
        } else if (expense.getShipmentStatus() != null && !expense.getShipmentStatus().isEmpty()) {
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText("Status: " + expense.getShipmentStatus().toUpperCase(Locale.ROOT));
        } else {
            holder.tvStatus.setVisibility(View.GONE);
        }

        StringBuilder details = new StringBuilder();
        if (expense.getImportSource() != null && !expense.getImportSource().isEmpty()) {
            details.append("From: ").append(expense.getImportSource());
        }
        if (expense.getReceiptNumber() != null && !expense.getReceiptNumber().trim().isEmpty()) {
            if (details.length() > 0) details.append(" · ");
            details.append("Ref: ").append(expense.getReceiptNumber().trim());
        }
        if (details.length() > 0) {
            holder.tvSource.setVisibility(View.VISIBLE);
            holder.tvSource.setText(details.toString());
        } else {
            holder.tvSource.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return expenses.size();
    }

    class ExpenseViewHolder extends RecyclerView.ViewHolder {
        TextView tvDescription;
        TextView tvAmount;
        TextView tvDate;
        TextView tvCategory;
        TextView tvStatus;
        TextView tvSource;

        ExpenseViewHolder(View itemView) {
            super(itemView);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvSource = itemView.findViewById(R.id.tvSource);

            itemView.setOnClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onExpenseClick(expenses.get(getAdapterPosition()));
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onExpenseLongClick(expenses.get(getAdapterPosition()));
                    return true;
                }
                return false;
            });
        }
    }
}
