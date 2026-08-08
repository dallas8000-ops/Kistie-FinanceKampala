package com.financeapp.uganda;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.financeapp.uganda.accounting.AccountingService;
import com.financeapp.uganda.database.AppDatabase;
import com.financeapp.uganda.utils.OpenAIService;
import com.financeapp.uganda.utils.PrefsHelper;
import com.google.android.material.button.MaterialButton;

public class AiAssistantActivity extends BaseToolbarActivity {

    private TextView tvChat;
    private ScrollView scrollChat;
    private OpenAIService openAI;
    private AccountingService accounting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_assistant);
        setupToolbar("AI Assistant");
        openAI = new OpenAIService();
        accounting = new AccountingService(AppDatabase.getInstance(this).financeDao());
        tvChat = findViewById(R.id.tvChat);
        scrollChat = findViewById(R.id.scrollChat);
        EditText etQuestion = findViewById(R.id.etQuestion);
        MaterialButton btnSend = findViewById(R.id.btnSend);

        btnSend.setOnClickListener(v -> {
            String question = etQuestion.getText().toString().trim();
            if (question.isEmpty()) return;
            appendChat("You: " + question + "\n\n");
            etQuestion.setText("");
            btnSend.setEnabled(false);
            String context = accounting.getFinancialSummary();
            openAI.ask(PrefsHelper.getApiKey(this), context, question, new OpenAIService.CallbackResult() {
                @Override
                public void onSuccess(String reply) {
                    runOnUiThread(() -> {
                        appendChat("Assistant: " + reply + "\n\n");
                        btnSend.setEnabled(true);
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(AiAssistantActivity.this, error, Toast.LENGTH_LONG).show();
                        btnSend.setEnabled(true);
                    });
                }
            });
        });
    }

    private void appendChat(String text) {
        tvChat.append(text);
        scrollChat.post(() -> scrollChat.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
