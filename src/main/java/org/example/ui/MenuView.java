package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MenuView {

    private final Stage       stage;
    private final org.example.model.User        user;
    private final org.example.model.Restaurant  restaurant;
    private final StackPane   contentArea;
    private final ObjectMapper mapper = ApiClient.mapper();

    // Cart stored as list of (item, quantity) pairs
    private final List<CartEntry> cart = new ArrayList<>();
    private Button viewCartBtn;

    /** True if the customer currently has at least one item in this menu's cart. */
    public boolean hasItemsInCart() {
        return cart.stream().anyMatch(ce -> ce.quantity > 0);
    }

    public org.example.model.Restaurant getRestaurant() {
        return restaurant;
    }

    public MenuView(Stage stage, org.example.model.User user, org.example.model.Restaurant restaurant, StackPane contentArea) {
        this.stage       = stage;
        this.user        = user;
        this.restaurant  = restaurant;
        this.contentArea = contentArea;
    }

    public void load() {
        try {
            String catJson   = ApiClient.get("/restaurants/" + restaurant.getRestaurantId() + "/categories", String.class);
            String menuJson  = ApiClient.get("/restaurants/" + restaurant.getRestaurantId() + "/menu", String.class);
            List<org.example.model.MenuCategory> categories = mapper.readValue(catJson, new TypeReference<>() {});
            List<org.example.model.MenuItem>     items      = mapper.readValue(menuJson, new TypeReference<>() {});
            buildUI(categories, items);
        } catch (Exception e) {
            VBox v = new VBox(UiUtil.label(
                    "Could not load menu: " + UiUtil.friendlyMessage(e), "status-error"));
            v.setPadding(new Insets(24));
            contentArea.getChildren().setAll(v);
        }
    }

    private void buildUI(List<org.example.model.MenuCategory> categories, List<org.example.model.MenuItem> items) {
        Label title = UiUtil.label(restaurant.getName(), "h2");
        Label sub   = UiUtil.label(restaurant.getCuisineType() + " • Menu", "subtle");

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        for (org.example.model.MenuCategory cat : categories) {
            VBox catBox = new VBox(8);
            catBox.setPadding(new Insets(14));
            for (org.example.model.MenuItem item : items) {
                if (item.getCategoryId() == cat.getCategoryId()) {
                    catBox.getChildren().add(buildItemRow(item));
                }
            }
            if (catBox.getChildren().isEmpty()) {
                catBox.getChildren().add(UiUtil.label(
                        "No items in this category yet.", "empty-state"));
            }
            ScrollPane sp = new ScrollPane(catBox);
            sp.setFitToWidth(true);
            tabPane.getTabs().add(new Tab(cat.getName(), sp));
        }
        if (categories.isEmpty()) {
            tabPane.getTabs().add(new Tab("Menu",
                    UiUtil.label("This restaurant has no menu yet.", "empty-state")));
        }

        viewCartBtn = new Button("View Cart (0 items)");
        viewCartBtn.getStyleClass().add("primary");
        viewCartBtn.setOnAction(e -> openCart());
        HBox actionBar = new HBox(viewCartBtn);
        actionBar.setAlignment(Pos.CENTER_RIGHT);

        VBox layout = new VBox(12, title, sub, tabPane, actionBar);
        layout.setPadding(new Insets(24));
        contentArea.getChildren().setAll(layout);
    }

    private HBox buildItemRow(org.example.model.MenuItem item) {
        Label nameLabel = UiUtil.label(item.getName(), "h3");
        Label priceLabel = UiUtil.label(String.format(java.util.Locale.ROOT, "%.2f TL", item.getPrice()), "price-tag");
        priceLabel.setPrefWidth(90);

        Text descText = new Text(item.getDescription() == null ? "" : item.getDescription());
        descText.setWrappingWidth(360);
        descText.setStyle("-fx-fill: #6b7280;");

        VBox details = new VBox(3, nameLabel, descText);
        HBox.setHgrow(details, Priority.ALWAYS);

        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("primary");
        addBtn.setOnAction(e -> {
            cart.stream().filter(ce -> ce.item.getItemId() == item.getItemId())
                    .findFirst().ifPresentOrElse(
                            ce -> ce.quantity++,
                            () -> cart.add(new CartEntry(item, 1))
                    );
            int total = cart.stream().mapToInt(ce -> ce.quantity).sum();
            if (viewCartBtn != null) viewCartBtn.setText("View Cart (" + total + " items)");
        });

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("item-row");

        ImageView image = tryLoadImage(item.getImagePath());
        if (image != null) row.getChildren().add(image);
        row.getChildren().addAll(details, priceLabel, addBtn);
        return row;
    }

    /**
     * Returns a sized ImageView for the given path, or null if the path is
     * empty or the image fails to load. Tries the path as a local file first,
     * then as a URL. Never throws.
     */
    private ImageView tryLoadImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) return null;
        Image img = null;
        try {
            File f = new File(imagePath);
            if (f.isFile()) {
                img = new Image(f.toURI().toString(), 80, 80, true, true);
            } else {
                img = new Image(imagePath, 80, 80, true, true);
            }
            if (img.isError()) return null;
        } catch (Exception ignored) {
            return null;
        }
        ImageView view = new ImageView(img);
        view.setFitWidth(80);
        view.setFitHeight(80);
        view.setPreserveRatio(true);
        return view;
    }

    private void openCart() {
        CartView cv = new CartView(stage, user, restaurant, cart, contentArea);
        cv.load();
    }

    static class CartEntry {
        org.example.model.MenuItem item;
        int quantity;
        CartEntry(org.example.model.MenuItem item, int qty) { this.item = item; this.quantity = qty; }
    }
}
