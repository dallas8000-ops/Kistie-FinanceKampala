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
import com.financeapp.uganda.accounting.FinanceTransactionService;
import com.financeapp.uganda.adapters.InventoryAdapter;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.InventoryItem;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.Date;
import java.util.List;

public class InventoryActivity extends BaseToolbarActivity {

    private RecyclerView recyclerView;
    private InventoryAdapter adapter;
    private AppDatabase db;
    private TextView tvTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupToolbar("Inventory Control");

        db = AppDatabase.getInstance(this);
        recyclerView = findViewById(R.id.recyclerView);
        tvTotal = findViewById(R.id.tvTotal);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InventoryAdapter();
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showAddInventoryDialog());

        adapter.setOnInventoryClickListener(new InventoryAdapter.OnInventoryClickListener() {
            @Override
            public void onInventoryClick(InventoryItem item) {}

            @Override
            public void onInventoryLongClick(InventoryItem item) {
                showDeleteDialog(item);
            }
        });

        loadInventory();
    }

    private void loadInventory() {
        List<InventoryItem> items = db.financeDao().getAllInventory();
        adapter.setItems(items);
        double value = db.financeDao().getTotalInventoryValue();
        int count = db.financeDao().getTotalStockCount();
        tvTotal.setText(CurrencyFormatter.formatUGX(value) + "  ·  " + count + " items");
    }

    private void showAddInventoryDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_inventory, null);
        EditText etName = view.findViewById(R.id.etName);
        Spinner spinnerCategory = view.findViewById(R.id.spinnerCategory);
        EditText etSize = view.findViewById(R.id.etSize);
        EditText etColor = view.findViewById(R.id.etColor);
        EditText etQuantity = view.findViewById(R.id.etQuantity);
        EditText etUnitCost = view.findViewById(R.id.etUnitCost);
        EditText etSellingPrice = view.findViewById(R.id.etSellingPrice);
        EditText etMinStock = view.findViewById(R.id.etMinStock);
        Spinner spinnerSource = view.findViewById(R.id.spinnerSource);
        EditText etSupplier = view.findViewById(R.id.etSupplier);

        spinnerCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"clothing", "accessories", "shoes"}));
        spinnerSource.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Turkey", "China", "Local"}));

        new AlertDialog.Builder(this)
                .setTitle("Add Inventory Item")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String qtyStr = etQuantity.getText().toString().trim();
                    String costStr = etUnitCost.getText().toString().trim();
                    String sellStr = etSellingPrice.getText().toString().trim();
                    if (name.isEmpty() || qtyStr.isEmpty() || costStr.isEmpty() || sellStr.isEmpty()) {
                        Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    InventoryItem item = new InventoryItem();
                    item.setName(name);
                    item.setCategory(spinnerCategory.getSelectedItem().toString());
                    item.setSize(etSize.getText().toString().trim());
                    item.setColor(etColor.getText().toString().trim());
                    item.setQuantity(Integer.parseInt(qtyStr));
                    item.setUnitCost(Double.parseDouble(costStr));
                    item.setSellingPrice(Double.parseDouble(sellStr));
                    item.setMinStockLevel(etMinStock.getText().toString().isEmpty() ? 5 : Integer.parseInt(etMinStock.getText().toString()));
                    item.setImportSource(spinnerSource.getSelectedItem().toString());
                    item.setSupplier(etSupplier.getText().toString().trim());
                    item.setDateAdded(new Date());
                    new FinanceTransactionService(this, db).recordInventoryItem(item);
                    loadInventory();
                    Toast.makeText(this, "Item added!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteDialog(InventoryItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Item")
                .setMessage("Delete " + item.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    new FinanceTransactionService(this, db).deleteInventoryItem(item.getId());
                    loadInventory();
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInventory();
    }
}
