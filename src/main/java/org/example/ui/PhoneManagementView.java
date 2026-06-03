package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;

/**
 * Customer-facing phone-number management. Mirrors AddressManagementView
 * minus the "selected" concept — phones are a flat 1:N list per spec §2.1.
 */
public class PhoneManagementView {

    private final Stage     stage;
    private final org.example.model.User      user;
    private final StackPane contentArea;

    private final VBox  listBox    = new VBox(8);
    private final Label listStatus = UiUtil.label("", "subtle");

    public PhoneManagementView(Stage stage, org.example.model.User user, StackPane contentArea) {
        this.stage       = stage;
        this.user        = user;
        this.contentArea = contentArea;
    }

    public void load() {
        Label title = UiUtil.label("Phone Numbers", "h2");
        Label sub   = UiUtil.label(
                "Add phone numbers to your account. You may have more than one.",
                "subtle");
        sub.setWrapText(true);

        VBox listPanel = new VBox(10, UiUtil.label("Your Phones", "h3"),
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
        List<PhoneService.Phone> phones;
        try {
            phones = PhoneService.list(user.getUserId());
        } catch (Exception e) {
            listBox.getChildren().add(UiUtil.label(
                    "Could not load phones: " + UiUtil.friendlyMessage(e),
                    "status-error"));
            return;
        }
        if (phones.isEmpty()) {
            listBox.getChildren().add(UiUtil.label(
                    "No phone numbers yet. Add one below.", "empty-state"));
            return;
        }
        for (PhoneService.Phone p : phones) {
            listBox.getChildren().add(buildRow(p));
        }
    }

    private HBox buildRow(PhoneService.Phone p) {
        Label num = UiUtil.label(p.toString(), "h3");
        VBox info = new VBox(2, num);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("danger");
        deleteBtn.setOnAction(e -> doDelete(p, deleteBtn));

        HBox row = new HBox(12, info, deleteBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("item-row");
        return row;
    }

    private void doDelete(PhoneService.Phone p, Button btn) {
        if (!UiUtil.confirm("Delete phone?",
                "Delete \"" + p + "\"?",
                "This phone number will be removed from your account.")) {
            return;
        }
        btn.setDisable(true);
        try {
            PhoneService.delete(user.getUserId(), p.id);
            refresh();
        } catch (Exception ex) {
            UiUtil.error("Could not delete phone", ex);
            btn.setDisable(false);
        }
    }

    private VBox buildAddForm() {
        Label heading = UiUtil.label("Add a New Phone", "h3");

        TextField phoneField = new TextField();
        phoneField.setPromptText("e.g. +90 5XX XXX XXXX");

        Button addBtn = new Button("Add Phone");
        addBtn.getStyleClass().add("primary");
        Label status = UiUtil.label("", "subtle");
        status.setWrapText(true);

        addBtn.setOnAction(e -> {
            String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();
            if (phone.isEmpty()) {
                status.setText("Phone number is required.");
                status.getStyleClass().setAll("status-error");
                return;
            }
            if (phone.length() > 20) {
                status.setText("Phone must be 20 characters or fewer.");
                status.getStyleClass().setAll("status-error");
                return;
            }
            addBtn.setDisable(true);
            try {
                PhoneService.add(user.getUserId(), phone);
                status.setText("Phone added.");
                status.getStyleClass().setAll("status-success");
                phoneField.clear();
                refresh();
            } catch (Exception ex) {
                status.setText("Could not add phone: " + UiUtil.friendlyMessage(ex));
                status.getStyleClass().setAll("status-error");
            } finally {
                addBtn.setDisable(false);
            }
        });

        HBox actions = new HBox(addBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(12, heading, phoneField, actions, status);
        box.getStyleClass().add("panel");
        return box;
    }
}
