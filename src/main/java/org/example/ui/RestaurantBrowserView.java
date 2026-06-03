package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;

public class RestaurantBrowserView {

    private final Stage       stage;
    private final org.example.model.User        user;
    private final StackPane   contentArea;
    private final ObjectMapper mapper = ApiClient.mapper();

    // Tracks the menu the customer is currently building a cart in, so we can
    // confirm before discarding it when they switch to another restaurant.
    private MenuView activeMenu;

    public RestaurantBrowserView(Stage stage, org.example.model.User user, StackPane contentArea) {
        this.stage       = stage;
        this.user        = user;
        this.contentArea = contentArea;
    }

    /**
     * City is driven by the customer's SELECTED delivery address (multi-address
     * feature). Falls back to the legacy {@code user.getCity()} when the
     * addresses endpoint is unavailable, so the screen always works.
     */
    private String browsingCity() {
        return AddressService.resolveBrowsingCity(user);
    }

    public void loadRestaurants(String keyword) {
        String city = browsingCity();
        if (city == null) {
            contentArea.getChildren().setAll(
                    UiUtil.label("Set a delivery address to browse restaurants.", "empty-state"));
            return;
        }
        try {
            String path = "/restaurants?city=" + AddressService.enc(city);
            if (keyword != null && !keyword.isBlank()) {
                path += "&keyword=" + AddressService.enc(keyword);
            }
            String json = ApiClient.get(path, String.class);
            List<org.example.model.Restaurant> restaurants = mapper.readValue(json,
                    new TypeReference<List<org.example.model.Restaurant>>() {});
            showRestaurantList(restaurants, city, keyword);
        } catch (Exception e) {
            contentArea.getChildren().setAll(errorPane(
                    "Could not load restaurants: " + UiUtil.friendlyMessage(e)));
        }
    }

    public void showSearchBar(StackPane content) {
        String city = browsingCity();
        TextField searchField = new TextField();
        searchField.setPromptText("Search restaurants by keyword…");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("primary");
        searchBtn.setOnAction(e -> {
            if (city == null) {
                content.getChildren().setAll(
                        UiUtil.label("Please set a delivery address first.", "empty-state"));
                return;
            }
            loadRestaurants(searchField.getText().trim());
        });
        searchField.setOnAction(e -> searchBtn.fire());

        HBox bar = new HBox(10, searchField, searchBtn);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox layout = new VBox(14,
                UiUtil.label("Search Restaurants", "h2"),
                UiUtil.label("Showing results in " + (city != null ? city : "—"), "subtle"),
                bar,
                UiUtil.label("Enter a keyword and press Search.", "empty-state"));
        layout.setPadding(new Insets(24));
        content.getChildren().setAll(layout);
    }

    private void showRestaurantList(List<org.example.model.Restaurant> restaurants, String city, String keyword) {
        Label heading = UiUtil.label("Restaurants in " + city, "h2");
        Label sub = UiUtil.label(keyword == null || keyword.isBlank()
                ? "Sorted by rating." : "Results for \"" + keyword + "\".", "subtle");

        ListView<org.example.model.Restaurant> listView = new ListView<>();
        listView.getItems().setAll(restaurants);
        listView.setPlaceholder(UiUtil.label(
                "No restaurants found in " + city + ".", "empty-state"));
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(org.example.model.Restaurant r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                boolean isNew = !r.hasEnoughRatings();
                String ratingText = isNew ? "Not yet rated"
                        : String.format("★ %.1f / 5", r.getAverageRating());

                Label name = UiUtil.label(r.getName(), "h3");
                Label meta = UiUtil.label(r.getCuisineType() + "  •  " + ratingText, "subtle");
                VBox info = new VBox(2, name, meta);
                HBox.setHgrow(info, Priority.ALWAYS);

                HBox row = new HBox(10, info);
                row.setAlignment(Pos.CENTER_LEFT);

                if (isNew) {
                    row.getChildren().add(UiUtil.label("New", "badge"));
                }
                Label open = UiUtil.label("View menu →", "subtle");
                row.getChildren().add(open);

                setText(null);
                setGraphic(row);
            }
        });

        listView.setOnMouseClicked(event -> {
            org.example.model.Restaurant selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null && event.getClickCount() == 2) {
                openMenu(selected);
            }
        });

        Label hint = UiUtil.label("Double-click a restaurant to view its menu.", "muted-italic");

        VBox layout = new VBox(12, heading, sub, listView, hint);
        layout.setPadding(new Insets(24));
        contentArea.getChildren().setAll(layout);
    }

    private VBox errorPane(String message) {
        Label l = UiUtil.label(message, "status-error");
        l.setWrapText(true);
        VBox v = new VBox(l);
        v.setPadding(new Insets(24));
        return v;
    }

    private void openMenu(org.example.model.Restaurant restaurant) {
        if (activeMenu != null
                && activeMenu.hasItemsInCart()
                && activeMenu.getRestaurant().getRestaurantId() != restaurant.getRestaurantId()) {
            boolean ok = UiUtil.confirm(
                    "Switch restaurant?",
                    "You have items in your cart for "
                            + activeMenu.getRestaurant().getName() + ".",
                    "Switching to " + restaurant.getName()
                            + " will discard your current cart. Continue?");
            if (!ok) {
                return;
            }
        }
        MenuView mv = new MenuView(stage, user, restaurant, contentArea);
        activeMenu = mv;
        mv.load();
    }
}
