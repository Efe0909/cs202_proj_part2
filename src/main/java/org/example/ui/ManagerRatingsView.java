package org.example.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;

/**
 * Manager-facing list of individual customer ratings for a restaurant.
 * Newest-first ordering is assumed to be applied by the backend endpoint.
 */
public class ManagerRatingsView {

    private final Stage     stage;
    private final org.example.model.User      user;
    private final StackPane contentArea;
    private final SalesStatisticsView parent;
    private final ObjectMapper mapper;

    public ManagerRatingsView(Stage stage, org.example.model.User user, StackPane contentArea,
                              SalesStatisticsView parent) {
        this.stage       = stage;
        this.user        = user;
        this.contentArea = contentArea;
        this.parent      = parent;
        this.mapper      = ApiClient.mapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void load(Map<String, Object> restaurant) {
        Label title = UiUtil.label("Ratings", "h2");

        String restName;
        int restaurantId;
        try {
            restName     = String.valueOf(restaurant.get("name"));
            restaurantId = ((Number) restaurant.get("restaurantId")).intValue();
        } catch (Exception e) {
            contentArea.getChildren().setAll(new Label("Invalid restaurant selection."));
            return;
        }

        Label sub = UiUtil.label("Restaurant: " + restName, "subtle");

        Button refreshBtn = new Button("Refresh");
        Button backBtn    = new Button("← Back to Statistics");
        backBtn.getStyleClass().add("ghost");
        backBtn.setOnAction(e -> { if (parent != null) parent.load(); });

        ListView<String> listView = new ListView<>();
        Label summary = new Label();

        Runnable fetch = () -> {
            listView.getItems().clear();
            try {
                String json = ApiClient.get(
                        "/restaurants/" + restaurantId + "/ratings", String.class);
                List<org.example.model.Rating> ratings = mapper.readValue(json, new TypeReference<>() {});
                if (ratings == null || ratings.isEmpty()) {
                    listView.setPlaceholder(new Label("No ratings yet."));
                    summary.setText("0 ratings");
                    return;
                }
                double sum = 0;
                for (org.example.model.Rating r : ratings) {
                    listView.getItems().add(formatRating(r));
                    sum += r.getScore();
                }
                summary.setText(String.format(java.util.Locale.ROOT, "%d ratings  |  average %.2f / 5",
                        ratings.size(), sum / ratings.size()));
            } catch (Exception ex) {
                listView.setPlaceholder(new Label(
                        "Error loading ratings: " + UiUtil.friendlyMessage(ex)));
                summary.setText("");
            }
        };
        refreshBtn.setOnAction(e -> fetch.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, backBtn, title, spacer, refreshBtn);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox.setVgrow(listView, Priority.ALWAYS);
        VBox layout = new VBox(12, header, sub, summary, listView);
        layout.setPadding(new Insets(24));
        contentArea.getChildren().setAll(layout);

        fetch.run();
    }

    private String formatRating(org.example.model.Rating r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.getScore()).append("/5");
        try {
            if (r.getCreatedAt() != null) {
                sb.append("  |  ").append(r.getCreatedAt());
            }
        } catch (Exception ignored) { }
        if (r.getCustomerId() > 0) {
            sb.append("  |  customer #").append(r.getCustomerId());
        }
        if (r.getOrderId() > 0) {
            sb.append("  |  order #").append(r.getOrderId());
        }
        String comment = r.getComment();
        sb.append("  —  ").append(comment == null || comment.isBlank()
                ? "(no comment)" : comment);
        return sb.toString();
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}
