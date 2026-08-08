package com.financeapp.uganda.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.R;
import com.financeapp.uganda.models.Shipment;
import com.financeapp.uganda.utils.CurrencyFormatter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShipmentAdapter extends RecyclerView.Adapter<ShipmentAdapter.ShipmentViewHolder> {
    private List<Shipment> shipments = new ArrayList<>();
    private OnShipmentClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public interface OnShipmentClickListener {
        void onShipmentClick(Shipment shipment);
    }

    public void setOnShipmentClickListener(OnShipmentClickListener listener) {
        this.listener = listener;
    }

    public void setShipments(List<Shipment> shipments) {
        this.shipments = shipments != null ? shipments : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ShipmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shipment, parent, false);
        return new ShipmentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShipmentViewHolder holder, int position) {
        Shipment shipment = shipments.get(position);
        holder.tvBatchId.setText("Batch: " + shipment.getBatchId());
        holder.tvSupplier.setText(shipment.getSupplierName());
        holder.tvSource.setText("From: " + shipment.getSourceCountry());
        String status = shipment.getStatus() != null ? shipment.getStatus() : "";
        holder.tvStatus.setText(status.isEmpty() ? "—" : status.toUpperCase());
        holder.tvTotalCost.setText("Total: " + CurrencyFormatter.formatUGX(shipment.getTotalImportCost()));

        if (shipment.getOrderDate() != null) {
            holder.tvOrderDate.setText("Ordered: " + dateFormat.format(shipment.getOrderDate()));
        }

        int textColor;
        int bgRes;
        switch (status) {
            case "delivered":
                textColor = R.color.success;
                bgRes = R.drawable.bg_chip_success;
                break;
            case "customs":
                textColor = R.color.warning;
                bgRes = R.drawable.bg_chip_warning;
                break;
            case "ordered":
                textColor = R.color.info;
                bgRes = R.drawable.bg_chip_info;
                break;
            default:
                textColor = R.color.text_secondary;
                bgRes = R.drawable.bg_summary_banner;
        }
        holder.tvStatus.setBackgroundResource(bgRes);
        holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), textColor));
    }

    @Override
    public int getItemCount() {
        return shipments.size();
    }

    class ShipmentViewHolder extends RecyclerView.ViewHolder {
        TextView tvBatchId, tvSupplier, tvSource, tvStatus, tvTotalCost, tvOrderDate;

        ShipmentViewHolder(View itemView) {
            super(itemView);
            tvBatchId = itemView.findViewById(R.id.tvBatchId);
            tvSupplier = itemView.findViewById(R.id.tvSupplier);
            tvSource = itemView.findViewById(R.id.tvSource);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvTotalCost = itemView.findViewById(R.id.tvTotalCost);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);

            itemView.setOnClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onShipmentClick(shipments.get(getAdapterPosition()));
                }
            });
        }
    }
}
