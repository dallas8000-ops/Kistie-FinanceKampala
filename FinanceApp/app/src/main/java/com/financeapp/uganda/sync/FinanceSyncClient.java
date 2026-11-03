package com.financeapp.uganda.sync;

import com.financeapp.uganda.models.SyncOutboxEntry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class FinanceSyncClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public enum Status {
        ACKNOWLEDGED,
        PULL_COMPLETE,
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

    public static final class RemoteOperation {
        private final long serverSequence;
        private final String operationId;
        private final String operationType;
        private final String aggregateId;
        private final int payloadVersion;
        private final JSONObject payload;

        RemoteOperation(long serverSequence, String operationId, String operationType,
                String aggregateId, int payloadVersion, JSONObject payload) {
            this.serverSequence = serverSequence;
            this.operationId = operationId;
            this.operationType = operationType;
            this.aggregateId = aggregateId;
            this.payloadVersion = payloadVersion;
            this.payload = payload;
        }

        public long getServerSequence() { return serverSequence; }
        public String getOperationId() { return operationId; }
        public String getOperationType() { return operationType; }
        public String getAggregateId() { return aggregateId; }
        public int getPayloadVersion() { return payloadVersion; }
        public JSONObject getPayload() { return payload; }
    }

    public static final class PullResult {
        private final Status status;
        private final int httpCode;
        private final List<RemoteOperation> operations;
        private final long lastSequence;

        PullResult(Status status, int httpCode, List<RemoteOperation> operations, long lastSequence) {
            this.status = status;
            this.httpCode = httpCode;
            this.operations = operations;
            this.lastSequence = lastSequence;
        }

        public Status getStatus() { return status; }
        public int getHttpCode() { return httpCode; }
        public List<RemoteOperation> getOperations() { return operations; }
        public long getLastSequence() { return lastSequence; }
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
                    .addHeader("apikey", config.getPublishableKey())
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

    public PullResult pull(long afterSequence, int limit, String accessToken) {
        if (config == null || !config.isConfigured()) {
            return pullFailure(Status.NOT_CONFIGURED, 0, afterSequence);
        }
        if (accessToken == null || accessToken.trim().isEmpty()) {
            return pullFailure(Status.AUTH_REQUIRED, 0, afterSequence);
        }
        if (afterSequence < 0 || limit <= 0) {
            return pullFailure(Status.REJECTED, 0, afterSequence);
        }
        int boundedLimit = Math.min(limit, 100);
        try {
            String projectUrl = config.getProjectUrl().replaceAll("/+$", "");
            HttpUrl baseUrl = HttpUrl.parse(projectUrl + "/rest/v1/finance_operations");
            if (baseUrl == null) return pullFailure(Status.NOT_CONFIGURED, 0, afterSequence);
            HttpUrl url = baseUrl.newBuilder()
                    .addQueryParameter("select", "server_sequence,operation_id,operation_type,aggregate_id,payload_version,payload")
                    .addQueryParameter("server_sequence", "gt." + afterSequence)
                    .addQueryParameter("order", "server_sequence.asc")
                    .addQueryParameter("limit", String.valueOf(boundedLimit))
                    .build();
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", config.getPublishableKey())
                    .addHeader("Authorization", "Bearer " + accessToken.trim())
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                int status = response.code();
                if (!response.isSuccessful()) {
                    if (status == 401 || status == 403) return pullFailure(Status.AUTH_REQUIRED, status, afterSequence);
                    if (status == 408 || status == 429 || status >= 500) return pullFailure(Status.RETRY, status, afterSequence);
                    return pullFailure(Status.REJECTED, status, afterSequence);
                }
                if (response.body() == null) return pullFailure(Status.RETRY, status, afterSequence);
                JSONArray rows = new JSONArray(response.body().string());
                List<RemoteOperation> operations = new ArrayList<>();
                long lastSequence = afterSequence;
                for (int index = 0; index < rows.length(); index++) {
                    JSONObject row = rows.getJSONObject(index);
                    long sequence = row.getLong("server_sequence");
                    String operationId = row.getString("operation_id");
                    String operationType = row.getString("operation_type");
                    String aggregateId = row.getString("aggregate_id");
                    int payloadVersion = row.getInt("payload_version");
                    JSONObject payload = row.getJSONObject("payload");
                    if (sequence <= lastSequence || operationId.isEmpty() || operationType.isEmpty()
                            || aggregateId.isEmpty() || payloadVersion < 1) {
                        return pullFailure(Status.RETRY, status, afterSequence);
                    }
                    operations.add(new RemoteOperation(sequence, operationId, operationType,
                            aggregateId, payloadVersion, payload));
                    lastSequence = sequence;
                }
                return new PullResult(Status.PULL_COMPLETE, status, operations, lastSequence);
            }
        } catch (IOException | JSONException e) {
            return pullFailure(Status.RETRY, 0, afterSequence);
        }
    }

    private PullResult pullFailure(Status status, int httpCode, long lastSequence) {
        return new PullResult(status, httpCode, new ArrayList<>(), lastSequence);
    }
}
