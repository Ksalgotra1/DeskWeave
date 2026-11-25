package com.deskweave.ui;

import com.deskweave.app.AppContext;
import com.deskweave.model.User;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Primary {@link BorderPane} application shell with sidebar router, toast overlay, and status bar.
 */
public class MainShellView extends BorderPane {

    public enum ToastType {
        SUCCESS("toast-success"),
        INFO("toast-info"),
        ERROR("toast-error");

        private final String styleClass;

        ToastType(String styleClass) {
            this.styleClass = styleClass;
        }
    }

    private static final String KEY_BROWSE = "browse";
    private static final String KEY_MY_BOOKINGS = "my_bookings";
    private static final String KEY_ADMIN_WORKSPACES = "admin_workspaces";
    private static final String KEY_ADMIN_SIMULATOR = "admin_simulator";
    private static final String KEY_ADMIN_CAPACITY = "admin_capacity";

    private final AppContext context;
    private final User currentUser;
    private final Runnable onLogout;

    private final StackPane contentHolder = new StackPane();
    private final Label toastLabel = new Label();
    private final Label expiryStatusLabel = new Label("Auto-expiry active (every 30s)");
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final Map<String, Node> views = new LinkedHashMap<>();

    private PauseTransition toastTimer;
    private String activeViewKey = KEY_BROWSE;

    public MainShellView(AppContext context, User currentUser, Runnable onLogout) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.onLogout = Objects.requireNonNull(onLogout, "onLogout must not be null");
        buildLayout();
        showView(KEY_BROWSE);
    }

    private void buildLayout() {
        setTop(buildTopBar());
        setLeft(buildSidebar());
        setCenter(buildCenterArea());
        setBottom(buildStatusBar());
    }

    private HBox buildTopBar() {
        Label title = new Label("DeskWeave");
        title.getStyleClass().add("brand-title");

        Label subtitle = new Label("Smart Workspace Booking & Capacity Planning");
        subtitle.getStyleClass().add("brand-subtitle");

        VBox brandBox = new VBox(2, title, subtitle);

        boolean dbUp = context.getDatabaseManager().testConnection();
        Label dbBadge = new Label(dbUp ? "PostgreSQL Connected" : "DB Offline");
        dbBadge.getStyleClass().add(dbUp ? "badge-ok" : "badge-warn");

        Label userBadge = new Label(currentUser.getFullName() + " • " + currentUser.getRole());
        userBadge.getStyleClass().add("badge-role");

        Button logoutBtn = new Button("Sign Out");
        logoutBtn.getStyleClass().add("ghost-light-btn");
        logoutBtn.setOnAction(e -> onLogout.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(12, brandBox, spacer, dbBadge, userBadge, logoutBtn);
        topBar.getStyleClass().add("top-bar");
        return topBar;
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(6);
        sidebar.getStyleClass().add("sidebar");

        Label empSection = new Label("EMPLOYEE WORKSPACE");
        empSection.getStyleClass().add("sidebar-section-label");
        sidebar.getChildren().add(empSection);

        EmployeeBookingView bookingView = new EmployeeBookingView(context, currentUser, this::showToast);
        MyBookingsView myBookingsView = new MyBookingsView(context, currentUser, this::showToast);

        registerView(sidebar, KEY_BROWSE, "Browse & Book", bookingView);
        registerView(sidebar, KEY_MY_BOOKINGS, "My Bookings & Waitlist", myBookingsView);

        if (currentUser.isAdmin()) {
            Label adminSection = new Label("FACILITIES ADMIN");
            adminSection.getStyleClass().add("sidebar-section-label");
            sidebar.getChildren().add(adminSection);

            registerView(sidebar, KEY_ADMIN_WORKSPACES, "Manage Workspaces",
                    buildPhase5Placeholder("Workspace Inventory Admin",
                            "Add, edit, and activate/deactivate Desks & Meeting Rooms (coming in Phase 5)."));
            registerView(sidebar, KEY_ADMIN_SIMULATOR, "Partition Simulator",
                    buildPhase5Placeholder("Interval Partitioning Simulator",
                            "Import CSV demand, inspect min-heap room tracks & overflow, and save/load .dws snapshots (coming in Phase 5)."));
            registerView(sidebar, KEY_ADMIN_CAPACITY, "Capacity Planner",
                    buildPhase5Placeholder("Capacity Planning & Demo Controls",
                            "Mine 30-day utilization, no-show, and size-mismatch rules + export CSV reports (coming in Phase 5)."));
        }
        return sidebar;
    }

    private void registerView(VBox sidebar, String key, String label, Node view) {
        Button navBtn = new Button(label);
        navBtn.getStyleClass().add("nav-btn");
        navBtn.setOnAction(e -> showView(key));
        navButtons.put(key, navBtn);
        views.put(key, view);
        sidebar.getChildren().add(navBtn);
    }

    private StackPane buildCenterArea() {
        toastLabel.getStyleClass().add("toast-banner");
        toastLabel.setVisible(false);
        toastLabel.setManaged(false);
        StackPane.setAlignment(toastLabel, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toastLabel, new Insets(0, 22, 22, 0));

        StackPane centerStack = new StackPane(contentHolder, toastLabel);
        centerStack.setPadding(new Insets(18));
        return centerStack;
    }

    private HBox buildStatusBar() {
        Label officeLabel = new Label("Office Hours: " + context.getConfig().getOfficeOpen()
                + " - " + context.getConfig().getOfficeClose()
                + "   |   Check-in Grace: " + context.getConfig().getExpiryGraceMinutes() + " min");
        officeLabel.getStyleClass().add("muted-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        expiryStatusLabel.getStyleClass().add("muted-label");
        HBox statusBar = new HBox(12, officeLabel, spacer, expiryStatusLabel);
        statusBar.getStyleClass().add("status-bar");
        return statusBar;
    }

    public void showView(String key) {
        Node view = views.get(key);
        if (view == null) {
            return;
        }
        activeViewKey = key;
        contentHolder.getChildren().setAll(view);

        navButtons.forEach((btnKey, btn) -> {
            btn.getStyleClass().remove("nav-btn-active");
            if (btnKey.equals(key)) {
                btn.getStyleClass().add("nav-btn-active");
            }
        });

        if (view instanceof RefreshableView refreshable) {
            refreshable.refreshData();
        }
    }

    public void refreshActiveView() {
        Node view = views.get(activeViewKey);
        if (view instanceof RefreshableView refreshable) {
            refreshable.refreshData();
        }
    }

    public void recordExpirySweep(int expiredCount, String timestamp) {
        if (expiredCount > 0) {
            expiryStatusLabel.setText("Last expiry sweep (" + timestamp + "): expired " + expiredCount + " booking(s)");
            showToast("Auto-expired " + expiredCount + " unclaimed booking(s)", ToastType.INFO);
            refreshActiveView();
        } else {
            expiryStatusLabel.setText("Last expiry sweep (" + timestamp + "): 0 overdue");
        }
    }

    public void showToast(String message, ToastType type) {
        toastLabel.setText(message);
        toastLabel.getStyleClass().removeAll("toast-success", "toast-info", "toast-error");
        toastLabel.getStyleClass().add(type.styleClass);
        toastLabel.setVisible(true);
        toastLabel.setManaged(true);

        if (toastTimer != null) {
            toastTimer.stop();
        }
        toastTimer = new PauseTransition(Duration.seconds(3.5));
        toastTimer.setOnFinished(e -> {
            toastLabel.setVisible(false);
            toastLabel.setManaged(false);
        });
        toastTimer.playFromStart();
    }

    private static Node buildPhase5Placeholder(String titleText, String bodyText) {
        Label title = new Label(titleText);
        title.getStyleClass().add("page-title");
        Label body = new Label(bodyText);
        body.getStyleClass().add("muted-label");
        body.setWrapText(true);
        VBox card = new VBox(10, title, body);
        card.getStyleClass().add("card");
        card.setMaxWidth(620);
        StackPane wrapper = new StackPane(card);
        wrapper.setAlignment(Pos.TOP_LEFT);
        return wrapper;
    }
}
