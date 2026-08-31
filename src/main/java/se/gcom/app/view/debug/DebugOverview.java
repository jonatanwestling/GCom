package se.gcom.app.view.debug;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import se.gcom.app.controller.DebugController;
import se.gcom.app.debug.DebugEvent;
import se.gcom.app.debug.DebugEventType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DebugOverview extends VBox {
    private final DebugController controller;
    private final TableView<PerformanceRow> performanceTable = new TableView<>();
    private final ListChangeListener<DebugEvent> eventListener = change -> refresh();

    public DebugOverview(DebugController controller) {
        this.controller = controller;
        createLayout();
        setupActions();
        refresh();
    }

    private void createLayout() {
        setPadding(new Insets(16));
        setSpacing(12);

        Label title = new Label("System performance");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        TableColumn<PerformanceRow, String> messageColumn = new TableColumn<>("Message");
        messageColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().messageId()));

        TableColumn<PerformanceRow, String> groupColumn = new TableColumn<>("Group");
        groupColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().group()));

        TableColumn<PerformanceRow, String> orderingColumn = new TableColumn<>("Ordering");
        orderingColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().ordering()));

        TableColumn<PerformanceRow, String> multicastColumn = new TableColumn<>("Multicast");
        multicastColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().multicast()));

        TableColumn<PerformanceRow, Integer> totalColumn = new TableColumn<>("Total");
        totalColumn.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().totalMessages()).asObject());

        performanceTable.getColumns().addAll(
                messageColumn,
                groupColumn,
                orderingColumn,
                multicastColumn,
                totalColumn
        );
        performanceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        performanceTable.setPlaceholder(new Label("No sent-message performance samples yet"));
        VBox.setVgrow(performanceTable, Priority.ALWAYS);

        getChildren().addAll(title, performanceTable);
    }

    private void setupActions() {
        controller.getEvents().addListener(eventListener);
    }

    private void refresh() {
        List<PerformanceRow> rows = new ArrayList<>(controller.getEvents().stream()
                .filter(event -> event.type() == DebugEventType.OPERATION_PERFORMANCE)
                .map(event -> parsePerformanceRow(event.details()))
                .toList());
        Collections.reverse(rows);
        performanceTable.setItems(FXCollections.observableArrayList(rows));
    }

    private PerformanceRow parsePerformanceRow(String details) {
        Map<String, String> values = new HashMap<>();
        for (String part : details.split(" ")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length == 2) {
                values.put(keyValue[0], keyValue[1]);
            }
        }

        return new PerformanceRow(
                values.getOrDefault("messageId", ""),
                values.getOrDefault("group", ""),
                values.getOrDefault("ordering", ""),
                values.getOrDefault("multicast", ""),
                parseInt(values.get("total"))
        );
    }

    private int parseInt(String value) {
        if (value == null) {
            return 0;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private record PerformanceRow(
            String messageId,
            String group,
            String ordering,
            String multicast,
            int totalMessages
    ) {
    }
}
