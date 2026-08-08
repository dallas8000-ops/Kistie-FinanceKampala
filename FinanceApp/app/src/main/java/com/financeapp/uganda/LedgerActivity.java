package com.financeapp.uganda;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.financeapp.uganda.accounting.AccountingService;
import com.financeapp.uganda.adapters.JournalAdapter;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.models.JournalEntry;
import com.financeapp.uganda.models.JournalLine;
import com.financeapp.uganda.utils.CurrencyFormatter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class LedgerActivity extends BaseToolbarActivity {

    private AppDatabase db;
    private AccountingService accounting;
    private JournalAdapter adapter;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        setupToolbar("General Ledger");

        db = AppDatabase.getInstance(this);
        accounting = new AccountingService(db.financeDao());
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        TextView tvTotal = findViewById(R.id.tvTotal);
        FloatingActionButton fab = findViewById(R.id.fabAdd);

        adapter = new JournalAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        adapter.setListener(entry -> showEntryDetail(entry));

        fab.setOnClickListener(v -> startActivity(new Intent(this, JournalEditActivity.class)));
        loadEntries(tvTotal);
    }

    private void loadEntries(TextView tvTotal) {
        List<JournalEntry> entries = db.financeDao().getAllJournalEntries();
        adapter.setEntries(entries);
        boolean balanced = accounting.isBooksBalanced();
        tvTotal.setText(balanced ? "Books balanced ✓" : "Warning: books out of balance");
        tvTotal.setTextColor(getColor(balanced ? R.color.success : R.color.danger));
    }

    private void showEntryDetail(JournalEntry entry) {
        List<JournalLine> lines = db.financeDao().getLinesForEntry(entry.getId());
        StringBuilder detail = new StringBuilder();
        detail.append(dateFormat.format(entry.getDate())).append("\n");
        detail.append(entry.getDescription()).append("\n");
        detail.append("Ref: ").append(entry.getReference()).append("\n\n");
        double dr = 0, cr = 0;
        for (JournalLine line : lines) {
            com.financeapp.uganda.models.Account acc = db.financeDao().getAccountById(line.getAccountId());
            String name = acc != null ? acc.getName() : "Account " + line.getAccountId();
            if (line.getDebit() > 0) {
                detail.append("Dr ").append(name).append(": ").append(CurrencyFormatter.formatUGX(line.getDebit())).append("\n");
                dr += line.getDebit();
            }
            if (line.getCredit() > 0) {
                detail.append("Cr ").append(name).append(": ").append(CurrencyFormatter.formatUGX(line.getCredit())).append("\n");
                cr += line.getCredit();
            }
        }
        detail.append("\nTotal Dr: ").append(CurrencyFormatter.formatUGX(dr));
        detail.append(" | Cr: ").append(CurrencyFormatter.formatUGX(cr));

        new AlertDialog.Builder(this)
                .setTitle("Journal Entry")
                .setMessage(detail.toString())
                .setPositiveButton("Edit", (d, w) -> {
                    Intent intent = new Intent(this, JournalEditActivity.class);
                    intent.putExtra("entryId", entry.getId());
                    startActivity(intent);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEntries(findViewById(R.id.tvTotal));
    }
}
