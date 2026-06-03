-- CS202 Spring 2026 - Online Food Ordering System
-- DDL: Data Definition Language
CREATE DATABASE IF NOT EXISTS food_ordering;

USE food_ordering;

-- Users table (covers both Customers and RestaurantManagers)
CREATE TABLE Users (
  user_id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(64) NOT NULL, -- PBKDF2-HMAC-SHA256 hex output
  salt VARCHAR(32) NOT NULL, -- 16-byte SecureRandom salt, hex encoded
  email VARCHAR(100) NOT NULL UNIQUE,
  full_name VARCHAR(100) NOT NULL,
  role ENUM('CUSTOMER', 'MANAGER') NOT NULL,
  -- city is intentionally NOT stored on Users; it lives on the user's
  -- selected UserAddress (3NF) — see selected_address_id below.
  -- Selected delivery address (1 user : N addresses; one is "selected").
  -- NULLable with ON DELETE SET NULL so the circular FK with UserAddress
  -- (UserAddress.user_id -> Users, Users.selected_address_id -> UserAddress)
  -- stays orderable for inserts and deletes. The FK itself is added via
  -- ALTER TABLE at the end of this file, after UserAddress exists.
  selected_address_id INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User addresses (1 user : N addresses). An address is just a city + province
-- (NOT a free-form street address). The customer picks one as their selected
-- delivery address (Users.selected_address_id), which drives restaurant
-- browsing and the order city-restriction guard.
CREATE TABLE UserAddress (
  address_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  city VARCHAR(100) NOT NULL,
  province VARCHAR(100) NOT NULL,
  FOREIGN KEY (user_id) REFERENCES Users (user_id) ON DELETE CASCADE
);

-- Circular FK: Users.selected_address_id -> UserAddress(address_id).
-- Declared after UserAddress exists to avoid circular CREATE TABLE dependency.
-- ON DELETE SET NULL: deleting a selected address automatically clears the pointer.
ALTER TABLE Users
    ADD CONSTRAINT fk_users_selected_address
    FOREIGN KEY (selected_address_id) REFERENCES UserAddress(address_id)
    ON DELETE SET NULL;

-- User phone numbers (multi-valued attribute)
CREATE TABLE UserPhone (
  phone_id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  phone VARCHAR(20) NOT NULL,
  FOREIGN KEY (user_id) REFERENCES Users (user_id) ON DELETE CASCADE
);

-- Restaurants
CREATE TABLE Restaurant (
    restaurant_id INT AUTO_INCREMENT PRIMARY KEY,
    manager_id    INT          NOT NULL,
    name          VARCHAR(100) NOT NULL,
    cuisine_type  VARCHAR(50)  NOT NULL,
    address       VARCHAR(255) NOT NULL,
    city          VARCHAR(100) NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- manager_id must reference a user whose role is 'MANAGER'. This rule is
    -- enforced in the service layer, because a SQL CHECK constraint cannot
    -- reference another table (it can only see columns of the same row).
    FOREIGN KEY (manager_id) REFERENCES Users(user_id) ON DELETE RESTRICT
);

-- Restaurant keywords (multi-valued, used for search)
CREATE TABLE RestaurantKeyword (
  keyword_id INT AUTO_INCREMENT PRIMARY KEY,
  restaurant_id INT NOT NULL,
  keyword VARCHAR(50) NOT NULL,
  FOREIGN KEY (restaurant_id) REFERENCES Restaurant (restaurant_id) ON DELETE CASCADE
);

-- Menu categories per restaurant
CREATE TABLE MenuCategory (
  category_id INT AUTO_INCREMENT PRIMARY KEY,
  restaurant_id INT NOT NULL,
  name VARCHAR(50) NOT NULL,
  FOREIGN KEY (restaurant_id) REFERENCES Restaurant (restaurant_id) ON DELETE CASCADE,
  -- Composite key so MenuItem can reference (restaurant_id, category_id) together,
  -- preventing a menu item from being assigned to a category owned by a different restaurant.
  UNIQUE KEY uk_cat_restaurant (restaurant_id, category_id)
);

-- Menu items
CREATE TABLE MenuItem (
  item_id INT AUTO_INCREMENT PRIMARY KEY,
  restaurant_id INT NOT NULL,
  category_id INT NOT NULL,
  name VARCHAR(100) NOT NULL,
  description TEXT NOT NULL,
  price DECIMAL(10, 2) NOT NULL CHECK (price > 0),
  image_path VARCHAR(255) NOT NULL,
  FOREIGN KEY (restaurant_id) REFERENCES Restaurant (restaurant_id) ON DELETE CASCADE,
  -- Composite FK ensures the category belongs to the same restaurant as the item.
  CONSTRAINT fk_item_category FOREIGN KEY (restaurant_id, category_id) REFERENCES MenuCategory (restaurant_id, category_id) ON DELETE RESTRICT
);

-- Coupons defined by restaurant managers
CREATE TABLE Coupon (
  coupon_id INT AUTO_INCREMENT PRIMARY KEY,
  restaurant_id INT NOT NULL,
  code VARCHAR(30) NOT NULL UNIQUE,
  discount_type ENUM('PERCENTAGE', 'AMOUNT') NOT NULL,
  discount_value DECIMAL(10, 2) NOT NULL CHECK (discount_value > 0),
  valid_from DATE NOT NULL,
  valid_until DATE NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  FOREIGN KEY (restaurant_id) REFERENCES Restaurant (restaurant_id) ON DELETE CASCADE,
  CONSTRAINT chk_coupon_dates CHECK (valid_until >= valid_from)
);

-- Orders placed by customers
CREATE TABLE `Order` (
  order_id INT AUTO_INCREMENT PRIMARY KEY,
  customer_id INT NOT NULL,
  restaurant_id INT NOT NULL,
  coupon_id INT,
  status ENUM('SENT', 'PREPARING', 'ARRIVED') NOT NULL DEFAULT 'SENT',
  total_price DECIMAL(10, 2) NOT NULL CHECK (total_price >= 0),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  preparing_at DATETIME NULL,
  arrived_at DATETIME NULL,
  FOREIGN KEY (customer_id) REFERENCES Users (user_id) ON DELETE RESTRICT,
  FOREIGN KEY (restaurant_id) REFERENCES Restaurant (restaurant_id) ON DELETE RESTRICT,
  FOREIGN KEY (coupon_id) REFERENCES Coupon (coupon_id) ON DELETE SET NULL
);

-- Items within an order
CREATE TABLE OrderItem (
  order_item_id INT AUTO_INCREMENT PRIMARY KEY,
  order_id INT NOT NULL,
  item_id INT NOT NULL,
  quantity INT NOT NULL CHECK (quantity > 0),
  unit_price DECIMAL(10, 2) NOT NULL CHECK (unit_price > 0),
  FOREIGN KEY (order_id) REFERENCES `Order` (order_id) ON DELETE CASCADE,
  FOREIGN KEY (item_id) REFERENCES MenuItem (item_id) ON DELETE RESTRICT
);

-- Ratings left by customers after order arrived
CREATE TABLE Rating (
  rating_id INT AUTO_INCREMENT PRIMARY KEY,
  customer_id INT NOT NULL,
  restaurant_id INT NOT NULL,
  order_id INT NOT NULL UNIQUE,
  score INT NOT NULL CHECK (score BETWEEN 1 AND 5),
  comment TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (customer_id) REFERENCES Users (user_id) ON DELETE CASCADE,
  FOREIGN KEY (restaurant_id) REFERENCES Restaurant (restaurant_id) ON DELETE CASCADE,
  FOREIGN KEY (order_id) REFERENCES `Order` (order_id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_restaurant_city ON Restaurant (city);

CREATE INDEX idx_order_customer ON `Order` (customer_id);

CREATE INDEX idx_order_restaurant ON `Order` (restaurant_id);

CREATE INDEX idx_order_status ON `Order` (status);

CREATE INDEX idx_orderitem_order ON OrderItem (order_id);

CREATE INDEX idx_rating_restaurant ON Rating (restaurant_id);

CREATE INDEX idx_menuitem_restaurant ON MenuItem (restaurant_id);

CREATE INDEX idx_keyword_restaurant ON RestaurantKeyword (restaurant_id);

