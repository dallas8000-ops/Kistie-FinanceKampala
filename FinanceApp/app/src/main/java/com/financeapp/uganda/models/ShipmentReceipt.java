package com.financeapp.uganda.models;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import com.financeapp.uganda.utils.DateConverter;
import java.util.Date;
import java.util.UUID;

@Entity(tableName = "shipment_receipts", indices = {
        @Index(value = {"reference"}, unique = true),
        @Index(value = {"syncId"}, unique = true)
})
@TypeConverters(DateConverter.class)
public class ShipmentReceipt {
    @PrimaryKey(autoGenerate = true)
    private int id;
    @androidx.annotation.NonNull
    @ColumnInfo(defaultValue = "''")
    private String syncId = UUID.randomUUID().toString();
    private int shipmentId;
    private Date receivedAt;
    private double goodsValueUgx;
    private double allocatedChargesUgx;
    private double totalLandedCostUgx;
    private int payableId;
    private int journalEntryId;
    @androidx.annotation.NonNull
    private String reference;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    @androidx.annotation.NonNull public String getSyncId() { return syncId; }
    public void setSyncId(@androidx.annotation.NonNull String syncId) { this.syncId = syncId; }
    public int getShipmentId() { return shipmentId; }
    public void setShipmentId(int shipmentId) { this.shipmentId = shipmentId; }
    public Date getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Date receivedAt) { this.receivedAt = receivedAt; }
    public double getGoodsValueUgx() { return goodsValueUgx; }
    public void setGoodsValueUgx(double goodsValueUgx) { this.goodsValueUgx = goodsValueUgx; }
    public double getAllocatedChargesUgx() { return allocatedChargesUgx; }
    public void setAllocatedChargesUgx(double allocatedChargesUgx) { this.allocatedChargesUgx = allocatedChargesUgx; }
    public double getTotalLandedCostUgx() { return totalLandedCostUgx; }
    public void setTotalLandedCostUgx(double totalLandedCostUgx) { this.totalLandedCostUgx = totalLandedCostUgx; }
    public int getPayableId() { return payableId; }
    public void setPayableId(int payableId) { this.payableId = payableId; }
    public int getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(int journalEntryId) { this.journalEntryId = journalEntryId; }
    @androidx.annotation.NonNull public String getReference() { return reference; }
    public void setReference(@androidx.annotation.NonNull String reference) { this.reference = reference; }
}