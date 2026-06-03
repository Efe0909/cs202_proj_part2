# Domain Models: Understanding the Data

This guide explains every model (data object) in the system—**WHAT** fields it has and **WHY**.

## Table of Contents

- [Overview](#overview)
- [User](#user)
- [Restaurant](#restaurant)
- [Menu Items](#menu-items)
- [Orders](#orders)
- [Ratings & Reviews](#ratings--reviews)
- [User Contact Info](#user-contact-info)
- [Coupons](#coupons)
- [Model Relationships](#model-relationships)

---

## Overview

Models are **plain Java objects** that represent database tables. No magic, no ORM:

```java
public class User {
    private int userId;
    private String username;
    private String email;
    // ... getters/setters
}
```

Jackson (a JSON library) automatically converts between:
- **JSON** (from/to HTTP) ↔ **Java objects** (in our code) ↔ **Database rows**

---

## User

**What:** A person who uses the system (customer or manager).

**Where:** `src/main/java/org/example/model/User.java`

**Database table:** `Users`

### Fields

```java
public class User {
    private int userId;                  // ← Primary key (auto-increment)
    private String username;             // Unique login name
    private String password;             // Hashed (PBKDF2), never plain text
    private String salt;                 // Random salt for hashing
    private String email;                // Unique email address
    private String fullName;             // Display name
    private String role;                 // "CUSTOMER" or "MANAGER"
    private Integer selectedAddressId;   // Current delivery address
    private LocalDateTime createdAt;     // When account was created
    
    // Getters/setters omitted for brevity
}
```

### Field Explanations

| Field | Why It Exists | Example |
|-------|---------------|---------|
| `userId` | Primary key to identify users uniquely. Auto-incrementing saves us from picking IDs. | `5` |
| `username` | Login credential. Unique = no two users with same username. | `"john_doe"` |
| `password` | Authentication. Hashed using PBKDF2 (industry-standard secure hashing). Never stored plain! | `"a1b2c3d4e5..."` (hashed) |
| `salt` | Random value added before hashing. Prevents rainbow table attacks. Different for each user. | `"xyz789abc..."` |
| `email` | Unique contact. Could be used for password recovery or notifications. | `"john@example.com"` |
| `fullName` | Display name in UI. Not necessarily unique. | `"John Doe"` |
| `role` | ENUM: "CUSTOMER" or "MANAGER". Determines which dashboard they see. | `"CUSTOMER"` |
| `selectedAddressId` | Points to the address record the user selected as "use this for delivery". Foreign key to `UserAddress`. | `10` |
| `createdAt` | Timestamp. Useful for analytics ("how many users signed up today?"). | `2025-05-21 14:30:00` |

### Why Password Hashing?

```
❌ NEVER store passwords in plain text:
INSERT INTO Users (username, password) VALUES ('john', 'mySecret123');
                                                        ↑ DISASTER if hacked!

✅ Always hash:
password = PBKDF2.hash('mySecret123', salt)
→ a1b2c3d4e5f6g7h8... (looks like garbage)

When user logs in:
1. Take their input: 'mySecret123'
2. Hash it with their stored salt
3. Compare hashes: does hash match what we stored?
4. If yes, login successful. We never saw their password!
```

**Code location:** [`PasswordUtil.java`](../src/main/java/org/example/util/PasswordUtil.java)

---

## Restaurant

**What:** A food business. Owned by one manager, has menu, gets rated.

**Where:** `src/main/java/org/example/model/Restaurant.java`

**Database tables:** `Restaurant`, `RestaurantKeyword`

### Fields

```java
public class Restaurant {
    private int restaurantId;              // Primary key
    private int managerId;                 // Which user owns this
    private String name;                   // Display name
    private String cuisineType;            // "Italian", "Sushi", etc.
    private String address;                // Street address
    private String city;                   // Delivery city (customers search by this)
    private List<String> keywords;         // Tags: "pizza", "vegetarian", "fast"
    private double averageRating;          // 0-5 stars (only shown if >= 10 ratings)
    private int ratingCount;               // How many ratings received
}
```

### Field Explanations

| Field | Why It Exists | Example |
|-------|---------------|---------|
| `restaurantId` | Primary key. | `2` |
| `managerId` | Foreign key to `Users`. Links restaurant to its owner. **Authorization:** Only this user can edit the restaurant. | `1` |
| `name` | Restaurant name (e.g., what customer sees in list). | `"Pizza Palace"` |
| `cuisineType` | Category for browsing. | `"Italian"` |
| `address` | Where to find the restaurant (UI shows this). | `"123 Main St"` |
| `city` | Filter for browsing. Customers select their delivery city, see restaurants in that city. | `"Toronto"` |
| `keywords` | Search tags. Stored in separate `RestaurantKeyword` table (many-to-many). | `["pizza", "vegetarian", "fast"]` |
| `averageRating` | Computed from all ratings. Shown in UI so customers can pick best-rated restaurants. | `4.5` |
| `ratingCount` | Count of ratings. Displayed as "Excellent (45 ratings)" to show popularity. | `45` |

### Special Rules

**Rating Display Rule:**
```
if ratingCount < 10:
    Show "New" badge, average = 0
else:
    Show actual average (e.g., 4.3 stars)
```

Why? Prevents a single rating (4 stars) from making a restaurant look bad. Needs 10+ ratings to be trustworthy.

---

## Menu Items

**What:** Food you can order (pizza, salad, etc.). Grouped into categories.

**Where:** `src/main/java/org/example/model/MenuItem.java` and `MenuCategory.java`

**Database tables:** `MenuItem`, `MenuCategory`

### MenuCategory

```java
public class MenuCategory {
    private int categoryId;        // Primary key
    private int restaurantId;      // FK to Restaurant
    private String name;           // "Appetizers", "Main Course", etc.
}
```

**Why categories?** Organization. Instead of a flat list of 50 items, customers see:
- Appetizers (3)
- Main Courses (12)
- Desserts (5)

Easier to browse, easier for manager to organize.

### MenuItem

```java
public class MenuItem {
    private int itemId;            // Primary key
    private int categoryId;        // FK to MenuCategory
    private String name;           // "Margherita Pizza", "Caesar Salad"
    private String description;    // "Fresh mozzarella, basil, tomato"
    private String image;          // Image filename (stored elsewhere)
    private double price;          // In dollars: 12.99
}
```

**Field explanations:**

| Field | Why | Example |
|-------|-----|---------|
| `itemId` | Primary key | `15` |
| `categoryId` | Links to category (one item in one category). | `3` (Appetizers) |
| `name` | What it's called. | `"Garlic Bread"` |
| `description` | Teases the customer. | `"6 pieces, buttery, fresh parsley"` |
| `image` | Filename of photo. | `"garlic-bread.jpg"` |
| `price` | What customer pays (in USD). | `5.99` |

---

## Orders

**What:** A customer's purchase. Contains multiple items, has a status, may have a coupon applied.

**Where:** `src/main/java/org/example/model/Order.java` and `OrderItem.java`

**Database tables:** `Order`, `OrderItem`

### Order

```java
public class Order {
    private int orderId;                   // Primary key
    private int userId;                    // FK to Users (who placed it)
    private int restaurantId;              // FK to Restaurant (who fulfills it)
    private int selectedAddressId;         // FK to UserAddress (delivery address)
    private String status;                 // "SENT" → "PREPARING" → "ARRIVED"
    private boolean couponApplied;         // Did we discount this?
    private double totalPrice;             // Final price (after coupon)
    private LocalDateTime createdAt;       // When ordered
    private LocalDateTime preparingAt;     // When manager accepted (SENT→PREPARING)
    private LocalDateTime arrivedAt;       // When order delivered (PREPARING→ARRIVED)
}
```

**Status Lifecycle:**

```
Customer places order
         │
         ▼
    SENT       (order received, waiting for kitchen)
         │
         ▼
    PREPARING  (manager accepted, being cooked)
         │
         ▼
    ARRIVED    (delivered to customer)
```

**Why this flow?**
- Orders start as SENT immediately on placement — no separate send step
- Manager transitions SENT → PREPARING via the accept endpoint
- Manager transitions PREPARING → ARRIVED via the arrive endpoint
- Only ARRIVED orders count toward statistics and are eligible for rating

### OrderItem

```java
public class OrderItem {
    private int itemId;            // Primary key (in OrderItem table)
    private int orderId;           // FK to Order
    private int menuItemId;        // FK to MenuItem (what item)
    private int quantity;          // How many
    private double unitPrice;      // Price at time of order
}
```

**Why separate table?**

One order can have many items:
```
Order #42 contains:
  - 2x Margherita Pizza ($12.99 each)
  - 1x Garlic Bread ($5.99)
  - 1x Coke ($2.50)
```

That's why `Order` and `OrderItem` are separate:
- `Order` = the transaction
- `OrderItem` = each line item

**Why store `unitPrice`?**

What if the restaurant changes prices?
```
Day 1: Margherita = $12.99
       Customer orders 2
       totalPrice = $25.98

Day 5: Restaurant changes price to $14.99

Day 10: We print receipt from order #42
        Should it show $25.98 or $29.98?
```

We store the price **at time of order**, so the receipt is accurate forever.

---

## Ratings & Reviews

**What:** Customer feedback after an order completes.

**Where:** `src/main/java/org/example/model/Rating.java`

**Database table:** `Rating`

### Rating

```java
public class Rating {
    private int ratingId;           // Primary key
    private int restaurantId;       // FK to Restaurant
    private int userId;             // FK to Users (who rated)
    private int score;              // 1-5 stars
    private String comment;         // Optional: "Great food but slow service"
    private LocalDateTime createdAt;  // When posted
}
```

**Field explanations:**

| Field | Why | Example |
|-------|-----|---------|
| `ratingId` | Primary key | `100` |
| `restaurantId` | Which restaurant (managers view all ratings for their restaurants). | `2` |
| `userId` | Who posted (so we know who to trust). | `5` |
| `score` | 1-5 stars. Used to compute average rating. | `4` |
| `comment` | Detailed feedback (optional). | `"Delicious pizza!"` |
| `createdAt` | When posted. Managers can sort "newest first". | `2025-05-21 15:00:00` |

### Rating Rules

**Can only rate within 24 hours of order acceptance:**

Why? Fresh feedback is more honest. If you wait a week, you might not remember details.

**Code check:** In `OrderService.java`, when creating a rating, we verify:
```java
long hoursSinceAccepted = ChronoUnit.HOURS.between(order.getAcceptedAt(), now);
if (hoursSinceAccepted > 24) {
    throw new IllegalArgumentException("Can only rate within 24 hours");
}
```

---

## User Contact Info

**What:** Customers have multiple delivery addresses and phone numbers.

**Where:** `UserAddress.java` and `UserPhone.java`

**Database tables:** `UserAddress`, `UserPhone`

### Why Multiple Addresses?

```
User "John" has:
- Home: 123 Main St, Toronto
- Work: 456 Office Blvd, Toronto
- Parent's house: 789 Elm, Mississauga

When ordering:
- John selects "Home"
- Delivery goes to 123 Main St
- Later, can switch to "Work" without re-entering address
```

### UserAddress

```java
public class UserAddress {
    private int addressId;         // Primary key
    private int userId;            // FK to Users
    private String street;         // "123 Main St"
    private String city;           // "Toronto"
    private String province;       // "Ontario"
}
```

**Why separate table?**
- Reusability: Same address used for multiple orders
- Normalization: Store once, reference many times (not repeated in every order)

### UserPhone

```java
public class UserPhone {
    private int phoneId;           // Primary key
    private int userId;            // FK to Users
    private String phoneNumber;    // "+1 416 555 0123"
}
```

**Why?** Restaurant might need to contact customer (order confirmation, delivery updates, etc.).

---

## Coupons

**What:** Discounts (soft-deletable, time-bounded).

**Where:** `src/main/java/org/example/model/Coupon.java`

**Database table:** `Coupon`

### Coupon

```java
public class Coupon {
    private int couponId;          // Primary key
    private int restaurantId;      // FK to Restaurant (which restaurant)
    private String code;           // "SAVE10", "WELCOME20" (what customer enters)
    private int discountPercent;   // 10 = 10% off
    private LocalDateTime validFrom;  // Start date/time
    private LocalDateTime validTo;    // End date/time
    private boolean isActive;      // Soft delete (true = active, false = expired/deleted)
}
```

**Field explanations:**

| Field | Why | Example |
|-------|-----|---------|
| `couponId` | Primary key | `5` |
| `restaurantId` | Coupon is specific to one restaurant (can't use Pizza Place coupon at Sushi Bar). | `2` |
| `code` | What customer types. Should be easy to remember. | `"WELCOME20"` |
| `discountPercent` | How much off. | `20` (means 20% off) |
| `validFrom` | When coupon becomes active. | `2025-05-21 00:00:00` |
| `validTo` | When coupon expires. | `2025-06-20 23:59:59` |
| `isActive` | Soft delete (don't actually delete from DB, just mark inactive). Keeps history for reports. | `true` |

### Soft Delete vs Hard Delete

```
❌ Hard delete (delete coupon from DB):
DELETE FROM Coupon WHERE coupon_id = 5;
Problem: Lost the history! Can't analyze "how many coupons issued?"

✅ Soft delete (mark as inactive):
UPDATE Coupon SET is_active = false WHERE coupon_id = 5;
Benefit: History preserved, can still analyze, can reactivate if needed
```

---

## Model Relationships

### Visual ER Diagram

```
┌────────────────┐
│     Users      │
├────────────────┤
│ user_id (PK)   │
│ username       │ 
│ email          │
│ role           │
│ selected_addr_id ────┐
└────────────────┘     │
       │               │
       │ 1:N           │
       └──────┐        │
              ▼        │
    ┌──────────────────┘
    ▼
┌────────────────┐         ┌──────────────────┐
│  UserAddress   │         │   Restaurant     │
├────────────────┤         ├──────────────────┤
│ address_id(PK) │         │restaurant_id(PK) │
│ user_id(FK)    │         │ manager_id(FK)   │
│ street, city   │         │ name, city       │
└────────────────┘         │ avg_rating       │
                           └──────────────────┘
                                    │
┌────────────────┐                  │ 1:N
│  UserPhone     │                  │
├────────────────┤      ┌───────────┴────────────┐
│ phone_id (PK)  │      │                        │
│ user_id (FK)   │      ▼                        ▼
│ phone_number   │   ┌──────────────┐    ┌──────────────┐
└────────────────┘   │    Order     │    │   Coupon     │
                     ├──────────────┤    ├──────────────┤
                     │ order_id(PK) │    │ coupon_id(PK)│
                     │ user_id(FK)  │    │restnt_id(FK) │
                     │ restaurant..│ │    │ code, percent│
                     │ status       │    │ valid_from..│
                     └──────┬───────┘    └──────────────┘
                            │ 1:N
                            │
                     ┌──────▼────────┐
                     │   OrderItem   │
                     ├───────────────┤
                     │ item_id (PK)  │
                     │ order_id (FK) │
                     │ menuitem_id..│
                     │ quantity      │
                     └───────────────┘
                            │
                            │ N:1
                            │
                     ┌──────▼────────┐
                     │   MenuItem    │
                     ├───────────────┤
                     │ item_id (PK)  │
                     │category_id(FK)│
                     │ name, price   │
                     └───────────────┘
                            │
                            │ N:1
                            │
                     ┌──────▼────────┐
                     │MenuCategory   │
                     ├───────────────┤
                     │category_id(PK)│
                     │restnt_id(FK)  │
                     │ name          │
                     └───────────────┘


    ┌──────────────────────────────────────────────────────┐
    │ RestaurantKeyword (many-to-many bridge table)        │
    ├──────────────────────────────────────────────────────┤
    │ restaurant_id (FK to Restaurant)                    │
    │ keyword (just a string: "pizza", "vegetarian")      │
    └──────────────────────────────────────────────────────┘


    ┌──────────────────────────────────────────────────────┐
    │              Rating                                  │
    ├──────────────────────────────────────────────────────┤
    │ rating_id (PK)                                       │
    │ restaurant_id (FK to Restaurant)                   │
    │ user_id (FK to Users)                              │
    │ score (1-5)                                        │
    │ comment (optional)                                 │
    └──────────────────────────────────────────────────────┘
```

### Key Relationships Explained

**1:N (One to Many)**
- One User → Many Orders
- One Restaurant → Many MenuCategories
- One Order → Many OrderItems

**N:1 (Many to One)**
- Many Orders → One Restaurant
- Many MenuItems → One MenuCategory

**N:M (Many to Many - via bridge table)**
- Many Restaurants ← RestaurantKeyword → Many Keywords
- Example: "Pizza Palace" has ["pizza", "vegetarian", "delivery"]

---

## Using Models in Code

### Creating a new object:

```java
// In Controller
Restaurant r = new Restaurant();
r.setManagerId(5);
r.setName("My Pizza Place");
r.setCuisineType("Italian");
r.setCity("Toronto");

// Pass to Service
Restaurant created = restaurantService.createRestaurant(r, keywords);

// Now it has an ID (from database)
System.out.println("Created restaurant #" + created.getRestaurantId());
```

### Reading from database:

```java
// In DAO
public Optional<User> findByUsername(String username) {
    // SQL returns data from database
    // DAO converts ResultSet row → User object
    User u = new User();
    u.setUserId(rs.getInt("user_id"));
    u.setUsername(rs.getString("username"));
    // ... etc
    return Optional.of(u);
}
```

### Converting to JSON:

```java
// Jackson automatically converts:
User u = new User();
u.setUserId(5);
u.setUsername("john_doe");

// When returned from Controller:
return ResponseEntity.ok(u);

// Jackson serializes to JSON:
{
  "userId": 5,
  "username": "john_doe",
  "email": "john@example.com",
  ...
}
```

---

## See Also

- [**ARCHITECTURE.md**](ARCHITECTURE.md) — System design & layers
- [**DATABASE.md**](DATABASE.md) — Schema & SQL queries
- [**SERVICES.md**](SERVICES.md) — Business logic for each model
