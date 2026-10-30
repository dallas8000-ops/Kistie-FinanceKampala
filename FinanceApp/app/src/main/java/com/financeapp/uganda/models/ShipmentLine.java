package com.financeapp.uganda.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.util.UUID;

@Entity(tableName = "shipment_lines", indices = {@Index(value = {"syncId"}, unique = true)})
public class ShipmentLine {
    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull
    @ColumnInfo(defaultValue = "''")
    private String syncId = UUID.randomUUID().toString();
    private int shipmentId;
    private int inventoryItemId;
    private int orderedQuantity;
    @ColumnInfo(defaultValue = "0")
    private int receivedQuantity;
    @ColumnInfo(defaultValue = "0")
    private int quantityStocked;
    private double unitCost;
    @NonNull
    private String currencyCode = "UGX";
    @ColumnInfo(defaultValue = "1")
    private double exchangeRate = 1d;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    @NonNull public String getSyncId() { return syncId; }
    public void setSyncId(@NonNull String syncId) { this.syncId = syncId; }
    public int getShipmentId() { return shipmentId; }
    public void setShipmentId(int shipmentId) { this.shipmentId = shipmentId; }
    public int getInventoryItemId() { return inventoryItemId; }
    public void setInventoryItemId(int inventoryItemId) { this.inventoryItemId = inventoryItemId; }
    public int getOrderedQuantity() { return orderedQuantity; }
    public void setOrderedQuantity(int orderedQuantity) { this.orderedQuantity = orderedQuantity; }
    public int getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(int receivedQuantity) { this.receivedQuantity = receivedQuantity; }
    public int getQuantityStocked() { return quantityStocked; }
    public void setQuantityStocked(int quantityStocked) { this.quantityStocked = quantityStocked; }
    public double getUnitCost() { return unitCost; }
    public void setUnitCost(double unitCost) { this.unitCost = unitCost; }
    @NonNull public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(@NonNull String currencyCode) { this.currencyCode = currencyCode; }
    public double getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(double exchangeRate) { this.exchangeRate = exchangeRate; }
}