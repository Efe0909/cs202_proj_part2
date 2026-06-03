package org.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Handles coupon creation, listing, validation, and deactivation. */
@RestController
@RequestMapping("/api/restaurants")
public class CouponController {

    private static final Logger log = LoggerFactory.getLogger(CouponController.class);

    private final org.example.service.CouponService couponService;
    private final org.example.service.RestaurantService restaurantService;

    public CouponController(org.example.service.CouponService couponService,
                            org.example.service.RestaurantService restaurantService) {
        this.couponService     = couponService;
        this.restaurantService = restaurantService;
    }

    /** Creates a coupon for a restaurant; enforces manager ownership. */
    @PostMapping("/{restaurantId}/coupons")
    public ResponseEntity<?> create(@PathVariable int restaurantId,
                                    @RequestBody Map<String, Object> body) {
        int managerId = ControllerInputs.requireInt(body, "managerId");
        restaurantService.requireOwnership(restaurantId, managerId);
        org.example.model.Coupon c = new org.example.model.Coupon();
        c.setRestaurantId(restaurantId);
        c.setCode(ControllerInputs.requireString(body, "code"));
        c.setDiscountType(ControllerInputs.requireString(body, "discountType"));
        c.setDiscountValue(asDouble(body.get("discountValue")));
        c.setValidFrom(parseDate(body.get("validFrom"), "validFrom"));
        c.setValidUntil(parseDate(body.get("validUntil"), "validUntil"));
        c.setActive(true);
        org.example.model.Coupon created = couponService.createCoupon(c);
        log.info("Coupon created: id={} restaurant={} code={}",
                created.getCouponId(), restaurantId, created.getCode());
        return ResponseEntity.ok(created);
    }

    /** Returns all coupons for a restaurant. */
    @GetMapping("/{restaurantId}/coupons")
    public List<org.example.model.Coupon> list(@PathVariable int restaurantId) {
        return couponService.listByRestaurant(restaurantId);
    }

    /** Validates a coupon code and returns the discount details without claiming it. */
    @GetMapping("/coupons/validate")
    public ResponseEntity<?> validate(@RequestParam String code,
                                      @RequestParam int restaurantId) {
        org.example.model.Coupon c = couponService.preview(code, restaurantId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("code",          c.getCode());
        result.put("discountType",  c.getDiscountType());
        result.put("discountValue", c.getDiscountValue());
        return ResponseEntity.ok(result);
    }

    /** Deactivates a coupon; enforces manager ownership of the coupon's restaurant. */
    @DeleteMapping("/coupons/{couponId}")
    public ResponseEntity<?> deactivate(@PathVariable int couponId,
                                        @RequestParam int managerId) {
        org.example.model.Coupon c = couponService.findById(couponId).orElse(null);
        if (c == null) {
            throw new IllegalArgumentException("Coupon not found: " + couponId);
        }
        restaurantService.requireOwnership(c.getRestaurantId(), managerId);
        couponService.deactivate(couponId);
        log.info("Coupon deactivated: id={}", couponId);
        return ResponseEntity.ok().build();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) throw new IllegalArgumentException("discountValue is required.");
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("discountValue must be a number.");
        }
    }

    private static LocalDate parseDate(Object o, String field) {
        if (o == null) throw new IllegalArgumentException(field + " is required.");
        try {
            return LocalDate.parse(o.toString().trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " must be a date in YYYY-MM-DD format.");
        }
    }
}
