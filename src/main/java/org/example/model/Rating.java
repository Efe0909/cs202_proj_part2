package org.example.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
/** Represents a customer rating (1-5) for a delivered order. */
public class Rating {
    private int ratingId;
    private int customerId;
    private int restaurantId;
    private int orderId;
    private int score;
    private String comment;
    private LocalDateTime createdAt;

    public Rating() {}

    public Rating(int customerId, int restaurantId, int orderId, int score, String comment) {
        this.customerId   = customerId;
        this.restaurantId = restaurantId;
        this.orderId      = orderId;
        this.score        = score;
        this.comment      = comment;
    }

    public int           getRatingId()     { return ratingId; }
    public int           getCustomerId()   { return customerId; }
    public int           getRestaurantId() { return restaurantId; }
    public int           getOrderId()      { return orderId; }
    public int           getScore()        { return score; }
    public String        getComment()      { return comment; }
    public LocalDateTime getCreatedAt()    { return createdAt; }

    public void setRatingId(int id)             { this.ratingId     = id; }
    public void setCustomerId(int id)           { this.customerId   = id; }
    public void setRestaurantId(int id)         { this.restaurantId = id; }
    public void setOrderId(int id)              { this.orderId      = id; }
    public void setScore(int s)                 { this.score        = s; }
    public void setComment(String c)            { this.comment      = c; }
    public void setCreatedAt(LocalDateTime dt)  { this.createdAt    = dt; }

    @Override
    public String toString() { return "Rating " + score + "/5 for order #" + orderId; }
}