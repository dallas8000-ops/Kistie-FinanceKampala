package com.financeapp.uganda.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "shipment_receipt_lines")
public class ShipmentReceiptLine {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private int receiptId;
    private int shipmentLineId;
    private int quantity;
    private double goodsValueUgx;
    private double allocatedShippingUgx;
    private double allocatedDutyVatUgx;
    private double allocatedClearingUgx;
    private double landedUnitCostUgx;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getReceiptId() { return receiptId; }
    public void setReceiptId(int receiptId) { this.receiptId = receiptId; }
    public int getShipmentLineId() { return shipmentLineId; }
    public void setShipmentLineId(int shipmentLineId) { this.shipmentLineId = shipmentLineId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getGoodsValueUgx() { return goodsValueUgx; }
    public void setGoodsValueUgx(double goodsValueUgx) { this.goodsValueUgx = goodsValueUgx; }
    public double getAllocatedShippingUgx() { return allocatedShippingUgx; }
    public void setAllocatedShippingUgx(double allocatedShippingUgx) { this.allocatedShippingUgx = allocatedShippingUgx; }
    public double getAllocatedDutyVatUgx() { return allocatedDutyVatUgx; }
    public void setAllocatedDutyVatUgx(double allocatedDutyVatUgx) { this.allocatedDutyVatUgx = allocatedDutyVatUgx; }
    public double getAllocatedClearingUgx() { return allocatedClearingUgx; }
    public void setAllocatedClearingUgx(double allocatedClearingUgx) { this.allocatedClearingUgx = allocatedClearingUgx; }
    public double getLandedUnitCostUgx() { return landedUnitCostUgx; }
    public void setLandedUnitCostUgx(double landedUnitCostUgx) { this.landedUnitCostUgx = landedUnitCostUgx; }
}