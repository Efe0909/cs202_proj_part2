package org.example.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
/** Represents a customer order with lifecycle status (SENT → PREPARING → ARRIVED). */
public class Order {
    private int orderId;
    private int customerId;
    private int restaurantId;
    private Integer couponId;
    private String status;
    private double totalPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime preparingAt; // nullable — stamped when manager accepts
    private LocalDateTime arrivedAt;   // nullable — stamped when manager marks delivered
    private List<OrderItem> items;

    public Order() {}

    public int           getOrderId()      { return orderId; }
    public int           getCustomerId()   { return customerId; }
    public int           getRestaurantId() { return restaurantId; }
    public Integer       getCouponId()     { return couponId; }
    public String        getStatus()       { return status; }
    public double        getTotalPrice()   { return totalPrice; }
    public LocalDateTime getCreatedAt()    { return createdAt; }
    public LocalDateTime getUpdatedAt()    { return updatedAt; }
    public LocalDateTime getPreparingAt()  { return preparingAt; }
    public LocalDateTime getArrivedAt()    { return arrivedAt; }
    public List<OrderItem> getItems()      { return items; }

    public void setOrderId(int id)               { this.orderId      = id; }
    public void setCustomerId(int id)            { this.customerId   = id; }
    public void setRestaurantId(int id)          { this.restaurantId = id; }
    public void setCouponId(Integer id)          { this.couponId     = id; }
    public void setStatus(String s)              { this.status       = s; }
    public void setTotalPrice(double tp)         { this.totalPrice   = tp; }
    public void setCreatedAt(LocalDateTime dt)   { this.createdAt    = dt; }
    public void setUpdatedAt(LocalDateTime dt)   { this.updatedAt    = dt; }
    public void setPreparingAt(LocalDateTime dt) { this.preparingAt  = dt; }
    public void setArrivedAt(LocalDateTime dt)   { this.arrivedAt    = dt; }
    public void setItems(List<OrderItem> items)  { this.items        = items; }

    public boolean isSent()      { return "SENT".equals(status); }
    public boolean isPreparing() { return "PREPARING".equals(status); }
    public boolean isArrived()   { return "ARRIVED".equals(status); }

    @Override
    public String toString() {
        return "Order #" + orderId + " [" + status + "] - " + String.format(java.util.Locale.ROOT, "%.2f TL", totalPrice);
    }
}
