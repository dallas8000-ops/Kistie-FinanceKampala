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
import com.financeapp.uganda.accounting.ExpenseAccountingHelper;
import com.financeapp.uganda.accounting.FinanceTransactionService;
import com.financeapp.uganda.adapters.PayableAdapter;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.AccountPayable;
import com.financeapp.uganda.models.Expense;
import com.financeapp.uganda.models.PayablePayment;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.financeapp.uganda.utils.MobileMoneyPaymentUi;
import com.financeapp.uganda.utils.PaymentMethods;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PayablesActivity extends BaseToolbarActivity {

    private AppDatabase db;
    private PayableAdapter adapter;
    private TextView tvTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupToolbar("Accounts Payable");
        db = AppDatabase.getInstance(this);
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
        if ("VOID".equals(ap.getStatus())) return;
        boolean canEdit = ap.getExpenseId() == 0 && ap.getAmountPaid() == 0;
        List<String> options = new ArrayList<>();
        options.add("Payment History");
        options.add("Record Payment");
        if (canEdit) options.add("Edit");
        options.add("Void");
        new AlertDialog.Builder(this).setTitle(ap.getVendorName())
            .setItems(options.toArray(new String[0]), (d, i) -> {
                String selected = options.get(i);
                if ("Payment History".equals(selected)) showPaymentHistory(ap);
                else if ("Record Payment".equals(selected)) showPaymentDialog(ap);
                else if ("Edit".equals(selected)) showAddDialog(ap);
                else showVoidDialog(ap);
                }).show();
    }

        private void showPaymentHistory(AccountPayable ap) {
        StringBuilder history = new StringBuilder("Original: ")
            .append(CurrencyFormatter.formatUGX(ap.getAmount()))
            .append("\nPaid: ").append(CurrencyFormatter.formatUGX(ap.getAmountPaid()))
            .append("\nBalance: ").append(CurrencyFormatter.formatUGX(ap.getBalanceDue()));
        List<PayablePayment> payments = db.financeDao().getPayablePayments(ap.getId());
        for (PayablePayment payment : payments) {
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
        new AlertDialog.Builder(this).setTitle("Vendor Payment History")
            .setMessage(history.toString()).setPositiveButton("Close", null).show();
        }

    private void showPaymentDialog(AccountPayable ap) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null);
        EditText etAmount = v.findViewById(R.id.etAmount);
        EditText etReference = v.findViewById(R.id.etTransactionReference);
        TextView tvReceivingNumber = v.findViewById(R.id.tvReceivingNumber);
        Spinner spinner = v.findViewById(R.id.spinnerPayment);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
            PaymentMethods.options(false, true)));
        MobileMoneyPaymentUi.bind(this, spinner, etReference, etReference, tvReceivingNumber, false);
        etAmount.setText(String.valueOf((long) ap.getBalanceDue()));
        new AlertDialog.Builder(this).setTitle("Pay Vendor").setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        new FinanceTransactionService(this, db).recordPayablePayment(
                                ap.getId(), Double.parseDouble(etAmount.getText().toString()),
                            spinner.getSelectedItem().toString(), new Date(),
                            etReference.getText().toString().trim());
                        load();
                    } catch (IllegalArgumentException e) {
                        android.widget.Toast.makeText(this, e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    }
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
                        new FinanceTransactionService(this, db).recordManualPayable(ap);
                    } else {
                        AccountPayable replacement = new AccountPayable();
                        replacement.setVendorName(etVendor.getText().toString().trim());
                        replacement.setDescription(etDesc.getText().toString().trim());
                        replacement.setAmount(amt);
                        replacement.setBillDate(new Date());
                        replacement.setDueDate(existing.getDueDate());
                        new FinanceTransactionService(this, db).replaceManualPayable(existing.getId(), replacement);
                    }
                    load();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showVoidDialog(AccountPayable payable) {
        EditText reason = new EditText(this);
        reason.setHint("Reason for voiding");
        new AlertDialog.Builder(this)
                .setTitle("Void payable")
                .setMessage("This reverses the payable and any recorded vendor payments. Confirm refunded funds have been returned.")
                .setView(reason)
                .setPositiveButton("Confirm and void", (dialog, which) -> {
                    try {
                        if (payable.getExpenseId() > 0) {
                            Expense expense = db.financeDao().getExpenseById(payable.getExpenseId());
                            if (expense == null) throw new IllegalStateException("Linked expense was not found.");
                            new ExpenseAccountingHelper(this, db).delete(expense);
                        } else {
                            new FinanceTransactionService(this, db).voidPayable(
                                    payable.getId(), reason.getText().toString(), true);
                        }
                        load();
                    } catch (RuntimeException e) {
                        android.widget.Toast.makeText(this, e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override protected void onResume() { super.onResume(); load(); }
}
