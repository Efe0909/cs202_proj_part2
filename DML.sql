-- CS202 Spring 2026 - Online Food Ordering System
-- DML: Data Modification Language (Sample Data)

USE food_ordering;

-- ── Users ─────────────────────────────────────────────────────────────────────
-- Passwords are PBKDF2-HMAC-SHA256 (100k iterations) over the salt and a known
-- demo plaintext. Login with username + the documented password works directly
-- against UserService.login. Demo credentials:
--   manager_ali     / ali2026     manager_ayse    / ayse2026
--   manager_mehmet  / mehmet2026  customer_ece    / ece2026
--   customer_berk   / berk2026    customer_selin  / selin2026
--   customer_can    / can2026     customer_pinar  / pinar2026
-- 3 Restaurant Managers (city lives on UserAddress, not on Users)
INSERT INTO Users (username, password, salt, email, full_name, role) VALUES
('manager_ali',    '9e86830d0b09b0a4586973d60d339563a54df412b7f869c88d2bc0a47be51a2e', '85772b59471b1113bc47e2aae7ee6b77', 'ali@example.com',    'Ali Yilmaz',   'MANAGER'),
('manager_ayse',   '1bdb6feaac62b3bf1c3d5356b249ff3c641bb2de1fa4de7966ff9752b03863f7', 'dec1cf7dc5d446ce6e5ed54ff8d4a3fa', 'ayse@example.com',   'Ayse Kaya',    'MANAGER'),
('manager_mehmet', '47c5f34ee08d22318e44340442de2b5a7ac10a8304f3c0eb2ed0dd4ceaea3771', '7a21764844a2897e73ce4c1c6e54446e', 'mehmet@example.com', 'Mehmet Demir', 'MANAGER');

-- 5 Customers
INSERT INTO Users (username, password, salt, email, full_name, role) VALUES
('customer_ece',   '23b8f1159913be2deabc66740220f423efb1ff48e76c1b2eedc3a2b3dae07372', 'af5e11c54ed9a9dab6f52f9f545953fd', 'ece@example.com',   'Ece Sahin',    'CUSTOMER'),
('customer_berk',  'bab58ba6a059c5e0aa6a9ead91f14a4274f745c8734941821ffd5519cd9cb832', '147186bda6fde7245f57c793390735a3', 'berk@example.com',  'Berk Ozturk',  'CUSTOMER'),
('customer_selin', 'e677804732c29a8687c0779506a377c4880115b644087cf1aad7329098ae320d', '15f6d1d6f060733c8bf566cfd1ea2930', 'selin@example.com', 'Selin Arslan', 'CUSTOMER'),
('customer_can',   '2ba08f45f7027c2d34295cd13b017038514cbe92864acf42239024051156ec53', '1bb6286310248a4c9259e4209eb5ad62', 'can@example.com',   'Can Celik',    'CUSTOMER'),
('customer_pinar', '77a5c4b6305795d96c213b920195eb498064e2d796cf02892d83770c00bf3d30', '704df19a168bd86025e06f5034630d85', 'pinar@example.com', 'Pinar Yildiz', 'CUSTOMER');

-- User addresses (city + province; 1 customer : N addresses). Insert ORDER
-- matters for the circular FK: Users rows exist (above), UserAddress rows are
-- inserted here, then Users.selected_address_id is set by the UPDATE below.
-- Customers (user_id 4-8) each get >=1 address; ece(4) and can(7) get TWO
-- addresses in DIFFERENT cities to exercise 1:N + selected-address switching.
-- Cities are Istanbul / Ankara so the city rule keeps matching + non-matching
-- cases against the seeded restaurants. Managers (1-3) need no addresses.
--   address_id 1 -> user 4 ece    Istanbul  (selected)
--   address_id 2 -> user 4 ece    Ankara    (second address, 1:N)
--   address_id 3 -> user 5 berk   Istanbul  (selected)
--   address_id 4 -> user 6 selin  Ankara    (selected)
--   address_id 5 -> user 7 can    Istanbul  (selected)
--   address_id 6 -> user 7 can    Ankara    (second address, 1:N)
--   address_id 7 -> user 8 pinar  Ankara    (selected)
INSERT INTO UserAddress (user_id, city, province) VALUES
(4, 'Istanbul', 'Istanbul'),   -- address_id 1
(4, 'Ankara',   'Ankara'),     -- address_id 2
(5, 'Istanbul', 'Istanbul'),   -- address_id 3
(6, 'Ankara',   'Ankara'),     -- address_id 4
(7, 'Istanbul', 'Istanbul'),   -- address_id 5
(7, 'Ankara',   'Ankara'),     -- address_id 6
(8, 'Ankara',   'Ankara');     -- address_id 7

-- Selected delivery address per customer (must be one of that user's own
-- addresses). ece -> Istanbul(1), berk -> Istanbul(3), selin -> Ankara(4),
-- can -> Istanbul(5), pinar -> Ankara(7). These match each customer's seeded
-- orders' restaurant cities so the It.3 city rule still holds.
UPDATE Users SET selected_address_id = 1 WHERE user_id = 4;
UPDATE Users SET selected_address_id = 3 WHERE user_id = 5;
UPDATE Users SET selected_address_id = 4 WHERE user_id = 6;
UPDATE Users SET selected_address_id = 5 WHERE user_id = 7;
UPDATE Users SET selected_address_id = 7 WHERE user_id = 8;

-- User phones
INSERT INTO UserPhone (user_id, phone) VALUES
(4, '+90 532 111 2233'),
(5, '+90 533 222 3344'),
(6, '+90 534 333 4455'),
(7, '+90 535 444 5566'),
(8, '+90 536 555 6677');

-- ── Restaurants ───────────────────────────────────────────────────────────────
-- 5 Restaurants (managers are user_id 1, 2, 3)
INSERT INTO Restaurant (manager_id, name, cuisine_type, address, city) VALUES
(1, 'Bosphorus Kebab',   'Turkish',   'Taksim Meydani No:5, Beyoglu',   'Istanbul'),
(1, 'Sushi Garden',      'Japanese',  'Bagdat Cad. No:120, Kadikoy',    'Istanbul'),
(2, 'Pasta Bella',       'Italian',   'Kizilay Mah. No:8, Cankaya',     'Ankara'),
(3, 'Burger Station',    'American',  'Mecidiyekoy Mah. No:45, Sisli',  'Istanbul'),
(2, 'Green Bowl',        'Healthy',   'Bahcelievler Mah. No:3, Cankaya','Ankara');

-- Restaurant keywords
INSERT INTO RestaurantKeyword (restaurant_id, keyword) VALUES
(1, 'kebab'), (1, 'turkish'), (1, 'grilled'), (1, 'meat'),
(2, 'sushi'), (2, 'japanese'), (2, 'seafood'), (2, 'roll'),
(3, 'pasta'), (3, 'italian'), (3, 'pizza'), (3, 'risotto'),
(4, 'burger'), (4, 'american'), (4, 'fast food'), (4, 'fries'),
(5, 'salad'), (5, 'healthy'), (5, 'vegan'), (5, 'bowl');

-- ── Menu Categories ───────────────────────────────────────────────────────────
-- 7 categories: each restaurant that has items now owns its own categories.
INSERT INTO MenuCategory (restaurant_id, name) VALUES
(1, 'Starters'),      -- cat 1
(1, 'Main Dishes'),   -- cat 2
(2, 'Rolls'),         -- cat 3
(4, 'Burgers'),       -- cat 4
(4, 'Sides'),         -- cat 5
(3, 'Antipasti'),     -- cat 6  (Pasta Bella — was missing, causing items to borrow r1's cats)
(3, 'Pasta & Pizza'), -- cat 7  (Pasta Bella)
(5, 'Bowls'),         -- cat 8  (Green Bowl)
(5, 'Drinks');        -- cat 9  (Green Bowl)

-- ── Menu Items ────────────────────────────────────────────────────────────────
-- 15 menu items across restaurants
INSERT INTO MenuItem (restaurant_id, category_id, name, description, price, image_path) VALUES
-- Bosphorus Kebab
(1, 1, 'Mercimek Corbasi',   'Classic red lentil soup with lemon',          45.00,  'img/lentil.jpg'),
(1, 1, 'Acili Ezme',         'Spicy tomato and pepper dip with bread',      35.00,  'img/ezme.jpg'),
(1, 2, 'Adana Kebab',        'Spicy minced lamb kebab with rice pilaf',    120.00,  'img/adana.jpg'),
(1, 2, 'Iskender Kebab',     'Sliced doner over bread with tomato sauce',  130.00,  'img/iskender.jpg'),
(1, 2, 'Kofte Platter',      'Grilled meatballs with salad and bread',     100.00,  'img/kofte.jpg'),
-- Sushi Garden
(2, 3, 'Salmon Nigiri (6pc)','Fresh salmon on seasoned rice',               95.00,  'img/salmon_nig.jpg'),
(2, 3, 'Spicy Tuna Roll',    '8-piece spicy tuna and cucumber roll',        85.00,  'img/spicy_tuna.jpg'),
(2, 3, 'Vegetable Tempura',  'Seasonal vegetables in light tempura batter', 70.00,  'img/veg_temp.jpg'),
-- Pasta Bella — category_ids now reference Pasta Bella's own categories (6, 7)
(3, 6, 'Bruschetta',         'Toasted bread with tomato, basil, garlic',   55.00,  'img/bruschetta.jpg'),
(3, 7, 'Carbonara',          'Spaghetti with egg, pancetta, pecorino',     110.00,  'img/carbonara.jpg'),
(3, 7, 'Margherita Pizza',   '30cm pizza with mozzarella and basil',       115.00,  'img/pizza.jpg'),
-- Burger Station
(4, 4, 'Classic Cheeseburger','Beef patty, cheddar, lettuce, tomato',       90.00,  'img/cheese.jpg'),
(4, 4, 'BBQ Bacon Burger',   'Beef patty, BBQ sauce, crispy bacon',        105.00,  'img/bbq.jpg'),
(4, 5, 'Onion Rings (8pc)',  'Crispy beer-battered onion rings',            45.00,  'img/onion.jpg'),
(4, 5, 'Sweet Potato Fries', 'Seasoned sweet potato fries with dip',       50.00,  'img/sp_fries.jpg'),
-- Green Bowl (Ankara) — was fully empty; customers could see it but not order
(5, 8, 'Quinoa Power Bowl',  'Quinoa, roasted veggies, tahini dressing',   85.00,  'img/quinoa.jpg'),
(5, 8, 'Vegan Caesar Salad', 'Romaine, croutons, cashew caesar dressing',  70.00,  'img/caesar.jpg'),
(5, 9, 'Green Detox Juice',  'Spinach, cucumber, apple, ginger, lemon',    40.00,  'img/detox.jpg');

-- ── Coupons ───────────────────────────────────────────────────────────────────
INSERT INTO Coupon (restaurant_id, code, discount_type, discount_value, valid_from, valid_until) VALUES
(1, 'KEBAB10',  'PERCENTAGE', 10.00, '2026-05-01', '2026-06-30'),
(2, 'SUSHI20',  'AMOUNT',     20.00, '2026-05-01', '2026-06-30'),
(3, 'PASTA15',  'PERCENTAGE', 15.00, '2026-04-01', '2026-05-20'),
(4, 'BURGER5',  'AMOUNT',      5.00, '2026-05-10', '2026-06-10'),
(5, 'BOWL25',   'PERCENTAGE', 25.00, '2026-05-01', '2026-06-30');

-- ── Orders ────────────────────────────────────────────────────────────────────
-- 12 orders. Every order's items belong to that order's restaurant, and every
-- customer orders only from a restaurant in their own city (city rule):
--   Istanbul customers: 4 ece, 5 berk, 7 can  -> restaurants 1 (Kebab), 2 (Sushi), 4 (Burger)
--   Ankara   customers: 6 selin, 8 pinar      -> restaurant 3 (Pasta)
-- total_price is the post-coupon total and exactly equals SUM(quantity*unit_price)
-- after the coupon math. 12 are ARRIVED (rateable), 2 SENT — the SENT orders
-- exist to demonstrate the sent -> preparing -> arrived lifecycle.
INSERT INTO `Order` (customer_id, restaurant_id, coupon_id, status, total_price, created_at, preparing_at, arrived_at) VALUES
(4, 1, 1,    'ARRIVED',   180.00, '2026-05-02 12:30:00', '2026-05-02 12:48:00', '2026-05-02 13:08:00'),  -- O1  ece @ kebab,  items 200.00, KEBAB10 -10%
(4, 2, NULL, 'ARRIVED',   180.00, '2026-05-05 19:00:00', '2026-05-05 19:19:00', '2026-05-05 19:39:00'),  -- O2  ece @ sushi,  items 180.00
(5, 1, NULL, 'ARRIVED',   275.00, '2026-05-06 13:00:00', '2026-05-06 13:17:00', '2026-05-06 13:37:00'),  -- O3  berk @ kebab, items 275.00
(5, 4, 4,    'ARRIVED',   190.00, '2026-05-10 20:00:00', '2026-05-10 20:20:00', '2026-05-10 20:40:00'),  -- O4  berk @ burger,items 195.00, BURGER5 -5
(7, 4, NULL, 'ARRIVED',   195.00, '2026-05-09 21:00:00', '2026-05-09 21:18:00', '2026-05-09 21:38:00'),  -- O5  can @ burger, items 195.00
(7, 1, 1,    'ARRIVED',   108.00, '2026-05-14 11:00:00', '2026-05-14 11:19:00', '2026-05-14 11:39:00'),  -- O6  can @ kebab,  items 120.00, KEBAB10 -10%
(4, 2, 2,    'ARRIVED',   145.00, '2026-05-15 18:00:00', '2026-05-15 18:17:00', '2026-05-15 18:37:00'),  -- O7  ece @ sushi,  items 165.00, SUSHI20 -20
(5, 2, NULL, 'ARRIVED',   170.00, '2026-05-16 13:30:00', '2026-05-16 13:50:00', '2026-05-16 14:10:00'),  -- O8  berk @ sushi, items 170.00
(6, 3, 3,    'ARRIVED',   238.00, '2026-05-08 18:30:00', '2026-05-08 18:48:00', '2026-05-08 19:08:00'),  -- O9  selin @ pasta,items 280.00, PASTA15 -15%
(6, 3, NULL, 'ARRIVED',   110.00, '2026-05-12 12:00:00', '2026-05-12 12:19:00', '2026-05-12 12:39:00'),  -- O10 selin @ pasta,items 110.00
(8, 3, NULL, 'SENT',      165.00, '2026-05-17 19:30:00', NULL, NULL),                                    -- O11 pinar @ pasta (lifecycle: SENT)
(7, 1, NULL, 'SENT',      100.00, '2026-05-18 11:00:00', NULL, NULL),                                    -- O12 can @ kebab  (lifecycle: SENT)
(6, 5, NULL, 'ARRIVED',   155.00, '2026-05-13 12:00:00', '2026-05-13 12:18:00', '2026-05-13 12:38:00'),  -- O13 selin @ green bowl
(8, 5, 5,    'ARRIVED',   116.25, '2026-05-19 10:00:00', '2026-05-19 10:17:00', '2026-05-19 10:37:00');  -- O14 pinar @ green bowl, BOWL25 -25%

-- ── Order Items ───────────────────────────────────────────────────────────────
-- Restaurant item ranges: r1 -> items 1-5, r2 -> 6-8, r3 -> 9-11, r4 -> 12-15
INSERT INTO OrderItem (order_id, item_id, quantity, unit_price) VALUES
-- O1 ece @ kebab (r1): 45 + 120 + 35 = 200.00
(1, 1, 1, 45.00), (1, 3, 1, 120.00), (1, 2, 1, 35.00),
-- O2 ece @ sushi (r2): 95 + 85 = 180.00
(2, 6, 1, 95.00), (2, 7, 1, 85.00),
-- O3 berk @ kebab (r1): 130 + 100 + 45 = 275.00
(3, 4, 1, 130.00), (3, 5, 1, 100.00), (3, 1, 1, 45.00),
-- O4 berk @ burger (r4): 90 + 105 = 195.00
(4, 12, 1, 90.00), (4, 13, 1, 105.00),
-- O5 can @ burger (r4): 90 + 105 = 195.00
(5, 12, 1, 90.00), (5, 13, 1, 105.00),
-- O6 can @ kebab (r1): 120.00
(6, 3, 1, 120.00),
-- O7 ece @ sushi (r2): 95 + 70 = 165.00
(7, 6, 1, 95.00), (7, 8, 1, 70.00),
-- O8 berk @ sushi (r2): 85 x 2 = 170.00
(8, 7, 2, 85.00),
-- O9 selin @ pasta (r3): 55 + 115 + 110 = 280.00
(9, 9, 1, 55.00), (9, 11, 1, 115.00), (9, 10, 1, 110.00),
-- O10 selin @ pasta (r3): 110.00
(10, 10, 1, 110.00),
-- O11 pinar @ pasta (r3): 55 + 110 = 165.00
(11, 9, 1, 55.00), (11, 10, 1, 110.00),
-- O12 can @ kebab (r1): 100.00
(12, 5, 1, 100.00),
-- O13 selin @ green bowl (r5): 85 + 70 = 155.00
(13, 16, 1, 85.00), (13, 17, 1, 70.00),
-- O14 pinar @ green bowl (r5): 85 + 70 = 155.00, BOWL25 -25% = 116.25
(14, 16, 1, 85.00), (14, 17, 1, 70.00);

-- ── Ratings ───────────────────────────────────────────────────────────────────
-- Exactly 10 ratings, one per distinct ARRIVED order (O1-O10). Rating.order_id
-- is UNIQUE, so each order is rated at most once. customer_id / restaurant_id
-- match the rated order. Distributed: r1 x3, r2 x3, r3 x2, r4 x2.
INSERT INTO Rating (customer_id, restaurant_id, order_id, score, comment) VALUES
(4, 1, 1,  5, 'Absolutely delicious! Best kebab in Istanbul.'),
(4, 2, 2,  4, 'Fresh sushi, fast delivery.'),
(5, 1, 3,  5, 'Amazing food, great portions!'),
(5, 4, 4,  4, 'Good value for money.'),
(7, 4, 5,  3, 'Good burgers but a bit slow.'),
(7, 1, 6,  4, 'Solid Adana, would order again.'),
(4, 2, 7,  5, 'The salmon nigiri was excellent.'),
(5, 2, 8,  4, 'Generous portions of spicy tuna.'),
(6, 3, 9,  5, 'Loved the carbonara and the pizza.'),
(6, 3, 10, 4, 'Consistent quality every time.');
