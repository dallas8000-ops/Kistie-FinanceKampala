package com.financeapp.uganda;

import android.os.Bundle;
import android.widget.Toast;
import com.financeapp.uganda.utils.PrefsHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends BaseToolbarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupToolbar("Settings");
        TextInputEditText etApiKey = findViewById(R.id.etApiKey);
        MaterialButton btnSave = findViewById(R.id.btnSave);
        etApiKey.setText(PrefsHelper.getApiKey(this));
        btnSave.setOnClickListener(v -> {
            PrefsHelper.saveApiKey(this, etApiKey.getText().toString());
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show();
        });
    }
}
