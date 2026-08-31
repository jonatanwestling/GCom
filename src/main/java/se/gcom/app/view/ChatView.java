package se.gcom.app.view;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import se.gcom.app.controller.ChatController;
import se.gcom.app.controller.DebugController;
import se.gcom.app.model.ChatGroup;
import se.gcom.app.model.ChatMessage;

import java.util.ArrayList;
import java.util.Optional;


public class ChatView {
    private final BorderPane root;
    private final ChatController chatController;
    private final DebugController debugController;
    private final ListView<ChatGroup> groups = new ListView<>();
    private final VBox messagesBox = new VBox(10);
    private final TextField inputField = new TextField();
    private final Button sendButton = new Button("Send");
    private final Button addGroupButton = new Button("+");
    private final Button joinGroupButton = new Button("Join");
    private final Label selectedGroupTitle = new Label();
    private final Button addUserButton = new Button("Add User");
    private final Button leaveGroupButton = new Button("Leave group");
    private ObservableList<ChatMessage> visibleMessages;
    private final ListChangeListener<ChatMessage> messageListener = change -> renderMessages();

    public ChatView(ChatController chatController, DebugController debugController) {
        this.chatController = chatController;
        this.debugController = debugController;
        this.root = new BorderPane();
        createLayout();
        setupActions();
    }

    private void createLayout() {
        VBox leftPane = createLeftPane();
        VBox rightPane = createRightPane();

        root.setTop(createMenuBar());
        root.setLeft(leftPane);
        root.setCenter(rightPane);
    }

    private VBox createRightPane() {
        selectedGroupTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        addUserButton.setOnAction(e -> openAddUserDialog());
        leaveGroupButton.setOnAction(e -> leaveGroupAction());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox chatHeader = new HBox(10, selectedGroupTitle, headerSpacer,leaveGroupButton , addUserButton);
        chatHeader.setAlignment(Pos.CENTER_LEFT);
        chatHeader.setPadding(new Insets(0, 25, 0, 10));

        messagesBox.setPadding(new Insets(10));
        messagesBox.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(messagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        inputField.setPromptText("Write a message...");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        HBox inputBar = new HBox(10, inputField, sendButton);
        inputBar.setPadding(new Insets(8,25,8,12));

        VBox rightPane = new VBox(10,
                chatHeader,
                scrollPane,
                inputBar
        );
        rightPane.setPadding(new Insets(0, 0, 0, 12));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        return rightPane;

    }

    private void leaveGroupAction() {
        chatController.leaveGroup();
        if (chatController.getGroups().isEmpty()) {
            groups.getSelectionModel().clearSelection();
            showMessages(null);
            updateSelectedGroupHeader(null);
        } else {
            groups.getSelectionModel().selectFirst();
        }
    }

    private VBox createLeftPane() {
        Label title = new Label("Groups");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        addGroupButton.setMinWidth(32);
        addGroupButton.setOnAction(e -> openCreateGroupDialog());

        joinGroupButton.setMinWidth(32);
        joinGroupButton.setOnAction(e -> openJoinGroupDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox titleBar = new HBox(8, title, spacer, joinGroupButton, addGroupButton);
        titleBar.setAlignment(Pos.CENTER_LEFT);

        groups.setItems(chatController.getGroups());
        groups.getSelectionModel().selectedItemProperty().addListener((obs, oldGroup, newGroup) -> {
            chatController.selectGroup(newGroup);
            showMessages(newGroup);
            updateSelectedGroupHeader(newGroup);
        });

        if (!chatController.getGroups().isEmpty()) {
            groups.getSelectionModel().selectFirst();
        } else {
            showMessages(null);
            updateSelectedGroupHeader(null);
        }
        Label titleText = new Label("My Address:");
        Label bottomText = new Label(chatController.getAddress());
        VBox bottomBox = new VBox(2, titleText, bottomText);
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setPadding(new Insets(5, 5, 10, 5));
        VBox leftPane = new VBox(10, titleBar, groups, bottomBox);
        leftPane.setPadding(new Insets(0, 12, 0, 0));
        leftPane.setPrefWidth(260);
        VBox.setVgrow(groups, Priority.ALWAYS);

        return leftPane;
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.setUseSystemMenuBar(true);

        Menu windowMenu = new Menu("Debug");

        MenuItem debugItem = new MenuItem("Open Debug Window");
        debugItem.setOnAction(e -> openDebugWindow());

        debugItem.setAccelerator(
                new KeyCodeCombination(
                        KeyCode.D,
                        KeyCombination.SHORTCUT_DOWN,
                        KeyCombination.SHIFT_DOWN
                )
        );

        windowMenu.getItems().add(debugItem);
        menuBar.getMenus().add(windowMenu);

        return menuBar;
    }

    private void setupActions() {
        sendButton.setOnAction(e -> sendMessage());
        inputField.setOnAction(e -> sendMessage());

    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        chatController.sendMessage(text);
        inputField.clear();
    }

    private void openCreateGroupDialog() {
        Dialog<GroupInput> dialog = new Dialog<>();
        dialog.setTitle("New Group");
        dialog.setHeaderText("Create group");

        ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        TextField groupNameField = new TextField();
        groupNameField.setPromptText("Group name");


        TextField ipAddressField = new TextField();
        ipAddressField.setPromptText("IP address");


        ToggleGroup orderingGroup = new ToggleGroup();

        RadioButton unorderedRadio = new RadioButton("Unordered");
        RadioButton causalRadio = new RadioButton("Causal Ordering");
        unorderedRadio.setToggleGroup(orderingGroup);
        causalRadio.setToggleGroup(orderingGroup);
        causalRadio.setSelected(true);

        HBox orderingBox = new HBox(15, unorderedRadio, causalRadio);
        orderingBox.setPadding(new Insets(5, 0, 5, 0));

        ToggleGroup multiCastGroup = new ToggleGroup();

        RadioButton reliableRadio = new RadioButton("Reliable");
        RadioButton nonReliableRadio = new RadioButton("Non-Reliable");
        reliableRadio.setToggleGroup(multiCastGroup);
        nonReliableRadio.setToggleGroup(multiCastGroup);
        reliableRadio.setSelected(true);

        HBox multiCastBox = new HBox(15, reliableRadio, nonReliableRadio);
        multiCastBox.setPadding(new Insets(5, 0, 5, 0));

        ToggleGroup groupGroup = new ToggleGroup();

        RadioButton staticRadio = new RadioButton("Static");
        RadioButton dynamicRadio = new RadioButton("Dynamic");
        staticRadio.setToggleGroup(groupGroup);
        dynamicRadio.setToggleGroup(groupGroup);
        dynamicRadio.setSelected(true);

        ArrayList<String> members = new ArrayList<>();

        Button addMemberButton = new Button("Add Member");

        ipAddressField.visibleProperty().bind(staticRadio.selectedProperty());
        ipAddressField.managedProperty().bind(staticRadio.selectedProperty());

        addMemberButton.visibleProperty().bind(staticRadio.selectedProperty());
        addMemberButton.managedProperty().bind(staticRadio.selectedProperty());

        Label ipLabel = new Label("IP address");

        ipLabel.visibleProperty().bind(staticRadio.selectedProperty());
        ipLabel.managedProperty().bind(staticRadio.selectedProperty());

        addMemberButton.setOnAction(e -> {
            members.add(ipAddressField.getText().trim());
            ipAddressField.clear();
        });

        HBox groupBox = new HBox(15, dynamicRadio, staticRadio);
        groupBox.setPadding(new Insets(5, 0, 5, 0));


        GridPane content = new GridPane();
        content.setHgap(10);
        content.setVgap(10);
        content.setPadding(new Insets(8, 0, 0, 0));
        content.addRow(0, new Label("Group name"), groupNameField);
        content.addRow(1, new Label("Ordering Type"), orderingBox);
        content.addRow(2, new Label("Multicast Type"), multiCastBox);
        content.addRow(3, new Label("Group Classification"), groupBox);;
        content.addRow(4, ipLabel, ipAddressField);
        content.addRow(5, addMemberButton);

        staticRadio.selectedProperty().addListener((obs, oldVal, isStatic) -> {
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        });

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button != createButtonType) {
                return null;
            }

            try {
                return new GroupInput(
                        groupNameField.getText().trim(),
                        members,
                        causalRadio.isSelected(),
                        staticRadio.isSelected(),
                        reliableRadio.isSelected()
                );
            } catch (NumberFormatException e) {
                return null;
            }
        });

        dialog.setOnShown(e -> groupNameField.requestFocus());
        dialog.showAndWait().ifPresent(this::createGroup);
    }

    private void openJoinGroupDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Join Group");
        dialog.setHeaderText("Join group");

        ButtonType joinButtonType = new ButtonType("Join", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(joinButtonType, ButtonType.CANCEL);

        TextField groupNameField = new TextField();
        groupNameField.setPromptText("Group name");

        TextField ipField = new TextField();
        ipField.setPromptText("IP address");

        GridPane content = new GridPane();
        content.setHgap(10);
        content.setVgap(10);
        content.setPadding(new Insets(8, 0, 0, 0));
        content.addRow(0, new Label("Group name"), groupNameField);
        if (!chatController.namingServerIsUp()) {
            content.addRow(1, new Label("IP address"), ipField);
        }
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button != joinButtonType) {
                return null;
            }

            return groupNameField.getText().trim() + ";" + ipField.getText().trim();

        });

        dialog.setOnShown(e -> groupNameField.requestFocus());
        dialog.showAndWait().ifPresent(result -> {
            String[] parts = result.split(";");
            if (parts.length != 2) {
                joinGroup(parts[0], null);
            } else {
                joinGroup(parts[0], parts[1]);
            }
        });
    }

    private void joinGroup(String name, String ip){
        try {
            ChatGroup group = chatController.joinGroup(name, ip);
            if (group != null) {
                groups.getSelectionModel().select(group);
            }
        }catch (StatusRuntimeException e){
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.PERMISSION_DENIED) {
                showErrorAlert("Can not join group",
                        "You do not have access to this group");
            } else {
                showErrorAlert("Error", "Something went wrong :(");
            }
        }
    }

    private void createGroup(GroupInput groupInput) {
        try {
            ChatGroup group = chatController.createGroup(
                    groupInput.groupName(),
                    groupInput.causalOrdering(),
                    groupInput.reliable(),
                    groupInput.staticGroup(),
                    groupInput.ipAddresses
            );
            if (group != null) {
                groups.getSelectionModel().select(group);
                chatController.setGroupOrdering(groupInput.groupName, groupInput.causalOrdering);
            }
        }catch (StatusRuntimeException e){
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.ALREADY_EXISTS) {
                showErrorAlert("Group Already Exists",
                        "A group with the name \"" + groupInput.groupName() + "\" already exists.\nPlease choose another name.");
            } else {
                showErrorAlert("Error", "Something went wrong :(");
            }
        }
    }

    private void openAddUserDialog() {
        Dialog<UserInput> dialog = new Dialog<>();
        dialog.setTitle("Add User");
        dialog.setHeaderText("Add user to " + selectedGroupTitle.getText());

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Name");

        TextField ipAddressField = new TextField();
        ipAddressField.setPromptText("IP address");

        TextField portField = new TextField();
        portField.setPromptText("Port");

        GridPane content = new GridPane();
        content.setHgap(10);
        content.setVgap(10);
        content.setPadding(new Insets(8, 0, 0, 0));
        content.addRow(0, new Label("Name"), nameField);
        content.addRow(1, new Label("IP address"), ipAddressField);
        content.addRow(2, new Label("Port"), portField);

        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> {
            if (button != addButtonType) {
                return null;
            }

            try {
                return new UserInput(
                        ipAddressField.getText().trim(),
                        nameField.getText().trim(),
                        Integer.parseInt(portField.getText().trim())
                );
            } catch (NumberFormatException e) {
                return null;
            }
        });

        dialog.setOnShown(e -> nameField.requestFocus());

        Optional<UserInput> result = dialog.showAndWait();
        result.ifPresent(user ->
                chatController.addUserToSelectedGroup(user.ipAddress(), user.name(), user.port())
        );
    }

    private void updateSelectedGroupHeader(ChatGroup group) {
        selectedGroupTitle.setText(group == null ? "" : group.getName());
        addUserButton.setDisable(group == null);
        leaveGroupButton.setDisable(group == null);
        inputField.setDisable(group == null);
        sendButton.setDisable(group == null);
    }

    private void showMessages(ChatGroup group) {
        if (visibleMessages != null) {
            visibleMessages.removeListener(messageListener);
        }

        visibleMessages = group == null ? null : group.getMessages();
        if (visibleMessages != null) {
            visibleMessages.addListener(messageListener);
        }
        renderMessages();
    }

    private void renderMessages() {
        messagesBox.getChildren().clear();
        if (visibleMessages == null) {
            Label emptyLabel = new Label("No groups. Create or join a group to start chatting.");
            emptyLabel.setStyle("-fx-opacity: 0.75;");
            messagesBox.getChildren().add(emptyLabel);
            return;
        }

        for (ChatMessage message : visibleMessages) {
            messagesBox.getChildren().add(createMessageRow(message));
        }
    }

    private HBox createMessageRow(ChatMessage message) {
        if (message.isOutgoing()) {
            return createOutgoingMessage(message.getText());
        }
        return createIncomingMessage(message.getSender(), message.getText());
    }

    private HBox createIncomingMessage(String sender, String text) {
        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(400);
        bubble.setPadding(new Insets(10));
        bubble.setStyle(
                "-fx-background-color: -color-neutral-muted;" +
                        "-fx-background-radius: 12;"
        );

        VBox bubbleBox = new VBox(4, senderLabel, bubble);
        bubbleBox.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(bubbleBox);
        row.setAlignment(Pos.CENTER_LEFT);

        return row;
    }

    private HBox createOutgoingMessage(String text) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(400);
        bubble.setPadding(new Insets(10));
        bubble.setStyle(
                "-fx-background-color: -color-accent-emphasis;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 12;"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(spacer, bubble);
        row.setAlignment(Pos.CENTER_RIGHT);

        return row;
    }

    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void openDebugWindow() {
        DebugView debugView = new DebugView(debugController);

        Stage debugStage = new Stage();
        debugStage.setTitle("Debug View -- " + chatController.getAddress());
        debugStage.setScene(new Scene(debugView, 1200, 800));
        debugStage.show();
    }


    public Parent getRoot() {
        return root;
    }

    private record UserInput(String ipAddress, String name, int port) {
    }

    private record GroupInput(String groupName, ArrayList<String> ipAddresses, boolean causalOrdering, boolean staticGroup, boolean reliable) {
    }
}
