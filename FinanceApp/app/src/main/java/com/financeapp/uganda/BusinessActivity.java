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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.adapters.ExpenseAdapter;
import com.financeapp.uganda.accounting.ExpenseAccountingHelper;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.Expense;
import com.financeapp.uganda.utils.MobileMoneyPaymentUi;
import com.financeapp.uganda.utils.PaymentMethods;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class BusinessActivity extends BaseToolbarActivity {

    private RecyclerView recyclerView;
    private ExpenseAdapter adapter;
    private AppDatabase db;
    private Date selectedDate;
    private String currentCategory = "business_operational";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business);
        setupToolbar("Business Operations");

        db = AppDatabase.getInstance(this);
        selectedDate = new Date();

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        recyclerView = findViewById(R.id.recyclerView);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExpenseAdapter();
        recyclerView.setAdapter(adapter);
        adapter.setOnExpenseClickListener(new ExpenseAdapter.OnExpenseClickListener() {
            @Override
            public void onExpenseClick(Expense expense) {
                showExpenseOptions(expense);
            }

            @Override
            public void onExpenseLongClick(Expense expense) {
                showExpenseOptions(expense);
            }
        });

        tabLayout.addTab(tabLayout.newTab().setText("Operational"));
        tabLayout.addTab(tabLayout.newTab().setText("Rent & Utilities"));
        tabLayout.addTab(tabLayout.newTab().setText("Staff"));
        tabLayout.addTab(tabLayout.newTab().setText("Marketing"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0: currentCategory = "business_operational"; break;
                    case 1: currentCategory = "business_rent"; break;
                    case 2: currentCategory = "business_staff"; break;
                    case 3: currentCategory = "business_marketing"; break;
                }
                loadExpenses();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        fabAdd.setOnClickListener(v -> showExpenseDialog(null));
        loadExpenses();
    }

    private void loadExpenses() {
        List<Expense> expenses = db.financeDao().getExpensesByCategory(currentCategory);
        adapter.setExpenses(expenses);
    }

    private void showExpenseDialog(Expense existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null);
        EditText etDescription = view.findViewById(R.id.etDescription);
        EditText etAmount = view.findViewById(R.id.etAmount);
        EditText etReference = view.findViewById(R.id.etTransactionReference);
        TextView tvReceivingNumber = new TextView(this);
        Spinner spinnerSubCategory = view.findViewById(R.id.spinnerSubCategory);
        Spinner spinnerPayment = view.findViewById(R.id.spinnerPayment);
        Button btnDate = view.findViewById(R.id.btnDate);

        String[] subCategories;
        String expenseCategory = existing != null ? existing.getCategory() : currentCategory;
        switch (expenseCategory) {
            case "business_operational":
                subCategories = new String[]{"Shop Supplies", "Packaging", "Maintenance", "Transport", "Communication", "Others"};
                break;
            case "business_rent":
                subCategories = new String[]{"Shop Rent", "Electricity (UMEME)", "Water", "Security", "Cleaning", "Others"};
                break;
            case "business_staff":
                subCategories = new String[]{"Salaries", "NSSF", "Bonuses", "Training", "Uniforms", "Others"};
                break;
            case "business_marketing":
                subCategories = new String[]{"Social Media Ads", "Posters/Flyers", "Promotions", "Website", "Events", "Others"};
                break;
            default:
                subCategories = new String[]{"Others"};
        }

        spinnerSubCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, subCategories));
        spinnerPayment.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
            PaymentMethods.options(false, true)));
        MobileMoneyPaymentUi.bind(this, spinnerPayment, etReference,
            view.findViewById(R.id.tilTransactionReference), tvReceivingNumber, false);

        if (existing != null) {
            etDescription.setText(existing.getDescription());
            etAmount.setText(String.valueOf(existing.getAmount()));
            selectSpinnerValue(spinnerSubCategory, existing.getSubCategory());
            selectSpinnerValue(spinnerPayment, existing.getPaymentMethod());
            selectedDate = existing.getDate() != null ? existing.getDate() : new Date();
        } else {
            selectedDate = new Date();
        }

        btnDate.setText(android.text.format.DateFormat.format("dd MMM yyyy", selectedDate));
        btnDate.setOnClickListener(v -> showDatePicker(btnDate));

        new AlertDialog.Builder(this)
            .setTitle(existing == null ? "Add Business Expense" : "Edit Business Expense")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    String description = etDescription.getText().toString().trim();
                    String amountStr = etAmount.getText().toString().trim();
                    if (description.isEmpty() || amountStr.isEmpty()) {
                        Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Expense expense = new Expense();
                    if (existing != null) expense.setId(existing.getId());
                    expense.setCategory(expenseCategory);
                    expense.setSubCategory(spinnerSubCategory.getSelectedItem().toString());
                    expense.setDescription(description);
                    expense.setAmount(Double.parseDouble(amountStr));
                    expense.setCurrency("UGX");
                    expense.setDate(selectedDate);
                    expense.setPaymentMethod(spinnerPayment.getSelectedItem().toString());
                    expense.setReceiptNumber(etReference.getText().toString().trim());
                    try {
                        ExpenseAccountingHelper helper = new ExpenseAccountingHelper(this, db);
                        if (existing == null) helper.saveNew(expense);
                        else helper.update(expense);
                        loadExpenses();
                        Toast.makeText(this, "Business expense saved!", Toast.LENGTH_SHORT).show();
                    } catch (RuntimeException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDatePicker(Button btnDate) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(selectedDate);
        new DatePickerDialog(this, (v, year, month, day) -> {
            cal.set(year, month, day);
            selectedDate = cal.getTime();
            btnDate.setText(android.text.format.DateFormat.format("dd MMM yyyy", selectedDate));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showExpenseOptions(Expense expense) {
        new AlertDialog.Builder(this)
                .setTitle(expense.getDescription())
                .setItems(new String[]{"Edit", "Void"}, (dialog, which) -> {
                    if (which == 0) showExpenseDialog(expense);
                    else showVoidDialog(expense);
                })
                .show();
    }

    private void showVoidDialog(Expense expense) {
        new AlertDialog.Builder(this)
                .setTitle("Void Expense")
                .setMessage("This keeps the expense for audit and reverses its accounting entry. Continue?")
                .setPositiveButton("Void", (dialog, which) -> {
                    try {
                        new ExpenseAccountingHelper(this, db).delete(expense);
                        loadExpenses();
                        Toast.makeText(this, "Expense voided", Toast.LENGTH_SHORT).show();
                    } catch (RuntimeException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void selectSpinnerValue(Spinner spinner, String value) {
        if (value == null) return;
        ArrayAdapter<?> adapter = (ArrayAdapter<?>) spinner.getAdapter();
        for (int index = 0; index < adapter.getCount(); index++) {
            if (value.equals(adapter.getItem(index))) {
                spinner.setSelection(index);
                return;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadExpenses();
    }
}
