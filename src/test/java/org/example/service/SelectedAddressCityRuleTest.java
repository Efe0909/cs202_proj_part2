package org.example.service;

import org.junit.jupiter.api.DisplayName;
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
@DisplayName("It.6 selected-address city rule + address ownership (unit)")
class SelectedAddressCityRuleTest {

    @Mock private org.example.dao.OrderDAO orderDAO;
    @Mock private org.example.dao.CouponDAO couponDAO;
    @Mock private org.example.dao.RatingDAO ratingDAO;
    @Mock private org.example.dao.MenuItemDAO menuItemDAO;
    @Mock private org.example.dao.UserDAO userDAO;
    @Mock private org.example.dao.RestaurantDAO restaurantDAO;

    @InjectMocks private OrderService orderService;

    private org.example.model.MenuItem menuItem(int itemId, double price) {
        return new org.example.model.MenuItem(itemId, 5, 1, "Item " + itemId, "desc", price, "img.jpg");
    }

    private org.example.model.User customer(int id) {
        return new org.example.model.User(id, "cust" + id, "pw", "c@e.com", "Cust", "CUSTOMER");
    }

    private org.example.model.Restaurant restaurant(int id, String city) {
        return new org.example.model.Restaurant(id, 5, "R", "Cuisine", "Addr", city);
    }

    @Test
    @DisplayName("selected-address city == restaurant city -> proceeds, insert once")
    void selectedAddressCity_drivesRule_notStaticUserCity() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 40.0));
        when(restaurantDAO.findById(5)).thenReturn(Optional.of(restaurant(5, "Ankara")));
        when(userDAO.findSelectedAddressCity(1)).thenReturn(Optional.of("Ankara"));
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 40.0)));
        when(orderDAO.insert(any(org.example.model.Order.class))).thenReturn(501);

        org.example.model.Order result = orderService.placeOrder(1, 5, items, null);

        assertEquals(501, result.getOrderId());
        verify(userDAO).findSelectedAddressCity(1);
        verify(orderDAO, times(1)).insert(any(org.example.model.Order.class));
    }

    @Test
    @DisplayName("selected-address present, case/space differs -> still same city, proceeds")
    void selectedAddressCity_caseInsensitiveTrimmed() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 25.0));
        when(restaurantDAO.findById(5)).thenReturn(Optional.of(restaurant(5, "Istanbul")));
        when(userDAO.findSelectedAddressCity(2)).thenReturn(Optional.of("  iStAnBuL  "));
        when(menuItemDAO.findById(1)).thenReturn(Optional.of(menuItem(1, 25.0)));
        when(orderDAO.insert(any(org.example.model.Order.class))).thenReturn(502);

        assertDoesNotThrow(() -> orderService.placeOrder(2, 5, items, null));
        verify(orderDAO, times(1)).insert(any(org.example.model.Order.class));
    }

    @Test
    @DisplayName("Optional.empty() selected city -> 'Select a delivery address...' BEFORE any side effect")
    void noSelectedAddress_guardRunsFirst_neverInserts() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 30.0));
        when(restaurantDAO.findById(5)).thenReturn(Optional.of(restaurant(5, "Ankara")));
        when(userDAO.findSelectedAddressCity(3)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(3, 5, items, "SAVE10"));
        assertTrue(ex.getMessage().toLowerCase().contains("select")
                        && ex.getMessage().toLowerCase().contains("address"),
                "must instruct customer to select a delivery address, got: " + ex.getMessage());

        verifyNoInteractions(couponDAO);
        verify(menuItemDAO, never()).findById(anyInt());
        verify(orderDAO, never()).insert(any());
        verify(orderDAO, never()).insertItem(any());
    }

    @Test
    @DisplayName("blank/whitespace selected city -> treated as no address (no silent bypass)")
    void blankSelectedCity_treatedAsNoAddress() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 30.0));
        when(restaurantDAO.findById(5)).thenReturn(Optional.of(restaurant(5, "Ankara")));
        when(userDAO.findSelectedAddressCity(4)).thenReturn(Optional.of("   "));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(4, 5, items, null));
        assertTrue(ex.getMessage().toLowerCase().contains("address"),
                "blank selected city must be rejected as 'no address', got: " + ex.getMessage());
        verify(orderDAO, never()).insert(any());
    }

    @Test
    @DisplayName("selected city present but DIFFERENT from restaurant -> own-city rejection")
    void selectedCityDifferent_rejectedNeverInserts() {
        List<org.example.model.OrderItem> items = List.of(new org.example.model.OrderItem(0, 1, 1, 30.0));
        when(restaurantDAO.findById(5)).thenReturn(Optional.of(restaurant(5, "Istanbul")));
        when(userDAO.findSelectedAddressCity(5)).thenReturn(Optional.of("Ankara"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(5, 5, items, null));
        assertTrue(ex.getMessage().toLowerCase().contains("own city"),
                "should be the own-city rejection, got: " + ex.getMessage());
        verify(orderDAO, never()).insert(any());
        verify(orderDAO, never()).insertItem(any());
    }

    private UserService userService() {
        return new UserService(userDAO);
    }

    @Test
    @DisplayName("setSelectedAddress: address belongs to ANOTHER user -> rejected")
    void setSelectedAddress_notOwned_rejected() {
        UserService svc = userService();
        when(userDAO.findById(10)).thenReturn(Optional.of(customer(10)));
        when(userDAO.addressBelongsToUser(77, 10)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.setSelectedAddress(10, 77));
        assertTrue(ex.getMessage().toLowerCase().contains("does not belong"),
                "ownership rejection expected, got: " + ex.getMessage());
        verify(userDAO, never()).setSelectedAddress(anyInt(), anyInt());
    }

    @Test
    @DisplayName("setSelectedAddress: owned address -> UserDAO.setSelectedAddress called once")
    void setSelectedAddress_owned_proceeds() {
        UserService svc = userService();
        when(userDAO.findById(11)).thenReturn(Optional.of(customer(11)));
        when(userDAO.addressBelongsToUser(33, 11)).thenReturn(true);

        svc.setSelectedAddress(11, 33);

        verify(userDAO, times(1)).setSelectedAddress(11, 33);
    }

    @Test
    @DisplayName("deleteAddress: address belongs to ANOTHER user -> rejected")
    void deleteAddress_notOwned_rejected() {
        UserService svc = userService();
        when(userDAO.findById(12)).thenReturn(Optional.of(customer(12)));
        when(userDAO.addressBelongsToUser(99, 12)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.deleteAddress(12, 99));
        assertTrue(ex.getMessage().toLowerCase().contains("does not belong"),
                "ownership rejection expected, got: " + ex.getMessage());
        verify(userDAO, never()).deleteAddress(anyInt());
        verify(userDAO, never()).setSelectedAddress(anyInt(), anyInt());
    }

    @Test
    @DisplayName("deleteAddress: deleting the SELECTED owned address repoints selection")
    void deleteAddress_wasSelected_repointsToRemaining() {
        UserService svc = userService();
        when(userDAO.findById(13)).thenReturn(Optional.of(customer(13)));
        when(userDAO.addressBelongsToUser(40, 13)).thenReturn(true);
        when(userDAO.findSelectedAddressId(13)).thenReturn(Optional.of(40));
        org.example.model.UserAddress remaining = new org.example.model.UserAddress(41, 13, "Ankara", "Ankara");
        when(userDAO.findAddressesByUserId(13)).thenReturn(List.of(remaining));

        svc.deleteAddress(13, 40);

        verify(userDAO, times(1)).deleteAddress(40);
        verify(userDAO, times(1)).setSelectedAddress(13, 41);
    }

    @Test
    @DisplayName("deleteAddress: deleting a NON-selected owned address does NOT repoint")
    void deleteAddress_notSelected_noRepoint() {
        UserService svc = userService();
        when(userDAO.findById(14)).thenReturn(Optional.of(customer(14)));
        when(userDAO.addressBelongsToUser(50, 14)).thenReturn(true);
        when(userDAO.findSelectedAddressId(14)).thenReturn(Optional.of(51));

        svc.deleteAddress(14, 50);

        verify(userDAO, times(1)).deleteAddress(50);
        verify(userDAO, never()).setSelectedAddress(anyInt(), anyInt());
    }

    @Test
    @DisplayName("addAddress: blank city -> IllegalArgumentException, no DAO insert")
    void addAddress_blankCity_rejected() {
        UserService svc = userService();
        when(userDAO.findById(15)).thenReturn(Optional.of(customer(15)));

        assertThrows(IllegalArgumentException.class,
                () -> svc.addAddress(15, "   ", "Ankara"));
        verify(userDAO, never()).addAddress(anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("addAddress: blank province -> IllegalArgumentException, no DAO insert")
    void addAddress_blankProvince_rejected() {
        UserService svc = userService();
        when(userDAO.findById(16)).thenReturn(Optional.of(customer(16)));

        assertThrows(IllegalArgumentException.class,
                () -> svc.addAddress(16, "Ankara", ""));
        verify(userDAO, never()).addAddress(anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("addAddress: first address for a user with no selection becomes selected")
    void addAddress_firstAddress_autoSelected() {
        UserService svc = userService();
        when(userDAO.findById(17)).thenReturn(Optional.of(customer(17)));
        when(userDAO.addAddress(17, "Ankara", "Ankara")).thenReturn(70);
        when(userDAO.findSelectedAddressId(17)).thenReturn(Optional.empty());

        org.example.model.UserAddress created = svc.addAddress(17, "Ankara", "Ankara");

        assertEquals(70, created.getAddressId());
        assertTrue(created.isSelected(),
                "first address with no prior selection must become the selected one");
        verify(userDAO, times(1)).setSelectedAddress(17, 70);
    }

    @Test
    @DisplayName("addAddress: when a selection already exists, a new address is NOT auto-selected")
    void addAddress_withExistingSelection_notAutoSelected() {
        UserService svc = userService();
        when(userDAO.findById(18)).thenReturn(Optional.of(customer(18)));
        when(userDAO.addAddress(18, "Izmir", "Izmir")).thenReturn(71);
        when(userDAO.findSelectedAddressId(18)).thenReturn(Optional.of(60));

        org.example.model.UserAddress created = svc.addAddress(18, "Izmir", "Izmir");

        assertFalse(created.isSelected(),
                "a new address must not steal the selection when one exists");
        verify(userDAO, never()).setSelectedAddress(anyInt(), anyInt());
    }
}
