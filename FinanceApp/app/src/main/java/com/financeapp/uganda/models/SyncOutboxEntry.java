package com.financeapp.uganda.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_outbox", indices = {
        @Index(value = {"operationId"}, unique = true),
        @Index(value = {"status", "nextAttemptAt"})
})
public class SyncOutboxEntry {
    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull private String operationId = "";
    @NonNull private String operationType = "";
    @NonNull private String aggregateId = "";
    @NonNull private String payloadJson = "";
    @ColumnInfo(defaultValue = "1") private int payloadVersion = 1;
    private long createdAt;
    @ColumnInfo(defaultValue = "0") private int attempts;
    @ColumnInfo(defaultValue = "0") private long nextAttemptAt;
    @NonNull
    @ColumnInfo(defaultValue = "'PENDING'")
    private String status = "PENDING";
    private String lastError;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    @NonNull public String getOperationId() { return operationId; }
    public void setOperationId(@NonNull String operationId) { this.operationId = operationId; }
    @NonNull public String getOperationType() { return operationType; }
    public void setOperationType(@NonNull String operationType) { this.operationType = operationType; }
    @NonNull public String getAggregateId() { return aggregateId; }
    public void setAggregateId(@NonNull String aggregateId) { this.aggregateId = aggregateId; }
    @NonNull public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(@NonNull String payloadJson) { this.payloadJson = payloadJson; }
    public int getPayloadVersion() { return payloadVersion; }
    public void setPayloadVersion(int payloadVersion) { this.payloadVersion = payloadVersion; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public long getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(long nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    @NonNull public String getStatus() { return status; }
    public void setStatus(@NonNull String status) { this.status = status; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
