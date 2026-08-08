package com.financeapp.uganda.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OpenAIService {

    public interface CallbackResult {
        void onSuccess(String reply);
        void onError(String error);
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public void ask(String apiKey, String systemContext, String userQuestion, CallbackResult callback) {
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("Add your OpenAI API key in Settings first.");
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("model", "gpt-4o-mini");
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content",
                    "You are a finance assistant for Kistie Store, a Uganda clothing retail business. " + systemContext));
            messages.put(new JSONObject().put("role", "user").put("content", userQuestion));
            body.put("messages", messages);
            body.put("max_tokens", 500);

            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    String text = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        callback.onError("API error: " + text);
                        return;
                    }
                    try {
                        JSONObject json = new JSONObject(text);
                        String reply = json.getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content");
                        callback.onSuccess(reply.trim());
                    } catch (Exception e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }
}
