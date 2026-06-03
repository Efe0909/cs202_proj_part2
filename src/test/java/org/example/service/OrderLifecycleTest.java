package org.example.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class OrderLifecycleTest {

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
    void placeOrder_persistsExactlySentStatus_noCoupon() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 3, 12.0));
        stubSameCity(7, 5);
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 12.0)));
        when(orderDAO.insert(any(org.example.model.Order.class))).thenReturn(101);

        org.example.model.Order result = orderService.placeOrder(7, 5, items, null);

        assertEquals("SENT", result.getStatus());
        assertTrue(result.isSent());
        assertFalse(result.isPreparing());
        assertFalse(result.isArrived());
        assertNull(result.getCouponId());
        assertEquals(36.0, result.getTotalPrice(), 0.001);

        ArgumentCaptor<org.example.model.Order> captor = ArgumentCaptor.forClass(org.example.model.Order.class);
        verify(orderDAO).insert(captor.capture());
        assertEquals("SENT", captor.getValue().getStatus(),
                "Persisted status must be exactly SENT for the markPreparing guard to match");
    }

    @Test
    void placeOrder_persistsExactlySentStatus_withCoupon() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 100.0));
        stubSameCity(1, 5);
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 100.0)));
        org.example.model.Coupon coupon = new org.example.model.Coupon(7, 5, "SAVE10", "PERCENTAGE", 10.0,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10), true);
        when(couponDAO.findByCode("SAVE10")).thenReturn(Optional.of(coupon));
        when(couponDAO.tryDeactivate(7)).thenReturn(true);
        when(orderDAO.insert(any(org.example.model.Order.class))).thenReturn(102);

        org.example.model.Order result = orderService.placeOrder(1, 5, items, "SAVE10");

        assertEquals("SENT", result.getStatus());
        assertEquals(90.0, result.getTotalPrice(), 0.001);
        assertEquals(7, result.getCouponId());

        ArgumentCaptor<org.example.model.Order> captor = ArgumentCaptor.forClass(org.example.model.Order.class);
        verify(orderDAO).insert(captor.capture());
        assertEquals("SENT", captor.getValue().getStatus());
        assertEquals(Integer.valueOf(7), captor.getValue().getCouponId());
    }

    @Test
    void acceptOrder_guardMatches_succeedsAndOnlyCallsMarkPreparing() {
        when(orderDAO.markPreparing(42)).thenReturn(1);

        assertDoesNotThrow(() -> orderService.acceptOrder(42));

        verify(orderDAO, times(1)).markPreparing(42);
        verify(orderDAO, never()).markArrived(anyInt());
    }

    @Test
    void acceptOrder_whenAlreadyPreparing_throwsIllegalState() {
        when(orderDAO.markPreparing(42)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> orderService.acceptOrder(42));
    }

    @Test
    void acceptOrder_whenAlreadyArrived_throwsIllegalState() {
        when(orderDAO.markPreparing(70)).thenReturn(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderService.acceptOrder(70));
        assertTrue(ex.getMessage().toLowerCase().contains("accept"));
    }

    @Test
    void acceptOrder_doubleFire_secondCallThrows() {
        when(orderDAO.markPreparing(80)).thenReturn(1).thenReturn(0);

        assertDoesNotThrow(() -> orderService.acceptOrder(80));
        assertThrows(IllegalStateException.class, () -> orderService.acceptOrder(80));
        verify(orderDAO, times(2)).markPreparing(80);
    }

    @Test
    void acceptOrder_whenNotSent_throwsAndNoSideEffects() {
        when(orderDAO.markPreparing(60)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> orderService.acceptOrder(60));

        verify(orderDAO, times(1)).markPreparing(60);
        verify(orderDAO, never()).insert(any());
        verify(orderDAO, never()).insertItem(any());
        verify(orderDAO, never()).markArrived(anyInt());
    }

    @Test
    void markArrived_guardMatches_succeedsAndOnlyCallsMarkArrived() {
        when(orderDAO.markArrived(42)).thenReturn(1);

        assertDoesNotThrow(() -> orderService.markArrived(42));

        verify(orderDAO, times(1)).markArrived(42);
        verify(orderDAO, never()).markPreparing(anyInt());
    }

    @Test
    void markArrived_whenStillSent_throwsIllegalState() {
        when(orderDAO.markArrived(55)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> orderService.markArrived(55));
    }

    @Test
    void markArrived_whenAlreadyArrived_throwsIllegalState() {
        when(orderDAO.markArrived(99)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> orderService.markArrived(99));
    }

    @Test
    void markArrived_doubleFire_secondCallThrows() {
        when(orderDAO.markArrived(50)).thenReturn(1).thenReturn(0);

        assertDoesNotThrow(() -> orderService.markArrived(50));
        assertThrows(IllegalStateException.class, () -> orderService.markArrived(50));
        verify(orderDAO, times(2)).markArrived(50);
    }

    @Test
    void arriveBeforeAccept_isRejected_thenAcceptStillWorks() {
        when(orderDAO.markArrived(6)).thenReturn(0);
        when(orderDAO.markPreparing(6)).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> orderService.markArrived(6));
        assertDoesNotThrow(() -> orderService.acceptOrder(6));

        verify(orderDAO, times(1)).markArrived(6);
        verify(orderDAO, times(1)).markPreparing(6);
    }

    @Test
    void fullLifecycle_sentToPreparingToArrived_succeedsInOrder() {
        when(orderDAO.markPreparing(5)).thenReturn(1);
        when(orderDAO.markArrived(5)).thenReturn(1);

        assertDoesNotThrow(() -> orderService.acceptOrder(5));
        assertDoesNotThrow(() -> orderService.markArrived(5));

        org.mockito.InOrder inOrder = inOrder(orderDAO);
        inOrder.verify(orderDAO).markPreparing(5);
        inOrder.verify(orderDAO).markArrived(5);
    }

    @Test
    void leaveRating_onSentOrder_throwsIllegalState() {
        org.example.model.Order sent = new org.example.model.Order();
        sent.setOrderId(1);
        sent.setCustomerId(10);
        sent.setRestaurantId(5);
        sent.setStatus("SENT");
        sent.setUpdatedAt(LocalDateTime.now());
        when(orderDAO.findById(1)).thenReturn(Optional.of(sent));

        assertThrows(IllegalStateException.class,
                () -> orderService.leaveRating(10, 5, 1, 5, "too early"));
        verify(ratingDAO, never()).insert(any());
    }

    @Test
    void leaveRating_onPreparingOrder_throwsIllegalState() {
        org.example.model.Order preparing = new org.example.model.Order();
        preparing.setOrderId(2);
        preparing.setCustomerId(10);
        preparing.setRestaurantId(5);
        preparing.setStatus("PREPARING");
        preparing.setUpdatedAt(LocalDateTime.now());
        when(orderDAO.findById(2)).thenReturn(Optional.of(preparing));

        assertThrows(IllegalStateException.class,
                () -> orderService.leaveRating(10, 5, 2, 5, "still too early"));
        verify(ratingDAO, never()).insert(any());
    }

    @Test
    void getPendingOrdersForRestaurant_delegatesToPendingDaoQuery() {
        org.example.model.Order sent = new org.example.model.Order();
        sent.setOrderId(9);
        sent.setStatus("SENT");
        when(orderDAO.findPendingByRestaurant(5)).thenReturn(List.of(sent));

        List<org.example.model.Order> result = orderService.getPendingOrdersForRestaurant(5);

        assertEquals(1, result.size());
        assertEquals("SENT", result.get(0).getStatus());
        verify(orderDAO, times(1)).findPendingByRestaurant(5);
        verify(orderDAO, never()).findByRestaurant(anyInt());
        verify(orderDAO, never()).findByCustomer(anyInt());
    }
}
