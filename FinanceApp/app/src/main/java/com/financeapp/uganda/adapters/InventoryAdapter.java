package com.financeapp.uganda.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.R;
import com.financeapp.uganda.models.InventoryItem;
import com.financeapp.uganda.utils.CurrencyFormatter;
import java.util.ArrayList;
import java.util.List;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.InventoryViewHolder> {
    private List<InventoryItem> items = new ArrayList<>();
    private OnInventoryClickListener listener;

    public interface OnInventoryClickListener {
        void onInventoryClick(InventoryItem item);
        void onInventoryLongClick(InventoryItem item);
    }

    public void setOnInventoryClickListener(OnInventoryClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<InventoryItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public InventoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inventory, parent, false);
        return new InventoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InventoryViewHolder holder, int position) {
        InventoryItem item = items.get(position);
        holder.tvName.setText(item.getName());
        holder.tvCategory.setText(item.getCategory());
        holder.tvPrice.setText(CurrencyFormatter.formatUGX(item.getUnitCost()));
        holder.tvSellingPrice.setText(CurrencyFormatter.formatUGX(item.getSellingPrice()));

        if (item.getQuantity() <= item.getMinStockLevel()) {
            holder.tvQuantity.setBackgroundResource(R.drawable.bg_chip_danger);
            holder.tvQuantity.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.danger));
            holder.tvQuantity.setText("Stock: " + item.getQuantity() + " LOW");
        } else {
            holder.tvQuantity.setBackgroundResource(R.drawable.bg_chip_success);
            holder.tvQuantity.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.success));
            holder.tvQuantity.setText("Stock: " + item.getQuantity());
        }

        holder.tvSource.setText("Source: " + (item.getImportSource() != null ? item.getImportSource() : "Local"));
        holder.tvProfit.setText(CurrencyFormatter.formatUGX(item.getProfitMargin()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class InventoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCategory, tvQuantity, tvPrice, tvSellingPrice, tvSource, tvProfit;

        InventoryViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvSellingPrice = itemView.findViewById(R.id.tvSellingPrice);
            tvSource = itemView.findViewById(R.id.tvSource);
            tvProfit = itemView.findViewById(R.id.tvProfit);

            itemView.setOnClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onInventoryClick(items.get(getAdapterPosition()));
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onInventoryLongClick(items.get(getAdapterPosition()));
                    return true;
                }
                return false;
            });
        }
    }
}
