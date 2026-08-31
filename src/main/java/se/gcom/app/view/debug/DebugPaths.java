package se.gcom.app.view.debug;

import com.brunomnsilva.smartgraph.graph.Digraph;
import com.brunomnsilva.smartgraph.graph.DigraphEdgeList;
import com.brunomnsilva.smartgraph.graph.Vertex;
import com.brunomnsilva.smartgraph.graphview.SmartCircularSortedPlacementStrategy;
import com.brunomnsilva.smartgraph.graphview.SmartGraphPanel;
import com.brunomnsilva.smartgraph.graphview.SmartGraphProperties;
import com.brunomnsilva.smartgraph.graphview.SmartStylableNode;
import com.brunomnsilva.smartgraph.graphview.SmartPlacementStrategy;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import se.gcom.app.controller.DebugController;
import se.gcom.app.debug.DebugEvent;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DebugPaths extends VBox {
    private static final String SOURCE_COLOR = "#2f80ed";
    private static final String HOP_COLOR = "#8e44ad";
    private static final String DESTINATION_COLOR = "#27ae60";

    private final DebugController controller;
    private final ComboBox<String> groupSelector = new ComboBox<>();
    private final ComboBox<String> memberSelector = new ComboBox<>();
    private final VBox graphContainer = new VBox(8);
    private final Label statusLabel = new Label("Select a message to view its path.");

    private final ListChangeListener<DebugEvent> eventListener = change -> refresh();

    public DebugPaths(DebugController controller) {
        this.controller = controller;
        createLayout();
        setupActions();
        refresh();
    }

    private void createLayout() {
        setPadding(new Insets(16));
        setSpacing(12);

        Label title = new Label("Paths");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        groupSelector.setPromptText("Group");
        memberSelector.setPromptText("Message");
        groupSelector.setPrefWidth(240);
        memberSelector.setPrefWidth(320);

        HBox controls = new HBox(8, new Label("Group:"), groupSelector, new Label("Message:"), memberSelector);
        HBox legend = new HBox(
                12,
                legendItem(SOURCE_COLOR, "Source"),
                legendItem(HOP_COLOR, "Hop"),
                legendItem(DESTINATION_COLOR, "Destination")
        );

        graphContainer.setMinHeight(420);
        graphContainer.getChildren().add(statusLabel);
        VBox.setVgrow(graphContainer, Priority.ALWAYS);

        getChildren().addAll(title, controls, legend, graphContainer);

    }

    private void setupActions() {
        groupSelector.setOnAction(event -> refreshContent());
        memberSelector.setOnAction(event -> displayPaths());
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
        String selectedMessage = memberSelector.getValue();
        String group = groupSelector.getValue();

        if (group == null || group.isBlank()) {
            memberSelector.setItems(FXCollections.observableArrayList());
            memberSelector.setValue(null);
            showStatus("No groups available.");
            return;
        }

        List<String> messages = controller.getMessages(group);

        if (messages == null) {
            messages = new ArrayList<>();
        }
        memberSelector.setItems(FXCollections.observableArrayList(messages));
        if (selectedMessage != null && messages.contains(selectedMessage)) {
            memberSelector.setValue(selectedMessage);
        } else if (!messages.isEmpty()) {
            memberSelector.setValue(messages.getFirst());
        } else {
            memberSelector.setValue(null);
            showStatus("No recorded message paths for this group yet.");
        }
    }

    private void displayPaths(){
        String message = memberSelector.getValue();
        ArrayList<ArrayList<String>> paths = controller.getPaths(message);
        if (paths == null || paths.isEmpty()) {
            showStatus("No path data recorded for this message yet.");
            return;
        }
        renderGraph(paths);
    }

    private void renderGraph(List<ArrayList<String>> paths) {
        Digraph<String, String> graph = new DigraphEdgeList<>();
        Map<String, Integer> depthByNode = new LinkedHashMap<>();
        Set<String> edges = new HashSet<>();

        for (List<String> path : paths) {
            for (int i = 0; i < path.size(); i++) {
                String node = path.get(i);
                if (node == null || node.isBlank()) {
                    continue;
                }
                if (!depthByNode.containsKey(node)) {
                    graph.insertVertex(node);
                    depthByNode.put(node, i);
                } else {
                    depthByNode.put(node, Math.min(depthByNode.get(node), i));
                }

                if (i > 0) {
                    String previous = path.get(i - 1);
                    if (previous == null || previous.isBlank()) {
                        continue;
                    }
                    String edgeKey = previous + " -> " + node;
                    if (edges.add(edgeKey)) {
                        graph.insertEdge(previous, node, edgeKey);
                    }
                }
            }
        }

        if (graph.numVertices() == 0) {
            showStatus("No usable path data recorded for this message.");
            return;
        }

        String propertiesText = """
                vertex.radius = 18
                vertex.label = true
                vertex.tooltip = true
                edge.label = false
                edge.tooltip = true
                edge.arrow = true
                edge.arrowsize = 8
                """;
        SmartGraphProperties properties = new SmartGraphProperties(propertiesText);
        SmartPlacementStrategy strategy = new SmartCircularSortedPlacementStrategy();
        SmartGraphPanel<String, String> graphView = new SmartGraphPanel<>(
                graph,
                properties,
                strategy,
                URI.create(DebugPaths.class.getResource("/smartgraph.css").toExternalForm())
        );
        graphView.setMinHeight(420);
        graphView.setPrefHeight(520);
        graphView.setMaxWidth(Double.MAX_VALUE);

        graphContainer.getChildren().setAll(graphView);
        VBox.setVgrow(graphView, Priority.ALWAYS);

        initializeWhenLaidOut(graphView, graph, depthByNode);
    }

    private void initializeWhenLaidOut(SmartGraphPanel<String, String> graphView,
                                       Digraph<String, String> graph,
                                       Map<String, Integer> depthByNode) {
        boolean[] initialized = {false};
        ChangeListener<Object>[] layoutListener = new ChangeListener[1];

        Runnable tryInitialize = new Runnable() {
            @Override
            public void run() {
                if (initialized[0]) {
                    return;
                }
                if (!graphContainer.getChildren().contains(graphView)) {
                    return;
                }
                if (graphView.getScene() == null || graphView.getWidth() <= 1 || graphView.getHeight() <= 1) {
                    Platform.runLater(this);
                    return;
                }

                initialized[0] = true;
                graphView.sceneProperty().removeListener(layoutListener[0]);
                graphView.widthProperty().removeListener(layoutListener[0]);
                graphView.heightProperty().removeListener(layoutListener[0]);
                initializeGraphView(graphView, graph, depthByNode);
            }
        };

        layoutListener[0] = (observable, oldValue, newValue) -> Platform.runLater(tryInitialize);
        graphView.sceneProperty().addListener(layoutListener[0]);
        graphView.widthProperty().addListener(layoutListener[0]);
        graphView.heightProperty().addListener(layoutListener[0]);
        Platform.runLater(tryInitialize);
    }

    private void initializeGraphView(SmartGraphPanel<String, String> graphView,
                                     Digraph<String, String> graph,
                                     Map<String, Integer> depthByNode) {
        graphView.init();
        graphView.updateAndWait();
        positionVertices(graphView, graph, depthByNode);
        styleGraph(graphView, graph, depthByNode);
    }

    private void positionVertices(SmartGraphPanel<String, String> graphView,
                                  Digraph<String, String> graph,
                                  Map<String, Integer> depthByNode) {
        Map<Integer, List<String>> nodesByDepth = new HashMap<>();
        int maxDepth = 0;
        for (Map.Entry<String, Integer> entry : depthByNode.entrySet()) {
            nodesByDepth.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>()).add(entry.getKey());
            maxDepth = Math.max(maxDepth, entry.getValue());
        }

        double width = Math.max(graphView.getWidth(), 640);
        double height = Math.max(graphView.getHeight(), 420);
        double xGap = maxDepth == 0 ? 0 : (width - 120) / maxDepth;

        for (Map.Entry<Integer, List<String>> entry : nodesByDepth.entrySet()) {
            List<String> nodes = entry.getValue();
            nodes.sort(String::compareToIgnoreCase);
            double yGap = height / (nodes.size() + 1);
            double x = 60 + xGap * entry.getKey();
            for (int i = 0; i < nodes.size(); i++) {
                graphView.setVertexPosition(findVertex(graph, nodes.get(i)), x, yGap * (i + 1));
            }
        }
    }

    private void styleGraph(SmartGraphPanel<String, String> graphView,
                            Digraph<String, String> graph,
                            Map<String, Integer> depthByNode) {
        int maxDepth = depthByNode.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (String node : depthByNode.keySet()) {
            String fill = depthByNode.get(node) == 0 ? SOURCE_COLOR : depthByNode.get(node) == maxDepth ? DESTINATION_COLOR : HOP_COLOR;
            SmartStylableNode vertex = graphView.getStylableVertex(node);
            if (vertex != null) {
                vertex.setStyleInline("-fx-fill: " + fill + "; -fx-stroke: white; -fx-stroke-width: 2;");
            }
        }
        graph.edges().forEach(edge -> {
            SmartStylableNode edgeNode = graphView.getStylableEdge(edge);
            if (edgeNode != null) {
                edgeNode.setStyleInline("-fx-stroke: #d6d8de; -fx-stroke-width: 2;");
            }
        });
    }

    private Vertex<String> findVertex(Digraph<String, String> graph, String node) {
        return graph.vertices().stream()
                .filter(vertex -> vertex.element().equals(node))
                .findFirst()
                .orElseThrow();
    }

    private void showStatus(String message) {
        statusLabel.setText(message);
        graphContainer.getChildren().setAll(statusLabel);
    }

    private HBox legendItem(String color, String text) {
        Circle marker = new Circle(6, Color.web(color));
        marker.setStroke(Color.WHITE);
        marker.setStrokeWidth(1.5);
        Label label = new Label(text);
        return new HBox(5, marker, label);
    }

}
