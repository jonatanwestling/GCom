package se.gcom.app.view.debug;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import se.gcom.app.controller.DebugController;
import se.gcom.app.debug.DebugEvent;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DebugConsole extends VBox {

    public DebugConsole(DebugController controller) {
        setPadding(new Insets(16));
        setSpacing(12);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                .withZone(ZoneId.systemDefault());

        TableView<DebugEvent> table = new TableView<>();
        table.setItems(controller.getEvents());

        TableColumn<DebugEvent, Number> sequenceColumn = new TableColumn<>("#");
        sequenceColumn.setCellValueFactory(cell ->
                new SimpleLongProperty(cell.getValue().sequenceNumber())
        );

        TableColumn<DebugEvent, String> timeColumn = new TableColumn<>("Time");
        timeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(timeFormatter.format(cell.getValue().timestamp()))
        );

        TableColumn<DebugEvent, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().type().toString())
        );

        TableColumn<DebugEvent, String> sourceColumn = new TableColumn<>("Source");
        sourceColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().source())
        );

        TableColumn<DebugEvent, String> detailsColumn = new TableColumn<>("Details");
        detailsColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().details())
        );

        table.getColumns().addAll(
                sequenceColumn,
                timeColumn,
                typeColumn,
                sourceColumn,
                detailsColumn
        );

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(table);
    }
}
