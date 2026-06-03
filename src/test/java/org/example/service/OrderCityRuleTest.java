package org.example.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCityRuleTest {

    @Mock private org.example.dao.OrderDAO orderDAO;
    @Mock private org.example.dao.CouponDAO couponDAO;
    @Mock private org.example.dao.RatingDAO ratingDAO;
    @Mock private org.example.dao.MenuItemDAO menuItemDAO;
    @Mock private org.example.dao.UserDAO userDAO;
    @Mock private org.example.dao.RestaurantDAO restaurantDAO;

    @InjectMocks
    private OrderService orderService;

    private org.example.model.MenuItem menuItem(int itemId, double price) {
        return new org.example.model.MenuItem(itemId, 5, 1, "Item " + itemId, "desc", price, "img.jpg");
    }

    private org.example.model.User customer(int id, String city) {
        return new org.example.model.User(id, "cust" + id, "pw", "c@e.com", "Cust", "CUSTOMER");
    }

    private org.example.model.Restaurant restaurant(int id, String city) {
        return new org.example.model.Restaurant(id, 5, "R", "Cuisine", "Addr", city);
    }

    private void stubCities(int customerId, String customerCity,
                            int restaurantId, String restaurantCity) {
        when(restaurantDAO.findById(restaurantId))
                .thenReturn(Optional.of(restaurant(restaurantId, restaurantCity)));
        when(userDAO.findSelectedAddressCity(customerId))
                .thenReturn(Optional.ofNullable(customerCity));
    }

    @Test
    void sameCity_sameCase_orderProceeds() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 20.0));
        stubCities(1, "Ankara", 5, "Ankara");
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 20.0)));
        when(orderDAO.insert(any(org.example.model.Order.class))).thenReturn(77);

        org.example.model.Order result = orderService.placeOrder(1, 5, items, null);

        assertEquals(77, result.getOrderId());
        assertEquals("SENT", result.getStatus());
        verify(orderDAO, times(1)).insert(any(org.example.model.Order.class));
    }

    @Test
    void differentCity_throwsAndNeverInserts() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 20.0));
        stubCities(1, "Ankara", 5, "Istanbul");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(1, 5, items, null));
        assertTrue(ex.getMessage().toLowerCase().contains("city"),
                "Rejection message should explain the city restriction");

        verify(orderDAO, never()).insert(any());
        verify(orderDAO, never()).insertItem(any());
    }

    @Test
    void caseInsensitiveAndTrimmed_sameCity_orderProceeds() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 30.0));
        stubCities(1, "istanbul ", 5, "Istanbul");
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 30.0)));
        when(orderDAO.insert(any(org.example.model.Order.class))).thenReturn(88);

        org.example.model.Order result = orderService.placeOrder(1, 5, items, null);

        assertEquals(88, result.getOrderId());
        verify(orderDAO, times(1)).insert(any(org.example.model.Order.class));
    }

    @Test
    void leadingWhitespaceAndUpperCase_sameCity_orderProceeds() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 10.0));
        stubCities(1, "  IZMIR", 5, "izmir");
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 10.0)));
        when(orderDAO.insert(any(org.example.model.Order.class))).thenReturn(91);

        assertDoesNotThrow(() -> orderService.placeOrder(1, 5, items, null));
        verify(orderDAO, times(1)).insert(any(org.example.model.Order.class));
    }

    @Test
    void ankaraVsIstanbul_rejected() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 20.0));
        stubCities(2, "Ankara", 9, "Istanbul");

        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(2, 9, items, null));
        verify(orderDAO, never()).insert(any());
    }

    @Test
    void unknownRestaurant_throwsAndNeverInserts() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 20.0));
        when(restaurantDAO.findById(999)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(1, 999, items, null));
        assertTrue(ex.getMessage().toLowerCase().contains("restaurant"),
                "Message should identify the unknown restaurant");

        verify(orderDAO, never()).insert(any());
    }

    @Test
    void cityGuardRunsBeforeCouponAndPriceSideEffects() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 50.0));
        stubCities(1, "Ankara", 5, "Istanbul");

        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(1, 5, items, "SAVE10"));

        verify(couponDAO, never()).findByCode(anyString());
        verifyNoInteractions(couponDAO);
        verify(menuItemDAO, never()).findById(anyInt());
        verify(orderDAO, never()).insert(any());
        verify(orderDAO, never()).insertItem(any());
    }

    @Test
    void noSelectedAddress_rejectedWithClearMessage() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 20.0));
        stubCities(1, null, 5, "Ankara");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(1, 5, items, null),
                "no selected address must be handled safely and rejected");
        assertTrue(ex.getMessage().toLowerCase().contains("address"),
                "Rejection should tell the customer to select a delivery address");
        verify(orderDAO, never()).insert(any());
    }

    @Test
    void noSelectedAddress_rejectedEvenWhenRestaurantCityNull() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 15.0));
        stubCities(1, null, 5, null);

        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(1, 5, items, null));
        verify(orderDAO, never()).insert(any());
    }
}
