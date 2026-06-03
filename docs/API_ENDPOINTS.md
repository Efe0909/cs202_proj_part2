# REST API Reference

Complete guide to all HTTP endpoints. Perfect for testing with curl, Postman, or understanding what the UI calls.

## Base URL

```
http://localhost:8080/api
```

## Table of Contents

- [Authentication](#authentication)
- [Restaurants](#restaurants)
- [Orders](#orders)
- [Coupons](#coupons)
- [Addresses](#addresses)
- [Phones](#phones)
- [Statistics](#statistics)
- [Error Responses](#error-responses)

---

## Authentication

### POST /auth/register

**What:** Create a new user account.

**Request:**
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "password": "SecurePassword123!",
  "role": "CUSTOMER"
}
```

**Response (200 OK):**
```json
{
  "userId": 5,
  "username": "john_doe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "role": "CUSTOMER",
  "createdAt": "2025-05-21T14:30:00"
}
```

**Error (400):**
```json
{
  "error": "Username already taken: john_doe"
}
```

**curl example:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "email": "john@example.com",
    "fullName": "John Doe",
    "password": "SecurePassword123!",
    "role": "CUSTOMER"
  }'
```

**Why it exists:** Customers and managers both need accounts. Password is hashed immediately (never stored plain).

---

### POST /auth/login

**What:** Authenticate and get user details.

**Request:**
```json
{
  "username": "john_doe",
  "password": "SecurePassword123!"
}
```

**Response (200 OK):**
```json
{
  "userId": 5,
  "username": "john_doe",
  "email": "john@example.com",
  "fullName": "John Doe",
  "role": "CUSTOMER",
  "selectedAddressId": 10
}
```

**Error (401):**
```json
{
  "error": "Invalid username or password"
}
```

**curl example:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "password": "SecurePassword123!"
  }'
```

**Why it exists:** No session tokens in this system. UI sends userId with every request. Stateless = simpler, scalable.

---

## Restaurants

### GET /restaurants

**What:** Browse all restaurants in a city, optionally filter by keyword.

**Query Parameters:**
- `city` (required): "Toronto", "Vancouver", etc.
- `keyword` (optional): Search in restaurant name + keywords

**Response (200 OK):**
```json
[
  {
    "restaurantId": 2,
    "managerId": 1,
    "name": "Pizza Palace",
    "cuisineType": "Italian",
    "address": "123 Main St",
    "city": "Toronto",
    "keywords": ["pizza", "vegetarian", "delivery"],
    "averageRating": 4.5,
    "ratingCount": 25
  },
  {
    "restaurantId": 3,
    "managerId": 1,
    "name": "Sushi Supreme",
    "cuisineType": "Japanese",
    "address": "456 Oak Ave",
    "city": "Toronto",
    "keywords": ["sushi", "fresh"],
    "averageRating": 0,
    "ratingCount": 5
  }
]
```

**Notes:**
- Sorted by rating (highest first), then name (A-Z)
- `averageRating` is 0 until restaurant has >= 10 ratings (spec requirement)
- Both parameters are LIKE queries (case-insensitive substring)

**curl examples:**
```bash
# Browse all restaurants in Toronto
curl "http://localhost:8080/api/restaurants?city=Toronto"

# Search for pizza in Toronto
curl "http://localhost:8080/api/restaurants?city=Toronto&keyword=pizza"

# Search for vegetarian options
curl "http://localhost:8080/api/restaurants?city=Toronto&keyword=vegetarian"
```

---

### GET /restaurants/{id}

**What:** Get a single restaurant with details.

**Response (200 OK):**
```json
{
  "restaurantId": 2,
  "managerId": 1,
  "name": "Pizza Palace",
  "cuisineType": "Italian",
  "address": "123 Main St",
  "city": "Toronto",
  "keywords": ["pizza", "vegetarian"],
  "averageRating": 4.5,
  "ratingCount": 25
}
```

**Error (404):**
```json
{
  "error": "Restaurant not found: 999"
}
```

**curl example:**
```bash
curl "http://localhost:8080/api/restaurants/2"
```

---

### POST /restaurants

**What:** Create a new restaurant (manager only).

**Request:**
```json
{
  "managerId": 1,
  "name": "New Pizzeria",
  "cuisineType": "Italian",
  "address": "789 Pine St",
  "city": "Toronto",
  "keywords": ["pizza", "vegetarian"]
}
```

**Response (200 OK):**
```json
{
  "restaurantId": 10,
  "managerId": 1,
  "name": "New Pizzeria",
  "cuisineType": "Italian",
  "address": "789 Pine St",
  "city": "Toronto",
  "keywords": ["pizza", "vegetarian"],
  "averageRating": 0,
  "ratingCount": 0
}
```

**curl example:**
```bash
curl -X POST http://localhost:8080/api/restaurants \
  -H "Content-Type: application/json" \
  -d '{
    "managerId": 1,
    "name": "New Pizzeria",
    "cuisineType": "Italian",
    "address": "789 Pine St",
    "city": "Toronto",
    "keywords": ["pizza", "vegetarian"]
  }'
```

---

### GET /restaurants/manager/{managerId}

**What:** Get all restaurants owned by a manager.

**Response (200 OK):**
```json
[
  {
    "restaurantId": 2,
    "managerId": 1,
    "name": "Pizza Palace",
    ...
  }
]
```

**curl example:**
```bash
curl "http://localhost:8080/api/restaurants/manager/1"
```

---

### GET /restaurants/{id}/ratings

**What:** Get all ratings for a restaurant (manager view).

**Response (200 OK):**
```json
[
  {
    "ratingId": 1,
    "restaurantId": 2,
    "userId": 5,
    "score": 5,
    "comment": "Amazing pizza!",
    "createdAt": "2025-05-21T14:30:00"
  },
  {
    "ratingId": 2,
    "restaurantId": 2,
    "userId": 6,
    "score": 4,
    "comment": "Good, but slow service",
    "createdAt": "2025-05-20T18:45:00"
  }
]
```

**curl example:**
```bash
curl "http://localhost:8080/api/restaurants/2/ratings"
```

---

## Orders

### POST /orders

**What:** Create a new order.

**Request:**
```json
{
  "userId": 5,
  "restaurantId": 2,
  "selectedAddressId": 10,
  "items": [
    {
      "menuItemId": 15,
      "quantity": 2,
      "unitPrice": 12.99
    },
    {
      "menuItemId": 18,
      "quantity": 1,
      "unitPrice": 5.99
    }
  ],
  "couponCode": "SAVE10"
}
```

**Response (200 OK):**
```json
{
  "orderId": 42,
  "userId": 5,
  "restaurantId": 2,
  "selectedAddressId": 10,
  "status": "PREPARING",
  "couponApplied": true,
  "totalPrice": 45.50,
  "createdAt": "2025-05-21T15:00:00",
  "preparingAt": null
}
```

**Errors (400):**
- "User not found"
- "Restaurant not found"
- "Please select a delivery address before ordering"
- "Coupon SAVE10 has expired"
- "Order must have at least 1 item"

**curl example:**
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 5,
    "restaurantId": 2,
    "selectedAddressId": 10,
    "items": [
      {"menuItemId": 15, "quantity": 2, "unitPrice": 12.99}
    ],
    "couponCode": "SAVE10"
  }'
```

---

### GET /orders/{id}

**What:** Get order details (customer can see, manager can see).

**Response (200 OK):**
```json
{
  "orderId": 42,
  "userId": 5,
  "restaurantId": 2,
  "selectedAddressId": 10,
  "status": "SENT",
  "couponApplied": true,
  "totalPrice": 45.50,
  "createdAt": "2025-05-21T15:00:00",
  "preparingAt": null
}
```

**curl example:**
```bash
curl "http://localhost:8080/api/orders/42"
```

---

### GET /orders/user/{userId}

**What:** Get all orders by a customer.

**Response (200 OK):**
```json
[
  {
    "orderId": 42,
    "status": "SENT",
    ...
  },
  {
    "orderId": 41,
    "status": "ARRIVED",
    ...
  }
]
```

**curl example:**
```bash
curl "http://localhost:8080/api/orders/user/5"
```

---

### PUT /orders/{id}/status

**What:** Update order status (manager only).

**Request:**
```json
{
  "managerId": 1,
  "status": "PREPARING"
}
```

**Response (200 OK):**
```json
{
  "orderId": 42,
  "status": "PREPARING",
  "preparingAt": "2025-05-21T15:15:00",
  ...
}
```

**Valid statuses:** `PREPARING` (SENT→PREPARING), `ARRIVED` (PREPARING→ARRIVED)

**curl example:**
```bash
curl -X PUT http://localhost:8080/api/orders/42/status \
  -H "Content-Type: application/json" \
  -d '{
    "managerId": 1,
    "status": "PREPARING"
  }'
```

---

## Coupons

### POST /coupons

**What:** Create a coupon (manager only).

**Request:**
```json
{
  "restaurantId": 2,
  "code": "SAVE10",
  "discountPercent": 10,
  "validFrom": "2025-05-21T00:00:00",
  "validTo": "2025-06-21T23:59:59",
  "managerId": 1
}
```

**Response (200 OK):**
```json
{
  "couponId": 5,
  "restaurantId": 2,
  "code": "SAVE10",
  "discountPercent": 10,
  "validFrom": "2025-05-21T00:00:00",
  "validTo": "2025-06-21T23:59:59",
  "isActive": true
}
```

**curl example:**
```bash
curl -X POST http://localhost:8080/api/coupons \
  -H "Content-Type: application/json" \
  -d '{
    "restaurantId": 2,
    "code": "SAVE10",
    "discountPercent": 10,
    "validFrom": "2025-05-21T00:00:00",
    "validTo": "2025-06-21T23:59:59",
    "managerId": 1
  }'
```

---

### GET /coupons

**What:** Get active coupons for a restaurant.

**Query Parameters:**
- `restaurantId`: Required

**Response (200 OK):**
```json
[
  {
    "couponId": 5,
    "code": "SAVE10",
    "discountPercent": 10,
    "validTo": "2025-06-21T23:59:59",
    "isActive": true
  }
]
```

**curl example:**
```bash
curl "http://localhost:8080/api/coupons?restaurantId=2"
```

---

## Addresses

### POST /addresses

**What:** Add a delivery address.

**Request:**
```json
{
  "userId": 5,
  "street": "123 Main St",
  "city": "Toronto",
  "province": "Ontario"
}
```

**Response (200 OK):**
```json
{
  "addressId": 10,
  "userId": 5,
  "street": "123 Main St",
  "city": "Toronto",
  "province": "Ontario"
}
```

**curl example:**
```bash
curl -X POST http://localhost:8080/api/addresses \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 5,
    "street": "123 Main St",
    "city": "Toronto",
    "province": "Ontario"
  }'
```

---

### GET /addresses/{userId}

**What:** Get all addresses for a user.

**Response (200 OK):**
```json
[
  {
    "addressId": 10,
    "userId": 5,
    "street": "123 Main St",
    "city": "Toronto",
    "province": "Ontario"
  },
  {
    "addressId": 11,
    "userId": 5,
    "street": "456 Oak Ave",
    "city": "Toronto",
    "province": "Ontario"
  }
]
```

**curl example:**
```bash
curl "http://localhost:8080/api/addresses/5"
```

---

## Phones

### POST /phones

**What:** Add a phone number.

**Request:**
```json
{
  "userId": 5,
  "phoneNumber": "+1 416 555 0123"
}
```

**Response (200 OK):**
```json
{
  "phoneId": 20,
  "userId": 5,
  "phoneNumber": "+1 416 555 0123"
}
```

**curl example:**
```bash
curl -X POST http://localhost:8080/api/phones \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 5,
    "phoneNumber": "+1 416 555 0123"
  }'
```

---

### GET /phones/{userId}

**What:** Get all phone numbers for a user.

**Response (200 OK):**
```json
[
  {
    "phoneId": 20,
    "userId": 5,
    "phoneNumber": "+1 416 555 0123"
  }
]
```

**curl example:**
```bash
curl "http://localhost:8080/api/phones/5"
```

---

## Statistics

### GET /statistics/manager/{managerId}/monthly

**What:** Get monthly sales metrics for all restaurants owned by manager.

**Query Parameters:**
- `year`: Required (e.g., 2025)
- `month`: Required (e.g., 5 for May)

**Response (200 OK):**
```json
{
  "month": "May 2025",
  "totalRevenue": 5423.50,
  "totalOrders": 42,
  "averageOrderValue": 129.13,
  "totalItemsSold": 156,
  "averageRating": 4.3,
  "statusBreakdown": {
    "PREPARING": 5,
    "SENT": 3,
    "ARRIVED": 34
  },
  "topCuisine": "Italian",
  "topSellingItem": "Margherita Pizza"
}
```

**curl example:**
```bash
curl "http://localhost:8080/api/statistics/manager/1/monthly?year=2025&month=5"
```

---

## Error Responses

### HTTP 400 Bad Request

Missing or invalid input.

```json
{
  "error": "Missing required field: userId"
}
```

### HTTP 401 Unauthorized

Authentication failed.

```json
{
  "error": "Invalid username or password"
}
```

### HTTP 404 Not Found

Resource doesn't exist.

```json
{
  "error": "Restaurant not found: 999"
}
```

### HTTP 500 Internal Server Error

Server error (database, unexpected exception).

```json
{
  "error": "Database error: connection lost"
}
```

---

## Testing Tips

### Use curl from command line:

```bash
# Simple GET
curl "http://localhost:8080/api/restaurants?city=Toronto"

# POST with JSON
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 5, ...}'

# Pretty-print JSON response
curl "http://localhost:8080/api/restaurants?city=Toronto" | jq '.'
```

### Or use Postman:

1. Set method (GET, POST, etc.)
2. Set URL: `http://localhost:8080/api/...`
3. Set body as JSON
4. Send

---

## See Also

- [**SERVICES.md**](SERVICES.md) — What each endpoint calls
- [**ARCHITECTURE.md**](ARCHITECTURE.md) — How endpoints fit in the system
