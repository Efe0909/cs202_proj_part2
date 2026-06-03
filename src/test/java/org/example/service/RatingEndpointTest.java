package org.example.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingEndpointTest {

    @Mock private org.example.dao.RestaurantDAO   restaurantDAO;
    @Mock private org.example.dao.MenuItemDAO     menuItemDAO;
    @Mock private org.example.dao.MenuCategoryDAO menuCategoryDAO;
    @Mock private org.example.dao.RatingDAO       ratingDAO;

    @InjectMocks private RestaurantService restaurantService;

    private org.example.model.Rating rating(int id, int order, int score, LocalDateTime when) {
        org.example.model.Rating r = new org.example.model.Rating(4, 1, order, score, "c" + id);
        r.setRatingId(id);
        r.setCreatedAt(when);
        return r;
    }

    @Test
    void getRatings_delegatesToDaoFindByRestaurant() {
        when(ratingDAO.findByRestaurant(1)).thenReturn(List.of());

        restaurantService.getRatings(1);

        verify(ratingDAO, times(1)).findByRestaurant(1);
        verifyNoInteractions(restaurantDAO, menuItemDAO, menuCategoryDAO);
    }

    @Test
    void getRatings_returnsDaoListUnchanged_newestFirstPreserved() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 15, 18, 17, 0);
        org.example.model.Rating newest = rating(7, 7, 5, base.plusDays(1));
        org.example.model.Rating mid    = rating(3, 3, 5, base);
        org.example.model.Rating oldest = rating(1, 1, 5, base.minusDays(2));
        List<org.example.model.Rating> daoOrder = List.of(newest, mid, oldest);
        when(ratingDAO.findByRestaurant(1)).thenReturn(daoOrder);

        List<org.example.model.Rating> result = restaurantService.getRatings(1);

        assertEquals(3, result.size());
        assertSame(daoOrder.get(0), result.get(0),
                "Service must preserve DAO order (newest first) exactly");
        assertEquals(7, result.get(0).getRatingId());
        assertEquals(3, result.get(1).getRatingId());
        assertEquals(1, result.get(2).getRatingId());
        assertFalse(result.get(0).getCreatedAt().isBefore(result.get(1).getCreatedAt()));
        assertFalse(result.get(1).getCreatedAt().isBefore(result.get(2).getCreatedAt()));
    }

    @Test
    void getRatings_emptyForRestaurantWithNoRatings() {
        when(ratingDAO.findByRestaurant(5)).thenReturn(List.of());

        List<org.example.model.Rating> result = restaurantService.getRatings(5);

        assertNotNull(result, "Empty restaurant must yield empty list, not null");
        assertTrue(result.isEmpty());
    }
}
