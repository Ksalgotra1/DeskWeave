package com.deskweave.app;

import com.deskweave.config.AppConfig;
import com.deskweave.config.AppLogger;
import com.deskweave.model.Booking;
import com.deskweave.model.User;
import com.deskweave.ui.LoginView;
import com.deskweave.ui.MainShellView;
import com.deskweave.ui.UiAlerts;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Primary JavaFX Application lifecycle entry point for DeskWeave.
 */
public class DeskWeaveApp extends Application {

    private static final DateTimeFormatter SWEEP_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private AppConfig config;
    private AppContext appContext;
    private Logger logger;
    private StackPane rootContainer;
    private Timeline expiryTimeline;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        config = AppConfig.loadDefault();
        AppLogger.configure(config.getLogFilePath());
        logger = AppLogger.getLogger(DeskWeaveApp.class);
        appContext = new AppContext(config);
        logger.info("Initialized DeskWeave configuration, logger, and AppContext (grace="
                + config.getExpiryGraceMinutes() + "m)");
    }

    @Override
    public void start(Stage primaryStage) {
        rootContainer = new StackPane();
        Scene scene = new Scene(rootContainer, 1160, 740);
        String stylesheet = Objects.requireNonNull(
                DeskWeaveApp.class.getResource("/styles/deskweave.css"),
                "Missing /styles/deskweave.css").toExternalForm();
        scene.getStylesheets().add(stylesheet);

        primaryStage.setTitle("DeskWeave — Smart Workspace Booking & Capacity Planning");
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);
        primaryStage.setScene(scene);
        UiAlerts.setOwner(primaryStage);

        showLoginView();
        primaryStage.show();

        boolean smokeTest = getParameters().getRaw().contains("--smoke-test");
        if (logger != null) {
            logger.info("Primary JavaFX stage shown (smokeTest=" + smokeTest + ")");
        }

        if (smokeTest) {
            Platform.runLater(Platform::exit);
        } else if (!appContext.getDatabaseManager().testConnection()) {
            UiAlerts.showError(
                    "PostgreSQL Connection Unavailable",
                    "Could not connect to PostgreSQL at " + config.getDbUrl() + ".\n\n"
                            + "Start PostgreSQL 16 (brew services start postgresql@16 or docker compose up -d) "
                            + "and apply sql/schema.sql + sql/seed.sql.");
        }
    }

    private void showLoginView() {
        stopExpiryTimeline();
        rootContainer.getChildren().setAll(new LoginView(appContext, this::handleLoginSuccess));
    }

    private void handleLoginSuccess(User user) {
        MainShellView shell = new MainShellView(appContext, user, this::showLoginView);
        rootContainer.getChildren().setAll(shell);
        startExpiryTimeline(user, shell);
    }

    private void startExpiryTimeline(User user, MainShellView shell) {
        stopExpiryTimeline();
        expiryTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e ->
                appContext.getBackgroundExecutor().submit(() -> {
                    try {
                        List<Booking> expired = appContext.getExpiryService().runExpirySweep(user.getUserId());
                        String ts = LocalTime.now().format(SWEEP_TIME_FMT);
                        Platform.runLater(() -> shell.recordExpirySweep(expired.size(), ts));
                    } catch (Exception ex) {
                        if (logger != null) {
                            logger.log(Level.WARNING, "Background expiry sweep failed", ex);
                        }
                    }
                })
        ));
        expiryTimeline.setCycleCount(Timeline.INDEFINITE);
        expiryTimeline.play();
    }

    private void stopExpiryTimeline() {
        if (expiryTimeline != null) {
            expiryTimeline.stop();
            expiryTimeline = null;
        }
    }

    @Override
    public void stop() {
        stopExpiryTimeline();
        if (appContext != null) {
            appContext.shutdown();
        }
        if (logger != null) {
            logger.info("Shutting down DeskWeave application");
        }
        AppLogger.shutdown();
    }
}
