package com.financeapp.uganda.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.R;
import com.financeapp.uganda.models.AccountPayable;
import com.financeapp.uganda.utils.CurrencyFormatter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PayableAdapter extends RecyclerView.Adapter<PayableAdapter.Holder> {
    private List<AccountPayable> items = new ArrayList<>();
    private Listener listener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public interface Listener {
        void onClick(AccountPayable ap);
        void onLongClick(AccountPayable ap);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void setItems(List<AccountPayable> items) {
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
        AccountPayable ap = items.get(position);
        h.tvName.setText(ap.getVendorName());
        h.tvDescription.setText(ap.getDescription());
        h.tvPaidInfo.setText("Total " + CurrencyFormatter.formatUGX(ap.getAmount())
            + "  ·  Paid " + CurrencyFormatter.formatUGX(ap.getAmountPaid()));
        h.tvAmount.setText(CurrencyFormatter.formatUGX(ap.getBalanceDue()) + " due");
        h.tvStatus.setText(ap.getStatus());
        h.tvDue.setText(ap.getDueDate() != null ? "Due " + fmt.format(ap.getDueDate()) : "");
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
