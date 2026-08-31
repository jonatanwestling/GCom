package se.gcom.app.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import se.gcom.app.model.ChatGroup;
import se.gcom.app.model.ChatMessage;
import se.gcom.middleware.Manager;
import se.gcom.middleware.messageOrderingModule.OrderingModule;

import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatController {

    private Manager manager;

    private final ObservableList<ChatGroup> groups =
            FXCollections.observableArrayList();

    private ChatGroup selectedGroup;

    public ChatController() {
    }

    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "message-send-thread");
        t.setDaemon(true);
        return t;
    });

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public ObservableList<ChatGroup> getGroups() {
        return groups;
    }


    public void selectGroup(ChatGroup group) {
        selectedGroup = group;
    }

    public ChatGroup createGroup(String name, boolean causalOrdering, boolean reliable, boolean staticGroup, ArrayList<String> ipAddresses) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String trimmedName = name.trim();

        if (manager != null) {
            if (staticGroup) {
                manager.addStaticGroup(trimmedName, ipAddresses);
            } else {
                manager.addGroup(trimmedName);
            }
            manager.addGroupToReliablePairing(trimmedName, reliable);
        }
        ChatGroup group = new ChatGroup(trimmedName, trimmedName);
        groups.add(group);
        selectedGroup = group;


        return group;
    }

    public void sendMessage(String text) {
        if (selectedGroup == null || text == null || text.isBlank()) {
            return;
        }

        String groupId = selectedGroup.getId();
        String messageText = text;

        if (manager != null) {
            sendExecutor.submit(() -> manager.sendMessage(groupId, messageText));
        }
    }

    public void addUserToSelectedGroup(String ipAddress, String name, int port) {
        if (selectedGroup == null || manager == null) {
            return;
        }

        manager.addUserToGroup(selectedGroup.getId(), ipAddress, name, port);
    }


    public void receiveMessage(String sender,String groupName, String text, boolean outgoing) {
        // Find the correct group using your existing method
        ChatGroup targetGroup = findGroup(groupName);

        // Fallback if group not found
        if (targetGroup == null) {
            targetGroup = selectedGroup;
        }
        if (targetGroup == null) {
            targetGroup = new ChatGroup(groupName, groupName);
            groups.add(targetGroup);
            selectedGroup = targetGroup;
        }
        System.out.println("Outgoing: " + outgoing);
        ChatGroup finalTargetGroup = targetGroup;
        Platform.runLater(() ->
                finalTargetGroup.getMessages().add(new ChatMessage(sender, text, outgoing))
        );
    }


    public ChatGroup joinGroup(String name, String ip) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String trimmedName = name.trim();
        ChatGroup existingGroup = findGroup(trimmedName);
        if (existingGroup != null) {
            selectedGroup = existingGroup;
            return existingGroup;
        }


        if (manager != null) {
            manager.joinGroup(trimmedName, ip);
        }
        ChatGroup group = new ChatGroup(trimmedName, trimmedName);
        groups.add(group);
        selectedGroup = group;

        return group;
    }

    private ChatGroup findGroup(String name) {
        for (ChatGroup group : groups) {
            if (group.getId().equals(name) || group.getName().equals(name)) {
                return group;
            }
        }
        return null;
    }

    public void leaveGroup() {
        ChatGroup group = selectedGroup;
        if (group == null || manager == null) {
            return;
        }
        manager.leaveGroup(group.getName());
        groups.remove(group);
        selectedGroup = groups.isEmpty() ? null : groups.getFirst();
    }

    public void setGroupOrdering(String groupId, boolean causal) {
        if (manager != null) {
            OrderingModule.OrderingType type = causal
                    ? OrderingModule.OrderingType.CAUSAL
                    : OrderingModule.OrderingType.UNORDERED;

            manager.setGroupOrdering(groupId, type);
        }
    }

    public String getAddress(){
        return manager.getMyAddress();
    }

    public Boolean namingServerIsUp(){
        return manager.namingServerIsUp();
    }
}
