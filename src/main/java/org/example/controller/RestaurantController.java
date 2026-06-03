package org.example.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Handles restaurant browsing, CRUD, and menu management. */
@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {

    private static final Logger log = LoggerFactory.getLogger(RestaurantController.class);

    private final org.example.service.RestaurantService restaurantService;

    public RestaurantController(org.example.service.RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    /** Browses or searches restaurants in a city. */
    @GetMapping
    public List<org.example.model.Restaurant> browse(@RequestParam String city,
                                   @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return restaurantService.searchByKeyword(keyword, city);
        }
        return restaurantService.browseByCity(city);
    }

    /** Returns a single restaurant by id. */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable int id) {
        org.example.model.Restaurant r = restaurantService.findById(id).orElse(null);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(r);
    }

    /** Returns restaurants owned by a manager. */
    @GetMapping("/manager/{managerId}")
    public List<org.example.model.Restaurant> byManager(@PathVariable int managerId) {
        return restaurantService.findByManager(managerId);
    }

    /** Creates a new restaurant. */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        org.example.model.Restaurant r = new org.example.model.Restaurant();
        r.setManagerId(ControllerInputs.requireInt(body, "managerId"));
        r.setName(ControllerInputs.requireString(body, "name"));
        r.setCuisineType(ControllerInputs.requireString(body, "cuisineType"));
        r.setAddress(ControllerInputs.requireString(body, "address"));
        r.setCity(ControllerInputs.requireString(body, "city"));
        List<String> keywords = ControllerInputs.optStringList(body, "keywords");
        return ResponseEntity.ok(restaurantService.createRestaurant(r, keywords));
    }

    /** Updates a restaurant's details and keyword set. */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody Map<String, Object> body) {
        int managerId = ControllerInputs.requireInt(body, "managerId");
        org.example.model.Restaurant r = new org.example.model.Restaurant();
        r.setRestaurantId(id);
        r.setManagerId(managerId);
        r.setName(ControllerInputs.requireString(body, "name"));
        r.setCuisineType(ControllerInputs.requireString(body, "cuisineType"));
        r.setAddress(ControllerInputs.requireString(body, "address"));
        r.setCity(ControllerInputs.requireString(body, "city"));
        List<String> keywords = ControllerInputs.optStringList(body, "keywords");
        restaurantService.updateRestaurant(r, keywords, managerId);
        return ResponseEntity.ok().build();
    }

    /** Returns all ratings for a restaurant, newest first. */
    @GetMapping("/{restaurantId}/ratings")
    public List<org.example.model.Rating> getRatings(@PathVariable int restaurantId) {
        return restaurantService.getRatings(restaurantId);
    }

    /** Returns all menu categories for a restaurant. */
    @GetMapping("/{id}/categories")
    public List<org.example.model.MenuCategory> getCategories(@PathVariable int id) {
        return restaurantService.getCategories(id);
    }

    /** Adds a menu category to a restaurant. */
    @PostMapping("/{id}/categories")
    public org.example.model.MenuCategory addCategory(@PathVariable int id, @RequestBody Map<String, Object> body) {
        int managerId = ControllerInputs.requireInt(body, "managerId");
        String name   = ControllerInputs.requireString(body, "name");
        return restaurantService.addCategory(id, name, managerId);
    }

    /** Deletes a menu category. */
    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<?> deleteCategory(@PathVariable int categoryId,
                                            @RequestParam int managerId) {
        restaurantService.deleteCategory(categoryId, managerId);
        return ResponseEntity.ok().build();
    }

    /** Returns the menu for a restaurant, optionally filtered by category. */
    @GetMapping("/{id}/menu")
    public List<org.example.model.MenuItem> getMenu(@PathVariable int id,
                                  @RequestParam(required = false) Integer categoryId) {
        if (categoryId != null) return restaurantService.getMenuItemsByCategory(categoryId);
        return restaurantService.getMenuItems(id);
    }

    /** Adds a menu item to a restaurant. */
    @PostMapping("/{id}/menu")
    public ResponseEntity<?> addMenuItem(@PathVariable int id, @RequestBody Map<String, Object> body) {
        int managerId = ControllerInputs.requireInt(body, "managerId");
        org.example.model.MenuItem item = new org.example.model.MenuItem();
        item.setRestaurantId(id);
        item.setCategoryId(ControllerInputs.requireInt(body, "categoryId"));
        item.setName(ControllerInputs.requireString(body, "name"));
        item.setDescription(ControllerInputs.requireString(body, "description"));
        item.setPrice(ControllerInputs.requireDouble(body, "price"));
        item.setImagePath(ControllerInputs.requireString(body, "imagePath"));
        return ResponseEntity.ok(restaurantService.addMenuItem(item, managerId));
    }

    /** Updates a menu item. */
    @PutMapping("/menu/{itemId}")
    public ResponseEntity<?> updateMenuItem(@PathVariable int itemId, @RequestBody Map<String, Object> body) {
        int managerId = ControllerInputs.requireInt(body, "managerId");
        org.example.model.MenuItem item = new org.example.model.MenuItem();
        item.setItemId(itemId);
        item.setCategoryId(ControllerInputs.requireInt(body, "categoryId"));
        item.setName(ControllerInputs.requireString(body, "name"));
        item.setDescription(ControllerInputs.requireString(body, "description"));
        item.setPrice(ControllerInputs.requireDouble(body, "price"));
        item.setImagePath(ControllerInputs.requireString(body, "imagePath"));
        restaurantService.updateMenuItem(item, managerId);
        return ResponseEntity.ok().build();
    }

    /** Deletes a menu item. */
    @DeleteMapping("/menu/{itemId}")
    public ResponseEntity<?> deleteMenuItem(@PathVariable int itemId,
                                            @RequestParam int managerId) {
        restaurantService.deleteMenuItem(itemId, managerId);
        return ResponseEntity.ok().build();
    }
}
