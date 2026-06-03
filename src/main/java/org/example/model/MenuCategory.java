package org.example.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
/** Represents a grouping category within a restaurant's menu. */
public class MenuCategory {
    private int categoryId;
    private int restaurantId;
    private String name;

    public MenuCategory() {}

    public MenuCategory(int categoryId, int restaurantId, String name) {
        this.categoryId   = categoryId;
        this.restaurantId = restaurantId;
        this.name         = name;
    }

    public int    getCategoryId()   { return categoryId; }
    public int    getRestaurantId() { return restaurantId; }
    public String getName()         { return name; }

    public void setCategoryId(int id)   { this.categoryId   = id; }
    public void setRestaurantId(int id) { this.restaurantId = id; }
    public void setName(String n)       { this.name         = n; }

    @Override
    public String toString() { return name; }
}