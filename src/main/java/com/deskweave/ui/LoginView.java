package com.deskweave.ui;

import com.deskweave.app.AppContext;
import com.deskweave.exception.AuthenticationException;
import com.deskweave.exception.DataAccessException;
import com.deskweave.exception.DeskWeaveException;
import com.deskweave.model.User;
import com.deskweave.model.UserRole;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Authentication view using a centered {@link GridPane} form and quick-fill demo accounts.
 */
public class LoginView extends StackPane {

    private final AppContext context;
    private final Consumer<User> onLoginSuccess;
    private final TextField emailField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label errorLabel = new Label();

    public LoginView(AppContext context, Consumer<User> onLoginSuccess) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.onLoginSuccess = Objects.requireNonNull(onLoginSuccess, "onLoginSuccess must not be null");
        buildUi();
    }

    private void buildUi() {
        setPadding(new Insets(32));

        Label heading = new Label("DeskWeave");
        heading.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        Label subtitle = new Label("Smart Workspace Booking & Capacity Planning Platform");
        subtitle.getStyleClass().add("muted-label");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);

        emailField.setPromptText("you@deskweave.io");
        emailField.setPrefWidth(280);
        passwordField.setPromptText("Enter password");
        passwordField.setPrefWidth(280);
        GridPane.setHgrow(emailField, Priority.ALWAYS);
        GridPane.setHgrow(passwordField, Priority.ALWAYS);

        form.add(new Label("Email"), 0, 0);
        form.add(emailField, 1, 0);
        form.add(new Label("Password"), 0, 1);
        form.add(passwordField, 1, 1);

        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button signInBtn = new Button("Sign In");
        signInBtn.getStyleClass().add("primary-btn");
        signInBtn.setDefaultButton(true);
        signInBtn.setMaxWidth(Double.MAX_VALUE);
        signInBtn.setOnAction(e -> handleSignIn());

        Button registerBtn = new Button("Register New Employee");
        registerBtn.getStyleClass().add("secondary-btn");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setOnAction(e -> openRegisterDialog());

        Label demoLabel = new Label("Quick-Fill Demo Accounts:");
        demoLabel.getStyleClass().add("muted-label");

        Button aliceBtn = quickFillButton("Employee: Alice", "alice@deskweave.io", "pass123");
        Button bobBtn = quickFillButton("Employee: Bob", "bob@deskweave.io", "pass123");
        Button adminBtn = quickFillButton("Admin: Priya", "admin@deskweave.io", "admin123");

        HBox quickFillRow = new HBox(8, aliceBtn, bobBtn, adminBtn);
        quickFillRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(14,
                heading,
                subtitle,
                new Separator(),
                form,
                errorLabel,
                signInBtn,
                registerBtn,
                new Separator(),
                demoLabel,
                quickFillRow
        );
        card.getStyleClass().add("card");
        card.setMaxWidth(450);
        card.setAlignment(Pos.CENTER_LEFT);

        getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);
    }

    private Button quickFillButton(String label, String email, String password) {
        Button button = new Button(label);
        button.getStyleClass().add("secondary-btn");
        button.setOnAction(e -> {
            emailField.setText(email);
            passwordField.setText(password);
            hideError();
        });
        return button;
    }

    private void handleSignIn() {
        hideError();
        try {
            User authenticated = context.getAuthService()
                    .authenticate(emailField.getText(), passwordField.getText());
            onLoginSuccess.accept(authenticated);
        } catch (AuthenticationException ex) {
            showError(ex.getMessage());
        } catch (DataAccessException ex) {
            showError("Database unavailable. Ensure PostgreSQL 16 is running and seeded.");
        }
    }

    private void openRegisterDialog() {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Register New Employee");
        dialog.setHeaderText("Create a DeskWeave Employee Account");

        ButtonType createBtnType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField nameInput = new TextField();
        nameInput.setPromptText("Full name");
        TextField emailInput = new TextField();
        emailInput.setPromptText("name@deskweave.io");
        TextField deptInput = new TextField();
        deptInput.setPromptText("Engineering");
        PasswordField passInput = new PasswordField();
        passInput.setPromptText("Minimum 6 characters");

        grid.add(new Label("Full Name"), 0, 0);
        grid.add(nameInput, 1, 0);
        grid.add(new Label("Email"), 0, 1);
        grid.add(emailInput, 1, 1);
        grid.add(new Label("Department"), 0, 2);
        grid.add(deptInput, 1, 2);
        grid.add(new Label("Password"), 0, 3);
        grid.add(passInput, 1, 3);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn != createBtnType) {
                return null;
            }
            try {
                return context.getAuthService().registerUser(
                        nameInput.getText(),
                        emailInput.getText(),
                        passInput.getText(),
                        UserRole.EMPLOYEE,
                        deptInput.getText()
                );
            } catch (DeskWeaveException | IllegalArgumentException | DataAccessException ex) {
                UiAlerts.showWarning("Registration Failed", ex.getMessage());
                return null;
            }
        });

        dialog.showAndWait().ifPresent(created -> {
            emailField.setText(created.getEmail());
            passwordField.clear();
            UiAlerts.showInfo("Account Created", "Registered " + created.getFullName() + ". You can sign in now.");
        });
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
