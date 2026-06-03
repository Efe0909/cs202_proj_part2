package org.example.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantService ownership guard behaviour")
class RequireOwnershipUnitTest {

    @Mock private org.example.dao.RestaurantDAO restaurantDAO;
    @Mock private org.example.dao.MenuItemDAO menuItemDAO;
    @Mock private org.example.dao.MenuCategoryDAO menuCategoryDAO;
    @Mock private org.example.dao.RatingDAO ratingDAO;

    @InjectMocks private RestaurantService service;

    @Test
    @DisplayName("addMenuItem with non-owned restaurant -> IllegalArgumentException, no insert")
    void addMenuItem_notOwned_throwsAndNoInsert() {
        when(restaurantDAO.existsOwnedBy(424242, 1)).thenReturn(false);
        org.example.model.MenuItem item = new org.example.model.MenuItem();
        item.setRestaurantId(424242);
        item.setName("X");
        item.setDescription("desc");
        item.setPrice(10.0);
        item.setImagePath("img.jpg");
        item.setCategoryId(1);
        assertThrows(IllegalArgumentException.class,
                () -> service.addMenuItem(item, 1));
        verifyNoInteractions(menuItemDAO);
    }

    @Test
    @DisplayName("updateMenuItem when DAO returns 0 rows -> IllegalArgumentException")
    void updateMenuItem_notOwned_throws() {
        org.example.model.MenuItem item = new org.example.model.MenuItem();
        item.setItemId(1);
        item.setRestaurantId(1);
        item.setName("X");
        item.setDescription("desc");
        item.setPrice(10.0);
        item.setImagePath("img.jpg");
        item.setCategoryId(1);
        when(menuItemDAO.updateIfOwner(item, 99)).thenReturn(0);
        assertThrows(IllegalArgumentException.class,
                () -> service.updateMenuItem(item, 99));
    }

    @Test
    @DisplayName("deleteMenuItem when DAO returns 0 rows -> IllegalArgumentException")
    void deleteMenuItem_notOwned_throws() {
        when(menuItemDAO.deleteIfOwner(7, 99)).thenReturn(0);
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteMenuItem(7, 99));
    }

    @Test
    @DisplayName("addMenuItem with correct owner -> item inserted, id set on returned object")
    void addMenuItem_correctOwner_insertsAndReturns() {
        when(restaurantDAO.existsOwnedBy(1, 1)).thenReturn(true);
        org.example.model.MenuItem item = new org.example.model.MenuItem();
        item.setRestaurantId(1);
        item.setName("Adana Kebab");
        item.setDescription("desc");
        item.setPrice(45.0);
        item.setImagePath("img.jpg");
        item.setCategoryId(1);
        when(menuItemDAO.insert(item)).thenReturn(42);
        org.example.model.MenuItem result = service.addMenuItem(item, 1);
        assertEquals(42, result.getItemId());
    }
}
