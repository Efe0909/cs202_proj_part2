package org.example.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private org.example.dao.CouponDAO couponDAO;

    @InjectMocks
    private CouponService couponService;

    private org.example.model.Coupon coupon(String code, String type, double value,
                          LocalDate from, LocalDate until) {
        return new org.example.model.Coupon(0, 5, code, type, value, from, until, true);
    }

    @Test
    void createCoupon_nullCoupon_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(null));
        verify(couponDAO, never()).insert(any());
    }

    @Test
    void createCoupon_nullCode_throws() {
        org.example.model.Coupon c = coupon(null, "PERCENTAGE", 10.0,
                LocalDate.now(), LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(c));
        verify(couponDAO, never()).insert(any());
    }

    @Test
    void createCoupon_blankCode_throws() {
        org.example.model.Coupon c = coupon("", "PERCENTAGE", 10.0,
                LocalDate.now(), LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(c));
        verify(couponDAO, never()).insert(any());
    }

    @Test
    void createCoupon_whitespaceOnlyCode_throws() {
        org.example.model.Coupon c = coupon("   \t  ", "PERCENTAGE", 10.0,
                LocalDate.now(), LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(c),
                "Whitespace-only code must be rejected (isBlank)");
        verify(couponDAO, never()).insert(any());
    }

    @Test
    void createCoupon_unknownDiscountType_throws() {
        org.example.model.Coupon c = coupon("SAVE", "FLAT", 10.0,
                LocalDate.now(), LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(c));
        verify(couponDAO, never()).insert(any());
    }

    @Test
    void createCoupon_lowercaseDiscountType_throws() {
        org.example.model.Coupon c = coupon("SAVE", "percentage", 10.0,
                LocalDate.now(), LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(c));
        verify(couponDAO, never()).insert(any());
    }

    @Test
    void createCoupon_zeroDiscountValue_throws() {
        org.example.model.Coupon c = coupon("SAVE", "AMOUNT", 0.0,
                LocalDate.now(), LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(c),
                "discountValue == 0 must be rejected");
        verify(couponDAO, never()).insert(any());
    }

    @Test
    void createCoupon_negativeDiscountValue_throws() {
        org.example.model.Coupon c = coupon("SAVE", "AMOUNT", -5.0,
                LocalDate.now(), LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(c),
                "Negative discountValue must be rejected");
        verify(couponDAO, never()).insert(any());
    }

    @Test
    void createCoupon_percentageOver100_throws() {
        org.example.model.Coupon c = coupon("SAVE", "PERCENTAGE", 150.0,
                LocalDate.now(), LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(c),
                "PERCENTAGE > 100 must be rejected");
        verify(couponDAO, never()).insert(any());
    }

    @Test
    void createCoupon_percentageJustOver100_throws() {
        org.example.model.Coupon c = coupon("SAVE", "PERCENTAGE", 100.01,
                LocalDate.now(), LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(c),
                "PERCENTAGE 100.01 must be rejected");
        verify(couponDAO, never()).insert(any());
    }

    @Test
    void createCoupon_validUntilBeforeValidFrom_throws() {
        org.example.model.Coupon c = coupon("SAVE", "AMOUNT", 5.0,
                LocalDate.now(), LocalDate.now().minusDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(c),
                "validUntil strictly before validFrom must be rejected");
        verify(couponDAO, never()).insert(any());
    }

    @Test
    void createCoupon_nullValidFrom_throws() {
        org.example.model.Coupon c = coupon("SAVE", "AMOUNT", 5.0,
                null, LocalDate.now().plusDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> couponService.createCoupon(c));
        verify(couponDAO, never()).insert(any());
    }

    @Test
    void createCoupon_validPercentageAt100Boundary_accepted() {
        org.example.model.Coupon c = coupon("HALF", "PERCENTAGE", 100.0,
                LocalDate.now(), LocalDate.now().plusDays(30));
        when(couponDAO.insert(any(org.example.model.Coupon.class))).thenReturn(77);

        org.example.model.Coupon result = couponService.createCoupon(c);

        assertEquals(77, result.getCouponId());
        verify(couponDAO, times(1)).insert(c);
    }

    @Test
    void createCoupon_validAmount_accepted() {
        org.example.model.Coupon c = coupon("FIVE", "AMOUNT", 5.0,
                LocalDate.now(), LocalDate.now().plusDays(10));
        when(couponDAO.insert(any(org.example.model.Coupon.class))).thenReturn(12);

        org.example.model.Coupon result = couponService.createCoupon(c);

        assertEquals(12, result.getCouponId());
        verify(couponDAO, times(1)).insert(c);
    }

    @Test
    void createCoupon_validFromEqualsValidUntil_accepted() {
        LocalDate day = LocalDate.now();
        org.example.model.Coupon c = coupon("ONEDAY", "AMOUNT", 3.0, day, day);
        when(couponDAO.insert(any(org.example.model.Coupon.class))).thenReturn(1);

        org.example.model.Coupon result = couponService.createCoupon(c);

        assertEquals(1, result.getCouponId());
        verify(couponDAO, times(1)).insert(c);
    }

    @Test
    void resolveForOrder_unknownCode_throws() {
        when(couponDAO.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> couponService.resolveForOrder("NOPE", 5));
    }

    @Test
    void resolveForOrder_differentRestaurant_throws() {
        org.example.model.Coupon c = new org.example.model.Coupon(2, 99, "OTHER", "AMOUNT", 5.0,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), true);
        when(couponDAO.findByCode("OTHER")).thenReturn(Optional.of(c));

        assertThrows(IllegalArgumentException.class,
                () -> couponService.resolveForOrder("OTHER", 5),
                "Coupon for restaurant 99 must not resolve for restaurant 5");
    }

    @Test
    void resolveForOrder_expiredDateRange_throws() {
        org.example.model.Coupon c = new org.example.model.Coupon(3, 5, "OLD", "PERCENTAGE", 10.0,
                LocalDate.now().minusDays(30), LocalDate.now().minusDays(1), true);
        when(couponDAO.findByCode("OLD")).thenReturn(Optional.of(c));

        assertThrows(IllegalArgumentException.class,
                () -> couponService.resolveForOrder("OLD", 5),
                "Coupon whose date range ended yesterday must be rejected");
    }

    @Test
    void resolveForOrder_notYetValid_throws() {
        org.example.model.Coupon c = new org.example.model.Coupon(4, 5, "FUTURE", "PERCENTAGE", 10.0,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(30), true);
        when(couponDAO.findByCode("FUTURE")).thenReturn(Optional.of(c));

        assertThrows(IllegalArgumentException.class,
                () -> couponService.resolveForOrder("FUTURE", 5),
                "Coupon starting tomorrow must be rejected today");
    }

    @Test
    void resolveForOrder_inactiveCoupon_throws() {
        org.example.model.Coupon c = new org.example.model.Coupon(5, 5, "DEAD", "PERCENTAGE", 10.0,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), false);
        when(couponDAO.findByCode("DEAD")).thenReturn(Optional.of(c));

        assertThrows(IllegalArgumentException.class,
                () -> couponService.resolveForOrder("DEAD", 5),
                "Inactive coupon must be rejected even within date range");
    }

    @Test
    void resolveForOrder_validCoupon_returnsIt() {
        org.example.model.Coupon c = new org.example.model.Coupon(6, 5, "GOOD", "PERCENTAGE", 25.0,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), true);
        when(couponDAO.findByCode("GOOD")).thenReturn(Optional.of(c));

        org.example.model.Coupon result = couponService.resolveForOrder("GOOD", 5);

        assertSame(c, result);
    }

    @Test
    void deactivate_delegatesToDao() {
        couponService.deactivate(42);
        verify(couponDAO, times(1)).tryDeactivate(42);
    }
}
