package com.financeapp.uganda.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.R;
import com.financeapp.uganda.models.AccountReceivable;
import com.financeapp.uganda.utils.CurrencyFormatter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReceivableAdapter extends RecyclerView.Adapter<ReceivableAdapter.Holder> {
    private List<AccountReceivable> items = new ArrayList<>();
    private Listener listener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public interface Listener {
        void onClick(AccountReceivable ar);
        void onLongClick(AccountReceivable ar);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void setItems(List<AccountReceivable> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ar_ap, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AccountReceivable ar = items.get(position);
        h.tvName.setText(ar.getCustomerName());
        h.tvDescription.setText(ar.getDescription());
        h.tvPaidInfo.setText("Total " + CurrencyFormatter.formatUGX(ar.getAmount())
            + "  ·  Paid " + CurrencyFormatter.formatUGX(ar.getAmountPaid()));
        h.tvAmount.setText(CurrencyFormatter.formatUGX(ar.getBalanceDue()) + " due");
        h.tvStatus.setText(ar.getStatus());
        h.tvDue.setText(ar.getDueDate() != null ? "Due " + fmt.format(ar.getDueDate()) : "");
    }

    @Override
    public int getItemCount() { return items.size(); }

    class Holder extends RecyclerView.ViewHolder {
        TextView tvName, tvDescription, tvPaidInfo, tvAmount, tvStatus, tvDue;
        Holder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvDescription = v.findViewById(R.id.tvDescription);
            tvPaidInfo = v.findViewById(R.id.tvPaidInfo);
            tvAmount = v.findViewById(R.id.tvAmount);
            tvStatus = v.findViewById(R.id.tvStatus);
            tvDue = v.findViewById(R.id.tvDue);
            v.setOnClickListener(x -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION)
                    listener.onClick(items.get(getAdapterPosition()));
            });
            v.setOnLongClickListener(x -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onLongClick(items.get(getAdapterPosition()));
                    return true;
                }
                return false;
            });
        }
    }
}
