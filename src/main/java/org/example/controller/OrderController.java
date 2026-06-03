package org.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Handles order placement, status transitions, and rating submission. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final org.example.service.OrderService orderService;

    public OrderController(org.example.service.OrderService orderService) {
        this.orderService = orderService;
    }

    /** Places a new order for a customer. */
    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody Map<String, Object> body) {
        int customerId    = ControllerInputs.requireInt(body, "customerId");
        int restaurantId  = ControllerInputs.requireInt(body, "restaurantId");
        String couponCode = ControllerInputs.optString(body, "couponCode", null);
        List<Map<String, Object>> rawItems = ControllerInputs.requireList(body, "items");

        List<org.example.model.OrderItem> items = new ArrayList<>();
        for (Map<String, Object> m : rawItems) {
            org.example.model.OrderItem oi = new org.example.model.OrderItem();
            oi.setItemId(ControllerInputs.requireInt(m, "itemId"));
            oi.setQuantity(ControllerInputs.requireInt(m, "quantity"));
            items.add(oi);
        }

        org.example.model.Order order = orderService.placeOrder(customerId, restaurantId, items, couponCode);
        log.info("Order placed: id={} customer={} restaurant={} total={}",
                order.getOrderId(), customerId, restaurantId, order.getTotalPrice());
        return ResponseEntity.ok(order);
    }

    /** Returns a customer's orders, optionally filtered by status. */
    @GetMapping("/customer/{customerId}")
    public List<org.example.model.Order> customerOrders(@PathVariable int customerId,
                                                        @RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return orderService.getCustomerOrdersByStatus(customerId, status);
        }
        return orderService.getCustomerOrders(customerId);
    }

    /** Returns pending (SENT and PREPARING) orders for a restaurant. */
    @GetMapping("/restaurant/{restaurantId}/pending")
    public List<org.example.model.Order> pendingOrders(@PathVariable int restaurantId) {
        return orderService.getPendingOrdersForRestaurant(restaurantId);
    }

    /** Returns all orders for a restaurant. */
    @GetMapping("/restaurant/{restaurantId}")
    public List<org.example.model.Order> allRestaurantOrders(@PathVariable int restaurantId) {
        return orderService.getAllOrdersForRestaurant(restaurantId);
    }

    /** Returns the line items for an order. */
    @GetMapping("/{orderId}/items")
    public List<org.example.model.OrderItem> orderItems(@PathVariable int orderId) {
        return orderService.getOrderItems(orderId);
    }

    /** Transitions an order from SENT to PREPARING. */
    @PutMapping("/{orderId}/accept")
    public ResponseEntity<?> acceptOrder(@PathVariable int orderId) {
        orderService.acceptOrder(orderId);
        log.info("Order accepted (now PREPARING): id={}", orderId);
        return ResponseEntity.ok().build();
    }

    /** Transitions an order from PREPARING to ARRIVED. */
    @PutMapping("/{orderId}/arrive")
    public ResponseEntity<?> arriveOrder(@PathVariable int orderId) {
        orderService.markArrived(orderId);
        log.info("Order arrived: id={}", orderId);
        return ResponseEntity.ok().build();
    }

    /** Submits a rating for an arrived order. */
    @PostMapping("/{orderId}/rate")
    public ResponseEntity<?> rateOrder(@PathVariable int orderId,
                                       @RequestBody Map<String, Object> body) {
        int customerId   = ControllerInputs.requireInt(body, "customerId");
        int restaurantId = ControllerInputs.requireInt(body, "restaurantId");
        int score        = ControllerInputs.requireInt(body, "score");
        String comment   = ControllerInputs.optString(body, "comment", "");
        orderService.leaveRating(customerId, restaurantId, orderId, score, comment);
        log.info("Rating submitted: order={} customer={} score={}", orderId, customerId, score);
        return ResponseEntity.ok().build();
    }
}
