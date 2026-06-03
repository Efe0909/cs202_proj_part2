# Architecture: Understanding the System Design

This guide explains **WHAT** the system is built like and **WHY** it's designed that way. If you're new to the codebase, read this first.

## Table of Contents

- [The Big Picture](#the-big-picture)
- [Why This Architecture?](#why-this-architecture)
- [The Three Tiers](#the-three-tiers)
- [How a Request Travels](#how-a-request-travels)
- [Where Code Lives](#where-code-lives)
- [Key Design Decisions](#key-design-decisions)

---

## The Big Picture

Imagine a food delivery app with two types of users:
- **Customers:** Browse restaurants, place orders, track delivery, rate restaurants
- **Managers:** Manage restaurants, menus, see sales reports, accept orders

This app is split into **three separate worlds** that talk via HTTP:

```
┌──────────────────┐
│   Your Computer  │
│   (JavaFX UI)    │  ← Customer clicks buttons here
└────────┬─────────┘
         │ HTTP Request: "Give me all restaurants in Toronto"
         │ (like asking a waiter)
         ▼
┌──────────────────────────────────────────┐
│   Server (Spring Boot)                   │
│   - Validates the request                │
│   - Finds restaurants in database        │
│   - Sends back JSON response             │
└────────┬─────────────────────────────────┘
         │ HTTP Response: [restaurant1, restaurant2, ...]
         │ (waiter brings the answer)
         ▼
┌──────────────────┐
│   Your Computer  │
│   (Shows list)   │  ← User sees restaurants
└──────────────────┘

         ▼
      Database (MySQL)
      (where everything is stored)
```

**Why split it this way?**
- **Separation of concerns:** The UI doesn't need to know HOW restaurants are stored, just asks the server
- **Reusability:** Someone could build a mobile app or web app using the same server
- **Security:** Database passwords never reach the client computer
- **Scalability:** You can upgrade the database without changing the UI

---

## Why This Architecture?

### The Layered Approach (Why 3 layers?)

When you have code without layers, everything mixes together:

```
❌ BAD: Spaghetti code
┌─────────────────────────────────┐
│ Check input                     │
│ Validate user permission        │
│ Run SQL query                   │
│ Process results                 │
│ Format JSON response            │
│ Log errors                      │
│ Send email notification         │ ← All jumbled together!
└─────────────────────────────────┘
Problem: Change one thing, everything breaks. Hard to test.
```

**Our approach: 3 clean layers**

```
✅ GOOD: Layered approach
┌─────────────────────────────────┐
│ Controller                      │ ← HTTP entry point
│ (Check input, format response)  │   WHAT user asked for?
└─────────┬───────────────────────┘
          │
          ▼
┌─────────────────────────────────┐
│ Service                         │ ← Business logic
│ (Validate rules, check auth)    │   HOW should this work?
└─────────┬───────────────────────┘
          │
          ▼
┌─────────────────────────────────┐
│ DAO (Data Access)              │ ← Database layer
│ (Run SQL, return data)          │   WHERE is the data?
└─────────────────────────────────┘
```

Each layer has **one job:**
- **Controller** = "I'm a customer asking for restaurants"
- **Service** = "Let me check if this customer is allowed, fetch them"
- **DAO** = "Here's the SQL to get restaurants from the database"

**Why? Three benefits:**

1. **Easy to test** — You can test Service without touching the database (mock the DAO)
2. **Easy to change** — Need different database? Only change the DAO layer
3. **Easy to understand** — Each piece does one thing, not everything

---

## The Three Tiers

### Tier 1: Presentation (UI)

**Location:** `src/main/java/org/example/ui/`

**WHAT:** The screen the user sees. Built with JavaFX (a Java toolkit for desktop apps).

**WHY:** JavaFX is lightweight, cross-platform, and part of Java—no extra setup needed.

**Key classes:**
- `MainApp.java` — The window itself (like a browser's main window)
- `LoginView.java` — Login screen
- `CustomerDashboard.java` — Home screen for customers
- `RestaurantBrowserView.java` — "See all restaurants" screen
- `CartView.java` — Shopping cart screen
- `OrderTrackingView.java` — "Where's my order?" screen
- `ApiClient.java` — **The bridge!** Sends HTTP requests to the server

**How it works:**

```
User clicks "Search for restaurants"
           │
           ▼
RestaurantBrowserView reads the city name
           │
           ▼
Calls ApiClient.get("/api/restaurants?city=Toronto")
           │
           ▼
ApiClient sends HTTP request over the network
           │
           ▼
Server responds with JSON: [{"name":"Pizza Place",...}, ...]
           │
           ▼
ApiClient converts JSON back to Java objects
           │
           ▼
RestaurantBrowserView displays the list on screen
```

### Tier 2: API (REST Controller)

**Location:** `src/main/java/org/example/controller/`

**WHAT:** The "waiter" that receives requests and decides what to do.

**WHY:** REST means every request follows a pattern:
- `GET /api/restaurants` = "Give me restaurants"
- `POST /api/orders` = "Create a new order"
- `PUT /api/orders/42` = "Update order 42"
- `DELETE /api/addresses/5` = "Delete address 5"

This pattern is **standard across the web**, so anyone can use your API.

**Key classes:**
- `RestaurantController` — Handles restaurant requests
- `OrderController` — Handles order requests
- `AuthController` — Handles login/signup
- `ControllerInputs` — Validates that the request has all required fields
- `GlobalExceptionHandler` — If something breaks, convert it to a nice error message

**How it works:**

```
UI sends: POST /api/orders with body: {userId: 5, restaurantId: 2, items: [...]}
           │
           ▼
OrderController.createOrder() receives the request
           │
           ▼
ControllerInputs.requireInt(body, "userId") checks userId exists and is a number
ControllerInputs.requireInt(body, "restaurantId") checks restaurantId exists
           │
           ▼
If missing: throw exception → GlobalExceptionHandler → HTTP 400 "Missing userId"
If valid: continue to Service layer
           │
           ▼
orderService.createOrder(order, items)
           │
           ▼
Response with new Order object as JSON
```

### Tier 3: Business Logic (Service)

**Location:** `src/main/java/org/example/service/`

**WHAT:** The rules of the business. "Can this user do this action?"

**WHY:** Business rules are separate from HTTP and database details. Examples:
- "Can only edit your own restaurant" ← Authorization rule
- "Coupon must be active" ← Validation rule
- "Multiple updates must happen together or not at all" ← Transaction rule

**Key classes:**
- `UserService` — User registration and authentication
- `RestaurantService` — Restaurant creation, searching, authorization
- `OrderService` — Order creation, status updates
- `CouponService` — Coupon validation
- `StatisticsService` — Sales reports for managers

**Example: Authorization**

```java
// In RestaurantService.java
public void updateRestaurant(Restaurant r, int managerId) {
    // BUSINESS RULE: Only the manager who owns this restaurant can edit it
    Restaurant existing = restaurantDAO.findById(r.getRestaurantId())
        .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
    
    if (existing.getManagerId() != managerId) {
        // BLOCK unauthorized access!
        throw new IllegalArgumentException("You don't own this restaurant");
    }
    
    // Safe to proceed
    restaurantDAO.update(r);
}
```

Why separate this from the controller? Because:
1. **Testable:** You can test the rule without HTTP
2. **Reusable:** Could be called from web app, mobile app, CLI tool
3. **Maintainable:** All business rules in one place

---

### Tier 4: Data Access (DAO)

**Location:** `src/main/java/org/example/dao/`

**WHAT:** The code that talks to MySQL. Every SQL statement is here.

**WHY:** This "data layer" separation means:
- **No SQL scattered everywhere** — All queries in one place
- **Easy to change database** — Only touch DAO code
- **Testable** — Can mock the DAO and test Service logic
- **Secure** — Uses `PreparedStatement` to prevent SQL injection

**Key classes:**
- `UserDAO` — SQL for users, addresses, phones
- `RestaurantDAO` — SQL for restaurants and keywords
- `OrderDAO` — SQL for orders
- `MenuItemDAO`, `MenuCategoryDAO` — SQL for menus
- `CouponDAO`, `RatingDAO` — SQL for coupons and ratings

**Example: Safe SQL**

```java
// ✅ SAFE: Parameter binding prevents SQL injection
public Optional<User> findByUsername(String username) {
    String sql = "SELECT * FROM Users WHERE username = ?";
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, username);  // ← Parameter binding
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return Optional.of(mapRowToUser(rs));
        }
    } catch (SQLException e) {
        throw new RuntimeException("Database error", e);
    }
    return Optional.empty();
}

// ❌ UNSAFE: String concatenation (DON'T DO THIS!)
String sql = "SELECT * FROM Users WHERE username = '" + username + "'";
// If username = "'; DROP TABLE Users; --" → DISASTER!
```

---

## How a Request Travels

Let's trace: **Customer creates an order**

```
╔════════════════════════════════════════════════════════════════════╗
║ STEP 1: User clicks "Place Order" button                          ║
╚════════════════════════════════════════════════════════════════════╝

CartView.java:
  @FXML void onPlaceOrderClicked() {
      Order order = new Order();
      order.setUserId(currentUser.getId());
      order.setRestaurantId(restaurant.getId());
      // ... fill in items, coupon, etc.
      
      ApiClient.post("/api/orders", order);  // ← Send to server
  }

╔════════════════════════════════════════════════════════════════════╗
║ STEP 2: HTTP Request travels to server                            ║
╚════════════════════════════════════════════════════════════════════╝

HTTP POST http://localhost:8080/api/orders
Content-Type: application/json
Body: {
  "userId": 5,
  "restaurantId": 2,
  "selectedAddressId": 10,
  "items": [
    {"menuItemId": 15, "quantity": 2},
    {"menuItemId": 18, "quantity": 1}
  ],
  "couponCode": "SAVE10"
}

╔════════════════════════════════════════════════════════════════════╗
║ STEP 3: Server receives request → OrderController                 ║
╚════════════════════════════════════════════════════════════════════╝

OrderController.java:
  @PostMapping("/orders")
  public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body) {
      // Extract input
      int userId = ControllerInputs.requireInt(body, "userId");
      int restaurantId = ControllerInputs.requireInt(body, "restaurantId");
      // ... more validation
      
      // Delegate to service
      Order created = orderService.createOrder(order, items, couponCode);
      
      // Send back response
      return ResponseEntity.ok(created);
  }

╔════════════════════════════════════════════════════════════════════╗
║ STEP 4: Service handles business logic                            ║
╚════════════════════════════════════════════════════════════════════╝

OrderService.java:
  @Transactional  // ← ALL of this is atomic (all succeed or all fail)
  public Order createOrder(Order order, List<OrderItem> items, 
                           String couponCode) {
      // VALIDATION: Check user exists
      User user = userDAO.findById(order.getUserId())
          .orElseThrow(() -> new IllegalArgumentException("User not found"));
      
      // AUTHORIZATION: Check restaurant exists
      Restaurant restaurant = restaurantDAO.findById(order.getRestaurantId())
          .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
      
      // VALIDATION: Check coupon is active
      if (couponCode != null) {
          couponService.validateCoupon(couponCode, restaurant.getRestaurantId());
      }
      
      // Now safe to create
      int orderId = orderDAO.insert(order);  // ← Database call
      for (OrderItem item : items) {
          item.setOrderId(orderId);
          orderItemDAO.insert(item);  // ← Another database call
      }
      
      return orderDAO.findById(orderId).orElseThrow();
  }

╔════════════════════════════════════════════════════════════════════╗
║ STEP 5: DAO accesses database                                     ║
╚════════════════════════════════════════════════════════════════════╝

OrderDAO.java:
  public int insert(Order order) {
      String sql = "INSERT INTO Order (user_id, restaurant_id, ...) 
                    VALUES (?, ?, ...)";
      PreparedStatement ps = conn.prepareStatement(sql, 
          Statement.RETURN_GENERATED_KEYS);
      ps.setInt(1, order.getUserId());
      ps.setInt(2, order.getRestaurantId());
      // ... bind all parameters
      ps.executeUpdate();  // ← Hit the database
      // Get the generated order ID
      return extractGeneratedKey(ps);
  }

╔════════════════════════════════════════════════════════════════════╗
║ STEP 6: Response travels back to client                           ║
╚════════════════════════════════════════════════════════════════════╝

OrderController responds:
  HTTP 200 OK
  Content-Type: application/json
  Body: {
    "orderId": 42,
    "userId": 5,
    "restaurantId": 2,
    "status": "PREPARING",
    "totalPrice": 45.50,
    "createdAt": "2025-05-21T14:30:00"
  }

╔════════════════════════════════════════════════════════════════════╗
║ STEP 7: Client receives and displays                              ║
╚════════════════════════════════════════════════════════════════════╝

CartView.java:
  ApiClient receives JSON response
  Jackson converts JSON → Order object
  
  Order order = objectMapper.readValue(jsonResponse, Order.class);
  
  UiUtil.showSuccessDialog("Order placed!", 
      "Your order #" + order.getOrderId() + " is being prepared");
  
  // Navigate to tracking screen
  primaryStage.setScene(new OrderTrackingView(order).getScene());
```

---

## Where Code Lives

**Quick reference map:**

```
src/main/java/org/example/
│
├── controller/          ← HTTP entry points (REST API)
│   ├── AuthController.java       (login, register)
│   ├── RestaurantController.java (browse, search, manage)
│   ├── OrderController.java      (create, track, update status)
│   ├── CouponController.java     (manage coupons)
│   ├── ControllerInputs.java     (validation helper)
│   └── GlobalExceptionHandler.java (error responses)
│
├── service/             ← Business logic & authorization
│   ├── UserService.java
│   ├── RestaurantService.java
│   ├── OrderService.java
│   ├── CouponService.java
│   └── StatisticsService.java
│
├── dao/                 ← Database access (SQL queries)
│   ├── UserDAO.java
│   ├── RestaurantDAO.java
│   ├── OrderDAO.java
│   ├── CouponDAO.java
│   └── ... (7 DAOs total)
│
├── model/               ← Data objects (User, Order, etc.)
│   ├── User.java
│   ├── Order.java
│   ├── Restaurant.java
│   └── ... (10 models total)
│
├── ui/                  ← JavaFX screens & HTTP client
│   ├── MainApp.java        (JavaFX entry point)
│   ├── ApiClient.java      (HTTP client)
│   ├── LoginView.java
│   ├── CustomerDashboard.java
│   ├── CartView.java
│   ├── OrderTrackingView.java
│   └── ... (20+ view classes)
│
├── util/                ← Helper utilities
│   └── PasswordUtil.java (password hashing)
│
└── config/              ← Spring configuration
    └── RequestLoggingFilter.java
```

---

## Key Design Decisions

### Decision 1: No ORM (Object-Relational Mapping)

**What:** No Hibernate, no JPA. We write SQL by hand.

**Why?**
- **Spec requirement:** Assignment says "no ORM"
- **Explicit control:** You see exactly what SQL runs
- **Learning:** You understand databases, not just objects
- **Simple:** Less "magic", easier to debug

**Example:**
```java
// Our way:
String sql = "SELECT * FROM Users WHERE username = ?";
PreparedStatement ps = conn.prepareStatement(sql);
ps.setString(1, username);

// ORM way (Hibernate):
User u = session.createQuery("from User where username = ?")
    .setParameter(0, username).getSingleResult();
// ^ Where's the SQL? Hidden in ORM magic.
```

### Decision 2: Transactions at Service Layer

**What:** `@Transactional` annotation on service methods.

**Why?**
- **Atomicity:** If creating an order fails halfway, everything rolls back
- **ACID guaranteed:** Spring + MySQL handle it
- **Declarative:** Just add `@Transactional`, Spring does the rest

```java
@Transactional
public Order createOrder(...) {
    // If ANY of this fails, the whole method rolls back
    orderDAO.insert(order);
    for (OrderItem item : items) {
        orderItemDAO.insert(item);  // If this fails, order.insert() is undone
    }
}
```

### Decision 3: Authorization in Service Layer

**What:** Services check "does this user own this resource?"

**Why?**
- **Business rule:** Not a controller concern, not a database concern
- **Reusable:** Same rule for all apps
- **Secure:** If you call service directly (not via HTTP), still protected

```java
public void updateRestaurant(Restaurant r, int managerId) {
    // Always check ownership, regardless of caller
    requireOwnership(r.getRestaurantId(), managerId);
    restaurantDAO.update(r);
}
```

### Decision 4: JSON over HTTP

**What:** UI and server talk via JSON.

**Why?**
- **Standard:** Every web/mobile app does this
- **Human-readable:** You can inspect requests with curl or browser tools
- **Language-agnostic:** Could swap JavaFX for Python GUI, server stays same

```
Client sends:  {"userId": 5, "restaurantId": 2}
Server sends:  {"orderId": 42, "status": "PREPARING"}
```

---

## Summary

| Layer | Job | Example |
|-------|-----|---------|
| **UI** | Draw buttons, handle clicks | CartView.java |
| **Controller** | Parse HTTP, validate input | OrderController.java |
| **Service** | Apply business rules | orderService.requireOwnership() |
| **DAO** | Run SQL queries | orderDAO.insert() |
| **Database** | Store data | MySQL table `Order` |

**Read next:**
- [**MODELS.md**](MODELS.md) — Understanding the data model
- [**SERVICES.md**](SERVICES.md) — Service layer deep dive
- [**DATABASE.md**](DATABASE.md) — SQL and schema
- [**API_ENDPOINTS.md**](API_ENDPOINTS.md) — REST API reference
