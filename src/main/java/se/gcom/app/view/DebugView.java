package se.gcom.app.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import se.gcom.app.controller.DebugController;
import se.gcom.app.view.debug.*;

public class DebugView extends BorderPane {

    private final ListView<String> sidebar = new ListView<>();
    private final DebugController controller;

    public DebugView(DebugController controller){
        this.controller = controller;
        createLayout();
        setupActions();

    }

    private void createLayout() {
        sidebar.setItems(FXCollections.observableArrayList(
                "Overview",
                "Ordering",
                "Console",
                "Connections",
                "Message Paths",
                "Message Count"
        ));

        sidebar.setPrefWidth(180);
        sidebar.getSelectionModel().selectFirst();

        VBox leftPane = new VBox(sidebar);
        leftPane.setPadding(new Insets(0,0,0,0));
        leftPane.setPrefWidth(200);
        VBox.setVgrow(sidebar, Priority.ALWAYS);

        setLeft(leftPane);
        setCenter(new DebugOverview(controller));
    }

    private void setupActions() {
        sidebar.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if ("Overview".equals(newValue)) {
                setCenter(new DebugOverview(controller));
            } else if ("Console".equals(newValue)) {
                setCenter(new DebugConsole(controller));
            } else if ("Ordering".equals(newValue)) {
                setCenter(new DebugOrdering(controller));
            } else if ("Connections".equals(newValue)) {
                setCenter(new DebugConnections(controller));
            } else if ("Message Paths".equals(newValue)) {
                setCenter(new DebugPaths(controller));
            } else if ("Message Count".equals(newValue)) {
                setCenter(new DebugCounter(controller));
            }
        });
    }

}
