package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RestaurantManagementView {

    private final Stage       stage;
    private final org.example.model.User        user;
    private final StackPane   contentArea;
    private final ObjectMapper mapper;

    public RestaurantManagementView(Stage stage, org.example.model.User user, StackPane contentArea) {
        this.stage       = stage;
        this.user        = user;
        this.contentArea = contentArea;
        this.mapper      = ApiClient.mapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void load() {
        try {
            String json = ApiClient.get("/restaurants/manager/" + user.getUserId(), String.class);
            List<Map<String, Object>> restaurants = mapper.readValue(json, new TypeReference<>() {});
            buildUI(restaurants);
        } catch (Exception e) {
            VBox v = new VBox(UiUtil.label(
                    "Error: " + UiUtil.friendlyMessage(e), "status-error"));
            v.setPadding(new Insets(24));
            contentArea.getChildren().setAll(v);
        }
    }

    private void buildUI(List<Map<String, Object>> restaurants) {
        Label title = UiUtil.label("My Restaurants", "h2");

        ListView<String> listView = new ListView<>();
        for (Map<String, Object> r : restaurants) {
            listView.getItems().add(r.get("name") + " — " + r.get("cuisineType") + " (" + r.get("city") + ")");
        }
        listView.setPlaceholder(UiUtil.label(
                "You have no restaurants yet. Add one below.", "empty-state"));

        // Action buttons (shown on selection)
        Button menuBtn   = new Button("Manage Menu");
        Button couponBtn = new Button("Manage Coupons");
        Button editBtn   = new Button("Edit Restaurant");
        menuBtn.getStyleClass().add("primary");
        menuBtn.setDisable(true);
        couponBtn.setDisable(true);
        editBtn.setDisable(true);

        TitledPane editPane = new TitledPane("Edit Restaurant", new Label("Select a restaurant to edit."));
        editPane.setExpanded(false);

        listView.setOnMouseClicked(e -> {
            int idx = listView.getSelectionModel().getSelectedIndex();
            boolean selected = idx >= 0;
            menuBtn.setDisable(!selected);
            couponBtn.setDisable(!selected);
            editBtn.setDisable(!selected);
            if (selected) {
                Map<String, Object> r = restaurants.get(idx);
                int    id   = (Integer) r.get("restaurantId");
                String name = (String)  r.get("name");
                menuBtn.setOnAction(ev -> {
                    new MenuManagementView(stage, user, id, name, contentArea).load();
                });
                couponBtn.setOnAction(ev -> {
                    new CouponManagementView(stage, user, id, name, contentArea).load();
                });
                editBtn.setOnAction(ev -> openEditForm(id, editPane));
            }
        });

        HBox actionBar = new HBox(10, menuBtn, couponBtn, editBtn);

        // ── Add new restaurant form ─────────────────────────────────────────
        TitledPane addPane = buildAddForm();

        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox layout = new VBox(14, title,
                UiUtil.label("Select a restaurant to manage its menu, coupons or details.",
                        "subtle"),
                listView, actionBar, editPane, addPane);
        layout.setPadding(new Insets(24));
        contentArea.getChildren().setAll(layout);
    }

    private void openEditForm(int restaurantId, TitledPane editPane) {
        try {
            String json = ApiClient.get("/restaurants/" + restaurantId, String.class);
            Map<String, Object> r = mapper.readValue(json, new TypeReference<>() {});
            editPane.setContent(buildEditForm(restaurantId, r));
            editPane.setExpanded(true);
        } catch (Exception ex) {
            editPane.setContent(new Label("Could not load restaurant: " + UiUtil.friendlyMessage(ex)));
            editPane.setExpanded(true);
        }
    }

    private VBox buildEditForm(int restaurantId, Map<String, Object> r) {
        TextField nameField     = new TextField(stringOrEmpty(r.get("name")));
        TextField cuisineField  = new TextField(stringOrEmpty(r.get("cuisineType")));
        TextField addressField  = new TextField(stringOrEmpty(r.get("address")));
        TextField cityField     = new TextField(stringOrEmpty(r.get("city")));
        TextField keywordsField = new TextField(joinKeywords(r.get("keywords")));

        nameField.setPromptText("Restaurant name");
        cuisineField.setPromptText("Cuisine type");
        addressField.setPromptText("Address");
        cityField.setPromptText("City");
        keywordsField.setPromptText("Keywords (comma-separated)");

        Button saveBtn   = new Button("Save Changes");
        Label  statusLbl = new Label();
        statusLbl.setWrapText(true);

        saveBtn.setOnAction(e -> {
            String name    = nameField.getText() == null ? "" : nameField.getText().trim();
            String cuisine = cuisineField.getText() == null ? "" : cuisineField.getText().trim();
            String address = addressField.getText() == null ? "" : addressField.getText().trim();
            String city    = cityField.getText() == null ? "" : cityField.getText().trim();

            String error = null;
            if (name.isEmpty())         error = "Restaurant name is required.";
            else if (cuisine.isEmpty()) error = "Cuisine type is required.";
            else if (address.isEmpty()) error = "Address is required.";
            else if (city.isEmpty())    error = "City is required.";
            if (error != null) {
                statusLbl.setText(error);
                statusLbl.getStyleClass().setAll("status-error");
                return;
            }

            saveBtn.setDisable(true);
            try {
                List<String> keywords = Arrays.stream(
                                (keywordsField.getText() == null ? "" : keywordsField.getText()).split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("managerId",   user.getUserId());
                payload.put("name",        name);
                payload.put("cuisineType", cuisine);
                payload.put("address",     address);
                payload.put("city",        city);
                payload.put("keywords",    keywords);
                ApiClient.put("/restaurants/" + restaurantId, payload);
                statusLbl.setText("Restaurant updated.");
                statusLbl.getStyleClass().setAll("status-success");
                load();
            } catch (Exception ex) {
                statusLbl.setText("Could not update restaurant: " + UiUtil.friendlyMessage(ex));
                statusLbl.getStyleClass().setAll("status-error");
                saveBtn.setDisable(false);
            }
        });

        VBox form = new VBox(8, nameField, cuisineField, addressField, cityField, keywordsField, saveBtn, statusLbl);
        form.setPadding(new Insets(10));
        return form;
    }

    private TitledPane buildAddForm() {
        TextField nameField     = new TextField(); nameField.setPromptText("Restaurant name");
        TextField cuisineField  = new TextField(); cuisineField.setPromptText("Cuisine type");
        TextField addressField  = new TextField(); addressField.setPromptText("Address");
        TextField cityField     = new TextField(); cityField.setPromptText("City");
        TextField keywordsField = new TextField(); keywordsField.setPromptText("Keywords (comma-separated)");

        Button saveBtn   = new Button("Save");
        Label  statusLbl = new Label();

        statusLbl.setWrapText(true);

        saveBtn.setOnAction(e -> {
            String name    = nameField.getText() == null ? "" : nameField.getText().trim();
            String cuisine = cuisineField.getText() == null ? "" : cuisineField.getText().trim();
            String address = addressField.getText() == null ? "" : addressField.getText().trim();
            String city    = cityField.getText() == null ? "" : cityField.getText().trim();

            String error = null;
            if (name.isEmpty())         error = "Restaurant name is required.";
            else if (cuisine.isEmpty()) error = "Cuisine type is required.";
            else if (address.isEmpty()) error = "Address is required.";
            else if (city.isEmpty())    error = "City is required.";
            if (error != null) {
                statusLbl.setText(error);
                statusLbl.getStyleClass().setAll("status-error");
                return;
            }

            saveBtn.setDisable(true);
            try {
                List<String> keywords = Arrays.stream(
                                (keywordsField.getText() == null ? "" : keywordsField.getText()).split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
                ApiClient.post("/restaurants", Map.of(
                        "managerId",   user.getUserId(),
                        "name",        name,
                        "cuisineType", cuisine,
                        "address",     address,
                        "city",        city,
                        "keywords",    keywords
                ));
                statusLbl.setText("Restaurant added!");
                statusLbl.getStyleClass().setAll("status-success");
                load();
            } catch (Exception ex) {
                statusLbl.setText("Could not add restaurant: " + UiUtil.friendlyMessage(ex));
                statusLbl.getStyleClass().setAll("status-error");
                saveBtn.setDisable(false);
            }
        });

        VBox form = new VBox(8, nameField, cuisineField, addressField, cityField, keywordsField, saveBtn, statusLbl);
        form.setPadding(new Insets(10));
        TitledPane pane = new TitledPane("Add New Restaurant", form);
        pane.setExpanded(false);
        return pane;
    }

    private static String stringOrEmpty(Object v) {
        return v == null ? "" : v.toString();
    }

    @SuppressWarnings("unchecked")
    private static String joinKeywords(Object kw) {
        if (kw instanceof List<?> list) {
            return String.join(", ", (List<String>) list);
        }
        return "";
    }
}
