package com.financeapp.uganda;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.accounting.AccountCodes;
import com.financeapp.uganda.accounting.AccountingService;
import com.financeapp.uganda.adapters.PayableAdapter;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.AccountPayable;
import com.financeapp.uganda.models.JournalEntry;
import com.financeapp.uganda.models.JournalLine;
import com.financeapp.uganda.utils.CurrencyFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PayablesActivity extends BaseToolbarActivity {

    private AppDatabase db;
    private AccountingService accounting;
    private PayableAdapter adapter;
    private TextView tvTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupToolbar("Accounts Payable");
        db = AppDatabase.getInstance(this);
        accounting = new AccountingService(db.financeDao());
        tvTotal = findViewById(R.id.tvTotal);
        RecyclerView rv = findViewById(R.id.recyclerView);
        adapter = new PayableAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        findViewById(R.id.fabAdd).setOnClickListener(v -> showAddDialog(null));
        adapter.setListener(new PayableAdapter.Listener() {
            @Override public void onClick(AccountPayable ap) { showOptions(ap); }
            @Override public void onLongClick(AccountPayable ap) { showAddDialog(ap); }
        });
        load();
    }

    private void load() {
        adapter.setItems(db.financeDao().getAllPayables());
        tvTotal.setText("Outstanding: " + CurrencyFormatter.formatUGX(db.financeDao().getTotalPayableBalance()));
    }

    private void showOptions(AccountPayable ap) {
        new AlertDialog.Builder(this).setTitle(ap.getVendorName())
                .setItems(new String[]{"Record Payment", "Edit", "Delete"}, (d, i) -> {
                    if (i == 0) showPaymentDialog(ap);
                    else if (i == 1) showAddDialog(ap);
                    else deleteAp(ap);
                }).show();
    }

    private void showPaymentDialog(AccountPayable ap) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null);
        EditText etAmount = v.findViewById(R.id.etAmount);
        Spinner spinner = v.findViewById(R.id.spinnerPayment);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Cash", "Mobile Money", "Bank Transfer"}));
        etAmount.setText(String.valueOf((long) ap.getBalanceDue()));
        new AlertDialog.Builder(this).setTitle("Pay Vendor").setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    accounting.recordPayablePayment(ap, Double.parseDouble(etAmount.getText().toString()),
                            spinner.getSelectedItem().toString());
                    load();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showAddDialog(AccountPayable existing) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_ap, null);
        EditText etVendor = v.findViewById(R.id.etVendor);
        EditText etDesc = v.findViewById(R.id.etDescription);
        EditText etAmount = v.findViewById(R.id.etAmount);
        if (existing != null) {
            etVendor.setText(existing.getVendorName());
            etDesc.setText(existing.getDescription());
            etAmount.setText(String.valueOf((long) existing.getAmount()));
        }
        new AlertDialog.Builder(this).setTitle(existing == null ? "New Payable" : "Edit Payable").setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    double amt = Double.parseDouble(etAmount.getText().toString());
                    if (existing == null) {
                        AccountPayable ap = new AccountPayable();
                        ap.setVendorName(etVendor.getText().toString().trim());
                        ap.setDescription(etDesc.getText().toString().trim());
                        ap.setAmount(amt);
                        ap.setAmountPaid(0);
                        ap.setBillDate(new Date());
                        ap.setDueDate(new Date());
                        ap.setStatus("OPEN");
                        long id = db.financeDao().insertPayable(ap);
                        ap.setId((int) id);
                        List<JournalLine> lines = new ArrayList<>();
                        lines.add(new JournalLine(0, AccountCodes.OPERATING_EXPENSE, amt, 0, "Expense"));
                        lines.add(new JournalLine(0, AccountCodes.ACCOUNTS_PAYABLE, 0, amt, "AP"));
                        long jeId = accounting.postManualEntry(new Date(), ap.getDescription(), lines);
                        ap.setJournalEntryId((int) jeId);
                        db.financeDao().updatePayable(ap);
                    } else {
                        existing.setVendorName(etVendor.getText().toString().trim());
                        existing.setDescription(etDesc.getText().toString().trim());
                        existing.setAmount(amt);
                        accounting.updatePayable(existing);
                    }
                    load();
                }).setNegativeButton("Cancel", null).show();
    }

    private void deleteAp(AccountPayable ap) {
        if (ap.getJournalEntryId() > 0) {
            db.financeDao().deleteLinesForEntry(ap.getJournalEntryId());
            JournalEntry je = db.financeDao().getJournalEntryById(ap.getJournalEntryId());
            if (je != null) db.financeDao().deleteJournalEntry(je);
        }
        db.financeDao().deletePayable(ap);
        load();
    }

    @Override protected void onResume() { super.onResume(); load(); }
}
