package com.financeapp.uganda;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.accounting.FinanceTransactionService;
import com.financeapp.uganda.adapters.ShipmentAdapter;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.InventoryItem;
import com.financeapp.uganda.models.Shipment;
import com.financeapp.uganda.models.ShipmentLine;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        adapter.setOnShipmentClickListener(this::showShipmentActions);
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
        EditText etShippingCost = view.findViewById(R.id.etShippingCost);
        EditText etInsuranceCost = view.findViewById(R.id.etInsuranceCost);
        EditText etImportDuty = view.findViewById(R.id.etImportDuty);
        EditText etVat = view.findViewById(R.id.etVat);
        EditText etClearingCharges = view.findViewById(R.id.etClearingCharges);
        EditText etTransportToStore = view.findViewById(R.id.etTransportToStore);
        EditText etTrackingNumber = view.findViewById(R.id.etTrackingNumber);
        EditText etNotes = view.findViewById(R.id.etNotes);

        etBatchId.setText("BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        spinnerCountry.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Turkey", "China"}));
        spinnerStatus.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
            new String[]{"ordered", "shipped", "in_transit", "at_port", "customs", "cleared"}));

        new AlertDialog.Builder(this)
                .setTitle("Add Import Shipment")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    String batchId = etBatchId.getText().toString().trim();
                    if (batchId.isEmpty()) {
                        Toast.makeText(this, "Batch ID required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Shipment shipment = new Shipment();
                    shipment.setBatchId(batchId);
                    shipment.setSourceCountry(spinnerCountry.getSelectedItem().toString());
                    shipment.setSupplierName(etSupplier.getText().toString().trim());
                    shipment.setStatus(spinnerStatus.getSelectedItem().toString());
                    shipment.setTotalValue(0);
                    shipment.setShippingCost(parseDouble(etShippingCost.getText().toString()));
                    shipment.setInsuranceCost(parseDouble(etInsuranceCost.getText().toString()));
                    shipment.setImportDuty(parseDouble(etImportDuty.getText().toString()));
                    shipment.setVat(parseDouble(etVat.getText().toString()));
                    shipment.setClearingCharges(parseDouble(etClearingCharges.getText().toString()));
                    shipment.setTransportToStore(parseDouble(etTransportToStore.getText().toString()));
                    shipment.setTrackingNumber(etTrackingNumber.getText().toString().trim());
                    shipment.setNotes(etNotes.getText().toString().trim());
                    shipment.setOrderDate(new Date());
                    shipment.setDelivered(false);
                    shipment.setId((int) db.financeDao().insertShipment(shipment));
                    loadShipments();
                    Toast.makeText(this, "Shipment added!", Toast.LENGTH_SHORT).show();
                    showShipmentActions(shipment);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showShipmentActions(Shipment shipment) {
        List<ShipmentLine> lines = db.financeDao().getShipmentLines(shipment.getId());
        boolean isReceived = shipment.isDelivered();
        boolean hasReceipts = !db.financeDao().getShipmentReceipts(shipment.getId()).isEmpty();
        String[] actions = isReceived
            ? new String[]{"View products", "Shipment status"}
            : hasReceipts
                ? new String[]{"View products", "Receive quantities", "Shipment status"}
                : new String[]{"Add product line", "View products", "Receive quantities", "Shipment status"};
        new AlertDialog.Builder(this)
                .setTitle(shipment.getBatchId() + " · " + lines.size() + " product lines")
                .setItems(actions, (dialog, which) -> {
                    String action = actions[which];
                    if ("Add product line".equals(action)) showAddShipmentLineDialog(shipment);
                    else if ("Receive quantities".equals(action)) showReceiveDialog(shipment);
                    else if ("View products".equals(action)) showShipmentLines(shipment);
                    else showStatusUpdateDialog(shipment);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showAddShipmentLineDialog(Shipment shipment) {
        List<InventoryItem> inventory = db.financeDao().getAllInventory();
        if (inventory.isEmpty()) {
            Toast.makeText(this, "Add an inventory item before adding a shipment line.", Toast.LENGTH_LONG).show();
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_shipment_line, null);
        Spinner spinnerItem = view.findViewById(R.id.spinnerLineItem);
        Spinner spinnerCurrency = view.findViewById(R.id.spinnerLineCurrency);
        EditText etQuantity = view.findViewById(R.id.etOrderQty);
        EditText etUnitCost = view.findViewById(R.id.etLineUnitCost);
        EditText etExchangeRate = view.findViewById(R.id.etExchangeRate);
        TextView tvLineValue = view.findViewById(R.id.tvLineValue);
        List<String> itemLabels = new java.util.ArrayList<>();
        for (InventoryItem item : inventory) itemLabels.add(item.getName() + " (stock " + item.getQuantity() + ")");
        spinnerItem.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, itemLabels));
        String[] currencies = {"UGX", "USD", "TRY", "CNY"};
        spinnerCurrency.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies));
        etExchangeRate.setText("1");
        spinnerCurrency.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View selectedView,
                                                int position, long id) {
                etExchangeRate.setText(position == 0 ? "1" : "");
                updateLinePreview(etQuantity, etUnitCost, etExchangeRate, tvLineValue);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        TextWatcher previewWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateLinePreview(etQuantity, etUnitCost, etExchangeRate, tvLineValue);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        etQuantity.addTextChangedListener(previewWatcher);
        etUnitCost.addTextChangedListener(previewWatcher);
        etExchangeRate.addTextChangedListener(previewWatcher);

        new AlertDialog.Builder(this).setTitle("Add shipment product")
                .setView(view)
                .setPositiveButton("Add line", (dialog, which) -> {
                    try {
                        ShipmentLine line = new ShipmentLine();
                        line.setShipmentId(shipment.getId());
                        line.setInventoryItemId(inventory.get(spinnerItem.getSelectedItemPosition()).getId());
                        line.setOrderedQuantity(Integer.parseInt(etQuantity.getText().toString().trim()));
                        line.setUnitCost(Double.parseDouble(etUnitCost.getText().toString().trim()));
                        line.setCurrencyCode(spinnerCurrency.getSelectedItem().toString());
                        line.setExchangeRate(Double.parseDouble(etExchangeRate.getText().toString().trim()));
                        new FinanceTransactionService(db).addShipmentLine(line);
                        loadShipments();
                    } catch (RuntimeException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void showReceiveDialog(Shipment shipment) {
        List<ShipmentLine> lines = db.financeDao().getShipmentLines(shipment.getId());
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        TextView preview = new TextView(this);
        preview.setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body1);
        content.addView(preview);
        Map<Integer, EditText> receivedFields = new HashMap<>();
        for (ShipmentLine line : lines) {
            int remaining = line.getOrderedQuantity() - line.getQuantityStocked();
            if (remaining <= 0) continue;
            InventoryItem item = db.financeDao().getInventoryById(line.getInventoryItemId());
            TextView label = new TextView(this);
            label.setText(item.getName() + " · " + remaining + " remaining");
            content.addView(label);
            EditText quantity = new EditText(this);
            quantity.setHint("Quantity received now");
            quantity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            content.addView(quantity);
            receivedFields.put(line.getId(), quantity);
            quantity.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateReceivePreview(shipment, receivedFields, preview);
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
        if (receivedFields.isEmpty()) {
            Toast.makeText(this, "All ordered quantities have already been received.", Toast.LENGTH_LONG).show();
            return;
        }
        updateReceivePreview(shipment, receivedFields, preview);
        String receiptReference = "RCV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        new AlertDialog.Builder(this).setTitle("Receive " + shipment.getBatchId())
                .setView(content)
                .setPositiveButton("Receive stock", (dialog, which) -> {
                    try {
                        Map<Integer, Integer> quantities = new HashMap<>();
                        for (Map.Entry<Integer, EditText> entry : receivedFields.entrySet()) {
                            String text = entry.getValue().getText().toString().trim();
                            if (!text.isEmpty() && Integer.parseInt(text) > 0) {
                                quantities.put(entry.getKey(), Integer.parseInt(text));
                            }
                        }
                        new FinanceTransactionService(db).receiveShipment(
                                shipment.getId(), quantities, new Date(), receiptReference);
                        loadShipments();
                        Toast.makeText(this, "Stock received and landed costs posted.", Toast.LENGTH_LONG).show();
                    } catch (RuntimeException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void updateReceivePreview(Shipment shipment, Map<Integer, EditText> fields, TextView preview) {
        List<ShipmentLine> lines = db.financeDao().getShipmentLines(shipment.getId());
        double orderValue = 0;
        double receivedValue = 0;
        for (ShipmentLine line : lines) {
            double unitValue = line.getUnitCost() * line.getExchangeRate();
            orderValue += line.getOrderedQuantity() * unitValue;
            EditText field = fields.get(line.getId());
            if (field != null) {
                int quantity = (int) parseDouble(field.getText().toString());
                receivedValue += quantity * unitValue;
            }
        }
        double charges = shipment.getShippingCost() + shipment.getInsuranceCost()
                + shipment.getImportDuty() + shipment.getVat() + shipment.getClearingCharges()
                + shipment.getTransportToStore();
        double allocatedCharges = orderValue > 0 ? charges * receivedValue / orderValue : 0;
        preview.setText("Goods " + CurrencyFormatter.formatUGX(receivedValue)
                + "  ·  Allocated charges " + CurrencyFormatter.formatUGX(allocatedCharges)
                + "  ·  Landed total " + CurrencyFormatter.formatUGX(receivedValue + allocatedCharges));
    }

    private void showShipmentLines(Shipment shipment) {
        StringBuilder details = new StringBuilder();
        for (ShipmentLine line : db.financeDao().getShipmentLines(shipment.getId())) {
            InventoryItem item = db.financeDao().getInventoryById(line.getInventoryItemId());
            List<com.financeapp.uganda.models.ShipmentReceiptLine> receipts =
                db.financeDao().getShipmentReceiptLinesForShipmentLine(line.getId());
            int receivedQuantity = 0;
            double landedValue = 0;
            for (com.financeapp.uganda.models.ShipmentReceiptLine receiptLine : receipts) {
            receivedQuantity += receiptLine.getQuantity();
            landedValue += receiptLine.getLandedUnitCostUgx() * receiptLine.getQuantity();
            }
            details.append(item.getName()).append(" · ")
                .append(receivedQuantity).append(" / ").append(line.getOrderedQuantity())
                .append(" received · Goods/unit ")
                    .append(CurrencyFormatter.formatUGX(line.getUnitCost() * line.getExchangeRate()))
                .append(" · Landed/unit ")
                .append(receivedQuantity > 0
                    ? CurrencyFormatter.formatUGX(landedValue / receivedQuantity)
                    : "pending receipt")
                .append('\n');
        }
        new AlertDialog.Builder(this).setTitle(shipment.getBatchId())
                .setMessage(details.length() == 0 ? "No products added." : details.toString())
                .setPositiveButton("Close", null).show();
    }

    private void showStatusUpdateDialog(Shipment shipment) {
        String[] statuses = {"ordered", "shipped", "in_transit", "at_port", "customs", "cleared"};
        new AlertDialog.Builder(this)
                .setTitle("Update Status: " + shipment.getBatchId())
                .setItems(statuses, (dialog, which) -> {
                    shipment.setStatus(statuses[which]);
                    shipment.setDelivered(false);
                    db.financeDao().updateShipment(shipment);
                    loadShipments();
                    Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateLinePreview(EditText quantity, EditText unitCost, EditText rate, TextView preview) {
        preview.setText("Line value: " + CurrencyFormatter.formatUGX(
                parseDouble(quantity.getText().toString())
                        * parseDouble(unitCost.getText().toString())
                        * parseDouble(rate.getText().toString())));
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
