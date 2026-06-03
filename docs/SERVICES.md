# Service Layer: Business Logic & Authorization

The service layer is where the "brains" live. This guide explains **WHAT** each service does and **WHY** its design protects your data.

## Table of Contents

- [Why Services Exist](#why-services-exist)
- [Authorization Pattern](#authorization-pattern)
- [UserService](#userservice)
- [RestaurantService](#restaurantservice)
- [OrderService](#orderservice)
- [CouponService](#couponservice)
- [StatisticsService](#statisticsservice)
- [Transactions](#transactions)

---

## Why Services Exist

Services are **not just helpers**. They are the **contract between HTTP and database**.

### Problem Without Services

```java
// ❌ BAD: Logic scattered in controller
@PostMapping("/orders")
public Order createOrder(@RequestBody Map<String, Object> body) {
    Order order = new Order();
    order.setUserId(ControllerInputs.requireInt(body, "userId"));
    
    // PROBLEM: Authorization code in controller
    User user = userDAO.findById(order.getUserId())
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
    
    if (user.getRole().equals("BANNED")) {
        throw new IllegalArgumentException("User banned");
    }
    
    // PROBLEM: Business logic in controller
    Coupon coupon = couponDAO.findByCode(couponCode)
        .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));
    
    if (coupon.getValidTo().isBefore(LocalDateTime.now())) {
        throw new IllegalArgumentException("Coupon expired");
    }
    
    // PROBLEM: Transaction logic mixed in
    orderDAO.insert(order);
    for (OrderItem item : items) {
        orderItemDAO.insert(item);
    }
    
    // What if DB fails after 1st insert but before 2nd?
    // No rollback, database is inconsistent!
}
```

**Problems:**
1. **Duplicated logic:** Same validation code in multiple controllers
2. **Hard to test:** Can't test business rules without HTTP/controller setup
3. **Unsafe:** No transaction boundaries, partial updates possible
4. **Tight coupling:** Controller knows about DAO details

### Solution: Services

```java
// ✅ GOOD: Service handles all logic
@Service
public class OrderService {
    
    @Transactional  // ← Spring ensures atomicity
    public Order createOrder(Order order, List<OrderItem> items, String couponCode) {
        // Authorization
        requireUserExists(order.getUserId());
        
        // Validation
        validateCoupon(couponCode);
        
        // Database operations (all succeed or all fail)
        int orderId = orderDAO.insert(order);
        for (OrderItem item : items) {
            item.setOrderId(orderId);
            orderItemDAO.insert(item);
        }
        
        return orderDAO.findById(orderId).orElseThrow();
    }
}

// In Controller:
@PostMapping("/orders")
public Order createOrder(@RequestBody Map<String, Object> body) {
    Order order = buildOrderFromInput(body);
    List<OrderItem> items = buildItemsFromInput(body);
    String coupon = ControllerInputs.optString(body, "couponCode");
    
    // ONE LINE: delegate to service
    return orderService.createOrder(order, items, coupon);
}
```

**Benefits:**
1. **Reusable:** Same service called from web, mobile, CLI, tests
2. **Testable:** Mock the DAO, test business logic in isolation
3. **Atomic:** All database changes succeed or all fail
4. **Clear:** Controller = HTTP details, Service = business logic

---

## Authorization Pattern

Services enforce **"does this user own this resource?"** checks.

### Example: Only managers can edit their restaurants

```java
public void updateRestaurant(Restaurant restaurant, int managerId) {
    // Step 1: Is the restaurant real?
    Restaurant existing = restaurantDAO.findById(restaurant.getRestaurantId())
        .orElseThrow(() -> new IllegalArgumentException(
            "Restaurant " + restaurant.getRestaurantId() + " not found"));
    
    // Step 2: Does this manager own it?
    if (existing.getManagerId() != managerId) {
        throw new IllegalArgumentException(
            "Manager " + managerId + " does not own restaurant " 
            + restaurant.getRestaurantId());
    }
    
    // Step 3: Safe to proceed
    restaurantDAO.update(restaurant);
}
```

**Why in the service, not database?**

1. **Reusable:** Same rule for all callers (web, API, CLI)
2. **Debuggable:** Clear error message says what's wrong
3. **Composable:** Multiple authorization checks can be combined

**In the controller:**

```java
@PutMapping("/restaurants/{id}")
public ResponseEntity<?> updateRestaurant(
        @PathVariable int id,
        @RequestBody Map<String, Object> body) {
    
    int managerId = ControllerInputs.requireInt(body, "managerId");
    // ... build restaurant ...
    
    try {
        restaurantService.updateRestaurant(restaurant, managerId);
        return ResponseEntity.ok().build();
    } catch (IllegalArgumentException e) {
        // Service throws with clear message
        // GlobalExceptionHandler converts to HTTP 400
        throw e;
    }
}
```

---

## UserService

**Location:** `src/main/java/org/example/service/UserService.java`

**Purpose:** Authentication, registration, password hashing.

### Key Methods

```java
@Service
public class UserService {
    
    private final UserDAO userDAO;
    private final PasswordUtil passwordUtil;
    
    // ─────────────────────────────────────────────────────────
    // Registration
    // ─────────────────────────────────────────────────────────
    
    @Transactional
    public User register(String username, String email, String fullName, 
                         String password, String role) {
        // Validation: Check if username already taken
        if (userDAO.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }
        
        // Validation: Check if email already used
        if (userDAO.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }
        
        // Hash password securely (PBKDF2 with salt)
        String[] hashed = passwordUtil.hashPassword(password);
        // hashed[0] = hash, hashed[1] = salt
        
        User user = new User();
        user.setUsername(username);
        user.setPassword(hashed[0]);
        user.setSalt(hashed[1]);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(role.toUpperCase());  // "CUSTOMER" or "MANAGER"
        
        int userId = userDAO.insert(user);
        user.setUserId(userId);
        return user;
    }
    
    // ─────────────────────────────────────────────────────────
    // Authentication
    // ─────────────────────────────────────────────────────────
    
    public Optional<User> authenticate(String username, String password) {
        Optional<User> user = userDAO.findByUsername(username);
        
        if (user.isPresent()) {
            // Verify password: hash input with stored salt, compare hashes
            String inputHash = passwordUtil.hashPassword(password, user.get().getSalt());
            
            if (inputHash.equals(user.get().getPassword())) {
                return user;  // Login successful!
            }
        }
        
        return Optional.empty();  // Login failed (wrong user or password)
    }
    
    // ─────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────
    
    public Optional<User> findById(int userId) {
        return userDAO.findById(userId);
    }
}
```

### Why Hash Passwords?

Imagine a hacker steals the database:

```
❌ Plain text:
username     password
john         mySecret123  ← Can immediately log in as john!
alice        password456  ← Can immediately log in as alice!

✅ Hashed:
username     password (hash)
john         a1b2c3d4e5...  ← Useless! Must know the password to log in
alice        f6g7h8i9j0...  ← Useless!
```

**How hashing works:**

```java
// Registration
password = "mySecret123"
salt = SecureRandom.nextBytes(16)  // Random unique salt
hash = PBKDF2.hash(password, salt, iterations=100000)
// Store: hash = "a1b2c3d4...", salt = "xyz789..."

// Login
userInput = "mySecret123"
userHash = PBKDF2.hash(userInput, stored_salt, iterations=100000)
// Does userHash == stored_hash?
// If yes, they know the password!
```

---

## RestaurantService

**Location:** `src/main/java/org/example/service/RestaurantService.java`

**Purpose:** Restaurant CRUD, search, keyword management, authorization.

### Key Methods

```java
@Service
public class RestaurantService {
    
    private final RestaurantDAO restaurantDAO;
    private final MenuItemDAO menuItemDAO;
    private final MenuCategoryDAO menuCategoryDAO;
    
    // ─────────────────────────────────────────────────────────
    // Browsing (Public)
    // ─────────────────────────────────────────────────────────
    
    public List<Restaurant> browseByCity(String city) {
        // Get all restaurants in city
        List<Restaurant> results = restaurantDAO.findByCity(city);
        
        // Fetch keywords for each (separate query, populated by DAO)
        results.forEach(r -> r.setKeywords(restaurantDAO.findKeywords(r.getRestaurantId())));
        
        return results;  // Already sorted by DAO (rating desc, name asc)
    }
    
    public List<Restaurant> searchByKeyword(String keyword, String city) {
        List<Restaurant> results = restaurantDAO.searchByKeywordAndCity(keyword, city);
        results.forEach(r -> r.setKeywords(restaurantDAO.findKeywords(r.getRestaurantId())));
        return results;
    }
    
    // ─────────────────────────────────────────────────────────
    // Authorization: Ownership check
    // ─────────────────────────────────────────────────────────
    
    public void requireOwnership(int restaurantId, int managerId) {
        Restaurant r = restaurantDAO.findById(restaurantId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Restaurant not found: " + restaurantId));
        
        if (r.getManagerId() != managerId) {
            throw new IllegalArgumentException(
                "Manager " + managerId + " does not own restaurant " + restaurantId);
        }
    }
    
    // ─────────────────────────────────────────────────────────
    // Create Restaurant
    // ─────────────────────────────────────────────────────────
    
    @Transactional
    public Restaurant createRestaurant(Restaurant restaurant, List<String> keywords) {
        // Insert restaurant
        int id = restaurantDAO.insert(restaurant);
        restaurant.setRestaurantId(id);
        
        // Add keywords (in separate RestaurantKeyword table)
        for (String kw : keywords) {
            restaurantDAO.addKeyword(id, kw);
        }
        
        restaurant.setKeywords(keywords);
        return restaurant;
    }
    
    // ─────────────────────────────────────────────────────────
    // Update Restaurant (Protected by authorization)
    // ─────────────────────────────────────────────────────────
    
    @Transactional
    public void updateRestaurant(Restaurant restaurant, List<String> keywords, 
                                  int managerId) {
        // First: check ownership
        requireOwnership(restaurant.getRestaurantId(), managerId);
        
        // Now safe to update
        restaurantDAO.update(restaurant);
        
        // Refresh keywords
        restaurantDAO.deleteKeywords(restaurant.getRestaurantId());
        for (String kw : keywords) {
            restaurantDAO.addKeyword(restaurant.getRestaurantId(), kw);
        }
    }
    
    // ─────────────────────────────────────────────────────────
    // Menu Management (Protected)
    // ─────────────────────────────────────────────────────────
    
    @Transactional
    public MenuCategory addCategory(int restaurantId, String name, int managerId) {
        requireOwnership(restaurantId, managerId);
        
        MenuCategory cat = new MenuCategory(0, restaurantId, name);
        int id = menuCategoryDAO.insert(cat);
        cat.setCategoryId(id);
        return cat;
    }
    
    public List<MenuCategory> getCategories(int restaurantId) {
        return menuCategoryDAO.findByRestaurant(restaurantId);
    }
}
```

### Why Separate Authorization Check?

```java
// ✅ GOOD: Explicit check
public void updateRestaurant(..., int managerId) {
    requireOwnership(restaurantId, managerId);  // ← Explicit, clear
    restaurantDAO.update(restaurant);
}

// ❌ BAD: Hidden in SQL
public void updateRestaurant(..., int managerId) {
    String sql = "UPDATE Restaurant SET ... WHERE restaurant_id = ? AND manager_id = ?";
    // If SQL fails silently, no one knows why restaurant didn't update!
}
```

---

## OrderService

**Location:** `src/main/java/org/example/service/OrderService.java`

**Purpose:** Order creation, status tracking, validation.

### Key Methods

```java
@Service
public class OrderService {
    
    private final OrderDAO orderDAO;
    private final OrderItemDAO orderItemDAO;
    private final UserDAO userDAO;
    private final RestaurantDAO restaurantDAO;
    private final CouponService couponService;
    
    // ─────────────────────────────────────────────────────────
    // Create Order (Multiple validations, Atomic)
    // ─────────────────────────────────────────────────────────
    
    @Transactional  // ← All of this succeeds or all fails
    public Order createOrder(Order order, List<OrderItem> items, String couponCode) {
        // Validation: User exists
        User user = userDAO.findById(order.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // Validation: User has selected a delivery address
        if (user.getSelectedAddressId() == null) {
            throw new IllegalArgumentException(
                "Please select a delivery address before ordering");
        }
        
        // Validation: Restaurant exists
        Restaurant restaurant = restaurantDAO.findById(order.getRestaurantId())
            .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
        
        // Validation: Has items
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least 1 item");
        }
        
        // Validation: Coupon if provided
        double discountPercent = 0;
        if (couponCode != null && !couponCode.isBlank()) {
            discountPercent = couponService.validateAndGetDiscount(
                couponCode, 
                restaurant.getRestaurantId()
            );
            order.setCouponApplied(true);
        }
        
        // Calculate total price
        double total = 0;
        for (OrderItem item : items) {
            total += item.getUnitPrice() * item.getQuantity();
        }
        total = total * (1 - discountPercent / 100.0);
        order.setTotalPrice(total);
        
        // Set status to initial state
        order.setStatus("PREPARING");
        order.setCreatedAt(LocalDateTime.now());
        
        // Insert order (generates ID)
        int orderId = orderDAO.insert(order);
        order.setOrderId(orderId);
        
        // Insert all items (linked to order)
        for (OrderItem item : items) {
            item.setOrderId(orderId);
            orderItemDAO.insert(item);
        }
        
        return order;
    }
    
    // ─────────────────────────────────────────────────────────
    // Track Order Status
    // ─────────────────────────────────────────────────────────
    
    public Order findById(int orderId) {
        Order order = orderDAO.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        
        // Fetch items for this order
        order.setItems(orderItemDAO.findByOrder(orderId));
        
        return order;
    }
    
    // ─────────────────────────────────────────────────────────
    // Manager: Update Order Status
    // ─────────────────────────────────────────────────────────
    
    @Transactional
    public Order updateOrderStatus(int orderId, String newStatus, int managerId) {
        Order order = orderDAO.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        
        // Authorization: Only manager of restaurant can update
        Restaurant restaurant = restaurantDAO.findById(order.getRestaurantId())
            .orElseThrow();
        
        if (restaurant.getManagerId() != managerId) {
            throw new IllegalArgumentException(
                "Only the restaurant manager can update this order");
        }
        
        // Validation: Valid status
        if (!isValidStatus(newStatus)) {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }
        
        // Update
        order.setStatus(newStatus);
        if (newStatus.equals("PREPARING")) {
            order.setPreparingAt(LocalDateTime.now());
        } else if (newStatus.equals("ARRIVED")) {
            order.setArrivedAt(LocalDateTime.now());
        }
        orderDAO.updateStatus(orderId, newStatus);
        
        return order;
    }
    
    private boolean isValidStatus(String status) {
        return status.equals("PREPARING")
            || status.equals("ARRIVED");
    }
}
```

### Why @Transactional?

Without it:
```
orderDAO.insert(order)  // Works
orderItemDAO.insert(item1)  // Works
orderItemDAO.insert(item2)  // FAILS (database full?)
// Problem: Order exists but has no items!
```

With @Transactional:
```
@Transactional
public Order createOrder(...) {
    orderDAO.insert(order)
    orderItemDAO.insert(item1)
    orderItemDAO.insert(item2)  // FAILS
    // Spring rolls back ALL of the above
    // Database is clean, as if nothing happened
}
```

---

## CouponService

**Location:** `src/main/java/org/example/service/CouponService.java`

**Purpose:** Coupon validation, discount calculation.

### Key Methods

```java
@Service
public class CouponService {
    
    private final CouponDAO couponDAO;
    
    // ─────────────────────────────────────────────────────────
    // Validate & Get Discount
    // ─────────────────────────────────────────────────────────
    
    public double validateAndGetDiscount(String code, int restaurantId) {
        Coupon coupon = couponDAO.findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException(
                "Coupon not found: " + code));
        
        // Check restaurant
        if (coupon.getRestaurantId() != restaurantId) {
            throw new IllegalArgumentException(
                "Coupon " + code + " is not valid for this restaurant");
        }
        
        // Check if active
        if (!coupon.isActive()) {
            throw new IllegalArgumentException("Coupon " + code + " is no longer active");
        }
        
        // Check expiration
        if (LocalDateTime.now().isAfter(coupon.getValidTo())) {
            throw new IllegalArgumentException("Coupon " + code + " has expired");
        }
        
        // Check not started yet
        if (LocalDateTime.now().isBefore(coupon.getValidFrom())) {
            throw new IllegalArgumentException(
                "Coupon " + code + " is not yet valid");
        }
        
        // All checks passed, return discount
        return coupon.getDiscountPercent();
    }
    
    // ─────────────────────────────────────────────────────────
    // Create Coupon (Protected)
    // ─────────────────────────────────────────────────────────
    
    @Transactional
    public Coupon createCoupon(Coupon coupon, int managerId) {
        // Manager must own the restaurant
        // (This check is typically in the service that manages restaurants)
        
        int id = couponDAO.insert(coupon);
        coupon.setCouponId(id);
        return coupon;
    }
}
```

---

## StatisticsService

**Location:** `src/main/java/org/example/service/StatisticsService.java`

**Purpose:** Manager sales reports (8 metrics).

### What It Calculates

For a manager's restaurants, across a specific month, it returns:

```
1. Total Revenue
2. Total Orders
3. Average Order Value
4. Total Items Sold
5. Average Rating (all restaurants)
6. Orders by Status (SENT, PREPARING, ARRIVED)
7. Top Performing Cuisine
8. Top Selling Item
```

### Implementation Note

This service writes direct SQL queries (not via DAOs). Why?

**Reason:** Complex aggregations with GROUP BY, CASE, JOINs.

```java
@Service
public class StatisticsService {
    
    public Map<String, Object> getMonthlySalesMetrics(int managerId, 
                                                        int year, int month) {
        // Complex SQL with:
        // - JOINs (Order JOIN OrderItem JOIN MenuItem)
        // - GROUP BY (group by status, cuisine, item)
        // - CASE statements (conditional aggregation)
        // - Date filtering (WHERE MONTH(created_at) = ?)
        
        // Too complex to split across multiple DAOs
        // Write SQL directly here
    }
}
```

---

## Transactions

### @Transactional Explained

```java
@Service
public class OrderService {
    
    @Transactional
    public Order createOrder(...) {
        // Step 1
        int orderId = orderDAO.insert(order);
        
        // Step 2
        for (OrderItem item : items) {
            orderItemDAO.insert(item);
        }
        
        // Step 3
        return orderDAO.findById(orderId).orElseThrow();
    }
}
```

**What Spring does:**

```
Spring starts a database TRANSACTION
    │
    ├─ orderDAO.insert(order)  ← Change 1
    ├─ orderItemDAO.insert(item1)  ← Change 2
    ├─ orderItemDAO.insert(item2)  ← Change 3
    │
    ├─ If all succeed → COMMIT (changes saved permanently)
    └─ If any fails → ROLLBACK (all changes undone)
```

**Why matter?**

```
Without @Transactional:
  Step 1: Order inserted ✓
  Step 2: Item 1 inserted ✓
  Step 3: Item 2 FAILS
  Result: Incomplete order in database (corrupted state)

With @Transactional:
  Step 1: Order inserted ✓
  Step 2: Item 1 inserted ✓
  Step 3: Item 2 FAILS
  Result: Spring rolls back Steps 1-2, database is clean
```

---

## Summary

| Service | Job | Key Characteristic |
|---------|-----|-------------------|
| **UserService** | Registration, authentication | Hashes passwords securely |
| **RestaurantService** | CRUD, search, menu management | Checks manager ownership |
| **OrderService** | Create orders, track status | @Transactional for atomicity |
| **CouponService** | Validate discounts | Checks expiration & active status |
| **StatisticsService** | Sales reports | Complex SQL with aggregations |

**Golden rule:** Services are the **contract between HTTP and database**. Everything important happens here: validation, authorization, transactions.

---

## See Also

- [**ARCHITECTURE.md**](ARCHITECTURE.md) — Where services fit in the layers
- [**API_ENDPOINTS.md**](API_ENDPOINTS.md) — How services are called from controllers
- [**DATABASE.md**](DATABASE.md) — SQL queries executed by DAOs
