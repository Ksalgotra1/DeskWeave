package com.deskweave.app;

import com.deskweave.config.AppConfig;
import com.deskweave.config.AppLogger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.logging.Logger;

/**
 * Primary JavaFX Application lifecycle entry point for DeskWeave.
 */
public class DeskWeaveApp extends Application {

    private AppConfig config;
    private Logger logger;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        config = AppConfig.loadDefault();
        AppLogger.configure(config.getLogFilePath());
        logger = AppLogger.getLogger(DeskWeaveApp.class);
        logger.info("Initialized DeskWeave configuration and logger (grace="
                + config.getExpiryGraceMinutes() + "m)");
    }

    @Override
    public void start(Stage primaryStage) {
        Label titleLabel = new Label("DeskWeave");
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitleLabel = new Label("Smart Workspace Booking & Capacity Planning Platform");
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #475569;");

        VBox header = new VBox(4, titleLabel, subtitleLabel);
        header.setPadding(new Insets(20, 24, 16, 24));
        header.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        Label statusLabel = new Label("Phase 1 Ready — Java "
                + Runtime.version().feature() + " + OpenJFX " + System.getProperty("javafx.version", "21"));
        statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #1e293b;");

        VBox centerBox = new VBox(10, statusLabel);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(24));

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(centerBox);
        root.setStyle("-fx-background-color: #ffffff;");

        Scene scene = new Scene(root, 840, 520);
        primaryStage.setTitle("DeskWeave — Smart Workspace Booking & Capacity Planning");
        primaryStage.setScene(scene);
        primaryStage.show();

        if (logger != null) {
            logger.info("Primary JavaFX stage shown");
        }

        if (getParameters().getRaw().contains("--smoke-test")) {
            Platform.runLater(Platform::exit);
        }
    }

    @Override
    public void stop() {
        if (logger != null) {
            logger.info("Shutting down DeskWeave application");
        }
        AppLogger.shutdown();
    }
}
