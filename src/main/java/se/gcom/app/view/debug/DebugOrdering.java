package se.gcom.app.view.debug;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.converter.IntegerStringConverter;
import se.gcom.app.controller.DebugController;
import se.gcom.app.debug.DebugEvent;
import se.gcom.middleware.communicationModule.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DebugOrdering extends VBox {

    private final DebugController controller;
    private final ComboBox<String> groupSelector = new ComboBox<>();
    private final TableView<Map.Entry<String, Integer>> clockTable = new TableView<>();
    private final TableView<ChatMessage> holdbackQueueTable = new TableView<>();
    private final ListChangeListener<DebugEvent> eventListener = change -> refresh();

    public DebugOrdering(DebugController controller) {
        this.controller = controller;
        createLayout();
        setupActions();
        refresh();
    }

    private void createLayout() {
        setPadding(new Insets(16));
        setSpacing(12);

        Label title = new Label("Vector clocks");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        groupSelector.setPromptText("Group");

        HBox controls = new HBox(8, groupSelector);

        TableColumn<Map.Entry<String, Integer>, String> processColumn = new TableColumn<>("Process");
        processColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getKey()));

        TableColumn<Map.Entry<String, Integer>, Integer> clockColumn = new TableColumn<>("Clock");
        clockColumn.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getValue()).asObject());
        clockColumn.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        clockColumn.setOnEditCommit(event -> {
            Map.Entry<String, Integer> row = event.getRowValue();
            controller.setVectorClockValue(groupSelector.getValue(), row.getKey(), event.getNewValue());
            refreshContent();
        });

        clockTable.setEditable(true);
        clockTable.getColumns().addAll(processColumn, clockColumn);
        clockTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ChatMessage, String> messageIdColumn = new TableColumn<>("Id");
        messageIdColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMessageId()));

        TableColumn<ChatMessage, String> messageColumn = new TableColumn<>("Message");
        messageColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPayload()));

        TableColumn<ChatMessage, String> vectorClockColumn = new TableColumn<>("Vector clock");
        vectorClockColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getVectorClockMap().toString()));

        holdbackQueueTable.getColumns().addAll(messageColumn, messageIdColumn, vectorClockColumn);
        holdbackQueueTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        holdbackQueueTable.setPlaceholder(new Label("No held-back messages"));

        VBox.setVgrow(clockTable, Priority.ALWAYS);
        VBox.setVgrow(holdbackQueueTable, Priority.ALWAYS);

        getChildren().addAll(title, controls, new Label("Current clock"), clockTable, new Label("Holdback Queue"), holdbackQueueTable);
    }

    private void setupActions() {
        groupSelector.setOnAction(event -> refreshContent());
        controller.getEvents().addListener(eventListener);
    }

    private void refresh() {
        String selectedGroup = groupSelector.getValue();
        List<String> groups = new ArrayList<>(controller.getGroupNames());
        groups.sort(String::compareToIgnoreCase);
        groupSelector.setItems(FXCollections.observableArrayList(groups));

        if (selectedGroup != null && groups.contains(selectedGroup)) {
            groupSelector.setValue(selectedGroup);
        } else if (!groups.isEmpty()) {
            groupSelector.setValue(groups.getFirst());
        }

        refreshContent();
    }

    private void refreshContent() {
        String group = groupSelector.getValue();
        Map<String, Integer> clock = controller.getCurrentVectorClock(group);

        clockTable.setItems(FXCollections.observableArrayList(
                clock.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList()
        ));

        holdbackQueueTable.setItems(FXCollections.observableArrayList(controller.getHoldbackQueue(group)));
    }
}
