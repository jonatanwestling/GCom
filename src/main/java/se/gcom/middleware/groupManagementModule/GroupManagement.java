package se.gcom.middleware.groupManagementModule;

import io.grpc.StatusRuntimeException;
import se.gcom.middleware.communicationModule.GroupMembership;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

public class GroupManagement {
    private NamingServerCommunication namingServerCommunication;
    private HashMap<String, ArrayList<String>> groupAddressMap;
    private HashMap<String, ArrayList<String>> staticGroupMembers;
    private Set<String> canSendMessages;
    private String address;
    private boolean namingServerIsUp;

    public GroupManagement(int port){
        namingServerCommunication = new NamingServerCommunication();
        groupAddressMap = new HashMap<>();
        staticGroupMembers = new HashMap<>();
        canSendMessages = new HashSet<>();
        String ip = getIpAddress();
        address = ip + ":" + port;
        namingServerIsUp = namingServerCommunication.isUp();
    }

    public boolean NamingServerIsUp() {
        return namingServerIsUp;
    }

    public List<String > getAddresses(String groupName){
        ArrayList<String> addresses = groupAddressMap.get(groupName);
        if (addresses == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(addresses);
    }

    public List<String> getGroupNames(){
        return new ArrayList<>(groupAddressMap.keySet());
    }

    public void createNewGroup(String groupName, ArrayList<String> addresses){
        // need to include our own address
        addresses.add(this.address);
        if(namingServerIsUp) {
            namingServerCommunication.createNewGroup(groupName, addresses);
        }
        groupAddressMap.put(groupName, addresses);
    }

    public void createNewStaticGroup(String groupName, ArrayList<String> groupMembers){
        groupMembers.add(address);
        staticGroupMembers.put(groupName, groupMembers);
        groupAddressMap.put(groupName, new ArrayList<>(List.of(address)));
        if(namingServerIsUp) {
            namingServerCommunication.createNewGroup(groupName, new ArrayList<>(List.of(address)));
        }
    }

    public ArrayList<String> joinGroup(String groupName) throws StatusRuntimeException {
        ArrayList<String> addresses = namingServerCommunication.getAddresses(groupName);
        ArrayList<String> a = new ArrayList<>(addresses);
        a.add(this.address);
        groupAddressMap.put(groupName, a);
        namingServerCommunication.addToGroup(groupName, this.address);
        return addresses;
    }
    public void joinGroup(String groupName, ArrayList<String> addresses){
        groupAddressMap.put(groupName, new ArrayList<>(addresses));
    }

    public void leaveGroup(String groupName){
        if (namingServerIsUp){
            namingServerCommunication.removeFromGroup(groupName, this.address);
        }
        groupAddressMap.remove(groupName);
    }

    private String getIpAddress(){
        String ip = null;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            ip = socket.getLocalAddress().getHostAddress();
            System.out.println("Local IP: " + ip);
        } catch (SocketException | UnknownHostException e) {
            System.out.println(e.getMessage());
        }
        return ip;
    }
    public String getAddress(){
        return address;
    }

    public void addNewMember(String groupName, String ipAddress){
        ArrayList<String> addresses = groupAddressMap.get(groupName);
        if (addresses != null && !addresses.contains(ipAddress)) {
            addresses.add(ipAddress);
        }
    }
    public void removeMember(String groupName, String ipAddress){
        groupAddressMap.get(groupName).remove(ipAddress);
    }

    public void addStaticGroup(String group, ArrayList<String> addresses){
        staticGroupMembers.put(group, addresses);
    }

    public boolean isStaticGroup(String group){
        return staticGroupMembers.containsKey(group);
    }

    public ArrayList<String> getStaticGroupMembers(String group){
        return staticGroupMembers.get(group);
    }
    public boolean canStartSendingMessages(String group){
        return staticGroupMembers.get(group).size() == groupAddressMap.get(group).size();
    }
    public void addCanSendMessages(String group){
        canSendMessages.add(group);
        staticGroupMembers.get(group).clear();
    }

    public boolean canSendMessages(String group){
        System.out.println("Current Members:" + groupAddressMap.get(group));
        System.out.println("Expected Members:" + staticGroupMembers.get(group));
        return canSendMessages.contains(group);
    }

    public boolean canJoinStaticGroup(String group, String address){
        System.out.println(address + "wants to join group " + group);
        System.out.println("Static Members:" + staticGroupMembers.get(group));
        return staticGroupMembers.get(group).contains(address);
    }

    public void shutdown(){
        namingServerCommunication.shutdown();
    }

}
