package org.example.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Coupon lifecycle management: creation, validation, and single-use atomic claim. */
@Service
public class CouponService {

    private final org.example.dao.CouponDAO couponDAO;

    public CouponService(org.example.dao.CouponDAO couponDAO) {
        this.couponDAO = couponDAO;
    }

    /** Validates and persists a new coupon; throws IllegalArgumentException on any spec violation. */
    public org.example.model.Coupon createCoupon(org.example.model.Coupon coupon) {
        if (coupon == null) {
            throw new IllegalArgumentException("Coupon is required.");
        }
        if (coupon.getCode() == null || coupon.getCode().isBlank()) {
            throw new IllegalArgumentException("Coupon code must not be blank.");
        }
        String type = coupon.getDiscountType();
        if (!"PERCENTAGE".equals(type) && !"AMOUNT".equals(type)) {
            throw new IllegalArgumentException("Discount type must be PERCENTAGE or AMOUNT.");
        }
        if (coupon.getDiscountValue() <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than 0.");
        }
        if ("PERCENTAGE".equals(type) && coupon.getDiscountValue() > 100) {
            throw new IllegalArgumentException("Percentage discount must not exceed 100.");
        }
        if (coupon.getValidFrom() == null || coupon.getValidUntil() == null) {
            throw new IllegalArgumentException("validFrom and validUntil are required.");
        }
        if (coupon.getValidUntil().isBefore(coupon.getValidFrom())) {
            throw new IllegalArgumentException("validUntil must not be before validFrom.");
        }

        int id = couponDAO.insert(coupon);
        coupon.setCouponId(id);
        return coupon;
    }

    /** Looks up a coupon by primary key. */
    public Optional<org.example.model.Coupon> findById(int couponId) {
        return couponDAO.findById(couponId);
    }

    /** Returns all coupons for a restaurant. */
    public List<org.example.model.Coupon> listByRestaurant(int restaurantId) {
        return couponDAO.findByRestaurant(restaurantId);
    }

    /** Manager-facing: deactivate a coupon by id (ignores whether it was already inactive). */
    public void deactivate(int couponId) {
        couponDAO.tryDeactivate(couponId);
    }

    /** Order-placement: atomically claims the coupon; returns false if already used. */
    public boolean tryDeactivate(int couponId) {
        return couponDAO.tryDeactivate(couponId);
    }

    /** Validates a coupon without claiming it; used by the preview endpoint before checkout. */
    public org.example.model.Coupon preview(String code, int restaurantId) {
        Optional<org.example.model.Coupon> opt = couponDAO.findByCode(code);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Invalid or expired coupon.");
        }
        org.example.model.Coupon coupon = opt.get();
        if (coupon.getRestaurantId() != restaurantId) {
            throw new IllegalArgumentException("Coupon is not valid for this restaurant.");
        }
        if (!coupon.isValidToday()) {
            throw new IllegalArgumentException("Invalid or expired coupon.");
        }
        return coupon;
    }

    /** Resolves and validates a coupon at checkout; throws if missing, expired, or wrong restaurant. */
    public org.example.model.Coupon resolveForOrder(String code, int restaurantId) {
        Optional<org.example.model.Coupon> opt = couponDAO.findByCode(code);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Invalid or expired coupon.");
        }
        org.example.model.Coupon coupon = opt.get();
        if (coupon.getRestaurantId() != restaurantId) {
            throw new IllegalArgumentException("Coupon is not valid for this restaurant.");
        }
        if (!coupon.isValidToday()) {
            throw new IllegalArgumentException("Invalid or expired coupon.");
        }
        return coupon;
    }
}
