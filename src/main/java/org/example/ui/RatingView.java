package org.example.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Map;

public class RatingView {

    private final org.example.model.User       user;
    private final org.example.model.Order      order;
    private final StackPane  contentArea;
    private final OrderTrackingView parent;

    public RatingView(org.example.model.User user, org.example.model.Order order, StackPane contentArea, OrderTrackingView parent) {
        this.user        = user;
        this.order       = order;
        this.contentArea = contentArea;
        this.parent      = parent;
    }

    public void load() {
        Label title = UiUtil.label("Rate Restaurant", "h2");
        Label sub   = UiUtil.label("Order #" + order.getOrderId(), "subtle");

        Spinner<Integer> scoreSpinner = new Spinner<>(1, 5, 5);

        TextArea commentArea = new TextArea();
        commentArea.setPrefRowCount(3);
        commentArea.setPromptText("Tell others about your experience (optional)");

        Button submitBtn = new Button("Submit Rating");
        submitBtn.getStyleClass().add("primary");
        Button backBtn   = new Button("Back");
        Label  status    = UiUtil.label("", "subtle");

        submitBtn.setOnAction(e -> {
            Integer score = scoreSpinner.getValue();
            if (score == null || score < 1 || score > 5) {
                status.setText("Please choose a score between 1 and 5.");
                status.getStyleClass().setAll("status-error");
                return;
            }
            submitBtn.setDisable(true);
            try {
                ApiClient.post("/orders/" + order.getOrderId() + "/rate", Map.of(
                        "customerId",   user.getUserId(),
                        "restaurantId", order.getRestaurantId(),
                        "score",        score,
                        "comment",      commentArea.getText() == null ? "" : commentArea.getText()
                ));
                status.setText("Rating submitted!");
                status.getStyleClass().setAll("status-success");
            } catch (Exception ex) {
                status.setText("Could not submit rating: " + UiUtil.friendlyMessage(ex));
                status.getStyleClass().setAll("status-error");
            } finally {
                submitBtn.setDisable(false);
            }
        });

        backBtn.setOnAction(e -> parent.load());

        HBox actions = new HBox(10, submitBtn, backBtn);
        VBox card = new VBox(12, title, sub, new Separator(),
                LoginView.fieldGroup("Score (1–5)", scoreSpinner),
                LoginView.fieldGroup("Comment", commentArea),
                actions, status);
        card.getStyleClass().add("panel");
        card.setMaxWidth(520);

        VBox layout = new VBox(card);
        layout.setPadding(new Insets(24));
        contentArea.getChildren().setAll(layout);
    }
}
