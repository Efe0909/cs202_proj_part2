package org.example.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Restaurant, menu category, and menu item management with ownership enforcement. */
@Service
public class RestaurantService {

    private final org.example.dao.RestaurantDAO restaurantDAO;
    private final org.example.dao.MenuItemDAO menuItemDAO;
    private final org.example.dao.MenuCategoryDAO menuCategoryDAO;
    private final org.example.dao.RatingDAO ratingDAO;
    private final org.example.dao.UserDAO userDAO;

    public RestaurantService(org.example.dao.RestaurantDAO restaurantDAO, org.example.dao.MenuItemDAO menuItemDAO,
            org.example.dao.MenuCategoryDAO menuCategoryDAO, org.example.dao.RatingDAO ratingDAO,
            org.example.dao.UserDAO userDAO) {
        this.restaurantDAO = restaurantDAO;
        this.menuItemDAO = menuItemDAO;
        this.menuCategoryDAO = menuCategoryDAO;
        this.ratingDAO = ratingDAO;
        this.userDAO = userDAO;
    }

    /** Manager rating list for a restaurant, newest first. */
    public List<org.example.model.Rating> getRatings(int restaurantId) {
        return ratingDAO.findByRestaurant(restaurantId);
    }

    /** Searches restaurants by keyword within a city and attaches their keyword lists. */
    public List<org.example.model.Restaurant> searchByKeyword(String keyword, String city) {
        List<org.example.model.Restaurant> results = restaurantDAO.searchByKeywordAndCity(keyword, city);
        for (org.example.model.Restaurant r : results) {
            r.setKeywords(restaurantDAO.findKeywords(r.getRestaurantId()));
        }
        return results;
    }

    /** Returns all restaurants in a city with their keyword lists attached. */
    public List<org.example.model.Restaurant> browseByCity(String city) {
        List<org.example.model.Restaurant> results = restaurantDAO.findByCity(city);
        for (org.example.model.Restaurant r : results) {
            r.setKeywords(restaurantDAO.findKeywords(r.getRestaurantId()));
        }
        return results;
    }

    /** Returns all restaurants owned by the given manager. */
    public List<org.example.model.Restaurant> findByManager(int managerId) {
        return restaurantDAO.findByManagerId(managerId);
    }

    /** Looks up a restaurant by id and attaches its keyword list. */
    public Optional<org.example.model.Restaurant> findById(int restaurantId) {
        Optional<org.example.model.Restaurant> r = restaurantDAO.findById(restaurantId);
        if (r.isPresent()) {
            r.get().setKeywords(restaurantDAO.findKeywords(restaurantId));
        }
        return r;
    }

    /** Throws IllegalArgumentException if managerId does not own restaurantId. */
    public void requireOwnership(int restaurantId, int managerId) {
        if (!restaurantDAO.existsOwnedBy(restaurantId, managerId)) {
            throw new IllegalArgumentException("Restaurant not found or access denied.");
        }
    }

    /** Creates a restaurant with its initial keyword set; throws if the user is not a manager. */
    public org.example.model.Restaurant createRestaurant(org.example.model.Restaurant restaurant,
            List<String> keywords) {
        if (!userDAO.isManager(restaurant.getManagerId())) {
            throw new IllegalArgumentException("User is not a restaurant manager.");
        }
        int id = restaurantDAO.insert(restaurant);
        restaurant.setRestaurantId(id);
        for (String kw : keywords)
            restaurantDAO.addKeyword(id, kw);
        restaurant.setKeywords(keywords);
        return restaurant;
    }

    /** Updates restaurant fields and replaces its keyword set; throws if ownership check fails. */
    public void updateRestaurant(org.example.model.Restaurant restaurant, List<String> keywords, int managerId) {
        int rowsAffected = restaurantDAO.update(restaurant, managerId);
        if (rowsAffected == 0)
            throw new IllegalArgumentException("Restaurant not found or access denied.");
        restaurantDAO.deleteKeywords(restaurant.getRestaurantId());
        for (String kw : keywords)
            restaurantDAO.addKeyword(restaurant.getRestaurantId(), kw);
    }

    /** Returns all menu categories for a restaurant. */
    public List<org.example.model.MenuCategory> getCategories(int restaurantId) {
        return menuCategoryDAO.findByRestaurant(restaurantId);
    }

    /** Adds a category to a restaurant; throws if the manager doesn't own the restaurant. */
    public org.example.model.MenuCategory addCategory(int restaurantId, String name, int managerId) {
        int categoryId = menuCategoryDAO.insertIfOwner(restaurantId, name, managerId);
        if (categoryId == -1)
            throw new IllegalArgumentException("Restaurant not found or access denied.");
        return new org.example.model.MenuCategory(categoryId, restaurantId, name);
    }

    /** Updates a menu category; throws if ownership check fails. */
    public void updateCategory(org.example.model.MenuCategory cat, int managerId) {
        int rows = menuCategoryDAO.updateIfOwner(cat, managerId);
        if (rows == 0)
            throw new IllegalArgumentException("Category not found or access denied.");
    }

    /** Deletes a menu category; throws if ownership check fails. */
    public void deleteCategory(int categoryId, int managerId) {
        int rows = menuCategoryDAO.deleteIfOwner(categoryId, managerId);
        if (rows == 0)
            throw new IllegalArgumentException("Category not found or access denied.");
    }

    /** Returns all menu items for a restaurant. */
    public List<org.example.model.MenuItem> getMenuItems(int restaurantId) {
        return menuItemDAO.findByRestaurant(restaurantId);
    }

    /** Returns all menu items in a category. */
    public List<org.example.model.MenuItem> getMenuItemsByCategory(int categoryId) {
        return menuItemDAO.findByCategory(categoryId);
    }

    /** Adds a menu item after validating all required fields and ownership. */
    public org.example.model.MenuItem addMenuItem(org.example.model.MenuItem item, int managerId) {
        if (!restaurantDAO.existsOwnedBy(item.getRestaurantId(), managerId)) {
            throw new IllegalArgumentException("Restaurant not found or access denied.");
        }
        validateMenuItem(item);
        int id = menuItemDAO.insert(item);
        item.setItemId(id);
        return item;
    }

    /** Updates a menu item after validation; throws if ownership check fails. */
    public void updateMenuItem(org.example.model.MenuItem item, int managerId) {
        validateMenuItem(item);
        int rows = menuItemDAO.updateIfOwner(item, managerId);
        if (rows == 0)
            throw new IllegalArgumentException("Menu item not found or access denied.");
    }

    /** Deletes a menu item; throws if ownership check fails. */
    public void deleteMenuItem(int itemId, int managerId) {
        int rows = menuItemDAO.deleteIfOwner(itemId, managerId);
        if (rows == 0)
            throw new IllegalArgumentException("Menu item not found or access denied.");
    }

    private void validateMenuItem(org.example.model.MenuItem item) {
        if (item.getName() == null || item.getName().isBlank()) {
            throw new IllegalArgumentException("Menu item name must not be blank.");
        }
        if (item.getDescription() == null || item.getDescription().isBlank()) {
            throw new IllegalArgumentException("Menu item description must not be blank.");
        }
        if (item.getImagePath() == null || item.getImagePath().isBlank()) {
            throw new IllegalArgumentException("Menu item image path must not be blank.");
        }
        if (item.getPrice() <= 0) {
            throw new IllegalArgumentException("Menu item price must be greater than 0.");
        }
    }
}
