package org.example.model;

import java.time.LocalDate;

/** Represents a restaurant discount coupon with a validity window and a single-use flag. */
public class Coupon {
    private int couponId;
    private int restaurantId;
    private String code;
    private String discountType;
    private double discountValue;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private boolean active;

    public Coupon() {}

    public Coupon(int couponId, int restaurantId, String code, String discountType,
                  double discountValue, LocalDate validFrom, LocalDate validUntil, boolean active) {
        this.couponId      = couponId;
        this.restaurantId  = restaurantId;
        this.code          = code;
        this.discountType  = discountType;
        this.discountValue = discountValue;
        this.validFrom     = validFrom;
        this.validUntil    = validUntil;
        this.active        = active;
    }

    public int        getCouponId()      { return couponId; }
    public int        getRestaurantId()  { return restaurantId; }
    public String     getCode()          { return code; }
    public String     getDiscountType()  { return discountType; }
    public double     getDiscountValue() { return discountValue; }
    public LocalDate  getValidFrom()     { return validFrom; }
    public LocalDate  getValidUntil()    { return validUntil; }
    public boolean    isActive()         { return active; }

    public void setCouponId(int id)          { this.couponId      = id; }
    public void setRestaurantId(int id)      { this.restaurantId  = id; }
    public void setCode(String c)            { this.code          = c; }
    public void setDiscountType(String dt)   { this.discountType  = dt; }
    public void setDiscountValue(double dv)  { this.discountValue = dv; }
    public void setValidFrom(LocalDate d)    { this.validFrom     = d; }
    public void setValidUntil(LocalDate d)   { this.validUntil    = d; }
    public void setActive(boolean a)         { this.active        = a; }

    /** Returns true if the coupon is active and today falls within its valid date range. */
    public boolean isValidToday() {
        LocalDate today = LocalDate.now();
        return active && !today.isBefore(validFrom) && !today.isAfter(validUntil);
    }

    /** Applies the coupon discount to an order total and returns the discounted amount. */
    public double applyDiscount(double orderTotal) {
        if ("PERCENTAGE".equals(discountType)) {
            double pct = Math.max(0.0, Math.min(100.0, discountValue));
            return orderTotal * (1 - pct / 100.0);
        } else {
            return Math.max(0, orderTotal - discountValue);
        }
    }

    @Override
    public String toString() { return code + " (" + discountType + " " + discountValue + ")"; }
}
