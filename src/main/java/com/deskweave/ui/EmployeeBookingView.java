package com.deskweave.ui;

import com.deskweave.app.AppContext;
import com.deskweave.exception.BookingConflictException;
import com.deskweave.exception.DataAccessException;
import com.deskweave.exception.DeskWeaveException;
import com.deskweave.model.Booking;
import com.deskweave.model.TimeSlot;
import com.deskweave.model.User;
import com.deskweave.model.Workspace;
import com.deskweave.model.WorkspaceType;
import com.deskweave.service.BookingService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Employee view for searching/filtering workspaces and reserving time slots.
 */
public class EmployeeBookingView extends VBox implements RefreshableView {

    private final AppContext context;
    private final User currentUser;
    private final BiConsumer<String, MainShellView.ToastType> toastNotifier;

    private final TextField searchField = new TextField();
    private final ComboBox<String> typeBox = new ComboBox<>();
    private final ComboBox<String> floorBox = new ComboBox<>();
    private final ComboBox<Integer> minCapBox = new ComboBox<>();
    private final CheckBox monitorCheck = new CheckBox("Monitor");
    private final CheckBox standingCheck = new CheckBox("Standing");
    private final CheckBox projectorCheck = new CheckBox("Projector");
    private final CheckBox whiteboardCheck = new CheckBox("Whiteboard");

    private final ObservableList<Workspace> workspaceRows = FXCollections.observableArrayList();
    private final TableView<Workspace> workspaceTable = new TableView<>(workspaceRows);

    private final Label selectedSpaceLabel = new Label("Select a workspace on the left");
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final ComboBox<LocalTime> startTimeBox = new ComboBox<>();
    private final ComboBox<LocalTime> endTimeBox = new ComboBox<>();
    private final TextField attendeesField = new TextField("1");
    private final TextArea notesArea = new TextArea();
    private final Label formErrorLabel = new Label();
    private final ObservableList<String> occupiedSlotItems = FXCollections.observableArrayList();
    private final ListView<String> occupiedSlotsList = new ListView<>(occupiedSlotItems);

    public EmployeeBookingView(AppContext context,
                               User currentUser,
                               BiConsumer<String, MainShellView.ToastType> toastNotifier) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.currentUser = Objects.requireNonNull(currentUser, "currentUser must not be null");
        this.toastNotifier = Objects.requireNonNull(toastNotifier, "toastNotifier must not be null");

        setSpacing(14);
        buildUi();
    }

    private void buildUi() {
        Label pageTitle = new Label("Browse & Reserve Workspaces");
        pageTitle.getStyleClass().add("page-title");

        VBox filterCard = buildFilterBar();
        HBox splitBody = new HBox(16, buildWorkspaceTable(), buildBookingPanel());
        VBox.setVgrow(splitBody, Priority.ALWAYS);

        getChildren().addAll(pageTitle, filterCard, splitBody);
    }

    private VBox buildFilterBar() {
        searchField.setPromptText("Search code or name...");
        searchField.setPrefWidth(180);
        searchField.textProperty().addListener((obs, oldV, newV) -> reloadWorkspaces());

        typeBox.getItems().addAll("All Types", "DESK", "ROOM");
        typeBox.setValue("All Types");
        typeBox.setOnAction(e -> reloadWorkspaces());

        floorBox.getItems().addAll("All Floors", "Floor 1", "Floor 2", "Floor 3");
        floorBox.setValue("All Floors");
        floorBox.setOnAction(e -> reloadWorkspaces());

        minCapBox.getItems().addAll(1, 2, 4, 6, 8, 10);
        minCapBox.setValue(1);
        minCapBox.setOnAction(e -> reloadWorkspaces());

        for (CheckBox cb : List.of(monitorCheck, standingCheck, projectorCheck, whiteboardCheck)) {
            cb.setOnAction(e -> reloadWorkspaces());
        }

        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().add("secondary-btn");
        resetBtn.setOnAction(e -> resetFilters());

        HBox row1 = new HBox(10,
                searchField,
                new Label("Type:"), typeBox,
                new Label("Floor:"), floorBox,
                new Label("Min Cap:"), minCapBox,
                resetBtn
        );
        row1.setAlignment(Pos.CENTER_LEFT);

        HBox row2 = new HBox(16,
                new Label("Amenities:"),
                monitorCheck, standingCheck, projectorCheck, whiteboardCheck
        );
        row2.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, row1, row2);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12, 16, 12, 16));
        return card;
    }

    private TableView<Workspace> buildWorkspaceTable() {
        TableColumn<Workspace, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getCode()));
        codeCol.setPrefWidth(78);

        TableColumn<Workspace, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getName()));
        nameCol.setPrefWidth(180);

        TableColumn<Workspace, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getWorkspaceType().name()));
        typeCol.setPrefWidth(72);

        TableColumn<Workspace, Integer> floorCol = new TableColumn<>("Floor");
        floorCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getFloor()));
        floorCol.setPrefWidth(62);

        TableColumn<Workspace, Integer> capCol = new TableColumn<>("Cap");
        capCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getCapacity()));
        capCol.setPrefWidth(56);

        TableColumn<Workspace, String> amenitiesCol = new TableColumn<>("Amenities");
        amenitiesCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getAmenitySummary()));
        amenitiesCol.setPrefWidth(175);

        TableColumn<Workspace, Integer> graceCol = new TableColumn<>("Grace (m)");
        graceCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getDefaultGraceMinutes()));
        graceCol.setPrefWidth(80);

        workspaceTable.getColumns().setAll(List.of(
                codeCol, nameCol, typeCol, floorCol, capCol, amenitiesCol, graceCol));
        workspaceTable.setPlaceholder(new Label("No active workspaces match your filters."));
        workspaceTable.getSelectionModel().selectedItemProperty().addListener((obs, oldWs, newWs) -> {
            updateSelectedWorkspace(newWs);
        });
        HBox.setHgrow(workspaceTable, Priority.ALWAYS);
        return workspaceTable;
    }

    private VBox buildBookingPanel() {
        Label panelTitle = new Label("Reserve Selected Space");
        panelTitle.getStyleClass().add("section-title");

        selectedSpaceLabel.getStyleClass().add("muted-label");
        selectedSpaceLabel.setWrapText(true);

        populateTimeCombos();
        datePicker.setOnAction(e -> refreshOccupiedSlots());

        notesArea.setPromptText("Optional meeting or equipment notes...");
        notesArea.setPrefRowCount(2);
        notesArea.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Date"), 0, 0);
        form.add(datePicker, 1, 0);
        form.add(new Label("Start"), 0, 1);
        form.add(startTimeBox, 1, 1);
        form.add(new Label("End"), 0, 2);
        form.add(endTimeBox, 1, 2);
        form.add(new Label("Attendees"), 0, 3);
        form.add(attendeesField, 1, 3);
        form.add(new Label("Notes"), 0, 4);
        form.add(notesArea, 1, 4);
        GridPane.setHgrow(datePicker, Priority.ALWAYS);
        GridPane.setHgrow(startTimeBox, Priority.ALWAYS);
        GridPane.setHgrow(endTimeBox, Priority.ALWAYS);

        formErrorLabel.getStyleClass().add("error-label");
        formErrorLabel.setWrapText(true);
        formErrorLabel.setVisible(false);
        formErrorLabel.setManaged(false);

        Button reserveBtn = new Button("Reserve Workspace");
        reserveBtn.getStyleClass().add("primary-btn");
        reserveBtn.setMaxWidth(Double.MAX_VALUE);
        reserveBtn.setOnAction(e -> handleReserve());

        Label slotsHeading = new Label("Existing Active Bookings on Date:");
        slotsHeading.getStyleClass().add("muted-label");
        occupiedSlotsList.setPrefHeight(130);
        occupiedSlotsList.setPlaceholder(new Label("No active bookings on selected date."));

        VBox panel = new VBox(12,
                panelTitle,
                selectedSpaceLabel,
                new Separator(),
                form,
                formErrorLabel,
                reserveBtn,
                new Separator(),
                slotsHeading,
                occupiedSlotsList
        );
        panel.getStyleClass().add("card");
        panel.setPrefWidth(365);
        panel.setMinWidth(340);
        return panel;
    }

    private void populateTimeCombos() {
        List<LocalTime> times = new ArrayList<>();
        LocalTime cursor = context.getConfig().getOfficeOpen();
        LocalTime close = context.getConfig().getOfficeClose();
        while (!cursor.isAfter(close)) {
            times.add(cursor);
            cursor = cursor.plusMinutes(30);
        }
        startTimeBox.getItems().setAll(times.subList(0, Math.max(1, times.size() - 1)));
        endTimeBox.getItems().setAll(times.subList(1, times.size()));

        LocalTime defaultStart = LocalTime.of(10, 0);
        LocalTime defaultEnd = LocalTime.of(11, 0);
        startTimeBox.setValue(startTimeBox.getItems().contains(defaultStart)
                ? defaultStart : startTimeBox.getItems().get(0));
        endTimeBox.setValue(endTimeBox.getItems().contains(defaultEnd)
                ? defaultEnd : endTimeBox.getItems().get(endTimeBox.getItems().size() - 1));
    }

    @Override
    public void refreshData() {
        reloadWorkspaces();
    }

    private void resetFilters() {
        searchField.clear();
        typeBox.setValue("All Types");
        floorBox.setValue("All Floors");
        minCapBox.setValue(1);
        monitorCheck.setSelected(false);
        standingCheck.setSelected(false);
        projectorCheck.setSelected(false);
        whiteboardCheck.setSelected(false);
        reloadWorkspaces();
    }

    private void reloadWorkspaces() {
        try {
            WorkspaceType type = switch (typeBox.getValue()) {
                case "DESK" -> WorkspaceType.DESK;
                case "ROOM" -> WorkspaceType.ROOM;
                default -> null;
            };
            Integer floor = switch (floorBox.getValue()) {
                case "Floor 1" -> 1;
                case "Floor 2" -> 2;
                case "Floor 3" -> 3;
                default -> null;
            };
            int minCap = minCapBox.getValue() != null ? minCapBox.getValue() : 1;

            List<Workspace> matching = context.getWorkspaceService().searchAvailable(
                    type, floor, minCap,
                    monitorCheck.isSelected(),
                    standingCheck.isSelected(),
                    projectorCheck.isSelected(),
                    whiteboardCheck.isSelected()
            );

            String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
            if (!query.isEmpty()) {
                matching = matching.stream()
                        .filter(ws -> ws.getCode().toLowerCase().contains(query)
                                || ws.getName().toLowerCase().contains(query))
                        .toList();
            }

            Workspace previouslySelected = workspaceTable.getSelectionModel().getSelectedItem();
            workspaceRows.setAll(matching);

            if (previouslySelected != null) {
                matching.stream()
                        .filter(ws -> ws.getId() == previouslySelected.getId())
                        .findFirst()
                        .ifPresent(ws -> workspaceTable.getSelectionModel().select(ws));
            } else if (!matching.isEmpty()) {
                workspaceTable.getSelectionModel().selectFirst();
            } else {
                updateSelectedWorkspace(null);
            }
        } catch (DataAccessException ex) {
            toastNotifier.accept("Failed to load workspaces from database", MainShellView.ToastType.ERROR);
        }
    }

    private void updateSelectedWorkspace(Workspace ws) {
        hideFormError();
        if (ws == null) {
            selectedSpaceLabel.setText("Select a workspace on the left");
            occupiedSlotItems.clear();
            return;
        }
        selectedSpaceLabel.setText(ws.getDisplayName() + "  |  Floor " + ws.getFloor()
                + "  |  Max " + ws.getCapacity() + "  |  " + ws.getAmenitySummary());
        refreshOccupiedSlots();
    }

    private void refreshOccupiedSlots() {
        Workspace selected = workspaceTable.getSelectionModel().getSelectedItem();
        LocalDate date = datePicker.getValue();
        if (selected == null || date == null) {
            occupiedSlotItems.clear();
            return;
        }
        try {
            List<Booking> active = context.getBookingService()
                    .findActiveForWorkspaceOnDate(selected.getId(), date);
            List<String> lines = active.stream()
                    .map(b -> b.getSlot().getStartTime() + " - " + b.getSlot().getEndTime()
                            + "  [" + b.getStatus() + ", " + b.getAttendeeCount() + " ppl]")
                    .toList();
            occupiedSlotItems.setAll(lines);
        } catch (DataAccessException ex) {
            occupiedSlotItems.clear();
        }
    }

    private void handleReserve() {
        hideFormError();
        Workspace selected = workspaceTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showFormError("Please select a workspace from the table first.");
            return;
        }
        LocalDate date = datePicker.getValue();
        LocalTime start = startTimeBox.getValue();
        LocalTime end = endTimeBox.getValue();
        if (date == null || start == null || end == null) {
            showFormError("Please choose date, start time, and end time.");
            return;
        }
        if (date.isBefore(LocalDate.now())) {
            showFormError("Cannot book a workspace on a past date.");
            return;
        }

        int attendees;
        try {
            attendees = Integer.parseInt(attendeesField.getText().trim());
        } catch (NumberFormatException ex) {
            showFormError("Attendees must be a valid whole number.");
            return;
        }

        TimeSlot slot;
        try {
            slot = TimeSlot.of(date, start, end);
        } catch (DeskWeaveException ex) {
            showFormError(ex.getMessage());
            UiAlerts.showWarning("Invalid Time Slot", ex.getMessage());
            return;
        }

        String notes = notesArea.getText();
        try {
            Booking created = context.getBookingService().createBooking(
                    currentUser.getUserId(),
                    selected.getId(),
                    slot,
                    attendees,
                    notes
            );
            notesArea.clear();
            refreshOccupiedSlots();
            toastNotifier.accept(
                    "Confirmed #" + created.getBookingId() + " for " + selected.getCode() + " (" + slot + ")",
                    MainShellView.ToastType.SUCCESS);
        } catch (BookingConflictException conflict) {
            boolean joinWaitlist = UiAlerts.confirm(
                    "Time Slot Conflict",
                    selected.getDisplayName() + " is already booked for " + slot,
                    "Would you like to join the FIFO waitlist for this exact slot? "
                            + "If the holder cancels, your booking will be promoted automatically.");
            if (joinWaitlist) {
                context.getBookingService().joinWaitlist(new BookingService.WaitlistEntry(
                        currentUser.getUserId(),
                        currentUser.getFullName(),
                        selected.getId(),
                        slot,
                        attendees,
                        notes
                ));
                toastNotifier.accept(
                        "Joined waitlist for " + selected.getCode() + " (" + slot + ")",
                        MainShellView.ToastType.INFO);
            }
        } catch (DeskWeaveException ex) {
            showFormError(ex.getMessage());
            UiAlerts.showWarning("Reservation Rejected", ex.getMessage());
        } catch (DataAccessException ex) {
            showFormError("Database error while saving booking.");
            UiAlerts.showError("Database Error", ex.getMessage());
        }
    }

    private void showFormError(String msg) {
        formErrorLabel.setText(msg);
        formErrorLabel.setVisible(true);
        formErrorLabel.setManaged(true);
    }

    private void hideFormError() {
        formErrorLabel.setVisible(false);
        formErrorLabel.setManaged(false);
    }
}
