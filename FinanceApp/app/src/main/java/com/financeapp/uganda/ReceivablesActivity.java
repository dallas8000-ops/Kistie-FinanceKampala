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
import com.financeapp.uganda.accounting.FinanceTransactionService;
import com.financeapp.uganda.adapters.ReceivableAdapter;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.AccountReceivable;
import com.financeapp.uganda.models.ReceivablePayment;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.financeapp.uganda.utils.MobileMoneyPaymentUi;
import com.financeapp.uganda.utils.PaymentMethods;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ReceivablesActivity extends BaseToolbarActivity {

    private AppDatabase db;
    private ReceivableAdapter adapter;
    private TextView tvTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupToolbar("Accounts Receivable");
        db = AppDatabase.getInstance(this);
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
        if ("VOID".equals(ar.getStatus())) return;
        boolean canEdit = ar.getSaleId() == 0 && ar.getAmountPaid() == 0;
        List<String> options = new ArrayList<>();
        options.add("Payment History");
        options.add("Record Payment");
        if (canEdit) options.add("Edit");
        options.add("Void");
        new AlertDialog.Builder(this).setTitle(ar.getCustomerName()).setItems(
            options.toArray(new String[0]), (d, i) -> {
            String selected = options.get(i);
            if ("Payment History".equals(selected)) showPaymentHistory(ar);
            else if ("Record Payment".equals(selected)) showPaymentDialog(ar);
            else if ("Edit".equals(selected)) showAddDialog(ar);
            else showVoidDialog(ar);
        }).show();
    }

        private void showPaymentHistory(AccountReceivable ar) {
        StringBuilder history = new StringBuilder("Original: ")
            .append(CurrencyFormatter.formatUGX(ar.getAmount()))
            .append("\nPaid: ").append(CurrencyFormatter.formatUGX(ar.getAmountPaid()))
            .append("\nBalance: ").append(CurrencyFormatter.formatUGX(ar.getBalanceDue()));
        List<ReceivablePayment> payments = db.financeDao().getReceivablePayments(ar.getId());
        for (ReceivablePayment payment : payments) {
            history.append("\n\n")
                .append(payment.getPaidAt() != null
                    ? android.text.format.DateFormat.format("dd MMM yyyy", payment.getPaidAt()) : "Date unavailable")
                .append(" · ").append(CurrencyFormatter.formatUGX(payment.getAmount()))
                .append(" · ").append(payment.getPaymentMethod());
            if (payment.getReference() != null && !payment.getReference().isEmpty()) {
            history.append(" · ").append(payment.getReference());
            }
            if ("REVERSED".equals(payment.getStatus())) history.append(" · REVERSED");
        }
        if (payments.isEmpty()) history.append("\n\nNo payments recorded.");
        new AlertDialog.Builder(this).setTitle("Customer Payment History")
            .setMessage(history.toString()).setPositiveButton("Close", null).show();
        }

    private void showPaymentDialog(AccountReceivable ar) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null);
        EditText etAmount = v.findViewById(R.id.etAmount);
        EditText etReference = v.findViewById(R.id.etTransactionReference);
        TextView tvReceivingNumber = v.findViewById(R.id.tvReceivingNumber);
        Spinner spinner = v.findViewById(R.id.spinnerPayment);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
            PaymentMethods.options(false, false)));
        MobileMoneyPaymentUi.bind(this, spinner, etReference, etReference, tvReceivingNumber, true);
        etAmount.setText(String.valueOf((long) ar.getBalanceDue()));
        new AlertDialog.Builder(this).setTitle("Record Payment").setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    double amt = Double.parseDouble(etAmount.getText().toString());
                    try {
                        new FinanceTransactionService(db).recordReceivablePayment(
                            ar.getId(), amt, spinner.getSelectedItem().toString(), new Date(),
                            etReference.getText().toString().trim());
                        load();
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
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
                        new FinanceTransactionService(db).recordManualReceivable(ar);
                    } else {
                        AccountReceivable replacement = new AccountReceivable();
                        replacement.setCustomerName(etCustomer.getText().toString().trim());
                        replacement.setDescription(etDesc.getText().toString().trim());
                        replacement.setAmount(amt);
                        replacement.setInvoiceDate(new Date());
                        replacement.setDueDate(existing.getDueDate());
                        new FinanceTransactionService(db).replaceManualReceivable(existing.getId(), replacement);
                    }
                    load();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showVoidDialog(AccountReceivable receivable) {
        EditText reason = new EditText(this);
        reason.setHint("Reason for voiding");
        new AlertDialog.Builder(this)
                .setTitle("Void receivable")
                .setMessage("This reverses the receivable and any recorded customer payments. Confirm collected funds were refunded.")
                .setView(reason)
                .setPositiveButton("Confirm and void", (dialog, which) -> {
                    try {
                        new FinanceTransactionService(db).voidReceivable(
                                receivable.getId(), reason.getText().toString(), true);
                        load();
                    } catch (RuntimeException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override protected void onResume() { super.onResume(); load(); }
}
