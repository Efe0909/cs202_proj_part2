package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class ManagerDashboard {

    private final Stage stage;
    private final org.example.model.User  user;
    private final BorderPane root;
    private final StackPane content = new StackPane();

    public ManagerDashboard(Stage stage, org.example.model.User user) {
        this.stage = stage;
        this.user  = user;
        root = buildUI();
    }

    public Parent getRoot() { return root; }

    private BorderPane buildUI() {
        BorderPane bp = new BorderPane();
        bp.getStyleClass().add("content-pane");

        Label brand = UiUtil.label("Online Food Ordering", "brand");
        Label who   = UiUtil.label("Manager • " + user.getFullName(), "subtle");
        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("secondary");
        logoutBtn.setOnAction(e -> logout());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(16, brand, spacer, who, logoutBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");
        bp.setTop(header);

        Label navTitle = UiUtil.label("MANAGE", "subtle");
        Button myRestaurantsBtn = navButton("My Restaurants");
        Button ordersBtn        = navButton("Incoming Orders");
        Button statsBtn         = navButton("Sales Statistics");

        myRestaurantsBtn.setOnAction(e ->
                new RestaurantManagementView(stage, user, content).load());
        ordersBtn.setOnAction(e ->
                new IncomingOrdersView(stage, user, content).load());
        statsBtn.setOnAction(e ->
                new SalesStatisticsView(stage, user, content).load());

        VBox sidebar = new VBox(6, navTitle, myRestaurantsBtn, ordersBtn, statsBtn);
        sidebar.setPrefWidth(210);
        sidebar.getStyleClass().add("sidebar");
        bp.setLeft(sidebar);

        content.getStyleClass().add("content-pane");
        VBox welcome = new VBox(8,
                UiUtil.label("Welcome, " + user.getFullName() + "!", "h1"),
                UiUtil.label("Pick an option from the menu on the left.", "subtle"));
        welcome.setAlignment(Pos.CENTER);
        welcome.setPadding(new Insets(40));
        content.getChildren().setAll(welcome);
        bp.setCenter(content);
        return bp;
    }

    private Button navButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("nav-button");
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private void logout() {
        LoginView lv = new LoginView(stage);
        stage.setScene(UiUtil.styled(new Scene(lv.getRoot(), 460, 560)));
        stage.setResizable(false);
    }
}
