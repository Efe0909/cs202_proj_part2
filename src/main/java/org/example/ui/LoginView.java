package org.example.ui;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class LoginView {

    private final Stage stage;
    private final Parent root;

    public LoginView(Stage stage) {
        this.stage = stage;
        root = buildUI();
    }

    public Parent getRoot() { return root; }

    private Parent buildUI() {
        Label brand    = UiUtil.label("Online Food Ordering", "brand");
        Label title    = UiUtil.label("Welcome back", "h2");
        Label subtitle = UiUtil.label("Sign in to order from restaurants near you.", "subtle");
        subtitle.setWrapText(true);

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("primary");
        loginBtn.setMaxWidth(Double.MAX_VALUE);

        Button registerBtn = new Button("Create an account");
        registerBtn.getStyleClass().add("ghost");
        registerBtn.setMaxWidth(Double.MAX_VALUE);

        Label errorLabel = UiUtil.label("", "status-error");
        errorLabel.setWrapText(true);

        loginBtn.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            loginBtn.setDisable(true);
            errorLabel.setText("");
            try {
                String response = ApiClient.post("/auth/login",
                        java.util.Map.of("username", username, "password", password));
                ObjectMapper mapper = ApiClient.mapper();
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                org.example.model.User user = mapper.readValue(response, org.example.model.User.class);
                openDashboard(user);
            } catch (RuntimeException ex) {
                errorLabel.setText("Invalid username or password.");
                System.err.println("[LoginView] login error: " + ex.getMessage());
            } catch (Exception ex) {
                errorLabel.setText("Login failed. Please try again.");
                System.err.println("[LoginView] unexpected error: " + ex.getMessage());
            } finally {
                loginBtn.setDisable(false);
            }
        });
        passwordField.setOnAction(e -> loginBtn.fire());

        registerBtn.setOnAction(e -> openRegister());

        VBox card = new VBox(14, brand, title, subtitle,
                new Separator(),
                fieldGroup("Username", usernameField),
                fieldGroup("Password", passwordField),
                loginBtn, registerBtn, errorLabel);
        card.getStyleClass().add("card");
        card.setMaxWidth(360);
        card.setFillWidth(true);

        StackPane wrapper = new StackPane(card);
        wrapper.getStyleClass().add("content-pane");
        wrapper.setPadding(new Insets(40));
        StackPane.setAlignment(card, Pos.CENTER);
        return wrapper;
    }

    static VBox fieldGroup(String label, javafx.scene.Node field) {
        Label l = UiUtil.label(label, "subtle");
        VBox box = new VBox(4, l, field);
        return box;
    }

    private void openDashboard(org.example.model.User user) {
        if (user.isManager()) {
            ManagerDashboard dash = new ManagerDashboard(stage, user);
            stage.setScene(UiUtil.styled(new Scene(dash.getRoot(), 1024, 700)));
            stage.setResizable(true);
        } else {
            CustomerDashboard dash = new CustomerDashboard(stage, user);
            stage.setScene(UiUtil.styled(new Scene(dash.getRoot(), 1024, 700)));
            stage.setResizable(true);
        }
    }

    private void openRegister() {
        RegisterView rv = new RegisterView(stage);
        stage.setScene(UiUtil.styled(new Scene(rv.getRoot(), 460, 640)));
    }
}
