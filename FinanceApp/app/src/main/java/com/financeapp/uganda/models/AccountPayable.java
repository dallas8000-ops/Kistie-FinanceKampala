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

@Entity(tableName = "accounts_payable", indices = {@Index(value = {"syncId"}, unique = true)})
@TypeConverters(DateConverter.class)
public class AccountPayable {
    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull
    @ColumnInfo(defaultValue = "''")
    private String syncId = UUID.randomUUID().toString();
    private String vendorName;
    private String description;
    private double amount;
    private double amountPaid;
    private Date billDate;
    private Date dueDate;
    private String status; // OPEN, PARTIAL, PAID, VOID
    private int journalEntryId;
    private int expenseId;
    private String voidReason;
    private Date voidedAt;

    public AccountPayable() {}

    public double getBalanceDue() { return amount - amountPaid; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    @NonNull public String getSyncId() { return syncId; }
    public void setSyncId(@NonNull String syncId) { this.syncId = syncId; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public double getAmountPaid() { return amountPaid; }
    public void setAmountPaid(double amountPaid) { this.amountPaid = amountPaid; }
    public Date getBillDate() { return billDate; }
    public void setBillDate(Date billDate) { this.billDate = billDate; }
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(int journalEntryId) { this.journalEntryId = journalEntryId; }
    public int getExpenseId() { return expenseId; }
    public void setExpenseId(int expenseId) { this.expenseId = expenseId; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public Date getVoidedAt() { return voidedAt; }
    public void setVoidedAt(Date voidedAt) { this.voidedAt = voidedAt; }
}
