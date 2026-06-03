# Debugging Guide: Common Issues & Solutions

Troubleshooting strategies for the most frequent problems we have faced in development and testing.

## Table of Contents

- [Quick Diagnostics](#quick-diagnostics)
- [Database Issues](#database-issues)
- [Backend (Spring) Issues](#backend-spring-issues)
- [REST API Issues](#rest-api-issues)
- [Frontend (JavaFX) Issues](#frontend-javafx-issues)
- [Authentication Issues](#authentication-issues)
- [Logging & Inspection](#logging--inspection)

---

## Quick Diagnostics

### Is the database running?

```bash
# Check Docker containers
docker ps

# Should see: mysql:8.0 running, port 3306 exposed
```

If not running:

```bash
docker-compose up db  # Start database
```

### Is the Spring backend running?

```bash
# Check if server is listening on port 8080
curl -I http://localhost:8080/api/restaurants?city=Toronto

# Expected: HTTP/1.1 200 OK (or 400 Bad Request, but NOT "Connection refused")
```

If backend won't start, check logs:

```bash
mvn spring-boot:run 2>&1 | grep -i error
```

### Is the JavaFX app connecting?

```bash
# Open JavaFX app, try to login
# If login fails:
1. Check Spring backend is running: curl localhost:8080
2. Check network: can you ping localhost?
3. Check ApiClient.BASE_URL (should be http://localhost:8080/api)
```

---

## Database Issues

### Error: "Connection refused: localhost:3306"

**Problem:** MySQL container not running or port not exposed.

**Solution:**

```bash
# Start database
docker-compose up db

# Verify it's running
docker ps | grep mysql

# Check port is exposed
docker port <container-id> 3306

# Test connection
docker exec -it <container-id> mysql -u root -proot -e "SELECT 1"
```

### Error: "Unknown database 'food_ordering'"

**Problem:** DDL.sql hasn't run yet (first startup).

**Solution:**

```bash
# Docker runs DDL.sql automatically on first start
# If not:
docker exec -it <container-id> mysql -u root -proot < DDL.sql

# Verify schema exists
docker exec -it <container-id> mysql -u root -proot -e "SHOW DATABASES;"
```

### Error: "Table doesn't exist: Order"

**Problem:** DDL.sql not executed or schema is out of sync.

**Solution:**

```bash
# Check what tables exist
docker exec -it <container-id> mysql -u root -proot -D food_ordering -e "SHOW TABLES;"

# If tables are missing, run DDL.sql
docker exec -it <container-id> mysql -u root -proot -D food_ordering < DDL.sql

# Verify all 11 tables exist
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "SHOW TABLES;" | wc -l  # Should be 11 (plus header)
```

### Error: "Column doesn't exist: tip"

**Problem:** You added a new column to DDL.sql but didn't re-run it.

**Solution:**

```bash
# Option 1: Drop and recreate (dev only, loses data)
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "DROP TABLE Order; DROP TABLE OrderItem;" # Cascading
docker exec -it <container-id> mysql -u root -proot -D food_ordering < DDL.sql

# Option 2: Manually add the column
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "ALTER TABLE Order ADD COLUMN tip DECIMAL(10,2) DEFAULT 0;"

# Verify
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "DESCRIBE Order;" | grep tip
```

### Slow queries or timeouts

**Problem:** DAO layer executing inefficient SQL or without proper indexing.

**Solution:**

```bash
# Enable slow query log
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "SET GLOBAL slow_query_log = 'ON';" 

# Check slow query log
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "SELECT query_time, sql_text FROM mysql.slow_log ORDER BY query_time DESC LIMIT 5;"

# Add indexes for frequently-queried columns
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "CREATE INDEX idx_order_user ON Order(user_id);"

# Verify index was created
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "SHOW INDEXES FROM Order;" | grep idx_
```

### Deadlock or "Table is locked"

**Problem:** Concurrent requests holding locks; rare in single-user testing.

**Solution:**

```bash
# Check open transactions
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "SELECT * FROM INFORMATION_SCHEMA.PROCESSLIST WHERE db='food_ordering';"

# Kill stuck connection
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "KILL <process-id>;"

# If persistent, restart database
docker-compose restart db
```

---

## Backend (Spring) Issues

### Error: "Failed to initialize ApplicationContext"

**Problem:** Spring configuration is broken, usually a dependency or bean issue.

**Solution:**

```bash
# Check the full error
mvn spring-boot:run 2>&1 | tail -50

# Common causes:
1. Missing @Component/@Service on a class
2. Circular dependency (A depends on B, B depends on A)
3. @Autowired field not found in Spring context
4. Incorrect application.properties database connection string

# Fix and try again
mvn spring-boot:run
```

### Error: "No qualifying bean of type OrderDAO found"

**Problem:** OrderDAO not recognized as a Spring bean.

**Solution:**

```java
// Make sure DAO has @Component or @Repository
@Repository  // ← Add this
public class OrderDAO {
    @Autowired
    private DataSource dataSource;
}
```

### Error: "Request method 'GET' not supported; Content-Type 'application/json'"

**Problem:** Sent GET request with JSON body (GET should have query params).

**Solution:**

```bash
# ❌ Wrong
curl -X GET http://localhost:8080/api/restaurants \
  -H "Content-Type: application/json" \
  -d '{"city": "Toronto"}'

# ✅ Correct
curl "http://localhost:8080/api/restaurants?city=Toronto"
```

### Error: "Required request body is missing"

**Problem:** Sent POST/PUT without JSON body, or body is empty.

**Solution:**

```bash
# ❌ Wrong
curl -X POST http://localhost:8080/api/auth/login

# ✅ Correct
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"pass123"}'
```

### Backend logs show no errors, but request hangs

**Problem:** Likely database deadlock or infinite loop in DAO.

**Solution:**

```bash
# Kill the process and restart with verbose logging
mvn spring-boot:run -X 2>&1 | tee /tmp/spring.log

# In another terminal, watch for any SQL statements
tail -f /tmp/spring.log | grep SELECT
```

### Error: "Illegal attempt to associate a collection with two open sessions"

**Problem:** Hibernate/JPA session issue (shouldn't happen in this project, but if ORM is added).

**Solution:** This app uses raw SQL, not Hibernate, so this shouldn't occur. If it does, you've accidentally introduced an ORM dependency.

---

## REST API Issues

### Error: "Invalid JSON in request body"

**Problem:** Malformed JSON syntax.

**Solution:**

```bash
# Test JSON syntax
echo '{"username":"john","password":"pass123"}' | jq .

# If jq fails, JSON is invalid
# Common mistakes:
# - Missing quotes around string values
# - Trailing commas
# - Single quotes instead of double quotes
```

### Error: "Unknown property 'fullName' (type User, ignore)"

**Problem:** Jackson can't deserialize JSON to Java object (field mismatch).

**Solution:**

```java
// Make sure Java field matches JSON key (camelCase)
public class User {
    private String fullName;  // ← Must match JSON "fullName"
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
}
```

### Error: "HTTP 400: Missing required field 'userId'"

**Problem:** Request JSON is missing a required field.

**Solution:**

```bash
# Check request DTO to see what's required
# User request:
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john",
    "email": "john@example.com",
    "fullName": "John Doe",
    "password": "Password123!",
    "role": "CUSTOMER"
  }'
```

### Error: "HTTP 401: Invalid username or password"

**Problem:** Credentials don't match.

**Solution:**

```bash
# Verify user exists in database
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "SELECT username, email FROM Users WHERE username='john';"

# If not found, create user via register endpoint first
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"pass123",...}'
```

### Error: "HTTP 404: Restaurant not found: 999"

**Problem:** Restaurant ID doesn't exist in database.

**Solution:**

```bash
# List all restaurants
curl "http://localhost:8080/api/restaurants?city=Toronto"

# Or check database directly
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "SELECT restaurant_id, name FROM Restaurant LIMIT 5;"
```

### Error: "HTTP 500: Database error: connection lost"

**Problem:** Backend lost connection to database (network issue, DB crash).

**Solution:**

```bash
# Check database is still running
docker ps | grep mysql

# If not, restart it
docker-compose restart db

# Check backend can connect
mvn spring-boot:run 2>&1 | grep -i database

# Restart backend
mvn spring-boot:run
```

### Endpoint returns 200 OK but response is empty

**Problem:** ResultSet was empty (no data) or DAO returned null.

**Solution:**

```bash
# Verify data exists
curl "http://localhost:8080/api/restaurants?city=Toronto" | jq .

# Check database
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "SELECT COUNT(*) FROM Restaurant;"

# If count is 0, insert test data manually
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "INSERT INTO Restaurant (name, cuisine_type, address, city, manager_id) 
       VALUES ('Pizza Palace', 'Italian', '123 Main', 'Toronto', 1);"
```

---

## Frontend (JavaFX) Issues

### App freezes when clicking a button

**Problem:** API call is blocking the UI thread (UI thread shouldn't wait for HTTP).

**Solution:**

```java
// ❌ Wrong: Blocks UI thread
loginBtn.setOnAction(e -> {
    User user = ApiClient.login(username, password);  // Blocks!
    openDashboard(user);
});

// ✅ Correct: Use Task for background execution
loginBtn.setOnAction(e -> {
    Task<User> task = new Task<User>() {
        @Override protected User call() throws Exception {
            return ApiClient.login(username, password);
        }
    };
    
    task.setOnSucceeded(event -> {
        User user = task.getValue();
        openDashboard(user);
    });
    
    task.setOnFailed(event -> {
        UiUtil.error("Login failed", task.getException());
    });
    
    new Thread(task).start();
});
```

### App crashes with NullPointerException

**Problem:** UI code accessing null object (API returned null, or object wasn't initialized).

**Solution:**

```java
// Add null checks before accessing
try {
    List<Order> orders = ApiClient.getOrders(userId);
    if (orders == null) {
        UiUtil.error("No orders data", new Exception("API returned null"));
        return;
    }
    
    orders.forEach(order -> {
        if (order.getOrderId() > 0) {  // Null-safe check
            addOrderToUI(order);
        }
    });
} catch (NullPointerException e) {
    System.err.println("NPE: " + e.getMessage());
    e.printStackTrace();
}
```

### UI doesn't update after API call

**Problem:** Forgot to call `getChildren().setAll()` or didn't refresh the view.

**Solution:**

```java
// After fetching data, must update UI thread
List<Restaurant> restaurants = ApiClient.getRestaurants(city, keyword);

// Update must happen on JavaFX thread
javafx.application.Platform.runLater(() -> {
    restaurantList.getChildren().clear();
    restaurants.forEach(r -> {
        restaurantList.getChildren().add(createRestaurantCard(r));
    });
});
```

### Error: "Cannot access Stage from non-UI thread"

**Problem:** Trying to modify UI from background thread without `Platform.runLater()`.

**Solution:**

```java
// ❌ Wrong: Called from API response thread
ApiClient.login(username, password);  // runs on separate thread
stage.setScene(...);  // Crashes!

// ✅ Correct: Wrap UI changes
ApiClient.login(username, password);  // Runs on background thread
Platform.runLater(() -> {
    stage.setScene(...);  // Back on UI thread
});
```

### Button has no style (looks plain, not styled)

**Problem:** Stylesheet not loaded or CSS class not applied.

**Solution:**

```java
// Verify stylesheet is loaded
String stylesheetURL = getClass().getResource("/style.css").toExternalForm();
scene.getStylesheets().add(stylesheetURL);

// Verify CSS class is applied to element
Button btn = new Button("Click me");
btn.getStyleClass().add("primary");  // ← Must add CSS class

// Check style.css has the class
// style.css should contain:
// .primary { -fx-padding: 10px; -fx-font-size: 14px; ... }
```

### Dialog doesn't appear or is off-screen

**Problem:** Dialog created but not shown, or positioned off-screen.

**Solution:**

```java
// Verify dialog is shown
Dialog<String> dialog = new Dialog<>();
dialog.setTitle("Confirm");
dialog.setHeaderText("Are you sure?");

// Must add buttons and content
ButtonType okBtn = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
dialog.getDialogPane().getButtonTypes().add(okBtn);

// Show and wait
Optional<String> result = dialog.showAndWait();  // ← Don't forget showAndWait()!
result.ifPresent(value -> System.out.println("User clicked OK"));
```

---

## Authentication Issues

### Error: "Invalid username or password" (but credentials are correct)

**Problem:** Password hashing mismatch (frontend sending plain text, backend hashing it differently).

**Solution:**

```bash
# Verify user was created correctly
docker exec -it <container-id> mysql -u root -proot -D food_ordering \
  -e "SELECT username, password, salt FROM Users WHERE username='john';"

# Password field should be a long hash (64+ chars), not plain text
# If it's plain text, register again (should hash on registration)

curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"SecurePassword123!","role":"CUSTOMER",...}'

# Then try login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"SecurePassword123!"}'
```

### User can see another user's orders

**Problem:** Missing authorization check in service or controller.

**Solution:**

```java
// OrderService must verify userId
@Transactional
public Order getOrder(int orderId, int requestingUserId) {
    Order order = orderDAO.getOrder(orderId);
    if (order.getUserId() != requestingUserId) {
        throw new AuthorizationException("Cannot view another user's order");
    }
    return order;
}

// Controller must pass userId
@GetMapping("/{orderId}")
public ResponseEntity<?> getOrder(@PathVariable int orderId,
                                  @RequestParam int userId) {
    try {
        Order order = orderService.getOrder(orderId, userId);
        return ResponseEntity.ok(order);
    } catch (AuthorizationException e) {
        return ResponseEntity.status(403).body(
            Map.of("error", "Unauthorized")
        );
    }
}
```

### Manager can edit another manager's restaurant

**Problem:** Missing ownership check in restaurant update.

**Solution:**

```java
// RestaurantService must verify manager ownership
@Transactional
public void updateRestaurant(int restaurantId, int managerId, Restaurant updates) {
    Restaurant existing = restaurantDAO.getRestaurant(restaurantId);
    if (existing.getManagerId() != managerId) {
        throw new AuthorizationException("You don't own this restaurant");
    }
    // Proceed with update
}
```

---

## Logging & Inspection

### Enable DEBUG logging in Spring

**File:** `application.properties`

```properties
logging.level.root=INFO
logging.level.org.springframework.web=DEBUG
logging.level.org.example=DEBUG
```

Then restart:

```bash
./gradlew bootRun
```

### Inspect API request/response

**Using curl with verbose flag:**

```bash
curl -v -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"pass"}'

# Shows:
# > POST /api/auth/login HTTP/1.1
# > Host: localhost:8080
# > Content-Type: application/json
# < HTTP/1.1 200 OK
# < Content-Type: application/json
```

**Using jq for pretty-printed JSON:**

```bash
curl -s http://localhost:8080/api/restaurants?city=Toronto | jq .

# Formatted output
# [
#   {
#     "restaurantId": 2,
#     "name": "Pizza Palace",
#     ...
#   }
# ]
```

### Inspect database queries

**Enable query logging in application.properties:**

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

(Note: This app doesn't use Hibernate, so check logs from DAO layer instead.)

**Add debug logging in DAO:**

```java
public Order getOrder(int orderId) {
    String sql = "SELECT * FROM Order WHERE order_id = ?";
    System.out.println("DEBUG: Executing SQL: " + sql);
    System.out.println("DEBUG: With orderId = " + orderId);
    
    PreparedStatement ps = conn.prepareStatement(sql);
    ps.setInt(1, orderId);
    ResultSet rs = ps.executeQuery();
    
    if (rs.next()) {
        Order order = new Order();
        // ... map fields ...
        System.out.println("DEBUG: Found order: " + order.getOrderId());
        return order;
    }
    System.out.println("DEBUG: No order found with id " + orderId);
    return null;
}
```

### Check Java stack trace

**When an exception occurs:**

```
Exception in thread "JavaFX Application Thread" java.lang.NullPointerException
    at org.example.ui.OrderTrackingView.load(OrderTrackingView.java:45)
    at org.example.ui.CustomerDashboard.lambda$buildSidebar$0(CustomerDashboard.java:84)
    ...
```

**Read from bottom to top:**

1. Customer clicked "My Orders"
2. Triggered `buildSidebar` lambda
3. Called `OrderTrackingView.load()`
4. Line 45 threw NPE (likely `orders` is null)

**Fix:**

```java
public void load() {
    try {
        List<Order> orders = ApiClient.getOrdersByUser(user.getUserId());
        if (orders == null) {  // ← Add this check
            UiUtil.error("No data", new Exception("API returned null"));
            return;
        }
        displayOrders(orders);
    } catch (Exception e) {
        UiUtil.error("Could not load orders", e);
    }
}
```

---

## See Also

- [**ARCHITECTURE.md**](ARCHITECTURE.md) — Understand the system flow first
- [**DATABASE.md**](DATABASE.md) — Database structure and queries
- [**SERVICES.md**](SERVICES.md) — Service layer patterns and auth
- [**API_ENDPOINTS.md**](API_ENDPOINTS.md) — Expected request/response formats
