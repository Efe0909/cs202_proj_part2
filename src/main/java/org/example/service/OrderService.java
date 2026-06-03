package org.example.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Orchestrates order placement, status transitions, ratings, and coupon delegation. */
@Service
public class OrderService {

    private final org.example.dao.OrderDAO orderDAO;
    private final org.example.dao.CouponDAO couponDAO;
    private final org.example.dao.RatingDAO ratingDAO;
    private final org.example.dao.MenuItemDAO menuItemDAO;
    private final org.example.dao.UserDAO userDAO;
    private final org.example.dao.RestaurantDAO restaurantDAO;
    private final CouponService couponService;

    public OrderService(org.example.dao.OrderDAO orderDAO, org.example.dao.CouponDAO couponDAO,
            org.example.dao.RatingDAO ratingDAO, org.example.dao.MenuItemDAO menuItemDAO,
            org.example.dao.UserDAO userDAO, org.example.dao.RestaurantDAO restaurantDAO) {
        this.orderDAO = orderDAO;
        this.couponDAO = couponDAO;
        this.ratingDAO = ratingDAO;
        this.menuItemDAO = menuItemDAO;
        this.userDAO = userDAO;
        this.restaurantDAO = restaurantDAO;
        this.couponService = new CouponService(couponDAO);
    }

    @Transactional
    public org.example.model.Order placeOrder(int customerId, int restaurantId,
            List<org.example.model.OrderItem> items, String couponCode) {

        org.example.model.Restaurant restaurant = restaurantDAO.findById(restaurantId).orElse(null);
        String selectedCity = userDAO.findSelectedAddressCity(customerId).orElse(null);

        if (restaurant == null)
            throw new IllegalArgumentException("Unknown restaurant id: " + restaurantId);
        if (selectedCity == null || selectedCity.trim().isEmpty())
            throw new IllegalArgumentException("Select a delivery address before ordering.");

        String customerCity = selectedCity.trim();
        String restaurantCity = "";
        if (restaurant.getCity() != null)
            restaurantCity = restaurant.getCity().trim();

        if (!customerCity.equalsIgnoreCase(restaurantCity))
            throw new IllegalArgumentException("You can only order from restaurants in your own city.");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("An order must contain at least one item.");

        for (org.example.model.OrderItem item : items) {
            if (item.getQuantity() <= 0)
                throw new IllegalArgumentException(
                        "Item quantity must be greater than 0 (item id: " + item.getItemId() + ").");
        }

        // Resolve current prices from DB — never trust client-supplied prices.
        for (org.example.model.OrderItem item : items) {
            org.example.model.MenuItem resolved = menuItemDAO.findById(item.getItemId()).orElse(null);
            if (resolved == null) {
                throw new IllegalArgumentException("Unknown item id: " + item.getItemId());
            }
            if (resolved.getRestaurantId() != restaurantId) {
                throw new IllegalArgumentException(
                        "Item " + item.getItemId() + " does not belong to restaurant " + restaurantId + ".");
            }
            item.setUnitPrice(resolved.getPrice());
        }

        double subtotal = 0;
        for (org.example.model.OrderItem item : items) {
            subtotal += item.getSubtotal();
        }
        Integer couponId = null;

        if (couponCode != null && !couponCode.trim().isEmpty()) {
            org.example.model.Coupon coupon = couponService.resolveForOrder(couponCode, restaurantId);
            if (!couponService.tryDeactivate(coupon.getCouponId())) {
                throw new IllegalArgumentException("Invalid or expired coupon.");
            }
            couponId = coupon.getCouponId();
            subtotal = coupon.applyDiscount(subtotal);
        }

        org.example.model.Order order = new org.example.model.Order();
        order.setCustomerId(customerId);
        order.setRestaurantId(restaurantId);
        order.setCouponId(couponId);
        order.setStatus("SENT");
        order.setTotalPrice(subtotal);

        int orderId = orderDAO.insert(order);
        order.setOrderId(orderId);

        for (org.example.model.OrderItem item : items) {
            item.setOrderId(orderId);
            orderDAO.insertItem(item);
        }
        order.setItems(items);
        return order;
    }

    /** Transitions an order from SENT to PREPARING; throws if it is not in SENT state. */
    public void acceptOrder(int orderId) {
        if (orderDAO.markPreparing(orderId) == 0) {
            throw new IllegalStateException("Order cannot be accepted from its current state");
        }
    }

    /** Transitions an order from PREPARING to ARRIVED; throws if it is not in PREPARING state. */
    public void markArrived(int orderId) {
        if (orderDAO.markArrived(orderId) == 0) {
            throw new IllegalStateException("Order cannot be marked arrived from its current state");
        }
    }

    /** Returns all orders for a customer. */
    public List<org.example.model.Order> getCustomerOrders(int customerId) {
        return orderDAO.findByCustomer(customerId);
    }

    /** Returns orders for a customer filtered by status. */
    public List<org.example.model.Order> getCustomerOrdersByStatus(int customerId, String status) {
        return orderDAO.findByCustomerAndStatus(customerId, status);
    }

    /** Returns SENT and PREPARING orders for a restaurant. */
    public List<org.example.model.Order> getPendingOrdersForRestaurant(int restaurantId) {
        return orderDAO.findPendingByRestaurant(restaurantId);
    }

    /** Returns all orders for a restaurant. */
    public List<org.example.model.Order> getAllOrdersForRestaurant(int restaurantId) {
        return orderDAO.findByRestaurant(restaurantId);
    }

    /** Returns line items for an order. */
    public List<org.example.model.OrderItem> getOrderItems(int orderId) {
        return orderDAO.findItemsByOrder(orderId);
    }

    /** Looks up an order by primary key. */
    public Optional<org.example.model.Order> findById(int orderId) {
        return orderDAO.findById(orderId);
    }

    /** Submits a rating for an arrived order; enforces one-per-order and 24-hour window. */
    public void leaveRating(int customerId, int restaurantId, int orderId, int score, String comment) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Rating score must be between 1 and 5.");
        }
        Optional<org.example.model.Order> opt = orderDAO.findById(orderId);
        if (opt.isEmpty() || !opt.get().isArrived()) {
            throw new IllegalStateException("Order not yet arrived.");
        }
        org.example.model.Order order = opt.get();
        if (order.getCustomerId() != customerId) {
            throw new IllegalArgumentException("Order does not belong to this customer.");
        }
        if (ratingDAO.existsForOrder(orderId)) {
            throw new IllegalStateException("Rating already submitted for this order.");
        }
        // The window opens at arrived_at, not updated_at — updated_at is reset by MySQL ON UPDATE
        // CURRENT_TIMESTAMP on any later mutation and would silently extend the window.
        LocalDateTime arrivedAt = order.getArrivedAt();
        if (arrivedAt == null
                || arrivedAt.isBefore(LocalDateTime.now().minusHours(24))) {
            throw new IllegalStateException("Rating window has expired (24 hours).");
        }
        ratingDAO.insert(new org.example.model.Rating(customerId, restaurantId, orderId, score, comment));
    }

    /** Returns all coupons for a restaurant. */
    public List<org.example.model.Coupon> getCouponsForRestaurant(int restaurantId) {
        return couponService.listByRestaurant(restaurantId);
    }

    /** Creates a coupon via CouponService. */
    public org.example.model.Coupon createCoupon(org.example.model.Coupon coupon) {
        return couponService.createCoupon(coupon);
    }
}
