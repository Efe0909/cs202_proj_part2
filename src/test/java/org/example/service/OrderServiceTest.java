package org.example.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private org.example.dao.OrderDAO orderDAO;

    @Mock
    private org.example.dao.CouponDAO couponDAO;

    @Mock
    private org.example.dao.RatingDAO ratingDAO;

    @Mock
    private org.example.dao.MenuItemDAO menuItemDAO;

    @Mock
    private org.example.dao.UserDAO userDAO;

    @Mock
    private org.example.dao.RestaurantDAO restaurantDAO;

    @InjectMocks
    private OrderService orderService;

    private org.example.model.MenuItem menuItem(int itemId, double price) {
        return new org.example.model.MenuItem(itemId, 5, 1, "Item " + itemId, "desc", price, "img.jpg");
    }

    private void stubSameCity(int customerId, int restaurantId) {
        org.example.model.Restaurant restaurant = new org.example.model.Restaurant(restaurantId, 5, "R", "Cuisine",
                "Addr", "Ankara");
        when(restaurantDAO.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(userDAO.findSelectedAddressCity(customerId)).thenReturn(Optional.of("Ankara"));
    }

    @Test
    void placeOrder_noCoupon_insertsOrderAndItems() {
        List<org.example.model.OrderItem> items = List.of(
                new org.example.model.OrderItem(0, 1, 2, 10.0),
                new org.example.model.OrderItem(0, 2, 1, 15.0)
        );
        stubSameCity(1, 5);
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 10.0)));
        when(menuItemDAO.findById(2)).thenReturn(Optional.of(menuItem(2, 15.0)));
        when(orderDAO.insert(any(org.example.model.Order.class))).thenReturn(99);

        org.example.model.Order result = orderService.placeOrder(1, 5, items, null);

        assertEquals("SENT", result.getStatus());
        assertEquals(35.0, result.getTotalPrice(), 0.001);
        assertEquals(99, result.getOrderId());
        verify(orderDAO, times(1)).insert(any(org.example.model.Order.class));
        verify(orderDAO, times(2)).insertItem(any(org.example.model.OrderItem.class));
        verifyNoInteractions(couponDAO);
    }

    @Test
    void placeOrder_validPercentageCoupon_appliesToTotal() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 100.0));

        stubSameCity(1, 5);
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 100.0)));

        org.example.model.Coupon coupon = new org.example.model.Coupon(7, 5, "SAVE10", "PERCENTAGE", 10.0,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10), true);

        when(couponDAO.findByCode("SAVE10")).thenReturn(Optional.of(coupon));
        when(couponDAO.tryDeactivate(7)).thenReturn(true);
        when(orderDAO.insert(any(org.example.model.Order.class))).thenReturn(10);

        org.example.model.Order result = orderService.placeOrder(1, 5, items, "SAVE10");

        assertEquals(90.0, result.getTotalPrice(), 0.001);
        assertEquals(7, result.getCouponId());
    }

    @Test
    void placeOrder_invalidCouponCode_throwsIllegalArgumentException() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 50.0));

        stubSameCity(1, 5);
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 50.0)));
        when(couponDAO.findByCode("BADCODE")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(1, 5, items, "BADCODE"));

        verify(orderDAO, never()).insert(any());
    }

    @Test
    void placeOrder_expiredCoupon_throwsIllegalArgumentException() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 50.0));

        stubSameCity(1, 5);
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 50.0)));

        org.example.model.Coupon expiredCoupon = new org.example.model.Coupon(3, 5, "EXPIRED", "PERCENTAGE", 20.0,
                LocalDate.now().minusDays(30), LocalDate.now().minusDays(1), true);

        when(couponDAO.findByCode("EXPIRED")).thenReturn(Optional.of(expiredCoupon));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(1, 5, items, "EXPIRED"));

        verify(orderDAO, never()).insert(any());
    }

    @Test
    void acceptOrder_transitionSucceeds_callsMarkPreparing() {
        when(orderDAO.markPreparing(42)).thenReturn(1);

        assertDoesNotThrow(() -> orderService.acceptOrder(42));

        verify(orderDAO, times(1)).markPreparing(42);
    }

    @Test
    void acceptOrder_notSent_throwsIllegalStateException() {
        when(orderDAO.markPreparing(42)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> orderService.acceptOrder(42));
    }

    @Test
    void markArrived_transitionSucceeds_callsMarkArrived() {
        when(orderDAO.markArrived(42)).thenReturn(1);

        assertDoesNotThrow(() -> orderService.markArrived(42));

        verify(orderDAO, times(1)).markArrived(42);
    }

    @Test
    void markArrived_notPreparing_throwsIllegalStateException() {
        when(orderDAO.markArrived(42)).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> orderService.markArrived(42));
    }

    private org.example.model.Order arrivedOrder(int orderId, int customerId, int restaurantId) {
        org.example.model.Order order = new org.example.model.Order();
        order.setOrderId(orderId);
        order.setCustomerId(customerId);
        order.setRestaurantId(restaurantId);
        order.setStatus("ARRIVED");
        order.setArrivedAt(LocalDateTime.now().minusMinutes(30));
        return order;
    }

    @Test
    void leaveRating_orderNotArrived_throwsIllegalStateException() {
        org.example.model.Order sentOrder = new org.example.model.Order();
        sentOrder.setOrderId(1);
        sentOrder.setCustomerId(10);
        sentOrder.setRestaurantId(5);
        sentOrder.setStatus("SENT");
        sentOrder.setUpdatedAt(LocalDateTime.now());

        when(orderDAO.findById(1)).thenReturn(Optional.of(sentOrder));

        assertThrows(IllegalStateException.class,
                () -> orderService.leaveRating(10, 5, 1, 4, "Good"));
    }

    @Test
    void leaveRating_ratingAlreadyExists_throwsIllegalStateException() {
        org.example.model.Order order = arrivedOrder(2, 10, 5);

        when(orderDAO.findById(2)).thenReturn(Optional.of(order));
        when(ratingDAO.existsForOrder(2)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> orderService.leaveRating(10, 5, 2, 5, "Great"));
    }

    @Test
    void leaveRating_orderBelongsToDifferentCustomer_throwsIllegalArgumentException() {
        org.example.model.Order order = arrivedOrder(3, 99, 5);

        when(orderDAO.findById(3)).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.leaveRating(10, 5, 3, 4, "Nice"));
    }

    @Test
    void leaveRating_validRequest_insertsRating() {
        org.example.model.Order order = arrivedOrder(4, 10, 5);

        when(orderDAO.findById(4)).thenReturn(Optional.of(order));
        when(ratingDAO.existsForOrder(4)).thenReturn(false);

        orderService.leaveRating(10, 5, 4, 5, "Excellent!");

        verify(ratingDAO, times(1)).insert(any());
    }

    @Test
    void placeOrder_emptyItemList_throwsIllegalArgumentException() {
        stubSameCity(1, 5);

        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(1, 5, List.of(), null),
                "Empty item list must be rejected (order needs >=1 item)");

        verify(orderDAO, never()).insert(any());
        verify(orderDAO, never()).insertItem(any());
    }

    @Test
    void placeOrder_couponForDifferentRestaurant_throwsIllegalArgumentException() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 50.0));

        stubSameCity(1, 5);
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 50.0)));

        org.example.model.Coupon wrongRestaurantCoupon = new org.example.model.Coupon(8, 99, "OTHER", "AMOUNT", 5.0,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10), true);

        when(couponDAO.findByCode("OTHER")).thenReturn(Optional.of(wrongRestaurantCoupon));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(1, 5, items, "OTHER"),
                "Coupon from a different restaurant must be rejected");

        verify(orderDAO, never()).insert(any());
    }

    @Test
    void placeOrder_inactiveCoupon_throwsAndDoesNotInsert() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 50.0));

        stubSameCity(1, 5);
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 50.0)));

        org.example.model.Coupon inactiveCoupon = new org.example.model.Coupon(9, 5, "OFF", "PERCENTAGE", 20.0,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10), false);

        when(couponDAO.findByCode("OFF")).thenReturn(Optional.of(inactiveCoupon));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(1, 5, items, "OFF"),
                "Deactivated coupon must be rejected at checkout");

        verify(orderDAO, never()).insert(any());
    }

    @Test
    void placeOrder_validAmountCoupon_subtractsAndSetsCouponId() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 50.0));

        stubSameCity(1, 5);
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 50.0)));

        org.example.model.Coupon coupon = new org.example.model.Coupon(15, 5, "MINUS5", "AMOUNT", 5.0,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10), true);

        when(couponDAO.findByCode("MINUS5")).thenReturn(Optional.of(coupon));
        when(couponDAO.tryDeactivate(15)).thenReturn(true);
        when(orderDAO.insert(any(org.example.model.Order.class))).thenReturn(33);

        org.example.model.Order result = orderService.placeOrder(1, 5, items, "MINUS5");

        assertEquals(45.0, result.getTotalPrice(), 0.001);
        assertEquals(15, result.getCouponId());
        verify(orderDAO, times(1)).insert(any(org.example.model.Order.class));
    }
}
