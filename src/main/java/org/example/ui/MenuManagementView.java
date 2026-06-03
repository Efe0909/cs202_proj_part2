package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MenuManagementView {

    private final Stage       stage;
    private final org.example.model.User        user;
    private final int         restaurantId;
    private final String      restaurantName;
    private final StackPane   contentArea;
    private final ObjectMapper mapper;

    public MenuManagementView(Stage stage, org.example.model.User user, int restaurantId,
                              String restaurantName, StackPane contentArea) {
        this.stage          = stage;
        this.user           = user;
        this.restaurantId   = restaurantId;
        this.restaurantName = restaurantName;
        this.contentArea    = contentArea;
        this.mapper         = ApiClient.mapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void load() {
        try {
            String catJson  = ApiClient.get("/restaurants/" + restaurantId + "/categories", String.class);
            String menuJson = ApiClient.get("/restaurants/" + restaurantId + "/menu", String.class);
            List<Map<String, Object>> categories = mapper.readValue(catJson, new TypeReference<>() {});
            List<Map<String, Object>> items      = mapper.readValue(menuJson, new TypeReference<>() {});
            buildUI(categories, items);
        } catch (Exception e) {
            VBox v = new VBox(UiUtil.label(
                    "Error: " + UiUtil.friendlyMessage(e), "status-error"));
            v.setPadding(new Insets(24));
            contentArea.getChildren().setAll(v);
        }
    }

    private void buildUI(List<Map<String, Object>> categories, List<Map<String, Object>> items) {
        Label title = UiUtil.label("Menu — " + restaurantName, "h2");

        Button backBtn = new Button("← Back");
        backBtn.getStyleClass().add("ghost");
        backBtn.setOnAction(e -> new RestaurantManagementView(stage, user, contentArea).load());

        // ── Item list grouped by category in a TabPane ──────────────────────
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        for (Map<String, Object> cat : categories) {
            int    catId   = (Integer) cat.get("categoryId");
            String catName = (String)  cat.get("name");
            VBox catBox = new VBox(6);
            catBox.setPadding(new Insets(8));

            for (Map<String, Object> item : items) {
                if ((Integer) item.get("categoryId") == catId) {
                    catBox.getChildren().add(buildItemRow(item));
                }
            }
            ScrollPane sp = new ScrollPane(catBox);
            sp.setFitToWidth(true);
            tabPane.getTabs().add(new Tab(catName, sp));
        }
        if (categories.isEmpty()) {
            tabPane.getTabs().add(new Tab("Menu",
                    UiUtil.label("No categories yet. Add one below to start.",
                            "empty-state")));
        }

        // ── Add item form ───────────────────────────────────────────────────
        TitledPane addItemPane = buildAddItemForm(categories);

        // ── Add category form ───────────────────────────────────────────────
        TitledPane addCatPane = buildAddCategoryForm();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, backBtn, title, spacer);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox.setVgrow(tabPane, Priority.ALWAYS);
        VBox layout = new VBox(14, header, tabPane, addItemPane, addCatPane);
        layout.setPadding(new Insets(24));
        contentArea.getChildren().setAll(layout);
    }

    private VBox buildItemRow(Map<String, Object> item) {
        int    itemId = (Integer) item.get("itemId");
        String name   = (String)  item.get("name");
        double price  = ((Number) item.get("price")).doubleValue();

        Label nameLabel  = new Label(name);
        nameLabel.setPrefWidth(220);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        Label priceLabel = UiUtil.label(String.format(java.util.Locale.ROOT, "%.2f TL", price), "price-tag");
        priceLabel.setPrefWidth(90);

        Button editBtn   = new Button("Edit");
        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("danger");
        Label  status    = UiUtil.label("", "subtle");

        VBox container = new VBox(6);
        HBox row = new HBox(10, nameLabel, priceLabel, editBtn, deleteBtn, status);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getStyleClass().add("item-row");
        container.getChildren().add(row);

        editBtn.setOnAction(e -> {
            if (container.getChildren().size() > 1) {
                container.getChildren().remove(1, container.getChildren().size());
                return;
            }
            container.getChildren().add(buildEditItemForm(item, status));
        });

        deleteBtn.setOnAction(e -> {
            if (!UiUtil.confirm("Delete item?", "Delete \"" + name + "\"?",
                    "This will remove the menu item permanently.")) {
                return;
            }
            deleteBtn.setDisable(true);
            try {
                ApiClient.delete("/restaurants/menu/" + itemId);
                load();
            } catch (Exception ex) {
                status.setText("Could not delete: " + UiUtil.friendlyMessage(ex));
                status.getStyleClass().setAll("status-error");
                deleteBtn.setDisable(false);
            }
        });
        return container;
    }

    private VBox buildEditItemForm(Map<String, Object> item, Label rowStatus) {
        int     itemId      = (Integer) item.get("itemId");
        int     currentCat  = ((Number) item.get("categoryId")).intValue();
        String  currentName = (String)  item.get("name");
        String  currentDesc = item.get("description") == null ? "" : item.get("description").toString();
        double  currentPrice = ((Number) item.get("price")).doubleValue();
        String  currentImg   = item.get("imagePath") == null ? "" : item.get("imagePath").toString();

        TextField nameField  = new TextField(currentName);
        TextField descField  = new TextField(currentDesc);
        TextField priceField = new TextField(Double.toString(currentPrice));
        TextField imgField   = new TextField(currentImg);
        nameField.setPromptText("Item name");
        descField.setPromptText("Description");
        priceField.setPromptText("Price");
        imgField.setPromptText("Image path");

        Button saveBtn = new Button("Save Changes");
        saveBtn.getStyleClass().add("primary");
        Label  status  = new Label();
        status.setWrapText(true);

        saveBtn.setOnAction(e -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            String desc = descField.getText() == null ? "" : descField.getText().trim();
            String img  = imgField.getText()  == null ? "" : imgField.getText().trim();
            if (name.isEmpty()) { showError(status, "Item name must not be empty.");        return; }
            if (desc.isEmpty()) { showError(status, "Description must not be empty.");      return; }
            if (img.isEmpty())  { showError(status, "Image path must not be empty.");       return; }
            double price;
            try {
                price = Double.parseDouble(priceField.getText().trim());
            } catch (NumberFormatException | NullPointerException nfe) {
                showError(status, "Price must be a number.");
                return;
            }
            if (price <= 0) {
                showError(status, "Price must be a positive number.");
                return;
            }

            saveBtn.setDisable(true);
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("managerId",   user.getUserId());
                payload.put("categoryId",  currentCat);
                payload.put("name",        name);
                payload.put("description", desc);
                payload.put("price",       price);
                payload.put("imagePath",   img);
                ApiClient.put("/restaurants/menu/" + itemId, payload);
                rowStatus.setText("Updated.");
                rowStatus.getStyleClass().setAll("status-success");
                load();
            } catch (Exception ex) {
                showError(status, UiUtil.friendlyMessage(ex));
                saveBtn.setDisable(false);
            }
        });

        VBox form = new VBox(6, nameField, descField, priceField, imgField, saveBtn, status);
        form.setPadding(new Insets(6, 0, 6, 20));
        return form;
    }

    private TitledPane buildAddItemForm(List<Map<String, Object>> categories) {
        ChoiceBox<String> catChoice = new ChoiceBox<>();
        for (Map<String, Object> c : categories) catChoice.getItems().add(c.get("name") + "|" + c.get("categoryId"));
        if (!catChoice.getItems().isEmpty()) catChoice.setValue(catChoice.getItems().get(0));

        TextField nameField  = new TextField(); nameField.setPromptText("Item name");
        TextField descField  = new TextField(); descField.setPromptText("Description");
        TextField priceField = new TextField(); priceField.setPromptText("Price");
        TextField imgField   = new TextField(); imgField.setPromptText("Image path (optional)");

        Button saveBtn = new Button("Add Item");
        saveBtn.getStyleClass().add("primary");
        Label  status  = new Label();

        saveBtn.setOnAction(e -> {
            // ── Client-side validation ──────────────────────────────────────
            if (catChoice.getValue() == null) {
                showError(status, "Please pick a category first (add one below if none exist).");
                return;
            }
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            String desc = descField.getText() == null ? "" : descField.getText().trim();
            String img  = imgField.getText()  == null ? "" : imgField.getText().trim();
            if (name.isEmpty()) { showError(status, "Item name must not be empty.");   return; }
            if (desc.isEmpty()) { showError(status, "Description must not be empty."); return; }
            if (img.isEmpty())  { showError(status, "Image path must not be empty.");  return; }
            double price;
            try {
                price = Double.parseDouble(priceField.getText().trim());
            } catch (NumberFormatException | NullPointerException nfe) {
                showError(status, "Price must be a number.");
                return;
            }
            if (price <= 0) {
                showError(status, "Price must be a positive number.");
                return;
            }
            int catId;
            try {
                catId = Integer.parseInt(catChoice.getValue().split("\\|")[1]);
            } catch (Exception ex) {
                showError(status, "Invalid category selection.");
                return;
            }

            saveBtn.setDisable(true);
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("managerId",   user.getUserId());
                payload.put("categoryId",  catId);
                payload.put("name",        name);
                payload.put("description", desc);
                payload.put("price",       price);
                payload.put("imagePath",   img);
                ApiClient.post("/restaurants/" + restaurantId + "/menu", payload);
                status.setText("Item added!");
                status.getStyleClass().setAll("status-success");
                load();
            } catch (Exception ex) {
                showError(status, UiUtil.friendlyMessage(ex));
                saveBtn.setDisable(false);
            }
        });

        VBox form = new VBox(8, catChoice, nameField, descField, priceField, imgField, saveBtn, status);
        form.setPadding(new Insets(10));
        TitledPane pane = new TitledPane("Add Menu Item", form);
        pane.setExpanded(false);
        return pane;
    }

    private TitledPane buildAddCategoryForm() {
        TextField nameField = new TextField(); nameField.setPromptText("Category name");
        Button saveBtn = new Button("Add Category");
        saveBtn.getStyleClass().add("primary");
        Label  status  = new Label();

        saveBtn.setOnAction(e -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            if (name.isEmpty()) {
                showError(status, "Category name must not be empty.");
                return;
            }
            saveBtn.setDisable(true);
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("managerId", user.getUserId());
                payload.put("name",      name);
                ApiClient.post("/restaurants/" + restaurantId + "/categories", payload);
                status.setText("Category added!");
                status.getStyleClass().setAll("status-success");
                load();
            } catch (Exception ex) {
                showError(status, UiUtil.friendlyMessage(ex));
                saveBtn.setDisable(false);
            }
        });

        VBox form = new VBox(8, nameField, saveBtn, status);
        form.setPadding(new Insets(10));
        TitledPane pane = new TitledPane("Add Category", form);
        pane.setExpanded(false);
        return pane;
    }

    private void showError(Label label, String message) {
        label.setText("Error: " + message);
        label.getStyleClass().setAll("status-error");
    }
}
