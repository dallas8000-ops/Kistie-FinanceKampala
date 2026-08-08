package com.financeapp.uganda;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.adapters.ShipmentAdapter;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.Shipment;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ShipmentsActivity extends BaseToolbarActivity {

    private RecyclerView recyclerView;
    private ShipmentAdapter adapter;
    private AppDatabase db;
    private TextView tvTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupToolbar("Import Shipments");

        db = AppDatabase.getInstance(this);
        recyclerView = findViewById(R.id.recyclerView);
        tvTotal = findViewById(R.id.tvTotal);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ShipmentAdapter();
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showAddShipmentDialog());

        adapter.setOnShipmentClickListener(shipment -> showStatusUpdateDialog(shipment));
        loadShipments();
    }

    private void loadShipments() {
        List<Shipment> shipments = db.financeDao().getAllShipments();
        adapter.setShipments(shipments);
        double total = 0;
        for (Shipment s : shipments) {
            total += s.getTotalImportCost();
        }
        tvTotal.setText(shipments.size() + " shipments  ·  " + CurrencyFormatter.formatUGX(total));
    }

    private void showAddShipmentDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_shipment, null);
        EditText etBatchId = view.findViewById(R.id.etBatchId);
        Spinner spinnerCountry = view.findViewById(R.id.spinnerCountry);
        EditText etSupplier = view.findViewById(R.id.etSupplier);
        Spinner spinnerStatus = view.findViewById(R.id.spinnerStatus);
        EditText etTotalValue = view.findViewById(R.id.etTotalValue);
        EditText etShippingCost = view.findViewById(R.id.etShippingCost);
        EditText etImportDuty = view.findViewById(R.id.etImportDuty);
        EditText etClearingCharges = view.findViewById(R.id.etClearingCharges);
        EditText etTrackingNumber = view.findViewById(R.id.etTrackingNumber);
        EditText etNotes = view.findViewById(R.id.etNotes);

        etBatchId.setText("BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        spinnerCountry.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Turkey", "China"}));
        spinnerStatus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"ordered", "shipped", "in_transit", "at_port", "customs", "cleared", "delivered"}));

        new AlertDialog.Builder(this)
                .setTitle("Add Import Shipment")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    String batchId = etBatchId.getText().toString().trim();
                    String valueStr = etTotalValue.getText().toString().trim();
                    if (batchId.isEmpty() || valueStr.isEmpty()) {
                        Toast.makeText(this, "Batch ID and value required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Shipment shipment = new Shipment();
                    shipment.setBatchId(batchId);
                    shipment.setSourceCountry(spinnerCountry.getSelectedItem().toString());
                    shipment.setSupplierName(etSupplier.getText().toString().trim());
                    shipment.setStatus(spinnerStatus.getSelectedItem().toString());
                    shipment.setTotalValue(Double.parseDouble(valueStr));
                    shipment.setShippingCost(parseDouble(etShippingCost.getText().toString()));
                    shipment.setImportDuty(parseDouble(etImportDuty.getText().toString()));
                    shipment.setClearingCharges(parseDouble(etClearingCharges.getText().toString()));
                    shipment.setTrackingNumber(etTrackingNumber.getText().toString().trim());
                    shipment.setNotes(etNotes.getText().toString().trim());
                    shipment.setOrderDate(new Date());
                    shipment.setDelivered("delivered".equals(shipment.getStatus()));
                    db.financeDao().insertShipment(shipment);
                    loadShipments();
                    Toast.makeText(this, "Shipment added!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showStatusUpdateDialog(Shipment shipment) {
        String[] statuses = {"ordered", "shipped", "in_transit", "at_port", "customs", "cleared", "delivered"};
        new AlertDialog.Builder(this)
                .setTitle("Update Status: " + shipment.getBatchId())
                .setItems(statuses, (dialog, which) -> {
                    shipment.setStatus(statuses[which]);
                    shipment.setDelivered("delivered".equals(statuses[which]));
                    if ("delivered".equals(statuses[which])) {
                        shipment.setDeliveryDate(new Date());
                    }
                    db.financeDao().updateShipment(shipment);
                    loadShipments();
                    Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        return Double.parseDouble(value.trim());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadShipments();
    }
}
