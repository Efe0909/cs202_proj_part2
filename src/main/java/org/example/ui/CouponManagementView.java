package org.example.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CouponManagementView {

    private final Stage     stage;
    private final org.example.model.User      user;
    private final int       restaurantId;
    private final String    restaurantName;
    private final StackPane contentArea;

    private final TableView<org.example.model.Coupon> table = new TableView<>();
    private final Label listStatus = new Label();

    public CouponManagementView(Stage stage, org.example.model.User user, int restaurantId,
                                String restaurantName, StackPane contentArea) {
        this.stage          = stage;
        this.user           = user;
        this.restaurantId   = restaurantId;
        this.restaurantName = restaurantName;
        this.contentArea    = contentArea;
    }

    public void load() {
        Label title = UiUtil.label("Coupons — " + restaurantName, "h2");

        Button backBtn = new Button("← Back");
        backBtn.getStyleClass().add("ghost");
        backBtn.setOnAction(e -> new RestaurantManagementView(stage, user, contentArea).load());

        // ── Existing coupons table ──────────────────────────────────────────
        buildTable();

        VBox listBox = new VBox(8, UiUtil.label("Existing Coupons", "h3"), table, listStatus);
        listBox.setPadding(new Insets(10));
        VBox.setVgrow(table, Priority.ALWAYS);
        TitledPane listPane = new TitledPane("Coupons", listBox);
        listPane.setExpanded(true);

        // ── Create coupon form ──────────────────────────────────────────────
        TextField codeField  = new TextField(); codeField.setPromptText("Coupon code (e.g. SUMMER20)");
        ChoiceBox<String> typeChoice = new ChoiceBox<>();
        typeChoice.getItems().addAll("PERCENTAGE", "AMOUNT");
        typeChoice.setValue("PERCENTAGE");

        TextField valueField = new TextField(); valueField.setPromptText("Discount value (e.g. 10)");
        TextField fromField  = new TextField(); fromField.setPromptText("Valid from (YYYY-MM-DD)");
        TextField untilField = new TextField(); untilField.setPromptText("Valid until (YYYY-MM-DD)");

        Button saveBtn = new Button("Create Coupon");
        saveBtn.getStyleClass().add("primary");
        Label  status  = new Label();

        saveBtn.setOnAction(e -> {
            String code = codeField.getText() == null ? "" : codeField.getText().trim();

            // ── Client-side validation ──────────────────────────────────────
            if (code.isEmpty()) {
                showError(status, "Coupon code must not be empty.");
                return;
            }
            double discountValue;
            try {
                discountValue = Double.parseDouble(valueField.getText().trim());
            } catch (NumberFormatException | NullPointerException nfe) {
                showError(status, "Discount value must be a number.");
                return;
            }
            if (discountValue <= 0) {
                showError(status, "Discount value must be a positive number.");
                return;
            }
            LocalDate validFrom;
            LocalDate validUntil;
            try {
                validFrom = LocalDate.parse(fromField.getText().trim());
            } catch (DateTimeParseException | NullPointerException pe) {
                showError(status, "Valid from must be a date in YYYY-MM-DD format.");
                return;
            }
            try {
                validUntil = LocalDate.parse(untilField.getText().trim());
            } catch (DateTimeParseException | NullPointerException pe) {
                showError(status, "Valid until must be a date in YYYY-MM-DD format.");
                return;
            }
            if (validUntil.isBefore(validFrom)) {
                showError(status, "Valid until must not be before valid from.");
                return;
            }

            saveBtn.setDisable(true);
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("managerId",     user.getUserId());
                payload.put("restaurantId",  restaurantId);
                payload.put("code",          code);
                payload.put("discountType",  typeChoice.getValue());
                payload.put("discountValue", discountValue);
                payload.put("validFrom",     validFrom.toString());
                payload.put("validUntil",    validUntil.toString());
                ApiClient.post("/restaurants/" + restaurantId + "/coupons", payload);

                status.setText("Coupon created!");
                status.getStyleClass().setAll("status-success");
                codeField.clear(); valueField.clear(); fromField.clear(); untilField.clear();
                refreshCoupons();
            } catch (Exception ex) {
                showError(status, UiUtil.friendlyMessage(ex));
            } finally {
                saveBtn.setDisable(false);
            }
        });

        Label note = UiUtil.label(
                "Note: created coupons can be applied by customers at checkout.",
                "muted-italic");

        VBox form = new VBox(10, codeField, typeChoice, valueField, fromField, untilField, saveBtn, status, note);
        form.setPadding(new Insets(10));
        TitledPane pane = new TitledPane("Create New Coupon", form);
        pane.setExpanded(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, backBtn, title, spacer);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox layout = new VBox(14, header, listPane, pane);
        layout.setPadding(new Insets(24));
        contentArea.getChildren().setAll(layout);

        refreshCoupons();
    }

    @SuppressWarnings("unchecked")
    private void buildTable() {
        table.getColumns().clear();

        TableColumn<org.example.model.Coupon, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(new PropertyValueFactory<>("code"));
        codeCol.setPrefWidth(140);

        TableColumn<org.example.model.Coupon, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("discountType"));
        typeCol.setPrefWidth(120);

        TableColumn<org.example.model.Coupon, Number> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("discountValue"));
        valueCol.setPrefWidth(90);

        TableColumn<org.example.model.Coupon, LocalDate> fromCol = new TableColumn<>("Valid From");
        fromCol.setCellValueFactory(new PropertyValueFactory<>("validFrom"));
        fromCol.setPrefWidth(110);

        TableColumn<org.example.model.Coupon, LocalDate> untilCol = new TableColumn<>("Valid Until");
        untilCol.setCellValueFactory(new PropertyValueFactory<>("validUntil"));
        untilCol.setPrefWidth(110);

        TableColumn<org.example.model.Coupon, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));
        activeCol.setPrefWidth(70);

        TableColumn<org.example.model.Coupon, Void> actionCol = new TableColumn<>("Action");
        actionCol.setPrefWidth(120);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button deactivateBtn = new Button("Deactivate");
            {
                deactivateBtn.getStyleClass().add("danger");
                deactivateBtn.setOnAction(e -> {
                    org.example.model.Coupon coupon = getTableView().getItems().get(getIndex());
                    try {
                        ApiClient.delete("/restaurants/coupons/" + coupon.getCouponId());
                        refreshCoupons();
                    } catch (Exception ex) {
                        showError(listStatus, UiUtil.friendlyMessage(ex));
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    org.example.model.Coupon coupon = getTableView().getItems().get(getIndex());
                    deactivateBtn.setDisable(!coupon.isActive());
                    setGraphic(deactivateBtn);
                }
            }
        });

        table.getColumns().addAll(codeCol, typeCol, valueCol, fromCol, untilCol, activeCol, actionCol);
        table.setPlaceholder(new Label("No coupons yet."));
    }

    private void refreshCoupons() {
        try {
            org.example.model.Coupon[] coupons = ApiClient.get("/restaurants/" + restaurantId + "/coupons", org.example.model.Coupon[].class);
            List<org.example.model.Coupon> list = coupons == null ? List.of() : Arrays.asList(coupons);
            table.getItems().setAll(list);
            listStatus.setText("");
        } catch (Exception ex) {
            table.getItems().clear();
            showError(listStatus, "Could not load coupons: " + UiUtil.friendlyMessage(ex));
        }
    }

    private void showError(Label label, String message) {
        label.setText("Error: " + message);
        label.getStyleClass().setAll("status-error");
    }
}
