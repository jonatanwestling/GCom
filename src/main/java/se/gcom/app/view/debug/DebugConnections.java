package se.gcom.app.view.debug;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import se.gcom.app.controller.DebugController;
import se.gcom.app.debug.DebugEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DebugConnections extends VBox {
    private final DebugController controller;
    private final ComboBox<String> groupSelector = new ComboBox<>();
    private final TableView<ConnectionRow> connectedClientsTable = new TableView<>();

    private final ListChangeListener<DebugEvent> eventListener = change -> refresh();

    public DebugConnections(DebugController controller) {
        this.controller = controller;
        createLayout();
        setupActions();
        refresh();
    }

    private void createLayout() {
        setPadding(new Insets(16));
        setSpacing(12);

        Label title = new Label("Connections");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        groupSelector.setPromptText("Group");

        HBox controls = new HBox(8, groupSelector);

        Label connectedClientsLabel = new Label("Connected clients");
        TableColumn<ConnectionRow, String> nameColumn = new TableColumn<>("Id");
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().address()));

        TableColumn<ConnectionRow, Void> actionColumn = new TableColumn<>("Enabled");
        actionColumn.setCellFactory(column -> new TableCell<>() {
            private final CheckBox enabledToggle = new CheckBox();

            {
                enabledToggle.setOnAction(event -> {
                    ConnectionRow connection = getTableView().getItems().get(getIndex());
                    setConnectionEnabled(connection, enabledToggle.isSelected());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }

                enabledToggle.setSelected(getTableView().getItems().get(getIndex()).enabled());
                setGraphic(enabledToggle);
            }
        });

        TableColumn<ConnectionRow, Void> delayColumn = new TableColumn<>("Delay (s)");
        delayColumn.setCellFactory(column -> new TableCell<>() {
            private final TextField delayField = new TextField();

            {
                delayField.setPrefColumnCount(5);
                delayField.setOnAction(event -> saveDelay());
                delayField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                    if (!isFocused) {
                        saveDelay();
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }

                delayField.setText(formatSeconds(getTableView().getItems().get(getIndex()).delaySeconds()));
                setGraphic(delayField);
            }

            private void saveDelay() {
                if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    return;
                }

                ConnectionRow connection = getTableView().getItems().get(getIndex());
                setConnectionDelay(connection, parseDelaySeconds(delayField.getText()));
                delayField.setText(formatSeconds(connection.delaySeconds()));
            }
        });

        connectedClientsTable.getColumns().add(nameColumn);
        connectedClientsTable.getColumns().add(actionColumn);
        connectedClientsTable.getColumns().add(delayColumn);
        connectedClientsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        connectedClientsTable.setPlaceholder(new Label("No connected clients"));
        VBox.setVgrow(connectedClientsTable, Priority.ALWAYS);

        getChildren().addAll(title, controls, connectedClientsLabel, connectedClientsTable);

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
        if (group == null) {
            connectedClientsTable.setItems(FXCollections.observableArrayList());
            return;
        }

        List<String> activeMembers = new ArrayList<>(controller.getGroupMembers(group));
        List<String> knownMembers = new ArrayList<>(controller.getDebugKnownGroupMembers(group));

        Set<String> activeMemberSet = new HashSet<>(activeMembers);
        List<ConnectionRow> rows = knownMembers.stream()
                .sorted(String::compareToIgnoreCase)
                .map(address -> new ConnectionRow(
                        address,
                        activeMemberSet.contains(address),
                        controller.getSendDelaySeconds(address)
                ))
                .toList();
        connectedClientsTable.setItems(FXCollections.observableArrayList(rows));
    }

    private void setConnectionEnabled(ConnectionRow connection, boolean enabled) {
        String group = groupSelector.getValue();

        if (connection == null || group == null) {
            return;
        }

        controller.setMemberEnabled(group, connection.address(), enabled);
        connection.setEnabled(enabled);
        connectedClientsTable.refresh();
    }

    private void setConnectionDelay(ConnectionRow connection, double delaySeconds) {
        if (connection == null) {
            return;
        }

        controller.setSendDelaySeconds(connection.address(), delaySeconds);
        connection.setDelaySeconds(delaySeconds);
    }

    private double parseDelaySeconds(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Math.max(0, Double.parseDouble(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String formatSeconds(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }

    private static class ConnectionRow {
        private final String address;
        private boolean enabled;
        private double delaySeconds;

        private ConnectionRow(String address, boolean enabled, double delaySeconds) {
            this.address = address;
            this.enabled = enabled;
            this.delaySeconds = delaySeconds;
        }

        private String address() {
            return address;
        }

        private boolean enabled() {
            return enabled;
        }

        private void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        private double delaySeconds() {
            return delaySeconds;
        }

        private void setDelaySeconds(double delaySeconds) {
            this.delaySeconds = delaySeconds;
        }
    }
}
