package org.example.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CartView {

    private final Stage      stage;
    private final org.example.model.User       user;
    private final org.example.model.Restaurant restaurant;
    private final List<MenuView.CartEntry> cart;
    private final StackPane  contentArea;

    public CartView(Stage stage, org.example.model.User user, org.example.model.Restaurant restaurant,
                    List<MenuView.CartEntry> cart, StackPane contentArea) {
        this.stage       = stage;
        this.user        = user;
        this.restaurant  = restaurant;
        this.cart        = cart;
        this.contentArea = contentArea;
    }

    public void load() {
        Label title = UiUtil.label("Your Cart", "h2");
        Label sub   = UiUtil.label(restaurant.getName(), "subtle");

        VBox itemsBox = new VBox(8);
        double total  = 0;
        for (MenuView.CartEntry entry : cart) {
            double subPrice = entry.item.getPrice() * entry.quantity;
            total += subPrice;
            Label nameL = UiUtil.label(entry.item.getName() + "  ×" + entry.quantity, "label");
            HBox.setHgrow(nameL, Priority.ALWAYS);
            nameL.setMaxWidth(Double.MAX_VALUE);
            Label priceL = UiUtil.label(String.format(java.util.Locale.ROOT, "%.2f TL", subPrice), "price-tag");
            HBox row = new HBox(10, nameL, priceL);
            row.getStyleClass().add("item-row");
            itemsBox.getChildren().add(row);
        }
        if (cart.isEmpty()) {
            itemsBox.getChildren().add(UiUtil.label("Your cart is empty.", "empty-state"));
        }

        Label totalLabel = UiUtil.label("Subtotal: " + String.format(java.util.Locale.ROOT, "%.2f TL", total), "h3");

        TextField couponField = new TextField();
        couponField.setPromptText("Coupon code (optional)");
        Button checkBtn = new Button("Check");
        HBox couponRow = new HBox(8, couponField, checkBtn);
        HBox.setHgrow(couponField, Priority.ALWAYS);

        Label couponPreview = UiUtil.label("", "subtle");
        couponPreview.setWrapText(true);

        checkBtn.setOnAction(e -> {
            String code = couponField.getText().trim();
            if (code.isBlank()) {
                couponPreview.setText("");
                return;
            }
            try {
                String path = "/restaurants/coupons/validate?code="
                        + java.net.URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8)
                        + "&restaurantId=" + restaurant.getRestaurantId();
                String json = ApiClient.get(path, String.class);
                com.fasterxml.jackson.databind.JsonNode node = ApiClient.mapper().readTree(json);
                String type  = node.get("discountType").asText();
                double value = node.get("discountValue").asDouble();
                String desc  = "PERCENTAGE".equals(type)
                        ? String.format(java.util.Locale.ROOT, "%.0f%% off", value)
                        : String.format(java.util.Locale.ROOT, "%.2f TL off", value);
                couponPreview.setText("✓ " + node.get("code").asText() + " — " + desc);
                couponPreview.getStyleClass().setAll("status-success");
            } catch (Exception ex) {
                couponPreview.setText("✗ " + UiUtil.friendlyMessage(ex));
                couponPreview.getStyleClass().setAll("status-error");
            }
        });

        Button placeOrderBtn = new Button("Place Order");
        placeOrderBtn.getStyleClass().add("primary");
        Button backBtn = new Button("Back to Menu");
        Label  statusLabel = UiUtil.label("", "subtle");
        statusLabel.setWrapText(true);

        placeOrderBtn.setOnAction(e ->
                placeOrder(couponField.getText().trim(), statusLabel, placeOrderBtn));
        backBtn.setOnAction(e -> contentArea.getChildren().setAll(
                wrapEmpty("Go back to the menu to keep ordering.")));

        HBox actions = new HBox(10, placeOrderBtn, backBtn);

        VBox card = new VBox(14, title, sub, new Separator(), itemsBox,
                new Separator(), totalLabel,
                LoginView.fieldGroup("Coupon", couponRow),
                couponPreview, actions, statusLabel);
        card.getStyleClass().add("panel");
        card.setMaxWidth(560);

        VBox layout = new VBox(card);
        layout.setPadding(new Insets(24));
        contentArea.getChildren().setAll(layout);
    }

    private VBox wrapEmpty(String msg) {
        VBox v = new VBox(UiUtil.label(msg, "empty-state"));
        v.setPadding(new Insets(24));
        return v;
    }

    private void placeOrder(String couponCode, Label statusLabel, Button placeOrderBtn) {
        if (cart.isEmpty()) {
            statusLabel.setText("Cart is empty.");
            statusLabel.getStyleClass().setAll("status-error");
            return;
        }
        placeOrderBtn.setDisable(true);
        try {
            List<Map<String, Object>> items = cart.stream().map(ce -> Map.<String, Object>of(
                    "itemId",    ce.item.getItemId(),
                    "quantity",  ce.quantity,
                    "unitPrice", ce.item.getPrice()
            )).collect(Collectors.toList());

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("customerId",   user.getUserId());
            body.put("restaurantId", restaurant.getRestaurantId());
            body.put("couponCode",   couponCode);
            body.put("items",        items);

            ApiClient.post("/orders", body);
            statusLabel.setText("Order placed — status: Preparing (not yet sent). "
                    + "Go to 'My Orders' to send it to the restaurant.");
            statusLabel.getStyleClass().setAll("status-success");
            statusLabel.setWrapText(true);
            cart.clear();
        } catch (Exception ex) {
            statusLabel.setText("Could not place order: " + UiUtil.friendlyMessage(ex));
            statusLabel.getStyleClass().setAll("status-error");
        } finally {
            placeOrderBtn.setDisable(false);
        }
    }
}
