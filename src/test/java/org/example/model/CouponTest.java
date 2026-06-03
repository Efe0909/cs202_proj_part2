package org.example.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CouponTest {

    private Coupon percentage(double value) {
        return new Coupon(1, 5, "P", "PERCENTAGE", value,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), true);
    }

    private Coupon amount(double value) {
        return new Coupon(1, 5, "A", "AMOUNT", value,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), true);
    }

    @Test
    void applyDiscount_percentageNormalCase() {
        assertEquals(80.0, percentage(20.0).applyDiscount(100.0), 1e-9);
    }

    @Test
    void applyDiscount_percentage100_givesZeroNotNegative() {
        double result = percentage(100.0).applyDiscount(250.0);
        assertEquals(0.0, result, 1e-9, "100% off must yield exactly 0");
        assertTrue(result >= 0.0, "Total must never be negative");
    }

    @Test
    void applyDiscount_percentageOver100_clampedToZeroNeverNegative() {
        double result = percentage(150.0).applyDiscount(100.0);
        assertTrue(result >= 0.0,
                "Out-of-range percentage must never produce a negative total");
        assertEquals(0.0, result, 1e-9);
    }

    @Test
    void applyDiscount_percentageNegativeValue_clampedSoTotalNotInflated() {
        double result = percentage(-50.0).applyDiscount(100.0);
        assertEquals(100.0, result, 1e-9,
                "Negative percentage clamps to 0% -> total unchanged, not inflated");
        assertTrue(result <= 100.0);
    }

    @Test
    void applyDiscount_percentageZeroTotal_staysZero() {
        assertEquals(0.0, percentage(30.0).applyDiscount(0.0), 1e-9);
    }

    @Test
    void applyDiscount_amountNormalCase_subtractsCorrectly() {
        assertEquals(45.0, amount(5.0).applyDiscount(50.0), 1e-9);
    }

    @Test
    void applyDiscount_amountGreaterThanSubtotal_givesZeroNotNegative() {
        double result = amount(80.0).applyDiscount(50.0);
        assertEquals(0.0, result, 1e-9,
                "AMOUNT discount exceeding subtotal must floor at 0");
        assertTrue(result >= 0.0, "Total must never be negative");
    }

    @Test
    void applyDiscount_amountEqualToSubtotal_givesZero() {
        assertEquals(0.0, amount(50.0).applyDiscount(50.0), 1e-9);
    }

    @Test
    void isValidToday_activeWithinRange_true() {
        Coupon c = new Coupon(1, 5, "C", "AMOUNT", 5.0,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), true);
        assertTrue(c.isValidToday());
    }

    @Test
    void isValidToday_inactive_false() {
        Coupon c = new Coupon(1, 5, "C", "AMOUNT", 5.0,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), false);
        assertFalse(c.isValidToday(), "Inactive coupon is never valid today");
    }

    @Test
    void isValidToday_startsTodayInclusive_true() {
        Coupon c = new Coupon(1, 5, "C", "AMOUNT", 5.0,
                LocalDate.now(), LocalDate.now().plusDays(5), true);
        assertTrue(c.isValidToday(), "validFrom == today must be inclusive");
    }

    @Test
    void isValidToday_endsTodayInclusive_true() {
        Coupon c = new Coupon(1, 5, "C", "AMOUNT", 5.0,
                LocalDate.now().minusDays(5), LocalDate.now(), true);
        assertTrue(c.isValidToday(), "validUntil == today must be inclusive");
    }

    @Test
    void isValidToday_expiredYesterday_false() {
        Coupon c = new Coupon(1, 5, "C", "AMOUNT", 5.0,
                LocalDate.now().minusDays(10), LocalDate.now().minusDays(1), true);
        assertFalse(c.isValidToday());
    }

    @Test
    void isValidToday_startsTomorrow_false() {
        Coupon c = new Coupon(1, 5, "C", "AMOUNT", 5.0,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(10), true);
        assertFalse(c.isValidToday());
    }
}
