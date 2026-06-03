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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantService.findById populates keywords")
class RestaurantFindByIdKeywordsTest {

    @Mock private org.example.dao.RestaurantDAO restaurantDAO;
    @Mock private org.example.dao.MenuItemDAO menuItemDAO;
    @Mock private org.example.dao.MenuCategoryDAO menuCategoryDAO;
    @Mock private org.example.dao.RatingDAO ratingDAO;

    @InjectMocks private RestaurantService service;

    @Test
    @DisplayName("populates keywords from RestaurantDAO.findKeywords when row exists")
    void existingRestaurant_keywordsPopulated() {
        org.example.model.Restaurant raw = new org.example.model.Restaurant();
        raw.setRestaurantId(1);
        raw.setName("Bosphorus Kebab");
        when(restaurantDAO.findById(1)).thenReturn(Optional.of(raw));
        when(restaurantDAO.findKeywords(1))
                .thenReturn(List.of("kebab", "turkish", "grilled", "meat"));

        Optional<org.example.model.Restaurant> got = service.findById(1);
        assertTrue(got.isPresent(), "restaurant 1 should be returned");
        assertNotNull(got.get().getKeywords(),
                "keywords must be populated for the edit form to pre-fill");
        assertEquals(List.of("kebab", "turkish", "grilled", "meat"),
                got.get().getKeywords());
        verify(restaurantDAO).findKeywords(1);
    }

    @Test
    @DisplayName("missing restaurant returns empty Optional without a keyword lookup")
    void missingRestaurant_noKeywordLookup() {
        when(restaurantDAO.findById(999)).thenReturn(Optional.empty());
        Optional<org.example.model.Restaurant> got = service.findById(999);
        assertTrue(got.isEmpty(), "unknown restaurant should be Optional.empty");
        verify(restaurantDAO, never()).findKeywords(anyInt());
    }
}
