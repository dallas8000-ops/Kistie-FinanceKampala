package com.financeapp.uganda;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.adapters.SaleAdapter;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.Sale;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class SalesActivity extends BaseToolbarActivity {

    private AppDatabase db;
    private SaleAdapter adapter;
    private TextView tvTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupToolbar("Sales History");
        db = AppDatabase.getInstance(this);
        tvTotal = findViewById(R.id.tvTotal);
        RecyclerView rv = findViewById(R.id.recyclerView);
        adapter = new SaleAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> startActivity(new android.content.Intent(this, AddSaleActivity.class)));
        adapter.setListener(this::showSaleDetail);
        load();
    }

    private void load() {
        adapter.setSales(db.financeDao().getAllSales());
        tvTotal.setText("Total: " + CurrencyFormatter.formatUGX(db.financeDao().getTotalSales()));
    }

    private void showSaleDetail(Sale sale) {
        new AlertDialog.Builder(this)
                .setTitle(sale.getItemName())
                .setMessage("Qty: " + sale.getQuantitySold() + "\nAmount: " + CurrencyFormatter.formatUGX(sale.getTotalAmount())
                        + "\nProfit: " + CurrencyFormatter.formatUGX(sale.getProfit())
                        + "\nPayment: " + sale.getPaymentMethod()
                        + (sale.getCustomerName() != null && !sale.getCustomerName().isEmpty() ? "\nCustomer: " + sale.getCustomerName() : ""))
                .setPositiveButton("OK", null)
                .setNegativeButton("Delete", (d, w) -> {
                    db.financeDao().deleteSale(sale);
                    load();
                })
                .show();
    }

    @Override protected void onResume() { super.onResume(); load(); }
}
