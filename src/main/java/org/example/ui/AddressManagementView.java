package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;

/**
 * Customer-facing Address Management screen.
 *
 * Lists the customer's addresses (city + province), lets them add a new one
 * (city + province only — no street field), delete one (with confirm), and
 * mark one as the selected delivery address. Selecting an address re-drives
 * the rest of the customer area through the supplied callback.
 */
public class AddressManagementView {

    private final Stage       stage;
    private final org.example.model.User        user;
    private final StackPane   contentArea;
    /** Invoked after the selected address changes (so the header/browser refresh). */
    private final Runnable    onSelectionChanged;

    private final VBox listBox = new VBox(8);
    private final Label listStatus = UiUtil.label("", "subtle");

    public AddressManagementView(Stage stage, org.example.model.User user, StackPane contentArea,
                                 Runnable onSelectionChanged) {
        this.stage              = stage;
        this.user               = user;
        this.contentArea        = contentArea;
        this.onSelectionChanged = onSelectionChanged;
    }

    public void load() {
        Label title = UiUtil.label("Delivery Addresses", "h2");
        Label sub   = UiUtil.label(
                "Choose where your orders are delivered. Browsing is filtered by your "
                + "selected address's city.", "subtle");
        sub.setWrapText(true);

        VBox listPanel = new VBox(10, UiUtil.label("Your Addresses", "h3"),
                listBox, listStatus);
        listPanel.getStyleClass().add("panel");

        VBox addPanel = buildAddForm();

        VBox layout = new VBox(18, title, sub, listPanel, addPanel);
        layout.setPadding(new Insets(24));
        layout.setMaxWidth(680);

        ScrollPane scroll = new ScrollPane(layout);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-pane");
        contentArea.getChildren().setAll(scroll);

        refresh();
    }

    private void refresh() {
        listBox.getChildren().clear();
        listStatus.setText("");
        List<AddressService.Address> addresses;
        try {
            addresses = AddressService.list(user.getUserId());
        } catch (Exception e) {
            listBox.getChildren().add(UiUtil.label(
                    "Could not load addresses: " + UiUtil.friendlyMessage(e),
                    "status-error"));
            return;
        }
        if (addresses.isEmpty()) {
            listBox.getChildren().add(UiUtil.label(
                    "No addresses yet. Add one below to start ordering.", "empty-state"));
            return;
        }
        for (AddressService.Address a : addresses) {
            listBox.getChildren().add(buildRow(a));
        }
    }

    private HBox buildRow(AddressService.Address a) {
        Label where = UiUtil.label(a.toString(), "h3");
        VBox info = new VBox(2, where);
        if (a.selected) {
            Label badge = UiUtil.label("Selected", "badge-selected");
            info.getChildren().add(badge);
        }
        HBox.setHgrow(info, Priority.ALWAYS);

        Button selectBtn = new Button(a.selected ? "Selected" : "Set as delivery address");
        if (a.selected) {
            selectBtn.setDisable(true);
        } else {
            selectBtn.getStyleClass().add("primary");
            selectBtn.setOnAction(e -> doSelect(a, selectBtn));
        }

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("danger");
        deleteBtn.setOnAction(e -> doDelete(a, deleteBtn));

        HBox row = new HBox(12, info, selectBtn, deleteBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("item-row");
        return row;
    }

    private void doSelect(AddressService.Address a, Button btn) {
        btn.setDisable(true);
        try {
            AddressService.select(user.getUserId(), a.id);
            if (onSelectionChanged != null) onSelectionChanged.run();
            refresh();
        } catch (Exception ex) {
            UiUtil.error("Could not change selected address", ex);
            btn.setDisable(false);
        }
    }

    private void doDelete(AddressService.Address a, Button btn) {
        if (!UiUtil.confirm("Delete address?",
                "Delete \"" + a + "\"?",
                "This address will be removed from your account.")) {
            return;
        }
        btn.setDisable(true);
        try {
            AddressService.delete(user.getUserId(), a.id);
            if (a.selected && onSelectionChanged != null) onSelectionChanged.run();
            refresh();
        } catch (Exception ex) {
            UiUtil.error("Could not delete address", ex);
            btn.setDisable(false);
        }
    }

    private VBox buildAddForm() {
        Label heading = UiUtil.label("Add a New Address", "h3");

        TextField cityField = new TextField();
        cityField.setPromptText("City (e.g. İstanbul)");
        TextField provinceField = new TextField();
        provinceField.setPromptText("Province (e.g. Kadıköy)");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(UiUtil.label("City", "subtle"), 0, 0);
        grid.add(cityField, 1, 0);
        grid.add(UiUtil.label("Province", "subtle"), 0, 1);
        grid.add(provinceField, 1, 1);
        GridPane.setHgrow(cityField, Priority.ALWAYS);
        GridPane.setHgrow(provinceField, Priority.ALWAYS);

        Button addBtn = new Button("Add Address");
        addBtn.getStyleClass().add("primary");
        Label status = UiUtil.label("", "subtle");
        status.setWrapText(true);

        addBtn.setOnAction(e -> {
            String city = cityField.getText() == null ? "" : cityField.getText().trim();
            String prov = provinceField.getText() == null ? "" : provinceField.getText().trim();
            if (city.isEmpty()) {
                status.setText("City is required.");
                status.getStyleClass().setAll("status-error");
                return;
            }
            if (prov.isEmpty()) {
                status.setText("Province is required.");
                status.getStyleClass().setAll("status-error");
                return;
            }
            addBtn.setDisable(true);
            try {
                AddressService.add(user.getUserId(), city, prov);
                status.setText("Address added.");
                status.getStyleClass().setAll("status-success");
                cityField.clear();
                provinceField.clear();
                refresh();
            } catch (Exception ex) {
                status.setText("Could not add address: " + UiUtil.friendlyMessage(ex));
                status.getStyleClass().setAll("status-error");
            } finally {
                addBtn.setDisable(false);
            }
        });

        HBox actions = new HBox(addBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(12, heading, grid, actions, status);
        box.getStyleClass().add("panel");
        return box;
    }
}
