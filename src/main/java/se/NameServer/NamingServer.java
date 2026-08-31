package se.NameServer;

import java.util.ArrayList;
import java.util.HashMap;

public class NamingServer {
    private HashMap<String, ArrayList<String>> groupAddressMap = new HashMap<>();


    public ArrayList<String> getAddresses(String groupName) {
        if(!groupAddressMap.containsKey(groupName)){
            throw new GroupDoesNotExistException(groupName);
        }
        return new ArrayList<>(groupAddressMap.get(groupName));
    }
    public void addNewGroup(String groupName, ArrayList<String> addresses){
        if (groupAddressMap.containsKey(groupName)){
            throw new GroupAlreadyExistsException(groupName);
        }
        groupAddressMap.put(groupName, addresses);
    }
    public void addToGroup(String groupName, String address){
        if(!groupAddressMap.containsKey(groupName)){
            throw new GroupDoesNotExistException(groupName);
        }
        groupAddressMap.get(groupName).add(address);
    }
    public void removeFromGroup(String groupName, String address){
        if(!groupAddressMap.containsKey(groupName)){
            throw new GroupDoesNotExistException(groupName);
        }
        groupAddressMap.get(groupName).remove(address);
        if (groupAddressMap.get(groupName).isEmpty()){
            groupAddressMap.remove(groupName);
            System.out.println("Removed group: " + groupName);
        }
    }

    public ArrayList<String> getGroups(String address){
        ArrayList<String> groups = new ArrayList<>();
        for (String group : groupAddressMap.keySet()){
            if(groupAddressMap.get(group).contains(address)){
                groups.add(group);
            }
        }
        return groups;
    }

    public static class GroupAlreadyExistsException extends RuntimeException {
        public GroupAlreadyExistsException(String groupName) {
            super("Group already exists: " + groupName);
        }
    }

    public static class GroupDoesNotExistException extends RuntimeException {
        public GroupDoesNotExistException(String groupName) {
            super("Group does not exist: " + groupName);
        }
    }
}


