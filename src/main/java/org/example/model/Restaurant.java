package org.example.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
/** Represents a restaurant with its rating aggregate and keyword list. */
public class Restaurant {
    private int restaurantId;
    private int managerId;
    private String name;
    private String cuisineType;
    private String address;
    private String city;
    private List<String> keywords;
    private double averageRating;
    private int ratingCount;

    public Restaurant() {}

    public Restaurant(int restaurantId, int managerId, String name,
                      String cuisineType, String address, String city) {
        this.restaurantId = restaurantId;
        this.managerId    = managerId;
        this.name         = name;
        this.cuisineType  = cuisineType;
        this.address      = address;
        this.city         = city;
    }

    public int    getRestaurantId() { return restaurantId; }
    public int    getManagerId()    { return managerId; }
    public String getName()         { return name; }
    public String getCuisineType()  { return cuisineType; }
    public String getAddress()      { return address; }
    public String getCity()         { return city; }
    public List<String> getKeywords()  { return keywords; }
    public double getAverageRating()   { return averageRating; }
    public int    getRatingCount()     { return ratingCount; }

    public void setRestaurantId(int id)        { this.restaurantId = id; }
    public void setManagerId(int id)           { this.managerId    = id; }
    public void setName(String n)              { this.name         = n; }
    public void setCuisineType(String c)       { this.cuisineType  = c; }
    public void setAddress(String a)           { this.address      = a; }
    public void setCity(String c)              { this.city         = c; }
    public void setKeywords(List<String> kw)   { this.keywords     = kw; }
    public void setAverageRating(double r)     { this.averageRating = r; }
    public void setRatingCount(int rc)         { this.ratingCount  = rc; }

    public boolean hasEnoughRatings() { return ratingCount >= 10; }

    /** True when the restaurant has fewer than 10 ratings and must be shown as "New". */
    public boolean isNew() { return ratingCount < 10; }

    /** Returns 0 until the restaurant has at least 10 ratings, then the real average (spec requirement). */
    public double getDisplayRating() { return ratingCount >= 10 ? averageRating : 0.0; }

    @Override
    public String toString() { return name + " - " + cuisineType + " (" + city + ")"; }
}