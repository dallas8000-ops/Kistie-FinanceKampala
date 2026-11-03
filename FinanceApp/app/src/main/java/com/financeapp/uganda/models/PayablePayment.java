package com.financeapp.uganda.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import com.financeapp.uganda.utils.DateConverter;
import java.util.Date;
import java.util.UUID;

@Entity(tableName = "payable_payments", indices = {@Index(value = {"syncId"}, unique = true)})
@TypeConverters(DateConverter.class)
public class PayablePayment {
    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull
    @ColumnInfo(defaultValue = "''")
    private String syncId = UUID.randomUUID().toString();
    private int payableId;
    private double amount;
    private String paymentMethod;
    private Date paidAt;
    private String reference;
    private int journalEntryId;
    @NonNull
    @ColumnInfo(defaultValue = "'POSTED'")
    private String status = "POSTED";
    @ColumnInfo(defaultValue = "0")
    private int reversalJournalEntryId;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    @NonNull public String getSyncId() { return syncId; }
    public void setSyncId(@NonNull String syncId) { this.syncId = syncId; }
    public int getPayableId() { return payableId; }
    public void setPayableId(int payableId) { this.payableId = payableId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public Date getPaidAt() { return paidAt; }
    public void setPaidAt(Date paidAt) { this.paidAt = paidAt; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public int getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(int journalEntryId) { this.journalEntryId = journalEntryId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getReversalJournalEntryId() { return reversalJournalEntryId; }
    public void setReversalJournalEntryId(int reversalJournalEntryId) { this.reversalJournalEntryId = reversalJournalEntryId; }
}