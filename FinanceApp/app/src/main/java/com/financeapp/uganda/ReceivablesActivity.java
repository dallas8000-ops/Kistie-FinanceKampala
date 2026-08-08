package com.financeapp.uganda;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.accounting.AccountingService;
import com.financeapp.uganda.adapters.ReceivableAdapter;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.AccountReceivable;
import com.financeapp.uganda.models.JournalEntry;
import com.financeapp.uganda.models.JournalLine;
import com.financeapp.uganda.accounting.AccountCodes;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ReceivablesActivity extends BaseToolbarActivity {

    private AppDatabase db;
    private AccountingService accounting;
    private ReceivableAdapter adapter;
    private TextView tvTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupToolbar("Accounts Receivable");
        db = AppDatabase.getInstance(this);
        accounting = new AccountingService(db.financeDao());
        tvTotal = findViewById(R.id.tvTotal);
        RecyclerView rv = findViewById(R.id.recyclerView);
        adapter = new ReceivableAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        findViewById(R.id.fabAdd).setOnClickListener(v -> showAddDialog(null));
        adapter.setListener(new ReceivableAdapter.Listener() {
            @Override public void onClick(AccountReceivable ar) { showOptions(ar); }
            @Override public void onLongClick(AccountReceivable ar) { showAddDialog(ar); }
        });
        load();
    }

    private void load() {
        List<AccountReceivable> list = db.financeDao().getAllReceivables();
        adapter.setItems(list);
        tvTotal.setText("Outstanding: " + CurrencyFormatter.formatUGX(db.financeDao().getTotalReceivableBalance()));
    }

    private void showOptions(AccountReceivable ar) {
        String[] opts = {"Record Payment", "Edit", "Delete"};
        new AlertDialog.Builder(this).setTitle(ar.getCustomerName()).setItems(opts, (d, i) -> {
            if (i == 0) showPaymentDialog(ar);
            else if (i == 1) showAddDialog(ar);
            else deleteAr(ar);
        }).show();
    }

    private void showPaymentDialog(AccountReceivable ar) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null);
        EditText etAmount = v.findViewById(R.id.etAmount);
        Spinner spinner = v.findViewById(R.id.spinnerPayment);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Cash", "Mobile Money", "Bank Transfer"}));
        etAmount.setText(String.valueOf((long) ar.getBalanceDue()));
        new AlertDialog.Builder(this).setTitle("Record Payment").setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    double amt = Double.parseDouble(etAmount.getText().toString());
                    accounting.recordReceivablePayment(ar, amt, spinner.getSelectedItem().toString());
                    load();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showAddDialog(AccountReceivable existing) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_ar, null);
        EditText etCustomer = v.findViewById(R.id.etCustomer);
        EditText etDesc = v.findViewById(R.id.etDescription);
        EditText etAmount = v.findViewById(R.id.etAmount);
        if (existing != null) {
            etCustomer.setText(existing.getCustomerName());
            etDesc.setText(existing.getDescription());
            etAmount.setText(String.valueOf((long) existing.getAmount()));
        }
        new AlertDialog.Builder(this).setTitle(existing == null ? "New Receivable" : "Edit Receivable").setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    double amt = Double.parseDouble(etAmount.getText().toString());
                    if (existing == null) {
                        AccountReceivable ar = new AccountReceivable();
                        ar.setCustomerName(etCustomer.getText().toString().trim());
                        ar.setDescription(etDesc.getText().toString().trim());
                        ar.setAmount(amt);
                        ar.setAmountPaid(0);
                        ar.setInvoiceDate(new Date());
                        ar.setDueDate(new Date());
                        ar.setStatus("OPEN");
                        long id = db.financeDao().insertReceivable(ar);
                        ar.setId((int) id);
                        List<JournalLine> lines = new ArrayList<>();
                        lines.add(new JournalLine(0, AccountCodes.ACCOUNTS_RECEIVABLE, amt, 0, "AR"));
                        lines.add(new JournalLine(0, AccountCodes.SALES_REVENUE, 0, amt, "Revenue"));
                        long jeId = accounting.postManualEntry(new Date(), ar.getDescription(), lines);
                        ar.setJournalEntryId((int) jeId);
                        db.financeDao().updateReceivable(ar);
                    } else {
                        existing.setCustomerName(etCustomer.getText().toString().trim());
                        existing.setDescription(etDesc.getText().toString().trim());
                        existing.setAmount(amt);
                        accounting.updateReceivable(existing);
                    }
                    load();
                }).setNegativeButton("Cancel", null).show();
    }

    private void deleteAr(AccountReceivable ar) {
        if (ar.getJournalEntryId() > 0) {
            db.financeDao().deleteLinesForEntry(ar.getJournalEntryId());
            JournalEntry je = db.financeDao().getJournalEntryById(ar.getJournalEntryId());
            if (je != null) db.financeDao().deleteJournalEntry(je);
        }
        db.financeDao().deleteReceivable(ar);
        load();
    }

    @Override protected void onResume() { super.onResume(); load(); }
}
