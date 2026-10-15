package com.financeapp.uganda.sync;

import com.financeapp.uganda.models.SyncOutboxEntry;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

public final class FinanceSyncClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public enum Status {
        ACKNOWLEDGED,
        NOT_CONFIGURED,
        AUTH_REQUIRED,
        CONFLICT,
        RETRY,
        REJECTED
    }

    public static final class Result {
        private final Status status;
        private final int httpCode;

        Result(Status status, int httpCode) {
            this.status = status;
            this.httpCode = httpCode;
        }

        public Status getStatus() { return status; }
        public int getHttpCode() { return httpCode; }
    }

    private final SupabaseConfig config;
    private final OkHttpClient client;

    public FinanceSyncClient(SupabaseConfig config) {
        this(config, new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build());
    }

    FinanceSyncClient(SupabaseConfig config, OkHttpClient client) {
        this.config = config;
        this.client = client;
    }

    public Result push(SyncOutboxEntry entry, String accessToken) {
        if (config == null || !config.isConfigured()) return new Result(Status.NOT_CONFIGURED, 0);
        if (entry == null || accessToken == null || accessToken.trim().isEmpty()) {
            return new Result(Status.AUTH_REQUIRED, 0);
        }
        try {
            JSONObject payload = new JSONObject(entry.getPayloadJson());
            JSONObject body = new JSONObject();
            body.put("p_operation_id", entry.getOperationId());
            body.put("p_operation_type", entry.getOperationType());
            body.put("p_aggregate_id", entry.getAggregateId());
            body.put("p_payload_version", entry.getPayloadVersion());
            body.put("p_payload", payload);

            String projectUrl = config.getProjectUrl().replaceAll("/+$", "");
            Request request = new Request.Builder()
                    .url(projectUrl + "/rest/v1/rpc/apply_finance_operation")
                    .addHeader("apikey", config.getAnonKey())
                    .addHeader("Authorization", "Bearer " + accessToken.trim())
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                int status = response.code();
                if (response.isSuccessful()) {
                    if (response.body() == null) return new Result(Status.RETRY, status);
                    JSONObject reply = new JSONObject(response.body().string());
                    return new Result(reply.optBoolean("accepted", false)
                            ? Status.ACKNOWLEDGED : Status.RETRY, status);
                }
                if (status == 401 || status == 403) return new Result(Status.AUTH_REQUIRED, status);
                if (status == 409) return new Result(Status.CONFLICT, status);
                if (status == 408 || status == 429 || status >= 500) return new Result(Status.RETRY, status);
                return new Result(Status.REJECTED, status);
            }
        } catch (IOException | JSONException e) {
            return new Result(Status.RETRY, 0);
        }
    }
}
