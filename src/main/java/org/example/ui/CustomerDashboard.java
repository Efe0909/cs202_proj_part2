package org.example.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;

public class CustomerDashboard {

    private final Stage stage;
    private final org.example.model.User  user;
    private final BorderPane root;

    private final StackPane content = new StackPane();
    /** Header label that shows the active delivery city. */
    private final Button addressPill = new Button();

    public CustomerDashboard(Stage stage, org.example.model.User user) {
        this.stage = stage;
        this.user  = user;
        root = buildUI();
    }

    public Parent getRoot() { return root; }

    private BorderPane buildUI() {
        BorderPane bp = new BorderPane();
        bp.getStyleClass().add("content-pane");
        bp.setTop(buildHeader());
        bp.setLeft(buildSidebar());

        content.getStyleClass().add("content-pane");
        showWelcome();
        bp.setCenter(content);
        return bp;
    }

    // ── Header with brand, selected-address picker, logout ──────────────────
    private Region buildHeader() {
        Label brand = UiUtil.label("Online Food Ordering", "brand");
        Label hello = UiUtil.label("Hi, " + user.getFullName(), "subtle");

        refreshAddressPill();
        addressPill.getStyleClass().add("address-pill");
        addressPill.setOnAction(e -> showAddressPicker());

        Button logoutBtn = new Button("Logout");
        logoutBtn.getStyleClass().add("secondary");
        logoutBtn.setOnAction(e -> logout());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(16, brand, spacer, hello, addressPill, logoutBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");
        return header;
    }

    private Region buildSidebar() {
        Label navTitle = UiUtil.label("MENU", "subtle");

        Button browseBtn      = navButton("Browse Restaurants");
        Button searchBtn      = navButton("Search Restaurants");
        Button ordersBtn      = navButton("My Orders");
        Button historyBtn     = navButton("Order History");
        Button addressBtn     = navButton("Delivery Addresses");
        Button phonesBtn      = navButton("My Phones");

        browseBtn.setOnAction(e -> {
            RestaurantBrowserView rbv = new RestaurantBrowserView(stage, user, content);
            rbv.loadRestaurants(null);
        });
        searchBtn.setOnAction(e -> {
            RestaurantBrowserView rbv = new RestaurantBrowserView(stage, user, content);
            rbv.showSearchBar(content);
        });
        ordersBtn.setOnAction(e -> new OrderTrackingView(stage, user, content, false).load(content));
        historyBtn.setOnAction(e -> new OrderTrackingView(stage, user, content, true).load(content));
        addressBtn.setOnAction(e -> openAddressManagement());
        phonesBtn.setOnAction(e -> new PhoneManagementView(stage, user, content).load());

        VBox sidebar = new VBox(6, navTitle, browseBtn, searchBtn, ordersBtn, historyBtn,
                new Separator(), addressBtn, phonesBtn);
        sidebar.setPrefWidth(210);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    private Button navButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("nav-button");
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private void showWelcome() {
        VBox v = new VBox(8,
                UiUtil.label("Welcome, " + user.getFullName() + "!", "h1"),
                UiUtil.label("Pick an option from the menu on the left to get started.",
                        "subtle"));
        v.setAlignment(Pos.CENTER);
        v.setPadding(new Insets(40));
        content.getChildren().setAll(v);
    }

    private void openAddressManagement() {
        new AddressManagementView(stage, user, content, this::refreshAddressPill).load();
    }

    // ── Selected-address indicator + quick picker ───────────────────────────

    /** Refreshes the header pill text from the currently selected address. */
    private void refreshAddressPill() {
        String city;
        try {
            city = AddressService.resolveBrowsingCity(user);
        } catch (Exception e) {
            city = null;
        }
        addressPill.setText("Delivering to: "
                + (city == null || city.isBlank() ? "—" : city) + "  ▾");
    }

    /** A small popup-style chooser so the customer can switch address fast. */
    private void showAddressPicker() {
        List<AddressService.Address> addresses;
        try {
            addresses = AddressService.list(user.getUserId());
        } catch (Exception e) {
            UiUtil.error("Could not load your addresses", e);
            return;
        }
        if (addresses.isEmpty()) {
            if (UiUtil.confirm("No addresses",
                    "You have no delivery addresses yet.",
                    "Open Delivery Addresses to add one?")) {
                openAddressManagement();
            }
            return;
        }

        ChoiceDialog<AddressService.Address> dialog =
                new ChoiceDialog<>(AddressService.selectedOrFirst(addresses), addresses);
        dialog.setTitle("Switch delivery address");
        dialog.setHeaderText("Choose where to deliver");
        dialog.setContentText("Address:");
        try {
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource(UiUtil.STYLESHEET).toExternalForm());
        } catch (Exception ignored) { }

        dialog.showAndWait().ifPresent(chosen -> {
            try {
                AddressService.select(user.getUserId(), chosen.id);
                refreshAddressPill();
                // Re-load the restaurant list filtered by the new selected city.
                RestaurantBrowserView rbv = new RestaurantBrowserView(stage, user, content);
                rbv.loadRestaurants(null);
            } catch (Exception ex) {
                UiUtil.error("Could not switch address", ex);
            }
        });
    }

    private void logout() {
        LoginView lv = new LoginView(stage);
        stage.setScene(UiUtil.styled(new Scene(lv.getRoot(), 460, 560)));
        stage.setResizable(false);
    }
}
