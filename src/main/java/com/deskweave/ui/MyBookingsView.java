package com.deskweave.ui;

import com.deskweave.app.AppContext;
import com.deskweave.exception.DataAccessException;
import com.deskweave.exception.DeskWeaveException;
import com.deskweave.model.Booking;
import com.deskweave.model.BookingStatus;
import com.deskweave.model.User;
import com.deskweave.model.Workspace;
import com.deskweave.service.BookingService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Employee view for inspecting reservations, checking in, cancelling, and viewing the live waitlist.
 */
public class MyBookingsView extends VBox implements RefreshableView {

    private final AppContext context;
    private final User currentUser;
    private final BiConsumer<String, MainShellView.ToastType> toastNotifier;

    private final Label totalKpi = new Label("0");
    private final Label confirmedKpi = new Label("0");
    private final Label checkedInKpi = new Label("0");
    private final Label expiredCancelledKpi = new Label("0");

    private final CheckBox activeOnlyCheck = new CheckBox("Active Only (Confirmed / Checked In)");
    private final Button checkInBtn = new Button("Check In");
    private final Button cancelBtn = new Button("Cancel Booking");

    private final ObservableList<Booking> bookingRows = FXCollections.observableArrayList();
    private final TableView<Booking> bookingsTable = new TableView<>(bookingRows);
    private final ObservableList<String> waitlistRows = FXCollections.observableArrayList();
    private final ListView<String> waitlistList = new ListView<>(waitlistRows);

    private Map<Integer, Workspace> workspaceIndex = Collections.emptyMap();

    public MyBookingsView(AppContext context,
                          User currentUser,
                          BiConsumer<String, MainShellView.ToastType> toastNotifier) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.toastNotifier = Objects.requireNonNull(toastNotifier, "toastNotifier must not be null");

        setSpacing(14);
        buildUi();
    }

    private void buildUi() {
        Label pageTitle = new Label("My Bookings & Waitlist");
        pageTitle.getStyleClass().add("page-title");

        HBox kpiStrip = new HBox(12,
                kpiCard("Total Reservations", totalKpi),
                kpiCard("Confirmed", confirmedKpi),
                kpiCard("Checked In", checkedInKpi),
                kpiCard("Expired / Cancelled", expiredCancelledKpi)
        );

        HBox toolbar = buildToolbar();
        buildBookingsTable();
        VBox.setVgrow(bookingsTable, Priority.ALWAYS);

        Label waitlistTitle = new Label("Live FIFO Waitlist Queue");
        waitlistTitle.getStyleClass().add("section-title");
        waitlistList.setPrefHeight(120);
        waitlistList.setPlaceholder(new Label("Waitlist queue is currently empty."));
        VBox waitlistBox = new VBox(8, waitlistTitle, waitlistList);
        waitlistBox.getStyleClass().add("card");

        getChildren().addAll(pageTitle, kpiStrip, toolbar, bookingsTable, waitlistBox);
    }

    private VBox kpiCard(String title, Label valueLabel) {
        valueLabel.getStyleClass().add("kpi-value");
        Label lbl = new Label(title);
        lbl.getStyleClass().add("kpi-label");
        VBox box = new VBox(4, valueLabel, lbl);
        box.getStyleClass().add("kpi-card");
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private HBox buildToolbar() {
        activeOnlyCheck.setOnAction(e -> refreshData());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-btn");
        refreshBtn.setOnAction(e -> refreshData());

        checkInBtn.getStyleClass().add("success-btn");
        checkInBtn.setDisable(true);
        checkInBtn.setOnAction(e -> handleCheckIn());

        cancelBtn.getStyleClass().add("danger-btn");
        cancelBtn.setDisable(true);
        cancelBtn.setOnAction(e -> handleCancel());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, activeOnlyCheck, spacer, refreshBtn, checkInBtn, cancelBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void buildBookingsTable() {
        TableColumn<Booking, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getBookingId()));
        idCol.setPrefWidth(56);

        TableColumn<Booking, String> wsCol = new TableColumn<>("Workspace");
        wsCol.setCellValueFactory(c -> {
            Workspace ws = workspaceIndex.get(c.getValue().getWorkspaceId());
            String label = ws != null ? ws.getDisplayName() : "Workspace #" + c.getValue().getWorkspaceId();
            return new ReadOnlyStringWrapper(label);
        });
        wsCol.setPrefWidth(210);

        TableColumn<Booking, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getSlot().getDate().toString()));
        dateCol.setPrefWidth(105);

        TableColumn<Booking, String> timeCol = new TableColumn<>("Time Slot");
        timeCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getSlot().getStartTime() + " - " + c.getValue().getSlot().getEndTime()));
        timeCol.setPrefWidth(120);

        TableColumn<Booking, Integer> attCol = new TableColumn<>("Attendees");
        attCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getAttendeeCount()));
        attCol.setPrefWidth(82);

        TableColumn<Booking, BookingStatus> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getStatus()));
        statusCol.setPrefWidth(115);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BookingStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(status.name());
                String color = switch (status) {
                    case CONFIRMED -> "#1d4ed8";
                    case CHECKED_IN, COMPLETED -> "#15803d";
                    case EXPIRED -> "#dc2626";
                    case CANCELLED -> "#64748b";
                };
                setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + ";");
            }
        });

        TableColumn<Booking, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getNotes()));
        notesCol.setPrefWidth(220);

        bookingsTable.getColumns().setAll(List.of(idCol, wsCol, dateCol, timeCol, attCol, statusCol, notesCol));
        bookingsTable.setPlaceholder(new Label("You have no bookings matching this view."));
        bookingsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldB, newB) -> {
            updateActionButtons(newB);
        });
    }

    @Override
    public void refreshData() {
        try {
            workspaceIndex = context.getWorkspaceService().indexById();
            List<Booking> allUserBookings = context.getBookingService()
                    .findBookingsForUser(currentUser.getUserId());

            totalKpi.setText(String.valueOf(allUserBookings.size()));
            confirmedKpi.setText(String.valueOf(
                    allUserBookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count()));
            checkedInKpi.setText(String.valueOf(
                    allUserBookings.stream().filter(b -> b.getStatus() == BookingStatus.CHECKED_IN).count()));
            expiredCancelledKpi.setText(String.valueOf(
                    allUserBookings.stream().filter(b ->
                            b.getStatus() == BookingStatus.EXPIRED || b.getStatus() == BookingStatus.CANCELLED).count()));

            List<Booking> visible = activeOnlyCheck.isSelected()
                    ? allUserBookings.stream().filter(b -> b.getStatus().isActiveSlot()).toList()
                    : allUserBookings;

            bookingRows.setAll(visible);
            updateActionButtons(bookingsTable.getSelectionModel().getSelectedItem());
            refreshWaitlist();
        } catch (DataAccessException ex) {
            toastNotifier.accept("Failed to load bookings", MainShellView.ToastType.ERROR);
        }
    }

    private void refreshWaitlist() {
        List<BookingService.WaitlistEntry> entries = context.getBookingService().getWaitlistSnapshot();
        List<String> lines = entries.stream()
                .map(e -> {
                    Workspace ws = workspaceIndex.get(e.workspaceId());
                    String code = ws != null ? ws.getCode() : "WS#" + e.workspaceId();
                    return e.requesterName() + " waiting for " + code + " on " + e.slot()
                            + " (" + e.attendeeCount() + " ppl)";
                })
                .toList();
        waitlistRows.setAll(lines);
    }

    private void updateActionButtons(Booking selected) {
        if (selected == null) {
            checkInBtn.setDisable(true);
            cancelBtn.setDisable(true);
            return;
        }
        checkInBtn.setDisable(selected.getStatus() != BookingStatus.CONFIRMED);
        boolean canCancel = currentUser.isAdmin()
                ? selected.getStatus().isActiveSlot()
                : selected.getStatus() == BookingStatus.CONFIRMED;
        cancelBtn.setDisable(!canCancel);
    }

    private void handleCheckIn() {
        Booking selected = bookingsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            context.getBookingService().checkInNow(selected.getBookingId(), currentUser);
            refreshData();
            toastNotifier.accept("Checked in for booking #" + selected.getBookingId(), MainShellView.ToastType.SUCCESS);
        } catch (DeskWeaveException ex) {
            UiAlerts.showWarning("Check-In Unavailable", ex.getMessage());
        } catch (DataAccessException ex) {
            UiAlerts.showError("Database Error", ex.getMessage());
        }
    }

    private void handleCancel() {
        Booking selected = bookingsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String reason = null;
        if (currentUser.isAdmin() && selected.getStatus() == BookingStatus.CHECKED_IN) {
            TextInputDialog reasonDialog = new TextInputDialog("Facilities override");
            UiAlerts.configureDialog(reasonDialog);
            reasonDialog.setTitle("Admin Override Cancel");
            reasonDialog.setHeaderText("Cancel checked-in booking #" + selected.getBookingId()
                    + " (" + selected.getSlot() + ")?");
            reasonDialog.setContentText("Audit reason:");
            Optional<String> entered = reasonDialog.showAndWait();
            if (entered.isEmpty()) {
                return;
            }
            reason = entered.get();
        } else {
            boolean confirmed = UiAlerts.confirm(
                    "Cancel Booking",
                    "Cancel booking #" + selected.getBookingId() + " (" + selected.getSlot() + ")?",
                    "This slot will be released immediately and offered to the waitlist if applicable.");
            if (!confirmed) {
                return;
            }
        }
        try {
            Optional<Booking> promoted = context.getBookingService()
                    .cancelBooking(selected.getBookingId(), currentUser, reason);
            refreshData();
            if (promoted.isPresent()) {
                Booking next = promoted.get();
                UiAlerts.showInfo("Waitlist Promoted",
                        "Freed slot was automatically assigned to the next waitlisted employee (Booking #"
                                + next.getBookingId() + ").");
                toastNotifier.accept("Cancelled #" + selected.getBookingId() + " & promoted waitlist #"
                        + next.getBookingId(), MainShellView.ToastType.INFO);
            } else {
                toastNotifier.accept("Cancelled booking #" + selected.getBookingId(), MainShellView.ToastType.INFO);
            }
        } catch (DeskWeaveException ex) {
            UiAlerts.showWarning("Cancellation Failed", ex.getMessage());
        } catch (DataAccessException ex) {
            UiAlerts.showError("Database Error", ex.getMessage());
        }
    }
}
