package com.financeapp.uganda.sync;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

public final class SupabaseAuthClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final SupabaseConfig config;
    private final OkHttpClient client;
    private final String authBaseUrl;

    public SupabaseAuthClient(SupabaseConfig config) {
        this(config, new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build());
    }

    SupabaseAuthClient(SupabaseConfig config, OkHttpClient client) {
        this(config, client, config == null ? "" : config.getProjectUrl());
    }

    SupabaseAuthClient(SupabaseConfig config, OkHttpClient client, String authBaseUrl) {
        this.config = config;
        this.client = client;
        this.authBaseUrl = authBaseUrl == null ? "" : authBaseUrl.replaceAll("/+$", "");
    }

    public Result signIn(String email, String password) {
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) {
            return new Result(false, null, 0, "Email and password are required.");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("email", email.trim());
            body.put("password", password);
        } catch (JSONException e) {
            return new Result(false, null, 0, "Could not prepare sign-in request.");
        }
        return requestToken("password", body);
    }

    public Result refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return new Result(false, null, 0, "A saved sync session is required.");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("refresh_token", refreshToken.trim());
        } catch (JSONException e) {
            return new Result(false, null, 0, "Could not prepare session refresh.");
        }
        return requestToken("refresh_token", body);
    }

    public Result signOut(String accessToken) {
        if (!isConfigured()) return notConfigured();
        if (accessToken == null || accessToken.trim().isEmpty()) {
            return new Result(false, null, 0, "A signed-in session is required.");
        }
        Request request = new Request.Builder()
                .url(baseUrl() + "/auth/v1/logout")
                .addHeader("apikey", config.getAnonKey())
                .addHeader("Authorization", "Bearer " + accessToken.trim())
                .post(RequestBody.create("", JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            int status = response.code();
            return new Result(response.isSuccessful(), null, status,
                    response.isSuccessful() ? null : safeError(status));
        } catch (IOException e) {
            return new Result(false, null, 0, "Network request failed.");
        }
    }

    private Result requestToken(String grantType, JSONObject body) {
        if (!isConfigured()) return notConfigured();
        Request request = new Request.Builder()
                .url(baseUrl() + "/auth/v1/token?grant_type=" + grantType)
                .addHeader("apikey", config.getAnonKey())
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            int status = response.code();
            if (!response.isSuccessful()) return new Result(false, null, status, safeError(status));
            if (response.body() == null) return new Result(false, null, status, "Invalid auth response.");
            JSONObject json = new JSONObject(response.body().string());
            String accessToken = json.getString("access_token");
            String refreshToken = json.getString("refresh_token");
            long expiresInSeconds = json.getLong("expires_in");
            if (accessToken.isEmpty() || refreshToken.isEmpty() || expiresInSeconds <= 0) {
                return new Result(false, null, status, "Invalid auth response.");
            }
            SupabaseSessionStore.Session session = new SupabaseSessionStore.Session(
                    accessToken, refreshToken, System.currentTimeMillis() + expiresInSeconds * 1000L);
            return new Result(true, session, status, null);
        } catch (IOException | JSONException e) {
            return new Result(false, null, 0, "Auth request failed.");
        }
    }

    private boolean isConfigured() {
        return config != null && config.isConfigured();
    }

    private String baseUrl() {
        return authBaseUrl;
    }

    private Result notConfigured() {
        return new Result(false, null, 0, "Sync is not configured.");
    }

    private String safeError(int status) {
        return "Auth request failed (HTTP " + status + ").";
    }

    public static final class Result {
        private final boolean success;
        private final SupabaseSessionStore.Session session;
        private final int httpCode;
        private final String errorMessage;

        Result(boolean success, SupabaseSessionStore.Session session, int httpCode,
                String errorMessage) {
            this.success = success;
            this.session = session;
            this.httpCode = httpCode;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public SupabaseSessionStore.Session getSession() { return session; }
        public int getHttpCode() { return httpCode; }
        public String getErrorMessage() { return errorMessage; }
    }
}
