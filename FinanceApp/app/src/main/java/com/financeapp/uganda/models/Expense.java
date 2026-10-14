package com.financeapp.uganda.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import com.financeapp.uganda.utils.DateConverter;
import java.util.Date;

@Entity(tableName = "expenses")
@TypeConverters(DateConverter.class)
public class Expense {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String category;
    private String subCategory;
    private double amount;
    private String currency;
    private String description;
    private Date date;
    private String paymentMethod;
    private String receiptNumber;
    private boolean isRecurring;
    private String tags;
    private String importSource;
    private String shipmentStatus;
    private Date expectedDeliveryDate;
    private String supplierName;
    private String trackingNumber;
    private int journalEntryId;
    private int payableId;
    @NonNull
    @ColumnInfo(defaultValue = "'POSTED'")
    private String status = "POSTED";
    private String voidReason;
    private Date voidedAt;

    public Expense() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
    public boolean isRecurring() { return isRecurring; }
    public void setRecurring(boolean recurring) { isRecurring = recurring; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getImportSource() { return importSource; }
    public void setImportSource(String importSource) { this.importSource = importSource; }
    public String getShipmentStatus() { return shipmentStatus; }
    public void setShipmentStatus(String shipmentStatus) { this.shipmentStatus = shipmentStatus; }
    public Date getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(Date expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public int getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(int journalEntryId) { this.journalEntryId = journalEntryId; }
    public int getPayableId() { return payableId; }
    public void setPayableId(int payableId) { this.payableId = payableId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public Date getVoidedAt() { return voidedAt; }
    public void setVoidedAt(Date voidedAt) { this.voidedAt = voidedAt; }
}
