package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IncomingOrdersView {

    private final Stage       stage;
    private final org.example.model.User        user;
    private final StackPane   contentArea;
    private final ObjectMapper mapper;

    public IncomingOrdersView(Stage stage, org.example.model.User user, StackPane contentArea) {
        this.stage       = stage;
        this.user        = user;
        this.contentArea = contentArea;
        this.mapper      = ApiClient.mapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void load() {
        try {
            String restJson = ApiClient.get("/restaurants/manager/" + user.getUserId(), String.class);
            List<Map<String, Object>> restaurants = mapper.readValue(restJson, new TypeReference<>() {});

            // Collect SENT and PREPARING orders from every managed restaurant.
            List<Map<String, Object>> allPending = new ArrayList<>();
            for (Map<String, Object> r : restaurants) {
                int restaurantId = (Integer) r.get("restaurantId");
                String rName     = (String)  r.get("name");
                String ordJson   = ApiClient.get("/orders/restaurant/" + restaurantId + "/pending", String.class);
                List<Map<String, Object>> orders = mapper.readValue(ordJson, new TypeReference<>() {});
                for (Map<String, Object> o : orders) {
                    o.put("_restaurantName", rName);
                    allPending.add(o);
                }
            }

            buildUI(allPending);
        } catch (Exception e) {
            VBox v = new VBox(UiUtil.label(
                    "Could not load orders: " + UiUtil.friendlyMessage(e), "status-error"));
            v.setPadding(new Insets(24));
            contentArea.getChildren().setAll(v);
        }
    }

    private void buildUI(List<Map<String, Object>> orders) {
        Label title = UiUtil.label("Incoming Orders", "h2");

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> load());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, spacer, refreshBtn);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        ListView<String> listView = new ListView<>();
        for (Map<String, Object> o : orders) {
            listView.getItems().add(formatOrderRow(o));
        }
        listView.setPlaceholder(UiUtil.label(
                "No pending orders right now.", "empty-state"));

        VBox detailBox = new VBox(8);
        detailBox.setPadding(new Insets(10));

        listView.setOnMouseClicked(event -> {
            int idx = listView.getSelectionModel().getSelectedIndex();
            if (idx >= 0) showDetail(orders.get(idx), detailBox);
        });

        detailBox.getStyleClass().add("panel");
        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox layout = new VBox(14, header, listView, detailBox);
        layout.setPadding(new Insets(24));
        contentArea.getChildren().setAll(layout);
    }

    private void showDetail(Map<String, Object> order, VBox detailBox) {
        detailBox.getChildren().clear();
        int    orderId = (Integer) order.get("orderId");
        String status  = (String)  order.getOrDefault("status", "SENT");
        try {
            String itemJson = ApiClient.get("/orders/" + orderId + "/items", String.class);
            List<Map<String, Object>> items = mapper.readValue(itemJson, new TypeReference<>() {});

            detailBox.getChildren().add(new Label("Order #" + orderId + " [" + status + "] items:"));
            for (Map<String, Object> item : items) {
                String name  = (String) item.getOrDefault("itemName", "Item");
                int    qty   = (Integer) item.get("quantity");
                double price = ((Number) item.get("unitPrice")).doubleValue();
                detailBox.getChildren().add(new Label("  " + name + " x" + qty
                        + "  =  " + String.format(java.util.Locale.ROOT, "%.2f TL", qty * price)));
            }

            Label statusLabel = new Label();

            if ("SENT".equals(status)) {
                // Manager accepts: SENT → PREPARING
                Button acceptBtn = new Button("Accept Order");
                acceptBtn.getStyleClass().add("primary");
                acceptBtn.setOnAction(e -> {
                    acceptBtn.setDisable(true);
                    try {
                        ApiClient.put("/orders/" + orderId + "/accept",
                                Map.of("managerId", user.getUserId()));
                        statusLabel.setText("Order accepted — now preparing.");
                        statusLabel.getStyleClass().setAll("status-success");
                        load();
                    } catch (Exception ex) {
                        statusLabel.setText("Could not accept: " + UiUtil.friendlyMessage(ex));
                        statusLabel.getStyleClass().setAll("status-error");
                        acceptBtn.setDisable(false);
                    }
                });
                detailBox.getChildren().addAll(acceptBtn, statusLabel);

            } else if ("PREPARING".equals(status)) {
                // Manager marks delivered: PREPARING → ARRIVED
                Button arriveBtn = new Button("Mark Delivered");
                arriveBtn.getStyleClass().add("primary");
                arriveBtn.setOnAction(e -> {
                    arriveBtn.setDisable(true);
                    try {
                        ApiClient.put("/orders/" + orderId + "/arrive",
                                Map.of("managerId", user.getUserId()));
                        statusLabel.setText("Order marked as delivered.");
                        statusLabel.getStyleClass().setAll("status-success");
                        load();
                    } catch (Exception ex) {
                        statusLabel.setText("Could not mark delivered: " + UiUtil.friendlyMessage(ex));
                        statusLabel.getStyleClass().setAll("status-error");
                        arriveBtn.setDisable(false);
                    }
                });
                detailBox.getChildren().addAll(arriveBtn, statusLabel);
            }
        } catch (Exception e) {
            detailBox.getChildren().add(
                    new Label("Error loading items: " + UiUtil.friendlyMessage(e)));
        }
    }

    private String formatOrderRow(Map<String, Object> o) {
        int    orderId  = (Integer) o.get("orderId");
        double total    = ((Number) o.get("totalPrice")).doubleValue();
        String rName    = (String) o.getOrDefault("_restaurantName", "");
        String created  = (String) o.getOrDefault("createdAt", "");
        String status   = (String) o.getOrDefault("status", "SENT");
        return "#" + orderId + " | " + rName + " | [" + status + "] | "
                + String.format(java.util.Locale.ROOT, "%.2f TL", total) + " | " + created;
    }
}
