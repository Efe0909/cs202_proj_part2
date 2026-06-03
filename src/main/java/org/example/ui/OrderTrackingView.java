package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.util.List;

public class OrderTrackingView {

    private final Stage     stage;
    private final org.example.model.User      user;
    private final StackPane contentArea;
    private final ObjectMapper mapper;
    private final boolean   pastMode;

    public OrderTrackingView(Stage stage, org.example.model.User user, StackPane contentArea, boolean pastMode) {
        this.stage       = stage;
        this.user        = user;
        this.contentArea = contentArea;
        this.pastMode    = pastMode;
        this.mapper      = ApiClient.mapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void load(StackPane target) {
        try {
            String url = "/orders/customer/" + user.getUserId()
                    + (pastMode ? "?status=ARRIVED" : "");
            String json = ApiClient.get(url, String.class);
            List<org.example.model.Order> orders = mapper.readValue(json, new TypeReference<>() {});
            buildUI(orders);
        } catch (Exception e) {
            VBox v = new VBox(UiUtil.label(
                    "Could not load orders: " + UiUtil.friendlyMessage(e), "status-error"));
            v.setPadding(new Insets(24));
            contentArea.getChildren().setAll(v);
        }
    }

    public void load() {
        load(contentArea);
    }

    private void buildUI(List<org.example.model.Order> orders) {
        Label title = UiUtil.label(pastMode ? "Order History" : "My Orders", "h2");

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> load());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, spacer, refreshBtn);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox rows = new VBox(8);
        Label statusLabel = UiUtil.label("", "subtle");
        statusLabel.setWrapText(true);

        for (org.example.model.Order o : orders) {
            Label info = UiUtil.label(o.toString()
                    + (o.getCreatedAt() != null ? "  •  " + o.getCreatedAt() : ""), "label");
            HBox.setHgrow(info, Priority.ALWAYS);
            info.setMaxWidth(Double.MAX_VALUE);
            HBox row = new HBox(10, info);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.getStyleClass().add("item-row");


            Button detailBtn = new Button("Details");
            detailBtn.setOnAction(e -> showOrderDetail(o));
            row.getChildren().add(detailBtn);

            rows.getChildren().add(row);
        }

        if (orders.isEmpty()) {
            rows.getChildren().add(UiUtil.label(
                    pastMode
                            ? "No completed orders yet."
                            : "You have no orders yet. Browse restaurants to place one.",
                    "empty-state"));
        }

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-pane");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox layout = new VBox(14, header, scroll, statusLabel);
        layout.setPadding(new Insets(24));
        contentArea.getChildren().setAll(layout);
    }

    private void showOrderDetail(org.example.model.Order order) {
        try {
            String json = ApiClient.get("/orders/" + order.getOrderId() + "/items", String.class);
            VBox detail = new VBox(8);
            detail.setPadding(new Insets(24));
            detail.getChildren().add(UiUtil.label("Order #" + order.getOrderId(), "h2"));
            detail.getChildren().add(UiUtil.label("Status: " + order.getStatus(), "subtle"));
            detail.getChildren().add(UiUtil.label("Items", "h3"));

            mapper.readValue(json, new TypeReference<List<org.example.model.OrderItem>>() {})
                    .forEach(oi -> detail.getChildren().add(UiUtil.label("  " + oi, "label")));

            detail.getChildren().add(UiUtil.label(
                    "Total: " + String.format(java.util.Locale.ROOT, "%.2f TL", order.getTotalPrice()), "h3"));

            Button rateBtn = new Button("Leave Rating");
            rateBtn.getStyleClass().add("primary");
            boolean canRate = order.isArrived();
            if (canRate) {
                LocalDateTime arrivedAt = order.getArrivedAt();
                boolean windowOpen = arrivedAt != null
                        && arrivedAt.isAfter(LocalDateTime.now().minusHours(24));
                if (windowOpen) {
                    rateBtn.setOnAction(e -> openRatingView(order));
                } else {
                    rateBtn.setDisable(true);
                    rateBtn.setText("Rated");
                    rateBtn.setTooltip(new Tooltip("Rating window has expired (24 hours)."));
                }
            } else {
                rateBtn.setDisable(true);
                rateBtn.setTooltip(new Tooltip(
                        "You can rate this order once it has arrived."));
            }
            detail.getChildren().add(rateBtn);

            Button backBtn = new Button("Back");
            backBtn.setOnAction(e -> load());
            detail.getChildren().add(backBtn);

            contentArea.getChildren().setAll(detail);
        } catch (Exception e) {
            VBox v = new VBox(UiUtil.label(
                    "Error: " + UiUtil.friendlyMessage(e), "status-error"));
            v.setPadding(new Insets(24));
            contentArea.getChildren().setAll(v);
        }
    }

    private void openRatingView(org.example.model.Order order) {
        RatingView rv = new RatingView(user, order, contentArea, this);
        rv.load();
    }
}
