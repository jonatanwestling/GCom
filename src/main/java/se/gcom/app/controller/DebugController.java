package se.gcom.app.controller;

import javafx.collections.ObservableList;
import se.gcom.app.debug.DebugEvent;
import se.gcom.middleware.Manager;
import se.gcom.middleware.communicationModule.ChatMessage;

import java.util.*;

public class DebugController {
    private Manager manager;

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public ObservableList<DebugEvent> getEvents(){
        return manager.getDebugMonitor().getEvents();
    }

    public List<String> getGroupNames() {
        if (manager == null) {
            return Collections.emptyList();
        }
        return manager.getGroupNames();
    }

    public List<String> getGroupMembers(String groupName) {
        return manager.getGroupMembers(groupName);
    }

    public List<String> getDebugKnownGroupMembers(String groupName) {
        if (manager == null || groupName == null || groupName.isBlank()) {
            return Collections.emptyList();
        }
        return manager.getDebugKnownGroupMembers(groupName);
    }

    public void setMemberEnabled(String groupName, String address, boolean enabled) {
        if (manager == null || groupName == null || address == null) {
            return;
        }

        if (enabled) {
            manager.addMember(groupName, address);
        } else {
            manager.removeMember(groupName, address);
        }
    }

    public double getSendDelaySeconds(String address) {
        if (manager == null || address == null) {
            return 0;
        }
        return manager.getDebugSendDelay(address) / 1000.0;
    }

    public void setSendDelaySeconds(String address, double delaySeconds) {
        if (manager == null || address == null) {
            return;
        }
        long delayMillis = Math.max(0, Math.round(delaySeconds * 1000));
        manager.setDebugSendDelay(address, delayMillis);
    }

    public Map<String, Integer> getCurrentVectorClock(String groupName) {
        if (manager == null || groupName == null || groupName.isBlank()) {
            return Collections.emptyMap();
        }
        return manager.getCurrentVectorClock(groupName);
    }

    public void setVectorClockValue(String groupName, String process, int value) {
        if (manager != null && groupName != null && process != null) {
            manager.setVectorClockValue(groupName, process, value);
        }
    }

    public List<ChatMessage> getHoldbackQueue(String groupName) {
        if (manager == null || groupName == null || groupName.isBlank()) {
            return Collections.emptyList();
        }
        return manager.getHoldbackQueue(groupName);
    }
    public ArrayList<String> getMessages(String groupName) {
        return manager.getMessages(groupName);
    }
    public ArrayList<ArrayList<String>> getPaths(String message){
        return manager.getPaths(message);
    }

    public HashMap<String, Integer> getMessageCountMap(String groupName) {
        return manager.getMessageCountMap(groupName);
    }
}
