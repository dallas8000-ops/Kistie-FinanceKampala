package com.financeapp.uganda;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.adapters.SaleAdapter;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.Sale;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.financeapp.uganda.receipts.SaleReceiptShareHelper;
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(sale.getItemName())
                .setMessage("Qty: " + sale.getQuantitySold() + "\nAmount: " + CurrencyFormatter.formatUGX(sale.getTotalAmount())
                    + "\nPaid: " + CurrencyFormatter.formatUGX(sale.getAmountPaid())
                    + "\nBalance due: " + CurrencyFormatter.formatUGX(sale.getTotalAmount() - sale.getAmountPaid())
                        + "\nProfit: " + CurrencyFormatter.formatUGX(sale.getProfit())
                        + "\nPayment: " + sale.getPaymentMethod()
                        + (sale.getTransactionId() != null && !sale.getTransactionId().trim().isEmpty()
                            ? "\nTransaction reference: " + sale.getTransactionId().trim() : "")
                    + ("VOID".equals(sale.getStatus()) ? "\nStatus: VOID\nReason: " + sale.getVoidReason() : "")
                        + (sale.getCustomerName() != null && !sale.getCustomerName().isEmpty() ? "\nCustomer: " + sale.getCustomerName() : ""))
                .setNeutralButton("Share receipt", (dialog, which) -> shareReceipt(sale));
            if (!"VOID".equals(sale.getStatus())) {
                builder.setPositiveButton("Edit", (dialog, which) -> {
                    Intent intent = new Intent(this, AddSaleActivity.class);
                    intent.putExtra("sale_id", sale.getId());
                    startActivity(intent);
                });
                builder.setNegativeButton("Void", (d, w) -> showVoidSaleDialog(sale));
            } else {
                builder.setPositiveButton("Close", null);
            }
            builder.show();
    }

    private void shareReceipt(Sale sale) {
        try {
            SaleReceiptShareHelper.createAndShare(this, sale);
        } catch (java.io.IOException | RuntimeException e) {
            android.widget.Toast.makeText(this, "Receipt could not be shared.", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void showVoidSaleDialog(Sale sale) {
        android.widget.EditText reasonInput = new android.widget.EditText(this);
        reasonInput.setTag("void-reason");
        reasonInput.setHint("Reason for voiding");
        double refundAmount = sale.getAmountPaid();
        if (sale.getReceivableId() > 0) {
            for (com.financeapp.uganda.models.ReceivablePayment payment
                    : db.financeDao().getReceivablePayments(sale.getReceivableId())) {
                if ("POSTED".equals(payment.getStatus())
                        && payment.getJournalEntryId() != sale.getJournalEntryId()) {
                    refundAmount += payment.getAmount();
                }
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Void sale")
                .setMessage("This restores " + sale.getQuantitySold() + " item(s) to stock and reverses "
                        + CurrencyFormatter.formatUGX(refundAmount)
                        + " received. Confirm that collected money has been refunded.")
                .setView(reasonInput)
                .setPositiveButton("Confirm refund and void", (dialog, which) -> {
                    try {
                        new com.financeapp.uganda.accounting.FinanceTransactionService(this, db).voidSale(
                                sale.getId(), reasonInput.getText().toString(), true);
                        load();
                    } catch (IllegalArgumentException e) {
                        android.widget.Toast.makeText(this, e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override protected void onResume() { super.onResume(); load(); }
}
