# CS 202 Project — 15-Minute Demo Script

Run order: walk through every spec feature in ~15 min. Use **Iskender Kebab** as the canonical menu item and **KEBAB10** as the coupon (10% off, Bosphorus Kebab, valid May 2026).

---

## Setup (before demo, 1 min)

```bash
make run-fresh      # wipes the DB volume, reseeds DDL+DML, starts backend
make ui             # launches the JavaFX UI in a separate terminal
```

Confirm backend ready at http://localhost:8080 and a login window opens.

---

## Part A — Customer flow (7 min)

### A1. Register & Login (~1 min)
- Click **Register** on the login screen.
- Fill: `username=demo_customer`, `password=demo1234`, `email=demo@x.com`, `fullName=Demo User`, role=`CUSTOMER`, **`Delivery city=Istanbul`, `Province=Kadıköy`** (both fields are required — registration creates the first delivery address atomically and sets it as selected).
- Click **Register** → success → back to login.
- Login with `demo_customer / demo1234` → lands on **Customer Dashboard**.

### A1b. Manage delivery addresses + phones (~1 min)
- Click **My Addresses** in the sidebar. The address you registered with is listed and flagged "Selected". Add a second one if you want, set it as selected, watch the header pill update.
- Click **My Phones**. Add `+90 5XX XXX XXXX`. Add another. Delete one with the confirm dialog.

### A2. Browse by city (~1 min)
- Click **Browse Restaurants** sidebar item.
- Verify the list is filtered to Istanbul only (Bosphorus Kebab, Burger Station).
- Note the **"New"** badge on restaurants with <10 ratings (spec §3.2.1).
- Verify rating shows 0 for those (per spec until 10 ratings exist).

### A3. Search by keyword (~30 s)
- Type `kebab` in the search box → press Search.
- Result narrows to Bosphorus Kebab.

### A4. View menu — description + image (~1 min)
- Double-click **Bosphorus Kebab**.
- The menu opens, items grouped by category (Starters / Main Dishes).
- Each item shows: **image** (loaded from `imagePath`), **name**, **description**, **price**, **+ Add** button.
- If image files aren't on disk (DML uses paths like `img/lentil.jpg`), the ImageView is silently omitted — name/description/price/Add still render, by design.

### A5. Add to cart + apply coupon (~1.5 min)
- Click **+ Add** twice on **Iskender Kebab** (130 TL × 2 = 260 TL).
- Click **+ Add** on **Mercimek Corbasi** (45 TL × 1).
- Click **View Cart (3 items)** at the bottom → CartView opens.
- Enter coupon code `KEBAB10`.
- Click **Place Order** → success message: "Order placed — Preparing (not yet sent)".

### A6. Send order (~30 s)
- Click **My Orders** in the sidebar.
- Newest order at the top, status `PREPARING`.
- Click **Send to restaurant** → status flips to `SENT`.

### A7. (manager will accept here — switch to manager in Part B step B5)

### A8. Rate after acceptance (~1.5 min, after B5)
- Back to **My Orders**, click **Refresh**.
- Order now shows `ACCEPTED`.
- Click **Details** → shows items + total.
- Click **Leave Rating** → spinner 1–5, optional comment.
- Pick **5**, type "Excellent kebab", click **Submit Rating** → success.
- (Spec §3.2.1: rating must be within 24h of acceptance — enforced server-side.)

---

## Part B — Restaurant Manager flow (6 min)

### B1. Login as manager (~30 s)
- Click **Logout** if logged in as customer.
- Login as a manager. Use the seeded manager:
  ```
  username: manager_ali     password: (hashed in DML — easier: register a new MANAGER instead)
  ```
- OR register a new manager: `username=demo_manager`, role=MANAGER, delivery city=Istanbul, province=Kadıköy.

### B2. Create a restaurant (~1 min)
- Click **My Restaurants** in the sidebar.
- Expand **Add New Restaurant** at the bottom.
- Fill: name=`Demo Pizza`, cuisine=`Italian`, address=`Main St 1`, city=`Istanbul`, keywords=`pizza, italian, demo`.
- Click **Save** → row appears in the list.

### B3. **Edit a restaurant** (NEW — ~1 min)
- Select the row in the list → 3 action buttons enable: **Manage Menu**, **Manage Coupons**, **Edit Restaurant**.
- Click **Edit Restaurant** → form opens pre-filled with current values + existing keywords.
- Change name to `Demo Pizza & Pasta`. Add `pasta` to keywords.
- Click **Save Changes** → success, list refreshes.

### B4. Manage menu — add category + item (every field required), edit item (~2 min)
*All menu fields — name, description, image path, price, category — are now required per spec §3.2.1. The Add and Edit forms reject blank description or blank image with a clear error.*
- Select Demo Pizza row → click **Manage Menu**.
- Expand **Add Category**, type `Pizzas`, click Add Category.
- Switch to the Pizzas tab.
- Expand **Add Menu Item**, fill: name=`Margherita`, description=`Tomato + mozzarella + basil`, price=`120`, image path=`img/margherita.jpg`.
- Click **Add Item** → appears in the list.
- **Edit it (NEW)**: click **Edit** next to the item → inline form pre-filled; change price to `130`, click **Save Changes** → list refreshes with the new price.
- (Spec §3.2.1: add, **update**, and delete menu items.)

### B5. Accept the customer's order from Part A (~1 min)
- Switch demo restaurant to **Bosphorus Kebab** (or wherever the customer ordered from). Click **My Restaurants** → select Bosphorus Kebab — or have the seeded manager_ali handle it.
- Click **Incoming Orders** in sidebar.
- The PREPARING/SENT order from A6 is in the list. Click it → details show items + total.
- Click **Accept Order** → status flips to ACCEPTED.
- Click **Refresh** to confirm.
- (Customer can now rate — see A8.)

### B6. Create a coupon (~30 s)
- Back to **My Restaurants** → select restaurant → **Manage Coupons**.
- Expand the create-coupon form.
- Fill: code=`DEMO20`, type=`PERCENTAGE`, value=`20`, validFrom=`2026-05-01`, validUntil=`2026-12-31`.
- Click **Create** → coupon appears in the table.

### B7. View 8 sales statistics (~1 min)
- Click **Sales Statistics** in sidebar.
- Pick **Bosphorus Kebab** from the dropdown.
- Verify all 8 metrics render (no "—" placeholders):
  1. **Total Revenue** (e.g. 563.00 TL after coupon discount)
  2. **Total Orders** (3)
  3. **Per-item table** (qty + revenue)
  4. **Customer with most orders** (Ece Sahin)
  5. **Customer with highest-value order** + details (Berk Ozturk, order #3 = 275 TL with items)
  6. **Most frequently ordered item** (Mercimek Corbasi)
  7. **Top revenue category** (Main Dishes)
  8. **Total coupon discount** (32.00 TL)

### B8. View ratings list (~30 s)
- In Sales Statistics, click **View Ratings**.
- All ratings for the selected restaurant render, newest first, including the one the customer left in A8.
- Header shows count + average.

---

## Closing notes (1 min)

- Hand-written SQL: open any DAO (e.g. `OrderDAO.java`) — every operation is a literal SQL string.
- No ORM: open `pom.xml` — only `spring-boot-starter-jdbc`, no JPA/Hibernate.
- All 237 tests green: `make test-fresh` (or `mvn test` with a running DB).
- Spec compliance: see [misc/QA_REPORT.md](misc/QA_REPORT.md) — every spec section has ✅ next to it.

---

## API Shell Helpers (macOS / Linux only)

The best way to use `scripts/api.sh` is **alongside a running UI session**, not
instead of one. Keep the UI open as your primary client and open a second
terminal sourced with the helpers. You get the best of both:

**The UI live-logs every request to stdout.** When you click a restaurant page
the log prints the restaurant id; when you place an order it prints the order id.
Those ids are right there — copy one and fire `coupons <restaurantId>` or
`acceptorder <orderId>` from the second shell without ever logging out of your
customer session or opening a manager window.

**The helpers communicate through the REST API only** — no direct DB writes, no
bypassing the state machine. Every call goes through the same
`GlobalExceptionHandler` and service-layer guards as the UI does, so you can't
accidentally corrupt state that the UI depends on. If you try to accept an order
that isn't `SENT` yet, you get the same `409` the UI would.

**Common patterns during a demo or debugging session:**
- You're logged in as a customer and just placed an order → grab the `orderId`
  from the stdout log → `acceptorder <id>` in the second shell, skipping the
  manager login entirely.
- You want to see what coupons are available for the restaurant you just opened
  → `coupons <restaurantId>` (id is in the log from the menu fetch).
- You need the order in `ARRIVED` state to test the 24h rating window →
  `fullpipeline` sets up the whole pipeline in one command.
- You changed a backend endpoint and want raw JSON before touching the UI →
  `menu <restaurantId>` or `stats <restaurantId>` prints the full response.

> **Windows users:** these are Bash functions — use WSL or skip this section.

```bash
# load all functions into your current shell
source scripts/api.sh

# complete a pending order from the manager side (orders start as SENT)
acceptorder 13 1        # SENT → PREPARING  (orderId managerId)
arriveorder 13 1        # PREPARING → ARRIVED

# full order → accept → arrive in one go
fullpipeline 9 1 1 3:1          # customerId managerId restaurantId itemId:qty

# place an order with a coupon, then rate it
placeorder 9 4 BURGER5 12:1 13:1
acceptorder <orderId> 1
arriveorder <orderId> 1
rateorder <orderId> 9 5 "Great burgers!"

# other handy functions
restaurants Istanbul             # browse by city
menu 4                           # list menu for restaurant 4
stats 4                          # monthly statistics for restaurant 4
myorders 9                       # all orders for customer 9
coupons 1                        # active coupons for restaurant 1
addresses 9                      # delivery addresses for user 9
phones 9                         # phone numbers for user 9
```

Full function list printed on `source`. See `scripts/api.sh` for all signatures.
