package com.financeapp.uganda.models;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import com.financeapp.uganda.utils.DateConverter;
import java.util.Date;
import java.util.UUID;

@Entity(tableName = "inventory", indices = {@Index(value = {"syncId"}, unique = true)})
@TypeConverters(DateConverter.class)
public class InventoryItem {
    @PrimaryKey(autoGenerate = true)
    private int id;
    @androidx.annotation.NonNull
    @androidx.room.ColumnInfo(defaultValue = "''")
    private String syncId = UUID.randomUUID().toString();
    private String name;
    private String category;
    private String size;
    private String color;
    private int quantity;
    private double unitCost;
    private double sellingPrice;
    private String supplier;
    private String importSource;
    private Date dateAdded;
    private int minStockLevel;
    private String barcode;
    private String notes;
    private String imagePath;
    private String shipmentBatchId;
    private Date arrivalDate;
    private double importDuty;
    private double shippingCost;
    private double clearingCost;

    public InventoryItem() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    @androidx.annotation.NonNull public String getSyncId() { return syncId; }
    public void setSyncId(@androidx.annotation.NonNull String syncId) { this.syncId = syncId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getUnitCost() { return unitCost; }
    public void setUnitCost(double unitCost) { this.unitCost = unitCost; }
    public double getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(double sellingPrice) { this.sellingPrice = sellingPrice; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public String getImportSource() { return importSource; }
    public void setImportSource(String importSource) { this.importSource = importSource; }
    public Date getDateAdded() { return dateAdded; }
    public void setDateAdded(Date dateAdded) { this.dateAdded = dateAdded; }
    public int getMinStockLevel() { return minStockLevel; }
    public void setMinStockLevel(int minStockLevel) { this.minStockLevel = minStockLevel; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getShipmentBatchId() { return shipmentBatchId; }
    public void setShipmentBatchId(String shipmentBatchId) { this.shipmentBatchId = shipmentBatchId; }
    public Date getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(Date arrivalDate) { this.arrivalDate = arrivalDate; }
    public double getImportDuty() { return importDuty; }
    public void setImportDuty(double importDuty) { this.importDuty = importDuty; }
    public double getShippingCost() { return shippingCost; }
    public void setShippingCost(double shippingCost) { this.shippingCost = shippingCost; }
    public double getClearingCost() { return clearingCost; }
    public void setClearingCost(double clearingCost) { this.clearingCost = clearingCost; }

    public double getTotalCost() {
        return unitCost + importDuty + shippingCost + clearingCost;
    }

    public double getProfitMargin() {
        return sellingPrice - getTotalCost();
    }
}
