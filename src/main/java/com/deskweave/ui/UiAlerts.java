package com.deskweave.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Utility wrappers for consistent JavaFX {@link Alert} and {@link Dialog} behavior.
 */
public final class UiAlerts {

    private static Window ownerWindow;

    private UiAlerts() {
    }

    /**
     * Registers the primary window so alerts/dialogs stay attached in macOS Fullscreen mode.
     */
    public static void setOwner(Window owner) {
        ownerWindow = owner;
    }

    /**
     * Applies owner window and utility style so dialogs float cleanly over fullscreen windows.
     */
    public static void configureDialog(Dialog<?> dialog) {
        if (ownerWindow != null) {
            dialog.initOwner(ownerWindow);
        }
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setResizable(false);
    }

    public static void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    public static void showWarning(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, message);
    }

    public static void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    public static boolean confirm(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        configureDialog(alert);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.OK;
    }

    private static void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        configureDialog(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
