package com.financeapp.uganda;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.accounting.FinanceTransactionService;
import com.financeapp.uganda.models.InventoryItem;
import com.financeapp.uganda.models.Sale;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.financeapp.uganda.utils.MobileMoneyPaymentUi;
import com.financeapp.uganda.utils.PaymentMethods;
import com.financeapp.uganda.receipts.SaleReceiptShareHelper;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AddSaleActivity extends BaseToolbarActivity {

    private AppDatabase db;
    private Spinner spinnerItem;
    private List<InventoryItem> inventoryItems = new ArrayList<>();
    private Sale originalSale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_sale);
        db = AppDatabase.getInstance(this);
        int saleId = getIntent().getIntExtra("sale_id", 0);
        originalSale = saleId > 0 ? db.financeDao().getSaleById(saleId) : null;
        if (originalSale != null && "VOID".equals(originalSale.getStatus())) originalSale = null;
        setupToolbar(originalSale == null ? "Record Sale" : "Edit Sale");

        spinnerItem = findViewById(R.id.spinnerItem);
        EditText etQuantity = findViewById(R.id.etQuantity);
        EditText etUnitPrice = findViewById(R.id.etUnitPrice);
        EditText etDiscount = findViewById(R.id.etDiscount);
        EditText etAmountPaid = findViewById(R.id.etAmountPaid);
        TextView tvBalanceDue = findViewById(R.id.tvBalanceDue);
        TextView tvReceivingNumber = findViewById(R.id.tvReceivingNumber);
        Spinner spinnerPayment = findViewById(R.id.spinnerPayment);
        EditText etTransactionReference = findViewById(R.id.etTransactionReference);
        EditText etCustomerName = findViewById(R.id.etCustomerName);
        EditText etCustomerPhone = findViewById(R.id.etCustomerPhone);
        Button btnSaveSale = findViewById(R.id.btnSaveSale);

        ArrayAdapter<String> paymentAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
            PaymentMethods.options(true, false));
        spinnerPayment.setAdapter(paymentAdapter);
        Runnable refreshMobileMoney = MobileMoneyPaymentUi.bind(this, spinnerPayment, etTransactionReference,
            findViewById(R.id.tilTransactionReference), tvReceivingNumber, true);

        Runnable refreshBalance = () -> updateBalanceDisplay(
            etQuantity, etUnitPrice, etDiscount, etAmountPaid, spinnerPayment, tvBalanceDue);
        addBalanceWatcher(etQuantity, refreshBalance);
        addBalanceWatcher(etUnitPrice, refreshBalance);
        addBalanceWatcher(etDiscount, refreshBalance);
        addBalanceWatcher(etAmountPaid, refreshBalance);
        spinnerPayment.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
                           int position, long id) {
            refreshBalance.run();
            refreshMobileMoney.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
                refreshMobileMoney.run();
            }
        });

        loadInventoryItems();

        spinnerItem.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position >= 0 && position < inventoryItems.size()) {
                    etUnitPrice.setText(String.valueOf((long) inventoryItems.get(position).getSellingPrice()));
                    refreshBalance.run();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        if (originalSale != null) {
            for (int index = 0; index < inventoryItems.size(); index++) {
                if (inventoryItems.get(index).getId() == originalSale.getInventoryItemId()) {
                    spinnerItem.setSelection(index);
                    break;
                }
            }
            etQuantity.setText(String.valueOf(originalSale.getQuantitySold()));
            etUnitPrice.setText(String.valueOf(originalSale.getUnitPrice()));
            etDiscount.setText(String.valueOf(originalSale.getDiscount()));
            etAmountPaid.setText(String.valueOf(originalSale.getAmountPaid()));
            etCustomerName.setText(originalSale.getCustomerName());
            etCustomerPhone.setText(originalSale.getCustomerPhone());
            etTransactionReference.setText(originalSale.getTransactionId());
            int paymentPosition = paymentAdapter.getPosition(originalSale.getPaymentMethod());
            if (paymentPosition >= 0) spinnerPayment.setSelection(paymentPosition);
            btnSaveSale.setText("Save Changes");
            refreshBalance.run();
        }

        btnSaveSale.setOnClickListener(v -> {
            if (inventoryItems.isEmpty()) {
                Toast.makeText(this, "No inventory items. Add stock first.", Toast.LENGTH_LONG).show();
                return;
            }
            int position = spinnerItem.getSelectedItemPosition();
            if (position < 0) return;

            InventoryItem item = inventoryItems.get(position);
            String qtyStr = etQuantity.getText().toString().trim();
            String priceStr = etUnitPrice.getText().toString().trim();
            if (qtyStr.isEmpty() || priceStr.isEmpty()) {
                Toast.makeText(this, "Quantity and price required", Toast.LENGTH_SHORT).show();
                return;
            }

            int qty = Integer.parseInt(qtyStr);
            if (qty > item.getQuantity()) {
                Toast.makeText(this, "Not enough stock. Available: " + item.getQuantity(), Toast.LENGTH_LONG).show();
                return;
            }

            double unitPrice = Double.parseDouble(priceStr);
            double discount = etDiscount.getText().toString().isEmpty() ? 0 : Double.parseDouble(etDiscount.getText().toString());
            double total = (unitPrice * qty) - discount;
                String paymentMethod = spinnerPayment.getSelectedItem().toString();
                String amountPaidText = etAmountPaid.getText().toString().trim();
                double amountPaid = amountPaidText.isEmpty()
                    ? ("Credit".equals(paymentMethod) ? 0 : total)
                    : Double.parseDouble(amountPaidText);

            Sale sale = new Sale();
            sale.setInventoryItemId(item.getId());
            sale.setItemName(item.getName());
            sale.setQuantitySold(qty);
            sale.setUnitPrice(unitPrice);
            sale.setTotalAmount(total);
            sale.setAmountPaid(amountPaid);
            sale.setDiscount(discount);
            sale.setSaleDate(new Date());
            sale.setCustomerName(etCustomerName.getText().toString().trim());
            sale.setCustomerPhone(etCustomerPhone.getText().toString().trim());
            sale.setPaymentMethod(paymentMethod);
            sale.setTransactionId(etTransactionReference.getText().toString().trim());
            if (originalSale != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Confirm sale edit")
                        .setMessage("The original sale will be reversed and retained for audit. Confirm any collected funds were refunded.")
                        .setPositiveButton("Confirm refund and save", (dialog, which) -> saveSale(sale))
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                saveSale(sale);
            }
        });
    }

    private void saveSale(Sale sale) {
        try {
            FinanceTransactionService transactions = new FinanceTransactionService(this, db);
            if (originalSale == null) transactions.recordSale(sale);
            else transactions.replaceSale(originalSale.getId(), sale, true);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, originalSale == null ? "Sale recorded!" : "Sale updated!", Toast.LENGTH_SHORT).show();
        try {
            SaleReceiptShareHelper.createAndShare(this, sale);
        } catch (java.io.IOException | RuntimeException e) {
            Toast.makeText(this, "Sale saved, but receipt could not be shared.", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void loadInventoryItems() {
        inventoryItems = db.financeDao().getAllInventory();
        List<String> names = new ArrayList<>();
        for (InventoryItem item : inventoryItems) {
            names.add(item.getName() + " (Stock: " + item.getQuantity() + ")");
        }
        if (names.isEmpty()) {
            names.add("No items in inventory");
        }
        spinnerItem.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names));
    }

    private void addBalanceWatcher(EditText field, Runnable refreshBalance) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                refreshBalance.run();
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
    }

    private void updateBalanceDisplay(EditText quantity, EditText price, EditText discount,
                                      EditText amountPaid, Spinner payment, TextView balanceView) {
        double quantityValue = parseOrZero(quantity.getText().toString());
        double gross = Math.max(0, quantityValue * parseOrZero(price.getText().toString())
                - parseOrZero(discount.getText().toString()));
        String paidText = amountPaid.getText().toString().trim();
        double paid = paidText.isEmpty()
                ? ("Credit".equals(payment.getSelectedItem()) ? 0 : gross)
                : parseOrZero(paidText);
        balanceView.setText("Balance due: " + CurrencyFormatter.formatUGX(Math.max(0, gross - paid)));
    }

    private double parseOrZero(String value) {
        try {
            return value == null || value.trim().isEmpty() ? 0 : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
