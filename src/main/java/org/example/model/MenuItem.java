package org.example.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
/** Represents an orderable item on a restaurant's menu. */
public class MenuItem {
    private int itemId;
    private int restaurantId;
    private int categoryId;
    private String name;
    private String description;
    private double price;
    private String imagePath;

    public MenuItem() {}

    public MenuItem(int itemId, int restaurantId, int categoryId,
                    String name, String description, double price, String imagePath) {
        this.itemId       = itemId;
        this.restaurantId = restaurantId;
        this.categoryId   = categoryId;
        this.name         = name;
        this.description  = description;
        this.price        = price;
        this.imagePath    = imagePath;
    }

    public int    getItemId()       { return itemId; }
    public int    getRestaurantId() { return restaurantId; }
    public int    getCategoryId()   { return categoryId; }
    public String getName()         { return name; }
    public String getDescription()  { return description; }
    public double getPrice()        { return price; }
    public String getImagePath()    { return imagePath; }

    public void setItemId(int id)         { this.itemId       = id; }
    public void setRestaurantId(int id)   { this.restaurantId = id; }
    public void setCategoryId(int id)     { this.categoryId   = id; }
    public void setName(String n)         { this.name         = n; }
    public void setDescription(String d)  { this.description  = d; }
    public void setPrice(double p)        { this.price        = p; }
    public void setImagePath(String ip)   { this.imagePath    = ip; }

    @Override
    public String toString() { return name + " - " + String.format(java.util.Locale.ROOT, "%.2f TL", price); }
}