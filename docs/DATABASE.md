# Database Guide: Schema & Queries

Complete reference to the MySQL database structure and how DAOs access it.

## Table of Contents

- [Database Setup](#database-setup)
- [Schema Overview](#schema-overview)
- [Table Details](#table-details)
- [Key Queries](#key-queries)
- [Best Practices](#best-practices)

---

## Database Setup

**Database Name:** `food_ordering`

**Created by:** `DDL.sql` (automatic on first run)

**MySQL Version:** 8.0

**Docker:** Run via `docker-compose.yml`

```yaml
db:
  image: mysql:8.0
  environment:
    MYSQL_ROOT_PASSWORD: root
    MYSQL_DATABASE: food_ordering
  volumes:
    - ./DDL.sql:/docker-entrypoint-initdb.d/DDL.sql
```

**Schema location:** `DDL.sql` in project root

---

## Schema Overview

```
11 tables total:

┌──────────────────────────────────────────────────────────┐
│ Authentication & User                                    │
├──────────────────────────────────────────────────────────┤
│ • Users                                                  │
│ • UserAddress                                            │
│ • UserPhone                                              │
│                                                          │
│ Restaurants & Menu                                       │
│ • Restaurant                                             │
│ • RestaurantKeyword (many-to-many)                       │
│ • MenuCategory                                           │
│ • MenuItem                                               │
│                                                          │
│ Orders & Items                                           │
│ • Order                                                  │
│ • OrderItem                                              │
│                                                          │
│ Reviews & Promotions                                     │
│ • Rating                                                 │
│ • Coupon                                                 │
└──────────────────────────────────────────────────────────┘
```

---

## Table Details

### Users

**Purpose:** User accounts (customers and managers).

**Schema:**
```sql
CREATE TABLE Users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(64) NOT NULL,           -- Hashed PBKDF2
    salt VARCHAR(32) NOT NULL,               -- Random salt (hex encoded)
    email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    role ENUM('CUSTOMER', 'MANAGER') NOT NULL,
    selected_address_id INT,                 -- FK to UserAddress
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (selected_address_id) REFERENCES UserAddress(address_id)
        ON DELETE SET NULL
);
```

**Indexes:**
- Primary: `user_id`
- Unique: `username`, `email`

**Why these fields?**
- `password` + `salt`: Secure authentication
- `role`: Determine dashboard (customer vs manager)
- `selected_address_id`: FK (can be NULL if user deleted all addresses)
- `created_at`: Useful for analytics

**Row example:**
```
user_id=5, username='john_doe', password='a1b2c3d4...', 
email='john@example.com', role='CUSTOMER', 
selected_address_id=10, created_at='2025-05-21 14:30:00'
```

---

### UserAddress

**Purpose:** Multiple delivery addresses per customer.

**Schema:**
```sql
CREATE TABLE UserAddress (
    address_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    street VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    province VARCHAR(100) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
);
```

**Indexes:**
- Primary: `address_id`
- Foreign: `user_id`

**Why separate table?**
- Normalization: One user → many addresses, stored once
- Reusability: Same address can appear in many orders
- Flexibility: Add/remove addresses without touching Users table

**Row example:**
```
address_id=10, user_id=5, street='123 Main St', 
city='Toronto', province='Ontario'
```

---

### UserPhone

**Purpose:** Multiple phone numbers per customer.

**Schema:**
```sql
CREATE TABLE UserPhone (
    phone_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
);
```

**Why?** Restaurants may need to contact customer. One user might have work phone, mobile, etc.

---

### Restaurant

**Purpose:** Food businesses.

**Schema:**
```sql
CREATE TABLE Restaurant (
    restaurant_id INT AUTO_INCREMENT PRIMARY KEY,
    manager_id INT NOT NULL,              -- FK to Users (manager)
    name VARCHAR(255) NOT NULL,
    cuisine_type VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    FOREIGN KEY (manager_id) REFERENCES Users(user_id)
);
```

**Indexes:**
- Primary: `restaurant_id`
- Foreign: `manager_id`
- Search: `city` (customers browse by city)

**Why?**
- `manager_id`: Establishes ownership (only this user can edit)
- `city`: Filter for browsing (customers select their delivery city)
- `cuisine_type`: Browsing by category

**Row example:**
```
restaurant_id=2, manager_id=1, name='Pizza Palace', 
cuisine_type='Italian', address='123 Main St', city='Toronto'
```

---

### RestaurantKeyword

**Purpose:** Tags for searching (many-to-many relationship).

**Schema:**
```sql
CREATE TABLE RestaurantKeyword (
    restaurant_id INT NOT NULL,
    keyword VARCHAR(50) NOT NULL,
    PRIMARY KEY (restaurant_id, keyword),
    FOREIGN KEY (restaurant_id) REFERENCES Restaurant(restaurant_id)
        ON DELETE CASCADE
);
```

**Why many-to-many?**
- One restaurant has many keywords: ["pizza", "vegetarian", "fast"]
- One keyword appears in many restaurants
- Stored in separate table (normalized design)

**Example rows:**
```
restaurant_id=2, keyword='pizza'
restaurant_id=2, keyword='vegetarian'
restaurant_id=2, keyword='delivery'
```

---

### MenuCategory

**Purpose:** Organize menu items (appetizers, main courses, etc.).

**Schema:**
```sql
CREATE TABLE MenuCategory (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    FOREIGN KEY (restaurant_id) REFERENCES Restaurant(restaurant_id)
        ON DELETE CASCADE
);
```

**Why?** UX: Instead of flat list of 50 items, show organized categories.

**Row example:**
```
category_id=100, restaurant_id=2, name='Appetizers'
```

---

### MenuItem

**Purpose:** Individual items customers can order.

**Schema:**
```sql
CREATE TABLE MenuItem (
    item_id INT AUTO_INCREMENT PRIMARY KEY,
    category_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    image VARCHAR(255),                   -- Filename
    price DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (category_id) REFERENCES MenuCategory(category_id)
        ON DELETE CASCADE
);
```

**Indexes:**
- Primary: `item_id`
- Foreign: `category_id` (for browsing by category)

**Why DECIMAL for price?** Prevents floating-point rounding errors ($9.99 ≠ 9.99 as float).

**Row example:**
```
item_id=15, category_id=100, name='Margherita Pizza', 
description='Fresh mozzarella, basil, tomato', 
image='margherita.jpg', price=12.99
```

---

### Order

**Purpose:** Customer purchases.

**Schema:**
```sql
CREATE TABLE Order (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    restaurant_id INT NOT NULL,
    selected_address_id INT NOT NULL,
    status ENUM('PREPARING', 'SENT', 'ACCEPTED') NOT NULL,
    coupon_applied BOOLEAN DEFAULT FALSE,
    total_price DECIMAL(10, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at DATETIME,                 -- When manager accepted
    FOREIGN KEY (user_id) REFERENCES Users(user_id),
    FOREIGN KEY (restaurant_id) REFERENCES Restaurant(restaurant_id),
    FOREIGN KEY (selected_address_id) REFERENCES UserAddress(address_id)
);
```

**Indexes:**
- Primary: `order_id`
- Foreign: `user_id` (for "my orders"), `restaurant_id` (for "orders for my restaurant")

**Why snapshot fields?**
- `selected_address_id`: Capture address AT TIME OF ORDER (customer might move later)
- `total_price`: Snapshot of final price (ingredients prices might change)
- `created_at`, `accepted_at`: Timeline for tracking

**Row example:**
```
order_id=42, user_id=5, restaurant_id=2, 
selected_address_id=10, status='SENT', 
coupon_applied=true, total_price=45.50, 
created_at='2025-05-21 15:00:00', accepted_at=NULL
```

---

### OrderItem

**Purpose:** Individual items in an order (line items).

**Schema:**
```sql
CREATE TABLE OrderItem (
    item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    menu_item_id INT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,  -- Price at time of order
    FOREIGN KEY (order_id) REFERENCES Order(order_id) ON DELETE CASCADE,
    FOREIGN KEY (menu_item_id) REFERENCES MenuItem(item_id)
);
```

**Indexes:**
- Primary: `item_id`
- Foreign: `order_id` (to fetch all items for an order)

**Why snapshot `unit_price`?** Menu item price might change. We store what customer actually paid.

**Row example:**
```
item_id=1000, order_id=42, menu_item_id=15, quantity=2, unit_price=12.99
```

---

### Rating

**Purpose:** Customer reviews of restaurants.

**Schema:**
```sql
CREATE TABLE Rating (
    rating_id INT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id INT NOT NULL,
    user_id INT NOT NULL,
    score INT NOT NULL CHECK (score >= 1 AND score <= 5),
    comment VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (restaurant_id) REFERENCES Restaurant(restaurant_id),
    FOREIGN KEY (user_id) REFERENCES Users(user_id)
);
```

**Indexes:**
- Primary: `rating_id`
- Foreign: `restaurant_id` (for manager view)

**CHECK constraint:** Ensure score is 1-5.

**Row example:**
```
rating_id=1, restaurant_id=2, user_id=5, score=5, 
comment='Amazing pizza!', created_at='2025-05-21 14:30:00'
```

---

### Coupon

**Purpose:** Time-bounded discounts (soft-deletable).

**Schema:**
```sql
CREATE TABLE Coupon (
    coupon_id INT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id INT NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    discount_percent INT NOT NULL,
    valid_from DATETIME NOT NULL,
    valid_to DATETIME NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,       -- Soft delete
    FOREIGN KEY (restaurant_id) REFERENCES Restaurant(restaurant_id)
);
```

**Indexes:**
- Primary: `coupon_id`
- Unique: `code` (can't have duplicate coupon codes)
- Foreign: `restaurant_id`

**Why soft delete?** Keep history for analytics.

**Row example:**
```
coupon_id=5, restaurant_id=2, code='SAVE10', discount_percent=10, 
valid_from='2025-05-21 00:00:00', valid_to='2025-06-21 23:59:59', 
is_active=true
```

---

## Key Queries

These are the most common SQL patterns you'll see in DAOs.

### 1. Fetch restaurant with average rating

```sql
SELECT r.*,
  CASE WHEN COUNT(rt.rating_id) >= 10 
    THEN AVG(rt.score) 
    ELSE 0 
  END AS avg_rating,
  COUNT(rt.rating_id) AS rating_count
FROM Restaurant r
LEFT JOIN Rating rt ON r.restaurant_id = rt.restaurant_id
WHERE r.restaurant_id = ?
GROUP BY r.restaurant_id;
```

**Why CASE?** Show 0 rating until >= 10 ratings (spec requirement).

**Why LEFT JOIN?** Even if no ratings exist, still return the restaurant.

### 2. Search by keyword and city

```sql
SELECT r.*,
  CASE WHEN COUNT(DISTINCT rt.rating_id) >= 10 
    THEN AVG(rt.score) 
    ELSE 0 
  END AS avg_rating,
  COUNT(DISTINCT rt.rating_id) AS rating_count
FROM Restaurant r
JOIN RestaurantKeyword rk ON r.restaurant_id = rk.restaurant_id
LEFT JOIN Rating rt ON r.restaurant_id = rt.restaurant_id
WHERE rk.keyword LIKE ? AND r.city = ?
GROUP BY r.restaurant_id
ORDER BY avg_rating DESC, r.name ASC;
```

**Why LIKE?** Substring matching. "pi" matches "Pizza", "Sushi".

**Why ORDER BY?** Sort by rating (best first), then name (A-Z).

### 3. Get order with items

```sql
SELECT o.*, 
       oi.item_id as oi_item_id, oi.menu_item_id, 
       oi.quantity, oi.unit_price
FROM Order o
LEFT JOIN OrderItem oi ON o.order_id = oi.order_id
WHERE o.order_id = ?;
```

**Application code:** Loops through ResultSet, builds Order + list of OrderItems.

### 4. Calculate monthly revenue

```sql
SELECT 
  SUM(o.total_price) as total_revenue,
  COUNT(o.order_id) as total_orders,
  AVG(o.total_price) as avg_order_value,
  SUM(oi.quantity) as total_items_sold
FROM Order o
LEFT JOIN OrderItem oi ON o.order_id = oi.order_id
WHERE YEAR(o.created_at) = ? 
  AND MONTH(o.created_at) = ?
  AND o.restaurant_id IN (
    SELECT restaurant_id FROM Restaurant WHERE manager_id = ?
  );
```

**Why nested SELECT?** Get all restaurants owned by manager, then aggregate orders.

### 5. Get active coupons

```sql
SELECT * FROM Coupon
WHERE restaurant_id = ?
  AND is_active = true
  AND NOW() BETWEEN valid_from AND valid_to
ORDER BY valid_to DESC;
```

**NOW() BETWEEN?** Only coupons that are currently active.

---

## Best Practices

### ✅ Always Use PreparedStatement

```java
// ✅ Safe
String sql = "SELECT * FROM Users WHERE username = ?";
PreparedStatement ps = conn.prepareStatement(sql);
ps.setString(1, username);  // Parameter binding
```

```java
// ❌ Dangerous
String sql = "SELECT * FROM Users WHERE username = '" + username + "'";
// SQL injection: username = "'; DROP TABLE Users; --"
```

### ✅ Use Foreign Keys with CASCADE

```sql
-- ✅ Good
FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE

-- When user is deleted, all their addresses/phones deleted automatically
```

```sql
-- ❌ Avoid
FOREIGN KEY (user_id) REFERENCES Users(user_id)
-- Deleting user fails if they have addresses (orphaned rows)
```

### ✅ Use DECIMAL for Money

```java
// ✅ Correct
price DECIMAL(10, 2)  // $999,999.99 max, 2 decimal places

// ❌ Wrong
price FLOAT           // $9.99 might become 9.989999999...
```

### ✅ Index Frequently-Queried Columns

```sql
-- Add indexes for:
-- - Foreign keys (used in JOINs)
-- - WHERE clauses (city, restaurant_id, user_id)
-- - ORDER BY columns (created_at)

CREATE INDEX idx_order_user ON Order(user_id);
CREATE INDEX idx_restaurant_city ON Restaurant(city);
```

### ✅ Use AUTO_INCREMENT for Primary Keys

```sql
user_id INT AUTO_INCREMENT PRIMARY KEY
-- No need to assign IDs yourself, database does it
```

### ✅ Snapshot Important Values

```sql
-- ✅ OrderItem stores price AT TIME OF ORDER
CREATE TABLE OrderItem (
    ...
    unit_price DECIMAL(10, 2),  -- Snapshot
    ...
);

-- Even if MenuItem.price changes later, OrderItem has original price
```

---

## Connection String

**Location:** `application.properties`

```
spring.datasource.url=jdbc:mysql://localhost:3306/food_ordering
spring.datasource.username=root
spring.datasource.password=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

**In Docker:** Host is `db` (Docker Compose DNS), not `localhost`.

---

## Viewing Data

### Using MySQL CLI

```bash
# Inside container
docker exec -it <container_id> mysql -u root -proot -D food_ordering

# Then:
mysql> SELECT * FROM Users;
mysql> SELECT * FROM Order JOIN OrderItem USING (order_id);
```

### Using TablePlus or MySQL Workbench

1. Connection: `localhost:3306`
2. Username: `root`
3. Password: `root`
4. Database: `food_ordering`

---

## See Also

- [**MODELS.md**](MODELS.md) — Entity explanations
- [**API_ENDPOINTS.md**](API_ENDPOINTS.md) — What gets stored
