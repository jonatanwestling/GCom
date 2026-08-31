package se.gcom.app.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ChatGroup {
    private final String id;
    private final String name;
    private final ObservableList<ChatMessage> messages =
            FXCollections.observableArrayList();

    public ChatGroup(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ObservableList<ChatMessage> getMessages() {
        return messages;
    }

    @Override
    public String toString() {
        return name;
    }
}
