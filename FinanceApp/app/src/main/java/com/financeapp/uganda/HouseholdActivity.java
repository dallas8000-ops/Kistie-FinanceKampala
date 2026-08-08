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
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.Expense;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class HouseholdActivity extends BaseToolbarActivity {

    private RecyclerView recyclerView;
    private ExpenseAdapter adapter;
    private AppDatabase db;
    private TextView tvTotal;
    private Date selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupToolbar("Household Expenses");

        db = AppDatabase.getInstance(this);
        selectedDate = new Date();

        recyclerView = findViewById(R.id.recyclerView);
        tvTotal = findViewById(R.id.tvTotal);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ExpenseAdapter();
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showAddExpenseDialog());

        adapter.setOnExpenseClickListener(new ExpenseAdapter.OnExpenseClickListener() {
            @Override
            public void onExpenseClick(Expense expense) {}

            @Override
            public void onExpenseLongClick(Expense expense) {
                showDeleteDialog(expense);
            }
        });

        loadHouseholdExpenses();
    }

    private void loadHouseholdExpenses() {
        List<Expense> expenses = db.financeDao().getExpensesByCategory("household");
        adapter.setExpenses(expenses);
        double total = db.financeDao().getTotalByCategory("household");
        tvTotal.setText(CurrencyFormatter.formatUGX(total));
    }

    private void showAddExpenseDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null);
        EditText etDescription = view.findViewById(R.id.etDescription);
        EditText etAmount = view.findViewById(R.id.etAmount);
        Spinner spinnerSubCategory = view.findViewById(R.id.spinnerSubCategory);
        Spinner spinnerPayment = view.findViewById(R.id.spinnerPayment);
        Button btnDate = view.findViewById(R.id.btnDate);

        String[] subCategories = {"Rent", "Utilities (UMEME/Water)", "Food/Groceries", "Transport",
                "School Fees", "Medical", "Entertainment", "Clothing", "Others"};
        spinnerSubCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, subCategories));

        String[] payments = {"Cash", "Mobile Money", "Bank Transfer", "Credit"};
        spinnerPayment.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, payments));

        btnDate.setText(android.text.format.DateFormat.format("dd MMM yyyy", selectedDate));
        btnDate.setOnClickListener(v -> showDatePicker(btnDate));

        new AlertDialog.Builder(this)
                .setTitle("Add Household Expense")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    String description = etDescription.getText().toString().trim();
                    String amountStr = etAmount.getText().toString().trim();
                    if (description.isEmpty() || amountStr.isEmpty()) {
                        Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Expense expense = new Expense();
                    expense.setCategory("household");
                    expense.setSubCategory(spinnerSubCategory.getSelectedItem().toString());
                    expense.setDescription(description);
                    expense.setAmount(Double.parseDouble(amountStr));
                    expense.setCurrency("UGX");
                    expense.setDate(selectedDate);
                    expense.setPaymentMethod(spinnerPayment.getSelectedItem().toString());
                    db.financeDao().insertExpense(expense);
                    loadHouseholdExpenses();
                    Toast.makeText(this, "Expense saved!", Toast.LENGTH_SHORT).show();
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

    private void showDeleteDialog(Expense expense) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Expense")
                .setMessage("Are you sure you want to delete this expense?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.financeDao().deleteExpense(expense);
                    loadHouseholdExpenses();
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHouseholdExpenses();
    }
}
