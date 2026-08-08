package com.financeapp.uganda;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import com.financeapp.uganda.accounting.AccountingService;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.Account;
import com.financeapp.uganda.models.JournalEntry;
import com.financeapp.uganda.models.JournalLine;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class JournalEditActivity extends BaseToolbarActivity {

    private AppDatabase db;
    private AccountingService accounting;
    private Date selectedDate = new Date();
    private int entryId = 0;
    private List<Account> accounts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journal_edit);
        setupToolbar("Journal Entry");

        db = AppDatabase.getInstance(this);
        accounting = new AccountingService(db.financeDao());
        entryId = getIntent().getIntExtra("entryId", 0);

        EditText etDescription = findViewById(R.id.etDescription);
        EditText etDebit = findViewById(R.id.etDebit);
        EditText etCredit = findViewById(R.id.etCredit);
        Spinner spinnerDebit = findViewById(R.id.spinnerDebitAccount);
        Spinner spinnerCredit = findViewById(R.id.spinnerCreditAccount);
        Button btnDate = findViewById(R.id.btnDate);
        Button btnSave = findViewById(R.id.btnSave);

        accounts = db.financeDao().getAllAccounts();
        List<String> names = new ArrayList<>();
        for (Account a : accounts) names.add(a.getCode() + " " + a.getName());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDebit.setAdapter(adapter);
        spinnerCredit.setAdapter(adapter);
        if (accounts.size() > 1) spinnerCredit.setSelection(1);

        btnDate.setText(android.text.format.DateFormat.format("dd MMM yyyy", selectedDate));
        btnDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTime(selectedDate);
            new DatePickerDialog(this, (view, y, m, d) -> {
                cal.set(y, m, d);
                selectedDate = cal.getTime();
                btnDate.setText(android.text.format.DateFormat.format("dd MMM yyyy", selectedDate));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        if (entryId > 0) {
            JournalEntry entry = db.financeDao().getJournalEntryById(entryId);
            List<JournalLine> lines = db.financeDao().getLinesForEntry(entryId);
            if (entry != null) {
                etDescription.setText(entry.getDescription());
                selectedDate = entry.getDate();
                btnDate.setText(android.text.format.DateFormat.format("dd MMM yyyy", selectedDate));
            }
            for (JournalLine line : lines) {
                int idx = accountIndex(line.getAccountId());
                if (line.getDebit() > 0) {
                    spinnerDebit.setSelection(idx);
                    etDebit.setText(String.valueOf((long) line.getDebit()));
                }
                if (line.getCredit() > 0) {
                    spinnerCredit.setSelection(idx);
                    etCredit.setText(String.valueOf((long) line.getCredit()));
                }
            }
        }

        btnSave.setOnClickListener(v -> {
            String desc = etDescription.getText().toString().trim();
            String drStr = etDebit.getText().toString().trim();
            String crStr = etCredit.getText().toString().trim();
            if (desc.isEmpty() || drStr.isEmpty() || crStr.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            double debit = Double.parseDouble(drStr);
            double credit = Double.parseDouble(crStr);
            if (Math.abs(debit - credit) > 0.01) {
                Toast.makeText(this, "Debit must equal Credit", Toast.LENGTH_LONG).show();
                return;
            }
            int debitAcc = accounts.get(spinnerDebit.getSelectedItemPosition()).getId();
            int creditAcc = accounts.get(spinnerCredit.getSelectedItemPosition()).getId();
            List<JournalLine> lines = new ArrayList<>();
            lines.add(new JournalLine(0, debitAcc, debit, 0, desc));
            lines.add(new JournalLine(0, creditAcc, 0, credit, desc));

            if (entryId > 0) {
                JournalEntry entry = db.financeDao().getJournalEntryById(entryId);
                entry.setDescription(desc);
                entry.setDate(selectedDate);
                accounting.updateJournalEntry(entry, lines);
            } else {
                accounting.postManualEntry(selectedDate, desc, lines);
            }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private int accountIndex(int accountId) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getId() == accountId) return i;
        }
        return 0;
    }
}
