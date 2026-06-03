package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Map;

public class RegisterView {

    private final Stage stage;
    private final Parent root;

    public RegisterView(Stage stage) {
        this.stage = stage;
        root = buildUI();
    }

    public Parent getRoot() { return root; }

    private Parent buildUI() {
        Label brand = UiUtil.label("Online Food Ordering", "brand");
        Label title = UiUtil.label("Create your account", "h2");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        TextField emailField = new TextField();
        emailField.setPromptText("you@example.com");
        TextField fullNameField = new TextField();
        fullNameField.setPromptText("Full name");
        TextField cityField = new TextField();
        cityField.setPromptText("Delivery city (e.g. Istanbul)");
        TextField provinceField = new TextField();
        provinceField.setPromptText("Province (e.g. Kadıköy)");

        ToggleGroup roleGroup = new ToggleGroup();
        RadioButton customerBtn = new RadioButton("Customer");
        RadioButton managerBtn  = new RadioButton("Restaurant Manager");
        customerBtn.setToggleGroup(roleGroup);
        managerBtn.setToggleGroup(roleGroup);
        customerBtn.setSelected(true);
        HBox roleRow = new HBox(20, customerBtn, managerBtn);

        Button registerBtn = new Button("Register");
        registerBtn.getStyleClass().add("primary");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        Button backBtn = new Button("Back to login");
        backBtn.getStyleClass().add("ghost");
        backBtn.setMaxWidth(Double.MAX_VALUE);

        Label statusLabel = UiUtil.label("", "subtle");
        statusLabel.setWrapText(true);

        registerBtn.setOnAction(e -> {
            String role     = customerBtn.isSelected() ? "CUSTOMER" : "MANAGER";
            String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
            String password = passwordField.getText() == null ? "" : passwordField.getText();
            String email    = emailField.getText() == null ? "" : emailField.getText().trim();
            String fullName = fullNameField.getText() == null ? "" : fullNameField.getText().trim();
            String city     = cityField.getText() == null ? "" : cityField.getText().trim();
            String province = provinceField.getText() == null ? "" : provinceField.getText().trim();

            String error = null;
            if (username.isEmpty())      error = "Username is required.";
            else if (password.isEmpty()) error = "Password is required.";
            else if (email.isEmpty())    error = "Email is required.";
            else if (!email.contains("@") || !email.contains(".")) error = "Please enter a valid email address.";
            else if (fullName.isEmpty()) error = "Full name is required.";
            else if (city.isEmpty())     error = "Delivery city is required.";
            else if (province.isEmpty()) error = "Province is required.";
            if (error != null) {
                statusLabel.setText(error);
                statusLabel.getStyleClass().setAll("status-error");
                return;
            }

            registerBtn.setDisable(true);
            try {
                ApiClient.post("/auth/register", Map.of(
                        "username", username,
                        "password", password,
                        "email",    email,
                        "fullName", fullName,
                        "city",     city,
                        "province", province,
                        "role",     role
                ));
                statusLabel.setText("Registered successfully! Please login.");
                statusLabel.getStyleClass().setAll("status-success");
            } catch (Exception ex) {
                statusLabel.setText("Registration failed: " + UiUtil.friendlyMessage(ex));
                statusLabel.getStyleClass().setAll("status-error");
            } finally {
                registerBtn.setDisable(false);
            }
        });

        backBtn.setOnAction(e -> {
            LoginView lv = new LoginView(stage);
            stage.setScene(UiUtil.styled(new Scene(lv.getRoot(), 460, 560)));
        });

        VBox card = new VBox(12, brand, title, new Separator(),
                LoginView.fieldGroup("Username", usernameField),
                LoginView.fieldGroup("Password", passwordField),
                LoginView.fieldGroup("Email", emailField),
                LoginView.fieldGroup("Full name", fullNameField),
                LoginView.fieldGroup("Delivery city", cityField),
                LoginView.fieldGroup("Province", provinceField),
                LoginView.fieldGroup("Account type", roleRow),
                registerBtn, backBtn, statusLabel);
        card.getStyleClass().add("card");
        card.setMaxWidth(380);

        ScrollPane scroll = new ScrollPane(card);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-pane");

        StackPane wrapper = new StackPane(scroll);
        wrapper.getStyleClass().add("content-pane");
        wrapper.setPadding(new Insets(30));
        StackPane.setAlignment(scroll, Pos.CENTER);
        return wrapper;
    }
}
