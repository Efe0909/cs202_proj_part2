-- CouponDAO.java:22  findByCode
-- Fetch a single coupon by its code string.
SELECT * FROM Coupon WHERE code = ?;

-- CouponDAO.java:38  findById
-- Fetch a single coupon by its primary key.
SELECT * FROM Coupon WHERE coupon_id = ?;

-- CouponDAO.java:54  findByRestaurant
-- List all coupons belonging to a restaurant.
SELECT * FROM Coupon WHERE restaurant_id = ?;

-- CouponDAO.java:71  insert
-- Create a new coupon row and return the generated id.
INSERT INTO Coupon (restaurant_id, code, discount_type, discount_value, valid_from, valid_until, is_active) VALUES (?, ?, ?, ?, ?, ?, ?);

-- CouponDAO.java:93  tryDeactivate
-- Atomically claim a coupon: sets is_active = FALSE only if currently TRUE. Returns 0 rows if already claimed.
UPDATE Coupon SET is_active = FALSE WHERE coupon_id = ? AND is_active = TRUE;

-- MenuCategoryDAO.java:21  findByRestaurant
-- List all menu categories for a restaurant.
SELECT * FROM MenuCategory WHERE restaurant_id = ?;

-- MenuCategoryDAO.java:36  findById
-- Fetch a single menu category by its primary key.
SELECT * FROM MenuCategory WHERE category_id = ?;

-- MenuCategoryDAO.java:50  insert
-- Create a new menu category and return the generated id.
INSERT INTO MenuCategory (restaurant_id, name) VALUES (?, ?);

-- MenuCategoryDAO.java:66  insertIfOwner
-- Insert a category only if the caller is the restaurant's manager (ownership guard via subselect).
INSERT INTO MenuCategory (restaurant_id, name)
    SELECT ?, ? FROM Restaurant WHERE restaurant_id = ? AND manager_id = ?;

-- MenuCategoryDAO.java:81  update
-- Rename an existing menu category.
UPDATE MenuCategory SET name=? WHERE category_id=?;

-- MenuCategoryDAO.java:93  updateIfOwner
-- Rename a category only if the caller owns the restaurant it belongs to.
UPDATE MenuCategory SET name=? WHERE category_id=?
    AND restaurant_id IN (SELECT restaurant_id FROM Restaurant WHERE manager_id = ?);

-- MenuCategoryDAO.java:107  delete
-- Delete a menu category unconditionally.
DELETE FROM MenuCategory WHERE category_id = ?;

-- MenuCategoryDAO.java:118  deleteIfOwner
-- Delete a category only if the caller owns the restaurant it belongs to.
DELETE FROM MenuCategory WHERE category_id = ?
    AND restaurant_id IN (SELECT restaurant_id FROM Restaurant WHERE manager_id = ?);

-- MenuItemDAO.java:22  findByRestaurant
-- List all menu items for a restaurant.
SELECT * FROM MenuItem WHERE restaurant_id = ?;

-- MenuItemDAO.java:27  findByCategory
-- List all menu items under a specific category.
SELECT * FROM MenuItem WHERE category_id = ?;

-- MenuItemDAO.java:32  findById
-- Fetch a single menu item by its primary key.
SELECT * FROM MenuItem WHERE item_id = ?;

-- MenuItemDAO.java:50  insert
-- Create a new menu item and return the generated id.
INSERT INTO MenuItem (restaurant_id, category_id, name, description, price, image_path) VALUES (?, ?, ?, ?, ?, ?);

-- MenuItemDAO.java:73  update
-- Update all mutable fields of a menu item.
UPDATE MenuItem SET category_id=?, name=?, description=?, price=?, image_path=? WHERE item_id=?;

-- MenuItemDAO.java:91  updateIfOwner
-- Update a menu item only if the caller owns the restaurant it belongs to.
UPDATE MenuItem SET category_id=?, name=?, description=?, price=?, image_path=?
    WHERE item_id=?
    AND restaurant_id IN (SELECT restaurant_id FROM Restaurant WHERE manager_id = ?);

-- MenuItemDAO.java:112  delete
-- Delete a menu item unconditionally.
DELETE FROM MenuItem WHERE item_id = ?;

-- MenuItemDAO.java:125  deleteIfOwner
-- Delete a menu item only if the caller owns the restaurant it belongs to.
DELETE FROM MenuItem WHERE item_id = ?
    AND restaurant_id IN (SELECT restaurant_id FROM Restaurant WHERE manager_id = ?);

-- OrderDAO.java:22  insert
-- Create a new order row and return the generated id.
INSERT INTO `Order` (customer_id, restaurant_id, coupon_id, status, total_price) VALUES (?, ?, ?, ?, ?);

-- OrderDAO.java:44  insertItem
-- Attach a line item to an existing order.
INSERT INTO OrderItem (order_id, item_id, quantity, unit_price) VALUES (?, ?, ?, ?);

-- OrderDAO.java:64  markPreparing
-- Transition an order from SENT to PREPARING and stamp the timestamp (guarded, 0 rows if wrong state).
UPDATE `Order` SET status='PREPARING', preparing_at=NOW()
    WHERE order_id=? AND status='SENT';

-- OrderDAO.java:82  markArrived
-- Transition an order from PREPARING to ARRIVED and stamp the timestamp (guarded, 0 rows if wrong state).
UPDATE `Order` SET status='ARRIVED', arrived_at=NOW()
    WHERE order_id=? AND status='PREPARING';

-- OrderDAO.java:96  findById
-- Fetch a single order by its primary key.
SELECT * FROM `Order` WHERE order_id = ?;

-- OrderDAO.java:112  findByCustomer
-- List all orders placed by a customer, newest first.
SELECT * FROM `Order` WHERE customer_id = ? ORDER BY created_at DESC;

-- OrderDAO.java:117  findByCustomerAndStatus
-- List a customer's orders filtered to a specific status, newest first.
SELECT * FROM `Order` WHERE customer_id = ? AND status = ? ORDER BY created_at DESC;

-- OrderDAO.java:135  findByRestaurant
-- List all orders for a restaurant, newest first.
SELECT * FROM `Order` WHERE restaurant_id = ? ORDER BY created_at DESC;

-- OrderDAO.java:140  findPendingByRestaurant
-- List active (SENT or PREPARING) orders for a restaurant, oldest first (queue order).
SELECT * FROM `Order` WHERE restaurant_id = ? AND status IN ('SENT','PREPARING') ORDER BY created_at ASC;

-- OrderDAO.java:145  findItemsByOrder
-- Fetch all line items for an order joined with menu item names.
SELECT oi.*, mi.name AS item_name FROM OrderItem oi
    JOIN MenuItem mi ON oi.item_id = mi.item_id
    WHERE oi.order_id = ?;

-- RatingDAO.java:21  insert
-- Store a customer's rating and comment for a delivered order.
INSERT INTO Rating (customer_id, restaurant_id, order_id, score, comment) VALUES (?, ?, ?, ?, ?);

-- RatingDAO.java:38  existsForOrder
-- Check whether a rating has already been submitted for a given order.
SELECT COUNT(*) FROM Rating WHERE order_id = ?;

-- RatingDAO.java:57  findByRestaurant
-- List all ratings for a restaurant, newest first with rating_id as tiebreaker.
SELECT * FROM Rating WHERE restaurant_id = ? ORDER BY created_at DESC, rating_id DESC;

-- RestaurantDAO.java:21  findByCity
-- List all restaurants in a city with computed avg_rating (suppressed below 10 reviews), sorted by rating then name.
SELECT r.*,
    CASE WHEN COUNT(rt.rating_id) >= 10 THEN AVG(rt.score) ELSE 0 END AS avg_rating,
    COUNT(rt.rating_id) AS rating_count
    FROM Restaurant r
    LEFT JOIN Rating rt ON r.restaurant_id = rt.restaurant_id
    WHERE r.city = ?
    GROUP BY r.restaurant_id
    ORDER BY CASE WHEN COUNT(rt.rating_id) >= 10 THEN AVG(rt.score) ELSE 0 END DESC, r.name ASC;

-- RestaurantDAO.java:33  searchByKeywordAndCity
-- Search restaurants by keyword match in a city, ranked by keyword hit count then rating.
SELECT r.*,
    CASE WHEN COUNT(DISTINCT rt.rating_id) >= 10 THEN AVG(rt.score) ELSE 0 END AS avg_rating,
    COUNT(DISTINCT rt.rating_id) AS rating_count
    FROM Restaurant r
    JOIN RestaurantKeyword rk ON r.restaurant_id = rk.restaurant_id
    LEFT JOIN Rating rt ON r.restaurant_id = rt.restaurant_id
    WHERE rk.keyword LIKE ? AND r.city = ?
    GROUP BY r.restaurant_id
    ORDER BY COUNT(DISTINCT rk.keyword) DESC,
             CASE WHEN COUNT(DISTINCT rt.rating_id) >= 10 THEN AVG(rt.score) ELSE 0 END DESC,
             r.name ASC;

-- RestaurantDAO.java:57  findById
-- Fetch a single restaurant with its computed rating stats.
SELECT r.*,
    CASE WHEN COUNT(rt.rating_id) >= 10 THEN AVG(rt.score) ELSE 0 END AS avg_rating,
    COUNT(rt.rating_id) AS rating_count
    FROM Restaurant r
    LEFT JOIN Rating rt ON r.restaurant_id = rt.restaurant_id
    WHERE r.restaurant_id = ?
    GROUP BY r.restaurant_id;

-- RestaurantDAO.java:77  findByManagerId
-- List all restaurants owned by a manager with their computed rating stats.
SELECT r.*,
    CASE WHEN COUNT(rt.rating_id) >= 10 THEN AVG(rt.score) ELSE 0 END AS avg_rating,
    COUNT(rt.rating_id) AS rating_count
    FROM Restaurant r
    LEFT JOIN Rating rt ON r.restaurant_id = rt.restaurant_id
    WHERE r.manager_id = ?
    GROUP BY r.restaurant_id;

-- RestaurantDAO.java:88  insert
-- Create a new restaurant row and return the generated id.
INSERT INTO Restaurant (manager_id, name, cuisine_type, address, city) VALUES (?, ?, ?, ?, ?);

-- RestaurantDAO.java:107  update
-- Update a restaurant's profile fields, guarded so only its manager can do so.
UPDATE Restaurant SET name=?, cuisine_type=?, address=?, city=? WHERE restaurant_id=? AND manager_id=?;

-- RestaurantDAO.java:123  existsOwnedBy
-- Verify that a restaurant exists and belongs to a specific manager.
SELECT COUNT(*) FROM Restaurant WHERE restaurant_id=? AND manager_id=?;

-- RestaurantDAO.java:137  addKeyword
-- Associate a search keyword with a restaurant.
INSERT INTO RestaurantKeyword (restaurant_id, keyword) VALUES (?, ?);

-- RestaurantDAO.java:149  deleteKeywords
-- Remove all search keywords for a restaurant (used before re-inserting updated set).
DELETE FROM RestaurantKeyword WHERE restaurant_id = ?;

-- RestaurantDAO.java:160  findKeywords
-- Fetch all search keywords registered for a restaurant.
SELECT keyword FROM RestaurantKeyword WHERE restaurant_id = ?;

-- UserDAO.java:21  findByUsername
-- Fetch a user record by username for login lookup.
SELECT * FROM Users WHERE username = ?;

-- UserDAO.java:36  findSaltByUsername
-- Fetch only the password salt for a username (used during authentication before verifying the hash).
SELECT salt FROM Users WHERE username = ?;

-- UserDAO.java:50  findById
-- Fetch a user record by primary key.
SELECT * FROM Users WHERE user_id = ?;

-- UserDAO.java:65  insert
-- Register a new user with a pre-hashed password and its salt.
INSERT INTO Users (username, password, salt, email, full_name, role) VALUES (?, ?, ?, ?, ?, ?);

-- UserDAO.java:85  usernameExists
-- Check uniqueness of a username during registration.
SELECT COUNT(*) FROM Users WHERE username = ?;

-- UserDAO.java:99  emailExists
-- Check uniqueness of an email during registration.
SELECT COUNT(*) FROM Users WHERE email = ?;

-- UserDAO.java:116  findAddressesByUserId
-- Fetch all addresses for a user with a flag indicating which one is currently selected.
SELECT a.address_id, a.user_id, a.city, a.province,
       (u.selected_address_id = a.address_id) AS is_selected
    FROM UserAddress a
    JOIN Users u ON u.user_id = a.user_id
    WHERE a.user_id = ? ORDER BY a.address_id;

-- UserDAO.java:145  addAddress
-- Insert a new delivery address for a user and return the generated id.
INSERT INTO UserAddress (user_id, city, province) VALUES (?, ?, ?);

-- UserDAO.java:163  addressBelongsToUser
-- Verify that an address exists and belongs to a specific user.
SELECT 1 FROM UserAddress WHERE address_id = ? AND user_id = ?;

-- UserDAO.java:180  deleteAddress
-- Delete a user's address; the FK ON DELETE SET NULL clears it as selected if it was chosen.
DELETE FROM UserAddress WHERE address_id = ?;

-- UserDAO.java:192  findSelectedAddressId
-- Read the user's currently selected address id.
SELECT selected_address_id FROM Users WHERE user_id = ?;

-- UserDAO.java:211  findSelectedAddressCity
-- Read the city of the user's selected delivery address via a join.
SELECT a.city FROM Users u
    JOIN UserAddress a ON a.address_id = u.selected_address_id
    WHERE u.user_id = ?;

-- UserDAO.java:230  setSelectedAddress
-- Point the user's selected delivery address to a new address id.
UPDATE Users SET selected_address_id = ? WHERE user_id = ?;

-- UserDAO.java:244  findPhonesByUserId
-- Fetch all phone numbers for a user ordered by insertion id.
SELECT phone_id, user_id, phone FROM UserPhone WHERE user_id = ? ORDER BY phone_id;

-- UserDAO.java:265  addPhone
-- Add a new phone number for a user and return the generated id.
INSERT INTO UserPhone (user_id, phone) VALUES (?, ?);

-- UserDAO.java:281  phoneBelongsToUser
-- Verify that a phone number exists and belongs to a specific user.
SELECT 1 FROM UserPhone WHERE phone_id = ? AND user_id = ?;

-- UserDAO.java:295  deletePhone
-- Delete a user's phone number by its primary key.
DELETE FROM UserPhone WHERE phone_id = ?;

-- UserDAO.java  isManager
-- Check whether a user exists and holds the MANAGER role in a single query.
SELECT COUNT(*) FROM Users WHERE user_id = ? AND role = 'MANAGER';

-- StatisticsService.java:79  getTotalRevenue
-- Sum post-coupon order totals for a restaurant in the current calendar month.
SELECT COALESCE(SUM(total_price), 0) FROM `Order`
    WHERE restaurant_id = ? AND status = 'ARRIVED'
    AND YEAR(created_at) = YEAR(CURDATE()) AND MONTH(created_at) = MONTH(CURDATE());

-- StatisticsService.java:86  getTotalOrders
-- Count delivered orders for a restaurant in the current calendar month.
SELECT COUNT(*) FROM `Order`
    WHERE restaurant_id = ? AND status = 'ARRIVED'
    AND YEAR(created_at) = YEAR(CURDATE()) AND MONTH(created_at) = MONTH(CURDATE());

-- StatisticsService.java:95  getItemRevenue
-- Aggregate quantity sold and revenue per menu item for a restaurant this month, highest revenue first.
SELECT mi.name, SUM(oi.quantity) AS total_qty, SUM(oi.quantity * oi.unit_price) AS total_revenue
    FROM OrderItem oi
    JOIN MenuItem mi ON oi.item_id = mi.item_id
    JOIN `Order` o ON oi.order_id = o.order_id
    WHERE o.restaurant_id = ? AND o.status = 'ARRIVED'
    AND YEAR(o.created_at) = YEAR(CURDATE()) AND MONTH(o.created_at) = MONTH(CURDATE())
    GROUP BY mi.item_id, mi.name ORDER BY total_revenue DESC;

-- StatisticsService.java:126  getTopCustomerByOrders
-- Find the customer who placed the most orders at this restaurant this month.
SELECT u.user_id, u.full_name, COUNT(*) AS order_count
    FROM `Order` o JOIN Users u ON o.customer_id = u.user_id
    WHERE o.restaurant_id = ? AND o.status = 'ARRIVED'
    AND YEAR(o.created_at) = YEAR(CURDATE()) AND MONTH(o.created_at) = MONTH(CURDATE())
    GROUP BY o.customer_id, u.user_id, u.full_name
    ORDER BY order_count DESC, u.user_id ASC LIMIT 1;

-- StatisticsService.java:155  getTopCustomerByValue
-- Find the customer with the single highest-value order at this restaurant this month.
SELECT u.user_id, u.full_name, o.order_id, o.total_price, o.created_at
    FROM `Order` o JOIN Users u ON o.customer_id = u.user_id
    WHERE o.restaurant_id = ? AND o.status = 'ARRIVED'
    AND YEAR(o.created_at) = YEAR(CURDATE()) AND MONTH(o.created_at) = MONTH(CURDATE())
    ORDER BY o.total_price DESC, o.order_id ASC LIMIT 1;

-- StatisticsService.java:186  getOrderItems
-- Fetch line items of a specific order joined with menu item names, used for the top-value order detail.
SELECT mi.name, oi.quantity, oi.unit_price
    FROM OrderItem oi JOIN MenuItem mi ON oi.item_id = mi.item_id
    WHERE oi.order_id = ? ORDER BY oi.order_item_id ASC;

-- StatisticsService.java:213  getMostOrderedItem
-- Find the menu item with the highest total quantity sold at this restaurant this month.
SELECT mi.name, SUM(oi.quantity) AS total_qty
    FROM OrderItem oi
    JOIN MenuItem mi ON oi.item_id = mi.item_id
    JOIN `Order` o ON oi.order_id = o.order_id
    WHERE o.restaurant_id = ? AND o.status = 'ARRIVED'
    AND YEAR(o.created_at) = YEAR(CURDATE()) AND MONTH(o.created_at) = MONTH(CURDATE())
    GROUP BY mi.item_id, mi.name ORDER BY total_qty DESC, mi.item_id ASC LIMIT 1;

-- StatisticsService.java:226  getTopRevenueCategory
-- Find the menu category that generated the most revenue at this restaurant this month.
SELECT mc.name, SUM(oi.quantity * oi.unit_price) AS cat_revenue
    FROM OrderItem oi
    JOIN MenuItem mi ON oi.item_id = mi.item_id
    JOIN MenuCategory mc ON mi.category_id = mc.category_id
    JOIN `Order` o ON oi.order_id = o.order_id
    WHERE o.restaurant_id = ? AND o.status = 'ARRIVED'
    AND YEAR(o.created_at) = YEAR(CURDATE()) AND MONTH(o.created_at) = MONTH(CURDATE())
    GROUP BY mc.category_id, mc.name ORDER BY cat_revenue DESC, mc.category_id ASC LIMIT 1;

-- StatisticsService.java:246  getTotalDiscount
-- Sum coupon savings this month: difference between raw item totals and post-coupon order totals.
SELECT COALESCE(SUM(item_sum - o.total_price), 0)
    FROM `Order` o
    JOIN (SELECT order_id, SUM(quantity * unit_price) AS item_sum
          FROM OrderItem GROUP BY order_id) s ON s.order_id = o.order_id
    WHERE o.restaurant_id = ? AND o.status = 'ARRIVED'
    AND o.coupon_id IS NOT NULL
    AND YEAR(o.created_at) = YEAR(CURDATE()) AND MONTH(o.created_at) = MONTH(CURDATE());
