package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SalesStatisticsView {

    private final org.example.model.User      user;
    private final Stage     stage;
    private final StackPane contentArea;
    private final ObjectMapper mapper;

    private List<Map<String, Object>> restaurants;

    public SalesStatisticsView(org.example.model.User user, StackPane contentArea) {
        this(null, user, contentArea);
    }

    public SalesStatisticsView(Stage stage, org.example.model.User user, StackPane contentArea) {
        this.user        = user;
        this.stage       = stage;
        this.contentArea = contentArea;
        this.mapper      = ApiClient.mapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void load() {
        try {
            String restJson = ApiClient.get("/restaurants/manager/" + user.getUserId(), String.class);
            restaurants = mapper.readValue(restJson, new TypeReference<>() {});
            if (restaurants == null || restaurants.isEmpty()) {
                contentArea.getChildren().setAll(new Label("No restaurants found."));
                return;
            }
            buildShell();
        } catch (Exception e) {
            contentArea.getChildren().setAll(
                    new Label("Error loading statistics: " + UiUtil.friendlyMessage(e)));
        }
    }

    /** Builds the outer shell with a restaurant picker that auto-loads stats on selection. */
    private void buildShell() {
        Label title = UiUtil.label("Monthly Sales Statistics", "h2");

        ComboBox<String> picker = new ComboBox<>();
        for (Map<String, Object> r : restaurants) {
            picker.getItems().add(asText(r.get("name")));
        }

        Button refreshBtn = new Button("Refresh");
        Button ratingsBtn = new Button("View Ratings");
        ratingsBtn.getStyleClass().add("primary");

        VBox statsBox = new VBox(8);
        statsBox.setPadding(new Insets(10, 0, 0, 0));

        ScrollPane scroller = new ScrollPane(statsBox);
        scroller.setFitToWidth(true);
        VBox.setVgrow(scroller, Priority.ALWAYS);

        Runnable loadSelected = () -> {
            int idx = picker.getSelectionModel().getSelectedIndex();
            if (idx < 0) idx = 0;
            renderStats(restaurants.get(idx), statsBox);
        };

        picker.setOnAction(e -> loadSelected.run());
        refreshBtn.setOnAction(e -> loadSelected.run());
        ratingsBtn.setOnAction(e -> {
            int idx = picker.getSelectionModel().getSelectedIndex();
            if (idx < 0) idx = 0;
            new ManagerRatingsView(stage, user, contentArea, this)
                    .load(restaurants.get(idx));
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(10, title, spacer, picker, refreshBtn, ratingsBtn);
        topBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 5, 0));

        VBox layout = new VBox(12, topBar, new Separator(), scroller);
        layout.setPadding(new Insets(24));
        contentArea.getChildren().setAll(layout);

        // Auto-load the first restaurant immediately.
        picker.getSelectionModel().selectFirst();
        loadSelected.run();
    }

    @SuppressWarnings("unchecked")
    private void renderStats(Map<String, Object> restaurant, VBox box) {
        box.getChildren().clear();
        Map<String, Object> stats;
        try {
            int restaurantId = ((Number) restaurant.get("restaurantId")).intValue();
            String statsJson = ApiClient.get(
                    "/statistics/restaurant/" + restaurantId + "/monthly", String.class);
            stats = mapper.readValue(statsJson, new TypeReference<>() {});
            if (stats == null) stats = Map.of();
        } catch (Exception e) {
            box.getChildren().setAll(
                    new Label("Error loading stats: " + UiUtil.friendlyMessage(e)));
            return;
        }

        Label header = UiUtil.label("Restaurant: " + asText(restaurant.get("name")), "h3");
        box.getChildren().add(header);

        // 1. Total revenue
        box.getChildren().add(metric("Total Revenue",
                money(firstPresent(stats, "totalRevenue", "revenue", "total_revenue"))));

        // 2. Total number of orders
        box.getChildren().add(metric("Total Orders",
                asText(firstPresent(stats, "totalOrders", "orderCount", "total_orders"))));

        // 4. Customer with most orders
        box.getChildren().add(metric("Customer With Most Orders",
                describeCustomer(firstPresent(stats,
                        "topCustomerByOrders", "mostOrdersCustomer", "customerWithMostOrders"))));

        // 5. Customer with highest-value order + the order details
        box.getChildren().add(metric("Highest-Value-Order Customer",
                describeCustomer(firstPresent(stats,
                        "topCustomerByValue", "highestValueOrderCustomer", "customerWithHighestOrder"))));
        Object hvOrder = firstPresent(stats,
                "highestValueOrder", "topOrder", "highestOrder", "highestValueOrderDetails");
        box.getChildren().add(orderDetailNode(hvOrder));

        // 6. Most-ordered item
        box.getChildren().add(metric("Most Ordered Item",
                describeItem(firstPresent(stats,
                        "mostOrderedItem", "topItem", "most_ordered_item"))));

        // 7. Top-revenue category
        box.getChildren().add(metric("Top Revenue Category",
                asText(firstPresent(stats,
                        "topRevenueCategory", "bestCategory", "top_revenue_category"))));

        // 8. Total coupon discount
        box.getChildren().add(metric("Total Coupon Discount",
                money(firstPresent(stats,
                        "totalDiscount", "totalCouponDiscount", "couponDiscount", "total_discount"))));

        // 3. Per-item quantity & revenue table
        box.getChildren().add(new Separator());
        Label itemsLabel = new Label("Per-Item Quantity & Revenue");
        itemsLabel.setStyle("-fx-font-weight: bold;");
        box.getChildren().add(itemsLabel);
        box.getChildren().add(buildItemTable(stats));
    }

    /** Accepts either a list of {name,qty,revenue} objects or a map of name -> {qty,revenue}. */
    @SuppressWarnings("unchecked")
    private TableView<ItemRow> buildItemTable(Map<String, Object> stats) {
        TableView<ItemRow> table = new TableView<>();

        TableColumn<ItemRow, String> nameCol = new TableColumn<>("Item");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(220);

        TableColumn<ItemRow, String> qtyCol = new TableColumn<>("Quantity");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        qtyCol.setPrefWidth(120);

        TableColumn<ItemRow, String> revCol = new TableColumn<>("Revenue");
        revCol.setCellValueFactory(new PropertyValueFactory<>("revenue"));
        revCol.setPrefWidth(150);

        table.getColumns().add(nameCol);
        table.getColumns().add(qtyCol);
        table.getColumns().add(revCol);
        table.setPrefHeight(260);
        table.setPlaceholder(new Label("—"));

        Object raw = firstPresent(stats,
                "itemRevenue", "perItem", "itemStats", "items", "perItemStats");
        List<ItemRow> rows = new ArrayList<>();
        try {
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, Object> d = (Map<String, Object>) m;
                        rows.add(new ItemRow(
                                asText(firstPresent(d, "name", "itemName", "item")),
                                asText(firstPresent(d, "qty", "quantity", "totalQuantity")),
                                money(firstPresent(d, "revenue", "totalRevenue", "amount"))));
                    }
                }
            } else if (raw instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    Object v = e.getValue();
                    if (v instanceof Map<?, ?> m) {
                        Map<String, Object> d = (Map<String, Object>) m;
                        rows.add(new ItemRow(
                                asText(e.getKey()),
                                asText(firstPresent(d, "qty", "quantity", "totalQuantity")),
                                money(firstPresent(d, "revenue", "totalRevenue", "amount"))));
                    } else {
                        rows.add(new ItemRow(asText(e.getKey()), "—", money(v)));
                    }
                }
            }
        } catch (Exception ignored) {
            // leave rows as-is; table placeholder shows "—"
        }
        table.getItems().setAll(rows);
        return table;
    }

    private Node metric(String label, String value) {
        Label l = new Label(label + ":  ");
        l.setStyle("-fx-font-weight: bold;");
        Label v = new Label(value);
        HBox row = new HBox(4, l, v);
        return row;
    }

    @SuppressWarnings("unchecked")
    private Node orderDetailNode(Object order) {
        VBox v = new VBox(2);
        v.setPadding(new Insets(0, 0, 0, 18));
        if (!(order instanceof Map)) {
            v.getChildren().add(new Label("Order details: —"));
            return v;
        }
        Map<String, Object> o = (Map<String, Object>) order;
        v.getChildren().add(new Label("Order #"
                + asText(firstPresent(o, "orderId", "id", "order_id"))
                + "  |  Total: "
                + money(firstPresent(o, "totalPrice", "total", "amount", "value"))));
        Object items = firstPresent(o, "items", "orderItems", "lines");
        if (items instanceof List<?> list) {
            for (Object it : list) {
                if (it instanceof Map<?, ?> m) {
                    Map<String, Object> d = (Map<String, Object>) m;
                    v.getChildren().add(new Label("   "
                            + asText(firstPresent(d, "itemName", "name", "item"))
                            + " x" + asText(firstPresent(d, "quantity", "qty"))
                            + "  " + money(firstPresent(d, "unitPrice", "price", "amount"))));
                }
            }
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private String describeCustomer(Object c) {
        if (c == null) return "—";
        if (c instanceof Map<?, ?> m) {
            Map<String, Object> d = (Map<String, Object>) m;
            String name = asText(firstPresent(d,
                    "fullName", "name", "customerName", "username", "email"));
            Object count = firstPresent(d, "orderCount", "orders", "count");
            Object value = firstPresent(d, "totalValue", "totalSpent", "value");
            StringBuilder sb = new StringBuilder(name);
            if (count != null) sb.append(" (").append(count).append(" orders)");
            else if (value != null) sb.append(" (").append(money(value)).append(")");
            return sb.toString();
        }
        return asText(c);
    }

    @SuppressWarnings("unchecked")
    private String describeItem(Object i) {
        if (i == null) return "—";
        if (i instanceof Map<?, ?> m) {
            Map<String, Object> d = (Map<String, Object>) m;
            String name = asText(firstPresent(d, "name", "itemName", "item"));
            Object qty  = firstPresent(d, "qty", "quantity", "totalQuantity");
            return qty != null ? name + " (" + qty + " sold)" : name;
        }
        return asText(i);
    }

    // ---- defensive helpers ----

    private static Object firstPresent(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) return v;
        }
        return null;
    }

    private static String asText(Object o) {
        if (o == null) return "—";
        String s = String.valueOf(o);
        return s.isBlank() ? "—" : s;
    }

    private static String money(Object o) {
        if (o == null) return "—";
        try {
            return String.format(java.util.Locale.ROOT, "%.2f TL", ((Number) o).doubleValue());
        } catch (Exception e) {
            try {
                return String.format(java.util.Locale.ROOT, "%.2f TL", Double.parseDouble(String.valueOf(o)));
            } catch (Exception ex) {
                return asText(o);
            }
        }
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    /** Row backing for the per-item TableView (PropertyValueFactory needs public getters). */
    public static class ItemRow {
        private final String name;
        private final String qty;
        private final String revenue;

        public ItemRow(String name, String qty, String revenue) {
            this.name = name;
            this.qty = qty;
            this.revenue = revenue;
        }

        public String getName()    { return name; }
        public String getQty()     { return qty; }
        public String getRevenue() { return revenue; }
    }
}
