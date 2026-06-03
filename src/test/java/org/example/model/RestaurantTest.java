package org.example.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantTest {

    private Restaurant restaurantWith(int ratingCount, double averageRating) {
        Restaurant r = new Restaurant(1, 5, "Testaurant", "Cuisine", "Addr", "Ankara");
        r.setRatingCount(ratingCount);
        r.setAverageRating(averageRating);
        return r;
    }

    @Test
    void zeroRatings_isNew_displayRatingZero_evenIfAverageSetHigh() {
        Restaurant r = restaurantWith(0, 5.0);

        assertTrue(r.isNew(), "0 ratings must be flagged New");
        assertFalse(r.hasEnoughRatings(), "0 ratings is below the 10 threshold");
        assertEquals(0.0, r.getDisplayRating(), 0.0001,
                "Display rating must be suppressed to 0.0 below the threshold, "
                        + "regardless of the averageRating field");
        assertEquals(5.0, r.getAverageRating(), 0.0001);
    }

    @Test
    void nineRatings_justBelowBoundary_isNew_displayRatingZero() {
        Restaurant r = restaurantWith(9, 4.9);

        assertTrue(r.isNew(), "9 < 10 must still be New");
        assertFalse(r.hasEnoughRatings(), "9 ratings is not enough");
        assertEquals(0.0, r.getDisplayRating(), 0.0001,
                "9 ratings: display rating still suppressed to 0.0");
        assertEquals(4.9, r.getAverageRating(), 0.0001);
    }

    @Test
    void exactlyTenRatings_boundary_notNew_displayEqualsAverage() {
        Restaurant r = restaurantWith(10, 3.7);

        assertFalse(r.isNew(), "Exactly 10 ratings must NOT be New (>= 10 is enough)");
        assertTrue(r.hasEnoughRatings(), "10 is the inclusive threshold");
        assertEquals(3.7, r.getDisplayRating(), 0.0001,
                "At exactly 10 ratings the real average must be shown");
        assertEquals(10, r.getRatingCount());
    }

    @Test
    void elevenRatings_pastBoundary_notNew_displayEqualsAverage() {
        Restaurant r = restaurantWith(11, 2.5);

        assertFalse(r.isNew(), "11 ratings is clearly not New");
        assertTrue(r.hasEnoughRatings());
        assertEquals(2.5, r.getDisplayRating(), 0.0001,
                "Above threshold the real average must be shown");
    }

    @Test
    void boundaryCrossover_nineVsTen_flipsEveryGatedAccessor() {
        Restaurant nine = restaurantWith(9, 4.0);
        Restaurant ten  = restaurantWith(10, 4.0);

        assertNotEquals(nine.isNew(), ten.isNew(),
                "isNew() must flip between 9 and 10");
        assertNotEquals(nine.hasEnoughRatings(), ten.hasEnoughRatings(),
                "hasEnoughRatings() must flip between 9 and 10");
        assertEquals(0.0, nine.getDisplayRating(), 0.0001);
        assertEquals(4.0, ten.getDisplayRating(), 0.0001);
        assertNotEquals(nine.getDisplayRating(), ten.getDisplayRating(), 0.0001,
                "Display rating must change across the 9->10 boundary");
    }

    @Test
    void negativeRatingCount_treatedAsNew_displayZero() {
        Restaurant r = restaurantWith(-3, 5.0);

        assertTrue(r.isNew(), "Negative count is below threshold => New");
        assertFalse(r.hasEnoughRatings());
        assertEquals(0.0, r.getDisplayRating(), 0.0001,
                "A corrupt negative count must not expose a stored average");
    }

    @Test
    void tenRatingsWithZeroAverage_notNew_displayIsRealZero() {
        Restaurant r = restaurantWith(10, 0.0);

        assertFalse(r.isNew(), "10 ratings is not New even if the average is 0");
        assertTrue(r.hasEnoughRatings());
        assertEquals(0.0, r.getDisplayRating(), 0.0001,
                "Real average of 0.0 at/above threshold is shown as 0.0");
    }

    @Test
    void defaultConstructed_hasZeroRatings_isNew() {
        Restaurant r = new Restaurant();

        assertEquals(0, r.getRatingCount());
        assertTrue(r.isNew(), "A freshly created restaurant defaults to New");
        assertFalse(r.hasEnoughRatings());
        assertEquals(0.0, r.getDisplayRating(), 0.0001);
    }
}
