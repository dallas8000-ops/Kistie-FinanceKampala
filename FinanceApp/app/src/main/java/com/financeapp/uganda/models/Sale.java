package com.financeapp.uganda.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import com.financeapp.uganda.utils.DateConverter;
import java.util.Date;

@Entity(tableName = "sales")
@TypeConverters(DateConverter.class)
public class Sale {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private int inventoryItemId;
    private String itemName;
    private int quantitySold;
    private double unitPrice;
    private double totalAmount;
    private double discount;
    private Date saleDate;
    private String customerName;
    private String customerPhone;
    private String paymentMethod;
    private String transactionId;
    private double profit;
    private String notes;
    private int journalEntryId;
    private int receivableId;
    private double costOfGoodsSold;
    @ColumnInfo(defaultValue = "0")
    private double amountPaid;
    @NonNull
    @ColumnInfo(defaultValue = "'POSTED'")
    private String status = "POSTED";
    private String voidReason;
    private Date voidedAt;

    public Sale() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getInventoryItemId() { return inventoryItemId; }
    public void setInventoryItemId(int inventoryItemId) { this.inventoryItemId = inventoryItemId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public int getQuantitySold() { return quantitySold; }
    public void setQuantitySold(int quantitySold) { this.quantitySold = quantitySold; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }
    public Date getSaleDate() { return saleDate; }
    public void setSaleDate(Date saleDate) { this.saleDate = saleDate; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public double getProfit() { return profit; }
    public void setProfit(double profit) { this.profit = profit; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public int getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(int journalEntryId) { this.journalEntryId = journalEntryId; }
    public int getReceivableId() { return receivableId; }
    public void setReceivableId(int receivableId) { this.receivableId = receivableId; }
    public double getCostOfGoodsSold() { return costOfGoodsSold; }
    public void setCostOfGoodsSold(double costOfGoodsSold) { this.costOfGoodsSold = costOfGoodsSold; }
    public double getAmountPaid() { return amountPaid; }
    public void setAmountPaid(double amountPaid) { this.amountPaid = amountPaid; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public Date getVoidedAt() { return voidedAt; }
    public void setVoidedAt(Date voidedAt) { this.voidedAt = voidedAt; }
}
