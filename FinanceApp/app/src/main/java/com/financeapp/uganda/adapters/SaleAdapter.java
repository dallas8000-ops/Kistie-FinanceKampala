package com.financeapp.uganda.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.R;
import com.financeapp.uganda.models.Sale;
import com.financeapp.uganda.utils.CurrencyFormatter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SaleAdapter extends RecyclerView.Adapter<SaleAdapter.Holder> {
    private List<Sale> sales = new ArrayList<>();
    private Listener listener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public interface Listener {
        void onClick(Sale sale);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void setSales(List<Sale> sales) {
        this.sales = sales != null ? sales : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_expense, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Sale s = sales.get(position);
        h.tvDescription.setText(s.getItemName() + " x" + s.getQuantitySold());
        h.tvCategory.setText(s.getPaymentMethod());
        h.tvAmount.setText(CurrencyFormatter.formatUGX(s.getTotalAmount()));
        h.tvDate.setText(s.getSaleDate() != null ? fmt.format(s.getSaleDate()) : "");
        h.tvStatus.setVisibility(View.GONE);
        h.tvSource.setVisibility(View.VISIBLE);
        h.tvSource.setText("Profit: " + CurrencyFormatter.formatUGX(s.getProfit()));
    }

    @Override
    public int getItemCount() { return sales.size(); }

    class Holder extends RecyclerView.ViewHolder {
        TextView tvDescription, tvCategory, tvAmount, tvDate, tvStatus, tvSource;
        Holder(View v) {
            super(v);
            tvDescription = v.findViewById(R.id.tvDescription);
            tvCategory = v.findViewById(R.id.tvCategory);
            tvAmount = v.findViewById(R.id.tvAmount);
            tvDate = v.findViewById(R.id.tvDate);
            tvStatus = v.findViewById(R.id.tvStatus);
            tvSource = v.findViewById(R.id.tvSource);
            v.setOnClickListener(x -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION)
                    listener.onClick(sales.get(getAdapterPosition()));
            });
        }
    }
}
