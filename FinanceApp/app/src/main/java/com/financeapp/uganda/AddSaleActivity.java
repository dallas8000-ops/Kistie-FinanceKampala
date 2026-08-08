package com.financeapp.uganda;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.InventoryItem;
import com.financeapp.uganda.models.Sale;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AddSaleActivity extends BaseToolbarActivity {

    private AppDatabase db;
    private Spinner spinnerItem;
    private List<InventoryItem> inventoryItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_sale);
        setupToolbar("Record Sale");

        db = AppDatabase.getInstance(this);
        spinnerItem = findViewById(R.id.spinnerItem);
        EditText etQuantity = findViewById(R.id.etQuantity);
        EditText etUnitPrice = findViewById(R.id.etUnitPrice);
        EditText etDiscount = findViewById(R.id.etDiscount);
        Spinner spinnerPayment = findViewById(R.id.spinnerPayment);
        EditText etCustomerName = findViewById(R.id.etCustomerName);
        EditText etCustomerPhone = findViewById(R.id.etCustomerPhone);
        Button btnSaveSale = findViewById(R.id.btnSaveSale);

        spinnerPayment.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Cash", "Mobile Money", "Bank Transfer"}));

        loadInventoryItems();

        spinnerItem.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position >= 0 && position < inventoryItems.size()) {
                    etUnitPrice.setText(String.valueOf((long) inventoryItems.get(position).getSellingPrice()));
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

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
            double profit = total - (item.getTotalCost() * qty);

            Sale sale = new Sale();
            sale.setInventoryItemId(item.getId());
            sale.setItemName(item.getName());
            sale.setQuantitySold(qty);
            sale.setUnitPrice(unitPrice);
            sale.setTotalAmount(total);
            sale.setDiscount(discount);
            sale.setSaleDate(new Date());
            sale.setCustomerName(etCustomerName.getText().toString().trim());
            sale.setCustomerPhone(etCustomerPhone.getText().toString().trim());
            sale.setPaymentMethod(spinnerPayment.getSelectedItem().toString());
            sale.setProfit(profit);

            db.financeDao().insertSale(sale);

            item.setQuantity(item.getQuantity() - qty);
            db.financeDao().updateInventoryItem(item);

            Toast.makeText(this, "Sale recorded!", Toast.LENGTH_SHORT).show();
            finish();
        });
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
}
