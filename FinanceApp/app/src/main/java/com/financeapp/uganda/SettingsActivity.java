package com.financeapp.uganda;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.financeapp.uganda.backup.FinanceBackupManager;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.sync.FinanceSyncScheduler;
import com.financeapp.uganda.sync.SupabaseAuthClient;
import com.financeapp.uganda.sync.SupabaseConfig;
import com.financeapp.uganda.sync.SupabaseSessionStore;
import com.financeapp.uganda.utils.PrefsHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends BaseToolbarActivity {
    private ActivityResultLauncher<String> createBackupLauncher;
    private ActivityResultLauncher<String[]> openBackupLauncher;
    private ExecutorService backupExecutor;
    private ExecutorService syncExecutor;
    private TextView tvBackupStatus;
    private TextView tvSyncStatus;
    private SupabaseConfig syncConfig;
    private SupabaseSessionStore sessionStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backupExecutor = Executors.newSingleThreadExecutor();
        syncExecutor = Executors.newSingleThreadExecutor();
        syncConfig = SupabaseConfig.fromBuildConfig();
        sessionStore = new SupabaseSessionStore(getApplicationContext());
        createBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"),
            uri -> { if (uri != null) promptBackupPassphrase(uri, false); });
        openBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) confirmBackupRestore(uri); });
        setContentView(R.layout.activity_settings);
        setupToolbar("Settings");
        TextInputEditText etApiKey = findViewById(R.id.etApiKey);
        TextInputEditText etMtnNumber = findViewById(R.id.etMtnNumber);
        TextInputEditText etAirtelNumber = findViewById(R.id.etAirtelNumber);
        MaterialButton btnSave = findViewById(R.id.btnSave);
        MaterialButton btnSaveReceivingNumbers = findViewById(R.id.btnSaveReceivingNumbers);
        MaterialButton btnExportBackup = findViewById(R.id.btnExportBackup);
        MaterialButton btnRestoreBackup = findViewById(R.id.btnRestoreBackup);
        MaterialButton btnSyncSignIn = findViewById(R.id.btnSyncSignIn);
        MaterialButton btnSyncSignOut = findViewById(R.id.btnSyncSignOut);
        tvBackupStatus = findViewById(R.id.tvBackupStatus);
        tvSyncStatus = findViewById(R.id.tvSyncStatus);
        etApiKey.setText(PrefsHelper.getApiKey(this));
        etMtnNumber.setText(PrefsHelper.getMtnMobileMoneyNumber(this));
        etAirtelNumber.setText(PrefsHelper.getAirtelMoneyNumber(this));
        btnSave.setOnClickListener(v -> {
            PrefsHelper.saveApiKey(this, etApiKey.getText().toString());
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show();
        });
        btnSaveReceivingNumbers.setOnClickListener(v -> {
            PrefsHelper.saveMtnMobileMoneyNumber(this, etMtnNumber.getText().toString());
            PrefsHelper.saveAirtelMoneyNumber(this, etAirtelNumber.getText().toString());
            Toast.makeText(this, "Receiving numbers saved", Toast.LENGTH_SHORT).show();
        });
            btnSyncSignIn.setEnabled(syncConfig.isConfigured());
            btnSyncSignIn.setOnClickListener(v -> signInForSync(btnSyncSignIn,
                findViewById(R.id.etSyncEmail), findViewById(R.id.etSyncPassword)));
            btnSyncSignOut.setOnClickListener(v -> signOutFromSync(btnSyncSignOut));
            refreshSyncStatus();
        btnExportBackup.setOnClickListener(v -> createBackupLauncher.launch(
                "kistie-finance-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".kfb"));
        btnRestoreBackup.setOnClickListener(v -> openBackupLauncher.launch(
                new String[]{"application/octet-stream", "application/*"}));
    }

    private void confirmBackupRestore(Uri backupUri) {
        new AlertDialog.Builder(this)
                .setTitle("Restore backup")
                .setMessage("Restore replaces the current local finance database. The app first validates the backup and keeps a temporary rollback copy.")
                .setPositiveButton("Continue", (dialog, which) -> promptBackupPassphrase(backupUri, true))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void signInForSync(MaterialButton button, TextInputEditText emailField,
            TextInputEditText passwordField) {
        String email = emailField.getText() == null ? "" : emailField.getText().toString().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter the owner email and password.", Toast.LENGTH_SHORT).show();
            return;
        }
        button.setEnabled(false);
        tvSyncStatus.setText("Signing in...");
        syncExecutor.execute(() -> {
            SupabaseAuthClient.Result result = new SupabaseAuthClient(syncConfig).signIn(email, password);
            if (result.isSuccess()) {
                SupabaseSessionStore.Session session = result.getSession();
                sessionStore.saveSession(session.getAccessToken(), session.getRefreshToken(),
                        session.getExpiresAtMillis());
                AppDatabase.getInstance(getApplicationContext()).financeDao()
                        .resetAuthRequiredSyncOperations();
                FinanceSyncScheduler.schedule(getApplicationContext());
            }
            runOnUiThread(() -> {
                button.setEnabled(syncConfig.isConfigured());
                passwordField.setText("");
                if (result.isSuccess()) refreshSyncStatus();
                else tvSyncStatus.setText(result.getErrorMessage());
            });
        });
    }

    private void signOutFromSync(MaterialButton button) {
        SupabaseSessionStore.Session session = sessionStore.getSession();
        if (session == null) {
            sessionStore.clear();
            refreshSyncStatus();
            return;
        }
        button.setEnabled(false);
        syncExecutor.execute(() -> {
            new SupabaseAuthClient(syncConfig).signOut(session.getAccessToken());
            sessionStore.clear();
            runOnUiThread(() -> {
                button.setEnabled(true);
                refreshSyncStatus();
            });
        });
    }

    private void refreshSyncStatus() {
        if (tvSyncStatus == null) return;
        if (!syncConfig.isConfigured()) {
            tvSyncStatus.setText("Sync setup missing. Add the project URL and public anon key to local build configuration.");
            return;
        }
        int pending = AppDatabase.getInstance(getApplicationContext()).financeDao().getPendingSyncCount();
        int authRequired = AppDatabase.getInstance(getApplicationContext()).financeDao()
                .getSyncCountByStatus("AUTH_REQUIRED");
        int conflicts = AppDatabase.getInstance(getApplicationContext()).financeDao()
                .getSyncCountByStatus("CONFLICT");
        int rejected = AppDatabase.getInstance(getApplicationContext()).financeDao()
                .getSyncCountByStatus("REJECTED");
        if (sessionStore.getSession() == null || authRequired > 0) {
            tvSyncStatus.setText("Needs sign-in. " + (pending + authRequired) + " operation(s) waiting.");
        } else if (conflicts > 0) {
            tvSyncStatus.setText("Conflict: " + conflicts + " operation(s) need review.");
        } else if (rejected > 0) {
            tvSyncStatus.setText("Rejected: " + rejected + " operation(s) need review.");
        } else if (pending > 0) {
            tvSyncStatus.setText("Pending: " + pending + " operation(s). Sync resumes when online.");
        } else {
            tvSyncStatus.setText("Up to date.");
        }
    }

    private void promptBackupPassphrase(Uri uri, boolean restore) {
        EditText passphrase = new EditText(this);
        passphrase.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passphrase.setHint("Backup passphrase (12+ characters)");
        new AlertDialog.Builder(this)
                .setTitle(restore ? "Decrypt backup" : "Encrypt backup")
                .setView(passphrase)
                .setPositiveButton(restore ? "Restore" : "Create", (dialog, which) -> {
                    char[] secret = passphrase.getText().toString().toCharArray();
                    if (secret.length < 12) {
                        Toast.makeText(this, "Passphrase must be at least 12 characters.", Toast.LENGTH_LONG).show();
                        java.util.Arrays.fill(secret, '\0');
                        return;
                    }
                    tvBackupStatus.setText(restore ? "Validating and restoring backup…" : "Creating encrypted backup…");
                    backupExecutor.execute(() -> {
                        String result;
                        try {
                            if (restore) FinanceBackupManager.restoreDatabase(this, uri, secret);
                            else FinanceBackupManager.exportDatabase(this, uri, secret);
                            result = restore ? "Backup restored." : "Encrypted backup created.";
                        } catch (Exception e) {
                            result = "Backup operation failed: " + e.getMessage();
                        } finally {
                            java.util.Arrays.fill(secret, '\0');
                        }
                        String status = result;
                        runOnUiThread(() -> {
                            tvBackupStatus.setText(status);
                            Toast.makeText(this, status, Toast.LENGTH_LONG).show();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (backupExecutor != null) backupExecutor.shutdown();
        if (syncExecutor != null) syncExecutor.shutdown();
        super.onDestroy();
    }
}
