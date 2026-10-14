package com.financeapp.uganda;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import com.financeapp.uganda.accounting.AccountCodes;
import com.financeapp.uganda.accounting.AccountingService;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.utils.MobileMoneyPaymentUi;
import com.financeapp.uganda.utils.PaymentMethods;
import com.financeapp.uganda.utils.PrefsHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class CapitalSetupActivity extends BaseToolbarActivity {

    private AccountingService accounting;
    private boolean requiredSetup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capital_setup);
        accounting = new AccountingService(AppDatabase.getInstance(this).financeDao());
        requiredSetup = !accounting.hasInitialCapital() && !PrefsHelper.isCapitalConfigured(this);
        setupToolbar(requiredSetup ? "Set Initial Capital" : "Initial Capital");

        TextInputEditText etAmount = findViewById(R.id.etAmount);
        Spinner spinner = findViewById(R.id.spinnerAccount);
        TextInputEditText etReference = findViewById(R.id.etTransactionReference);
        android.widget.TextView tvReceivingNumber = findViewById(R.id.tvReceivingNumber);
        MaterialButton btnSave = findViewById(R.id.btnSave);

        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            PaymentMethods.options(false, false)));
        MobileMoneyPaymentUi.bind(this, spinner, etReference,
            findViewById(R.id.tilTransactionReference), tvReceivingNumber, true);

        if (accounting.hasInitialCapital()) {
            etAmount.setText(String.valueOf((long) accounting.getInitialCapitalBalance()));
            btnSave.setText("Update Initial Capital");
        }

        btnSave.setOnClickListener(v -> saveCapital(etAmount, spinner, etReference));
    }

    private void saveCapital(TextInputEditText etAmount, Spinner spinner, TextInputEditText etReference) {
        String amountStr = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";
        if (amountStr.isEmpty()) {
            Toast.makeText(this, "Enter the opening capital amount", Toast.LENGTH_SHORT).show();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
            return;
        }
        String paymentMethod = spinner.getSelectedItem().toString();
        String reference = etReference.getText() != null ? etReference.getText().toString().trim() : "";
        if (PaymentMethods.isMobileMoney(paymentMethod) && reference.isEmpty()) {
            Toast.makeText(this, "Enter the MTN/Airtel transaction reference.", Toast.LENGTH_LONG).show();
            return;
        }
        int accountId = accounting.paymentAccountId(paymentMethod);
        String label = reference.isEmpty() ? paymentMethod : paymentMethod + " · " + reference;
        try {
            if (accounting.hasInitialCapital()) {
                accounting.updateInitialCapital(amount, accountId, label);
            } else {
                accounting.postInitialCapital(amount, accountId, label);
            }
            PrefsHelper.setCapitalConfigured(this, true);
            Toast.makeText(this, "Initial capital saved", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (requiredSetup) {
            Toast.makeText(this, "Please set your opening capital to continue", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }
}
