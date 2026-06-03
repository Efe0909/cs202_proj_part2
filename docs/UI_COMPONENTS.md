# UI Components: JavaFX Views & Event Handling

Complete guide to the desktop application's user interface. All views are built with JavaFX and communicate with the backend via `ApiClient`.

## Table of Contents

- [UI Architecture](#ui-architecture)
- [Application Flow](#application-flow)
- [Authentication Views](#authentication-views)
- [Customer Views](#customer-views)
- [Manager Views](#manager-views)
- [Shared Components](#shared-components)
- [Navigation Patterns](#navigation-patterns)
- [Error Handling](#error-handling)

---

## UI Architecture

### Tech Stack

- **Framework:** JavaFX 21 (desktop UI)
- **HTTP Layer:** `ApiClient` (REST calls to Spring backend)
- **Styling:** CSS (stylesheet in `resources/`)
- **JSON Mapping:** Jackson (deserialize API responses to Java objects)
- **Stage Management:** Single-window app, view switching via `Scene` replacement

### Directory Structure

```
src/main/java/org/example/ui/
├── MainApp.java                    # Entry point, Stage setup
├── UiUtil.java                     # Styling, dialogs, error messages
├── ApiClient.java                  # HTTP client, request/response handling
├── ApiException.java               # API error wrapper
│
├── LoginView.java                  # Login form
├── RegisterView.java               # Registration form
│
├── CustomerDashboard.java          # Customer main menu (SideBar + content switcher)
├── ManagerDashboard.java           # Manager main menu
│
├── RestaurantBrowserView.java      # Browse/search restaurants
├── MenuView.java                   # Browse menu items (by category)
├── CartView.java                   # Shopping cart before checkout
├── OrderTrackingView.java          # Customer order history & status
├── RatingView.java                 # Leave review for restaurant
│
├── IncomingOrdersView.java         # Manager: new orders to accept
├── MenuManagementView.java         # Manager: edit menu items + categories
├── CouponManagementView.java       # Manager: create/edit coupons
├── SalesStatisticsView.java        # Manager: monthly sales metrics
├── ManagerRatingsView.java         # Manager: customer reviews
│
├── AddressManagementView.java      # CRUD delivery addresses
├── PhoneManagementView.java        # CRUD phone numbers
│
├── AddressService.java             # Local address helper
├── PhoneService.java               # Local phone helper
└── resources/
    └── style.css                   # All UI styling
```

### View Lifecycle

Every view follows this pattern:

1. **Constructor**: Receives `Stage`, `User`, and parent `StackPane` (where to render)
2. **buildUI()**: Constructs the JavaFX scene graph (buttons, labels, tables, etc.)
3. **load()** or **show()**: Fetches data from API and populates the UI
4. **getRoot()**: Returns the root node for insertion into a parent

**Example:**

```java
class OrderTrackingView {
    private final Stage stage;
    private final User user;
    private final StackPane parentPane;
    
    public OrderTrackingView(Stage stage, User user, StackPane parentPane) {
        this.stage = stage;
        this.user = user;
        this.parentPane = parentPane;
    }
    
    public void load() {
        // Fetch orders from API
        List<Order> orders = ApiClient.getOrdersByUser(user.getUserId());
        // Populate UI
        displayOrders(orders);
        // Insert into parent
        parentPane.getChildren().setAll(buildUI());
    }
}
```

---

## Application Flow

### Startup Sequence

```
MainApp.start(Stage)
    ↓
new LoginView(stage)
    ↓
User clicks "Login" or "Create an account"
    ↓
ApiClient.post("/auth/login", credentials)
    ↓
Jackson deserializes response → User object
    ↓
LoginView.openDashboard(user)
    ↓
new CustomerDashboard(stage, user)  [or ManagerDashboard]
    ↓
Stage.setScene(scene)
```

**Key Points:**

- No session tokens. UI sends `userId` with every request to identify the user.
- `User` object is deserialized from login response, stored in memory for the session.
- Views are created and swapped by replacing the `Scene` or using `StackPane.getChildren().setAll()`.

### Scene vs. View Hierarchy

```
Stage (fixed)
  └─ Scene (replaced on major navigation, e.g., login → dashboard)
      └─ Dashboard
          ├─ Header (buttons, address picker, logout)
          ├─ Sidebar (navigation menu)
          └─ Content StackPane (holds current view)
              └─ [Current View: MenuView, OrderTrackingView, etc.]
```

---

## Authentication Views

### LoginView

**Purpose:** Authenticate existing users.

**UI Components:**
- Brand label ("Online Food Ordering")
- Title & subtitle
- Username field (TextField)
- Password field (PasswordField)
- Login button (primary style)
- Register button (ghost style)
- Error message label

**Event Flow:**

```
User enters username + password
    ↓
Click "Login" button
    ↓
ApiClient.post("/auth/login", {"username": "...", "password": "..."})
    ↓
Response: User JSON (userId, username, role, etc.)
    ↓
Jackson deserializes → User object
    ↓
openDashboard(user)
    ↓
Create dashboard based on user.isManager()
    ↓
Replace Scene
```

**Error Handling:**

- Network error: `"Login failed. Please try again."`
- Invalid credentials: `"Invalid username or password."`
- Button is disabled during request to prevent double-clicks.

**Key Code:**

```java
// ApiClient handles JSON serialization
String response = ApiClient.post("/auth/login",
    Map.of("username", username, "password", password));

// Jackson deserializes response
ObjectMapper mapper = ApiClient.mapper();
mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
User user = mapper.readValue(response, User.class);

// Route based on role
if (user.isManager()) {
    new ManagerDashboard(stage, user);
} else {
    new CustomerDashboard(stage, user);
}
```

---

### RegisterView

**Purpose:** Create new user account.

**UI Components:**
- Form fields: username, email, full name, password, confirm password
- Role selector (RadioButton: CUSTOMER or MANAGER)
- Register button
- Login button (back link)
- Validation error messages

**Event Flow:**

```
User fills form + selects role
    ↓
Click "Register" button
    ↓
Validate all fields (not empty, passwords match, username length, etc.)
    ↓
ApiClient.post("/auth/register", {...})
    ↓
Success: New User created, redirect to LoginView
    ↓
Failure: Show error message (username taken, etc.)
```

**Validation:**

- Username: 3+ characters, unique
- Password: 8+ characters (enforced by Spring validation, not UI)
- Email: valid format
- All fields required

---

## Customer Views

### CustomerDashboard

**Purpose:** Main navigation hub for customers.

**Structure:**

```
┌─────────────────────────────────────────┐
│ Brand   Hi, John   [Toronto ▾]  Logout  │  ← Header
├──────────┬─────────────────────────────┤
│  MENU    │                             │
│ ────────│    Content StackPane        │
│ Browse  │  (swaps between views)      │
│ Search  │                             │
│ Orders  │                             │
│ ────────│                             │
│ Addrs   │                             │
│ Phones  │                             │
└─────────┴─────────────────────────────┘
```

**Navigation:**

- **Browse Restaurants**: `new RestaurantBrowserView(...).loadRestaurants(null)`
- **Search**: `new RestaurantBrowserView(...).showSearchBar(content)`
- **My Orders**: `new OrderTrackingView(...).load()`
- **Delivery Addresses**: `new AddressManagementView(...).load()`
- **My Phones**: `new PhoneManagementView(...).load()`

**Address Pill:**

The header button shows "Delivering to: [City] ▾". Clicking it opens a `ChoiceDialog` to switch addresses. Changing address re-filters the restaurant list to match the new city.

**Code Pattern:**

```java
private void openAddressManagement() {
    new AddressManagementView(stage, user, content, this::refreshAddressPill).load();
}
// Callback refreshes header after address changes
private void refreshAddressPill() {
    String city = AddressService.resolveBrowsingCity(user);
    addressPill.setText("Delivering to: " + city + "  ▾");
}
```

---

### RestaurantBrowserView

**Purpose:** Browse and search restaurants (with keyword filtering).

**Modes:**

1. **Browse All** (by city)
   ```java
   RestaurantBrowserView rbv = new RestaurantBrowserView(stage, user, content);
   rbv.loadRestaurants(null);  // Fetch all restaurants in user's city
   ```

2. **Search by Keyword**
   ```java
   rbv.showSearchBar(content);  // Display search field
   // User types "pizza"
   // ApiClient calls GET /restaurants?city=Toronto&keyword=pizza
   ```

**UI Components:**

- Restaurant list (ScrollPane with VBox of cards)
- Each card: name, cuisine type, rating (★★★★★), keywords
- Click card → opens `MenuView` for that restaurant

**Key Code:**

```java
// Fetch restaurants filtered by city + keyword
List<Restaurant> restaurants = ApiClient.getRestaurants(
    city, keyword  // keyword is optional
);

// Display as clickable cards
restaurants.forEach(r -> {
    Button card = createRestaurantCard(r);
    card.setOnAction(e -> {
        MenuView mv = new MenuView(stage, user, r, content);
        mv.load();
    });
    restaurantList.getChildren().add(card);
});
```

---

### MenuView

**Purpose:** Browse menu items organized by category.

**UI Structure:**

```
┌─ Restaurant Name (header)
│
├─ [Appetizers]        ← Category tabs (ToggleButton group)
│   Pizza Margherita ($12.99)
│   Caesar Salad ($8.99)
│
├─ [Main Courses]
│   Spaghetti Carbonara ($14.99)
│   Salmon Grilled ($18.99)
│
└─ [Sides]
    French Fries ($3.99)
```

**Event Flow:**

```
User clicks menu item
    ↓
Add to cart (quantity popup or default 1)
    ↓
Store in CartView's internal list
    ↓
"View Cart" button shows cart count
    ↓
Click cart → MenuView.showCart()
    ↓
Show CartView overlay with items + checkout button
```

**Key Code:**

```java
// Fetch menu items for restaurant
List<MenuCategory> categories = ApiClient.getMenuCategories(restaurantId);

// Group by category
categories.forEach(category -> {
    Button categoryBtn = new ToggleButton(category.getName());
    categoryBtn.setOnAction(e -> showCategory(category));
    categoryTabs.getChildren().add(categoryBtn);
});

// Add item to cart
MenuItem item = ...;
cart.add(new CartItem(item, quantity));
cartCountLabel.setText(String.valueOf(cart.size()));
```

---

### CartView

**Purpose:** Review items before checkout.

**UI Components:**

- List of items: name, quantity, unit price, remove button
- Subtotal calculation
- Coupon code input (optional)
- "Place Order" button
- "Continue Shopping" button

**Checkout Flow:**

```
User reviews items + quantity
    ↓
(Optional) Enter coupon code
    ↓
Click "Place Order"
    ↓
Validate:
  - At least 1 item in cart
  - User has selected a delivery address
  - Coupon is valid (if provided)
    ↓
ApiClient.post("/orders", {
  userId, restaurantId, selectedAddressId,
  items: [{menuItemId, quantity, unitPrice}, ...],
  couponCode
})
    ↓
Success: Show order confirmation (orderId, total)
    ↓
Redirect to OrderTrackingView
```

**Key Code:**

```java
// Calculate subtotal and apply coupon
BigDecimal subtotal = cart.stream()
    .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
    .reduce(BigDecimal.ZERO, BigDecimal::add);

if (couponCode != null) {
    // Fetch coupon and calculate discount
    Coupon coupon = ApiClient.getCoupon(couponCode);
    BigDecimal discount = subtotal.multiply(BigDecimal.valueOf(coupon.getDiscountPercent()))
        .divide(BigDecimal.valueOf(100));
    total = subtotal.subtract(discount);
}

// Submit order
ApiClient.post("/orders", Map.of(
    "userId", user.getUserId(),
    "restaurantId", restaurantId,
    "selectedAddressId", user.getSelectedAddressId(),
    "items", cart,
    "couponCode", couponCode
));
```

---

### OrderTrackingView

**Purpose:** Display customer's order history and current status.

**UI Components:**

- List of orders (sorted by date, newest first)
- Each order card: orderId, restaurant name, status, total, createdAt
- Color-coded status badges:
  - **PREPARING** (orange): Restaurant is preparing
  - **SENT** (blue): Out for delivery
  - **ACCEPTED** (green): Delivered

**Event Flow:**

```
View loads
    ↓
ApiClient.get("/orders/user/{userId}")
    ↓
Display orders in reverse-chronological order
    ↓
User clicks order card
    ↓
Show order details:
  - Items in order (MenuItems + quantity)
  - Delivery address
  - Total price
  - Timeline: created → sent → accepted
```

---

### RatingView

**Purpose:** Leave a review for a restaurant (after delivery).

**Restrictions:**

- Only available AFTER order is ACCEPTED
- Can only rate within 24 hours of acceptance
- One rating per customer per restaurant (no duplicate ratings)

**UI Components:**

- Star rating selector (1-5 stars, clickable)
- Comment text area (optional, up to 500 chars)
- Submit button
- Cancel button

**Event Flow:**

```
User opens RatingView (from OrderTrackingView)
    ↓
Show current rating (if exists) or empty form
    ↓
User selects stars + writes comment
    ↓
Click "Submit"
    ↓
ApiClient.post("/ratings", {restaurantId, userId, score, comment})
    ↓
Success: Close dialog, refresh OrderTrackingView
    ↓
Error: Show message (e.g., "24-hour window expired")
```

---

## Manager Views

### ManagerDashboard

**Purpose:** Main navigation hub for managers.

**Structure:** (Similar to CustomerDashboard)

```
┌─────────────────────────────────────────┐
│ Brand   Hi, Manager   [Create Rest ▾]   │  ← Header (can create restaurants)
├──────────┬─────────────────────────────┤
│  MENU    │                             │
│ ────────│    Content StackPane        │
│ Incoming│  (swaps between views)      │
│ Orders  │                             │
│ ────────│                             │
│ Menu    │                             │
│ Coupons │                             │
│ Ratings │                             │
│ Stats   │                             │
└─────────┴─────────────────────────────┘
```

**Navigation:**

- **Incoming Orders**: `new IncomingOrdersView(...).load()`
- **Menu Management**: `new MenuManagementView(...).load()`
- **Coupon Management**: `new CouponManagementView(...).load()`
- **Ratings**: `new ManagerRatingsView(...).load()`
- **Statistics**: `new SalesStatisticsView(...).load()`

---

### IncomingOrdersView

**Purpose:** Accept or decline new orders from customers.

**UI Components:**

- List of PREPARING orders for manager's restaurants
- Each order card: orderId, customer name, restaurant, items, total, createdAt
- Accept button (green) / Decline button (red) per order

**Event Flow:**

```
View loads (polls every 3 seconds or refreshes on demand)
    ↓
ApiClient.get("/orders/restaurant/{restaurantId}")  [filter by status=PREPARING]
    ↓
Display orders in chronological order (oldest first)
    ↓
Manager clicks "Accept"
    ↓
ApiClient.put("/orders/{orderId}/status", {managerId, status: "ACCEPTED"})
    ↓
Success: Move order to ACCEPTED, remove from list, show confirmation
    ↓
Failure: Show error (e.g., "Order no longer pending")
```

**Key Code:**

```java
// Fetch pending orders for all restaurants owned by manager
List<Order> orders = ApiClient.getOrdersByRestaurant(restaurantId)
    .stream()
    .filter(o -> o.getStatus().equals("PREPARING"))
    .toList();

// Accept order
acceptBtn.setOnAction(e -> {
    ApiClient.put("/orders/" + order.getOrderId() + "/status",
        Map.of("managerId", user.getUserId(), "status", "ACCEPTED"));
    refreshList();
});
```

---

### MenuManagementView

**Purpose:** Add/edit menu items and categories.

**UI Structure:**

```
┌─ [Create Category] button
│
├─ Restaurant selector (dropdown)
│
├─ Categories
│   └─ [Appetizers]
│       ├─ Pizza Margherita ($12.99) [Edit] [Delete]
│       ├─ Caesar Salad ($8.99)      [Edit] [Delete]
│
│   └─ [Main Courses]
│       ├─ Spaghetti ($14.99)        [Edit] [Delete]
```

**Event Flow:**

```
1. CREATE CATEGORY:
   User clicks "Create Category" → Show dialog
   Enter category name → ApiClient.post("/menu-categories", {...})
   Category added to list

2. CREATE ITEM:
   User clicks "Add Item" under category → Show form
   Enter: name, description, price, image → ApiClient.post("/menu-items", {...})
   Item added to category

3. EDIT ITEM:
   User clicks "Edit" → Populate form with current values
   Update fields → ApiClient.put("/menu-items/{itemId}", {...})
   Refresh list

4. DELETE ITEM:
   User clicks "Delete" → Confirm dialog
   → ApiClient.delete("/menu-items/{itemId}")
   Item removed
```

**Key Components:**

- TreeView or nested layout for categories
- TableView with columns: name, price, image, actions
- Modal dialogs for create/edit forms

---

### CouponManagementView

**Purpose:** Create and manage discount coupons.

**UI Components:**

- Create coupon button
- Table of coupons: code, discount %, valid from/to, status (active/inactive)
- Toggle active/inactive
- Delete button (soft-delete)

**Event Flow:**

```
User clicks "Create Coupon"
    ↓
Show modal form:
  - Code (e.g., "SAVE10")
  - Discount % (1-100)
  - Valid from (datetime picker)
  - Valid to (datetime picker)
    ↓
Click "Create"
    ↓
ApiClient.post("/coupons", {restaurantId, code, discountPercent, validFrom, validTo})
    ↓
Success: Add to table, close dialog
    ↓
Error: Show validation message (code taken, invalid dates, etc.)
```

**Active/Inactive Toggle:**

```java
// Toggle active status
toggleBtn.setOnAction(e -> {
    ApiClient.put("/coupons/" + coupon.getCouponId(),
        Map.of("isActive", !coupon.isActive()));
    refreshList();
});
```

---

### SalesStatisticsView

**Purpose:** View monthly sales metrics and analytics.

**UI Components:**

- Month/Year selector (dropdown or date picker)
- Key metrics (cards or labels):
  - Total Revenue
  - Total Orders
  - Average Order Value
  - Total Items Sold
  - Average Rating
- Status breakdown chart (PREPARING, SENT, ACCEPTED)
- Top cuisine type and top-selling item

**Event Flow:**

```
View loads (default to current month)
    ↓
User selects month/year
    ↓
ApiClient.get("/statistics/manager/{managerId}/monthly?year=2025&month=5")
    ↓
Display metrics:
  - totalRevenue: $5,423.50
  - totalOrders: 42
  - averageOrderValue: $129.13
  - totalItemsSold: 156
  - averageRating: 4.3
  - statusBreakdown: {PREPARING: 5, SENT: 3, ACCEPTED: 34}
  - topCuisine: "Italian"
  - topSellingItem: "Margherita Pizza"
    ↓
Charts/bar graphs update (JavaFX Chart API)
```

---

### ManagerRatingsView

**Purpose:** Review customer ratings and feedback.

**UI Components:**

- Restaurant selector (if manager owns multiple)
- Table of ratings: customer, score (★), comment, createdAt
- Sorting: by score (worst first) or by date (newest first)

**Event Flow:**

```
View loads
    ↓
ApiClient.get("/restaurants/{restaurantId}/ratings")
    ↓
Display in table:
  - 5★ "Amazing pizza!" - john_doe - 2025-05-21
  - 4★ "Good, slow service" - jane_doe - 2025-05-20
  - 3★ "Average" - bob_smith - 2025-05-19
    ↓
Manager can sort/filter by score or read comments
```

---

## Shared Components

### UiUtil

**Purpose:** Reusable UI helpers (styling, dialogs, validation).

**Key Methods:**

```java
// Styling
static Scene styled(Scene s)
static Label label(String text, String styleClass)
static Button button(String text, String styleClass)

// Dialogs
static void info(String title, String message)
static void error(String title, Exception e)
static boolean confirm(String title, String message)

// TextField validation
static void onlyNumeric(TextField field)
static void maxLength(TextField field, int max)
```

**Example Usage:**

```java
Label title = UiUtil.label("Welcome", "h1");
Button btn = UiUtil.button("Click me", "primary");
UiUtil.error("Oops", ex);
boolean confirmed = UiUtil.confirm("Delete?", "Remove this item?");
```

---

### ApiClient

**Purpose:** HTTP communication with Spring backend.

**Methods:**

```java
// Generic requests
static String post(String endpoint, Map<String, ?> body)
static String get(String endpoint)
static String put(String endpoint, Map<String, ?> body)
static String delete(String endpoint)

// JSON mapping
static ObjectMapper mapper()
static <T> T mapTo(String json, Class<T> type)

// Built-in typed methods
static User login(String username, String password)
static List<Restaurant> getRestaurants(String city, String keyword)
static Order createOrder(int userId, int restaurantId, ...)
```

**Exception Handling:**

```java
try {
    User user = ApiClient.mapTo(response, User.class);
} catch (JsonMappingException e) {
    throw new ApiException("Invalid response format", e);
} catch (IOException e) {
    throw new ApiException("Network error", e);
}
```

---

### AddressService

**Purpose:** Local helper for address management (no database, pure HTTP).

**Methods:**

```java
static List<Address> list(int userId)
    // GET /addresses/{userId}

static void add(int userId, String street, String city, String province)
    // POST /addresses

static void select(int userId, int addressId)
    // Updates user's selected_address_id

static Address selectedOrFirst(List<Address> addresses)
    // Returns selected or first in list

static String resolveBrowsingCity(User user)
    // Gets city of selected address for restaurant browsing
```

**Nested Class:**

```java
class Address {
    int id;
    String street, city, province;
    
    @Override public String toString() {
        return street + ", " + city;
    }
}
```

---

## Navigation Patterns

### View-to-View Navigation

**Pattern 1: Replace Scene (major transitions)**

```java
// From LoginView to Dashboard
LoginView.openDashboard(user) {
    if (user.isManager()) {
        ManagerDashboard dash = new ManagerDashboard(stage, user);
        Scene newScene = UiUtil.styled(new Scene(dash.getRoot(), 1024, 700));
        stage.setScene(newScene);
    } else {
        // Similar for CustomerDashboard
    }
}
```

**Pattern 2: Replace StackPane Children (within dashboard)**

```java
// From dashboard sidebar to view
browseBtn.setOnAction(e -> {
    RestaurantBrowserView rbv = new RestaurantBrowserView(stage, user, content);
    rbv.loadRestaurants(null);
    // content.getChildren().setAll(...) happens inside RestaurantBrowserView
});
```

### Passing Data Between Views

**Context Object (User)**

```java
// Every view receives the current User object
public RestaurantBrowserView(Stage stage, User user, StackPane parentPane) {
    this.user = user;
    // Use user.getUserId(), user.getSelectedAddressId(), etc.
}
```

**Parent StackPane Reference**

```java
// Views know where to insert themselves
public void load() {
    parentPane.getChildren().setAll(buildUI());
}
```

**Callbacks (for two-way updates)**

```java
// AddressManagementView accepts a callback to refresh parent dashboard
new AddressManagementView(stage, user, content, this::refreshAddressPill).load();

// After address changes, callback refreshes the header
private void refreshAddressPill() {
    String city = AddressService.resolveBrowsingCity(user);
    addressPill.setText("Delivering to: " + city);
}
```

---

## Error Handling

### API Errors

**HTTP Errors → User Messages**

```java
try {
    String response = ApiClient.post("/auth/login", credentials);
} catch (ApiException e) {
    // e.getMessage() from backend: "Invalid username or password"
    errorLabel.setText(e.getMessage());
    System.err.println("[LoginView] error: " + e.getMessage());
}
```

**Validation Errors**

```java
// Backend returns 400 Bad Request with error message
// Example: "Coupon SAVE10 has expired"
try {
    ApiClient.post("/orders", {...});
} catch (ApiException e) {
    UiUtil.error("Could not place order", e);
}
```

### User-Facing Error Dialogs

**Info Dialog**

```java
UiUtil.info("Success", "Your order has been placed!");
```

**Error Dialog**

```java
try {
    // API call
} catch (Exception e) {
    UiUtil.error("Could not load restaurants", e);
}
```

**Confirmation Dialog**

```java
if (UiUtil.confirm("Delete Item?",
        "Remove this item from your menu?")) {
    ApiClient.delete("/menu-items/" + itemId);
}
```

### Field Validation

**Before Submission**

```java
// LoginView validates before calling API
String username = usernameField.getText().trim();
String password = passwordField.getText();

if (username.isEmpty() || password.isEmpty()) {
    errorLabel.setText("Please enter username and password");
    return;
}

// Only then call API
ApiClient.post("/auth/login", ...);
```

---

## See Also

- [**ARCHITECTURE.md**](ARCHITECTURE.md) — How views fit in the 3-tier system
- [**API_ENDPOINTS.md**](API_ENDPOINTS.md) — REST endpoints views call
- [**MODELS.md**](MODELS.md) — Domain objects deserialized by Jackson
