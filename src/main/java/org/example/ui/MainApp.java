package org.example.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private static String[] launchArgs;

    public static void launchApp(String[] args) {
        launchArgs = args;
        launch(MainApp.class, args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Online Food Ordering System");

        LoginView loginView = new LoginView(primaryStage);
        Scene scene = UiUtil.styled(new Scene(loginView.getRoot(), 460, 560));
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }
}
