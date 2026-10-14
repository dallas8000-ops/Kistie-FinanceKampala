package com.financeapp.uganda.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import com.financeapp.uganda.utils.DateConverter;
import java.util.Date;

@Entity(tableName = "shipments")
@TypeConverters(DateConverter.class)
public class Shipment {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String batchId;
    private String sourceCountry;
    private String supplierName;
    private Date orderDate;
    private Date shipDate;
    private Date arrivalDate;
    private Date customsClearanceDate;
    private Date deliveryDate;
    private String status;
    private double totalValue;
    private double shippingCost;
    private double insuranceCost;
    private double importDuty;
    private double vat;
    private double clearingCharges;
    private double transportToStore;
    private String containerNumber;
    private String billOfLading;
    private String trackingNumber;
    private String notes;
    private boolean isDelivered;
    private Date stockReceivedAt;
    @ColumnInfo(defaultValue = "0")
    private int payableId;
    @ColumnInfo(defaultValue = "0")
    private int journalEntryId;

    public Shipment() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getSourceCountry() { return sourceCountry; }
    public void setSourceCountry(String sourceCountry) { this.sourceCountry = sourceCountry; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public Date getOrderDate() { return orderDate; }
    public void setOrderDate(Date orderDate) { this.orderDate = orderDate; }
    public Date getShipDate() { return shipDate; }
    public void setShipDate(Date shipDate) { this.shipDate = shipDate; }
    public Date getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(Date arrivalDate) { this.arrivalDate = arrivalDate; }
    public Date getCustomsClearanceDate() { return customsClearanceDate; }
    public void setCustomsClearanceDate(Date customsClearanceDate) { this.customsClearanceDate = customsClearanceDate; }
    public Date getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(Date deliveryDate) { this.deliveryDate = deliveryDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getTotalValue() { return totalValue; }
    public void setTotalValue(double totalValue) { this.totalValue = totalValue; }
    public double getShippingCost() { return shippingCost; }
    public void setShippingCost(double shippingCost) { this.shippingCost = shippingCost; }
    public double getInsuranceCost() { return insuranceCost; }
    public void setInsuranceCost(double insuranceCost) { this.insuranceCost = insuranceCost; }
    public double getImportDuty() { return importDuty; }
    public void setImportDuty(double importDuty) { this.importDuty = importDuty; }
    public double getVat() { return vat; }
    public void setVat(double vat) { this.vat = vat; }
    public double getClearingCharges() { return clearingCharges; }
    public void setClearingCharges(double clearingCharges) { this.clearingCharges = clearingCharges; }
    public double getTransportToStore() { return transportToStore; }
    public void setTransportToStore(double transportToStore) { this.transportToStore = transportToStore; }
    public String getContainerNumber() { return containerNumber; }
    public void setContainerNumber(String containerNumber) { this.containerNumber = containerNumber; }
    public String getBillOfLading() { return billOfLading; }
    public void setBillOfLading(String billOfLading) { this.billOfLading = billOfLading; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isDelivered() { return isDelivered; }
    public void setDelivered(boolean delivered) { isDelivered = delivered; }
    public Date getStockReceivedAt() { return stockReceivedAt; }
    public void setStockReceivedAt(Date stockReceivedAt) { this.stockReceivedAt = stockReceivedAt; }
    public int getPayableId() { return payableId; }
    public void setPayableId(int payableId) { this.payableId = payableId; }
    public int getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(int journalEntryId) { this.journalEntryId = journalEntryId; }

    public double getTotalImportCost() {
        return totalValue + shippingCost + insuranceCost + importDuty + vat + clearingCharges + transportToStore;
    }
}
