package org.example.model;

/** Represents a single line item within an order. */
public class OrderItem {
    private int orderItemId;
    private int orderId;
    private int itemId;
    private int quantity;
    private double unitPrice;
    private String itemName; // populated by JOIN query, not persisted

    public OrderItem() {}

    public OrderItem(int orderId, int itemId, int quantity, double unitPrice) {
        this.orderId   = orderId;
        this.itemId    = itemId;
        this.quantity  = quantity;
        this.unitPrice = unitPrice;
    }

    public int    getOrderItemId() { return orderItemId; }
    public int    getOrderId()     { return orderId; }
    public int    getItemId()      { return itemId; }
    public int    getQuantity()    { return quantity; }
    public double getUnitPrice()   { return unitPrice; }
    public String getItemName()    { return itemName; }

    public void setOrderItemId(int id)  { this.orderItemId = id; }
    public void setOrderId(int id)      { this.orderId     = id; }
    public void setItemId(int id)       { this.itemId      = id; }
    public void setQuantity(int q)      { this.quantity    = q; }
    public void setUnitPrice(double p)  { this.unitPrice   = p; }
    public void setItemName(String n)   { this.itemName    = n; }

    /** Returns quantity multiplied by unit price. */
    public double getSubtotal() { return quantity * unitPrice; }

    @Override
    public String toString() {
        return (itemName != null ? itemName : "Item #" + itemId)
                + " x" + quantity + " = " + String.format(java.util.Locale.ROOT, "%.2f TL", getSubtotal());
    }
}