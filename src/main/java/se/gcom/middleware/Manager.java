package se.gcom.middleware;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import se.gcom.app.controller.ChatController;
import se.gcom.app.controller.DebugController;
import se.gcom.app.debug.DebugEventType;
import se.gcom.app.debug.DebugMonitor;
import se.gcom.middleware.communicationModule.*;
import se.gcom.middleware.groupManagementModule.GroupManagement;
import se.gcom.middleware.messageOrderingModule.OrderingModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Manager {

    ChatController chatController;
    DebugController debugController;
    OrderingModule orderingModule;
    String myAddress;
    private long messageCounter;
    private final DebugMonitor debugMonitor = new DebugMonitor();
    private HashMap<String, ArrayList<ArrayList<String>>> messageToPathMap = new HashMap<>();
    private HashMap<String, ArrayList<String>> groupToMessageMap = new HashMap<>();
    private final Map<String, Boolean> groupReliableMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> debugKnownMembersByGroup = new ConcurrentHashMap<>();
    private final Map<String, Long> debugSendDelayByAddress = new ConcurrentHashMap<>();

    GroupManagement groupManagement;
    CommunicationService communicationService;

    public Manager(ChatController chatController, DebugController debugController) {
        this.chatController = chatController;
        this.debugController = debugController;
    }

    public void start() {
        communicationService = new CommunicationService(this);
        int port = startServer();
        this.groupManagement = new GroupManagement(port);
        this.myAddress = groupManagement.getAddress();
        this.orderingModule = new OrderingModule(this, myAddress);
        debugMonitor.recordEvent(
                DebugEventType.PROCESS_STARTED,
                myAddress,
                "Communication service started on port " + port
        );
    }

    public void stop(){
        communicationService.stop();
        groupManagement.shutdown();
    }

    public String getMyAddress() {
        return myAddress;
    }

    private String generateMessageId() {
        return groupManagement.getAddress() + "--" + (++messageCounter);
    }

    public void sendMessage(String groupName, String message) {
        List<String> addresses = groupManagement.getAddresses(groupName);
        // Create the message

        String messageId = generateMessageId();
        ChatMessage msg = ChatMessage.newBuilder()
                .setMessageId(messageId)
                .setSenderId(groupManagement.getAddress())
                .setGroupId(groupName)
                .setPayload(message)
                .addPath(this.myAddress)
                .build();

        if (!groupManagement.isStaticGroup(groupName) || groupManagement.canSendMessages(groupName)) {
            debugMonitor.recordEvent(
                    DebugEventType.MESSAGE_CREATED,
                    myAddress,
                    "Created message " + messageId + " in group " + groupName + ": " + message
            );
            // let ordering module append the vector clock if needed
            ChatMessage finalMsg = orderingModule.handleOutgoingMessage(msg, msg.getGroupId());
            Message m = Message.newBuilder().setChatMessage(finalMsg).build();
            // print all messages for debug
            System.out.println("Sending to" + addresses);
            // let communication module send the message
            int outgoingCount = communicationService.multicast(m, addresses);
            recordOperationPerformance(groupName, messageId, outgoingCount);
            if (!groupToMessageMap.containsKey(groupName)) {
                groupToMessageMap.put(groupName, new ArrayList<>());
            }
            groupToMessageMap.get(groupName).add(messageId);

        }
    }

    public void handleIncomingMessage(ChatMessage msg, boolean reliable){
        orderingModule.handleIncomingMessage(msg);
        groupToMessageMap.get(msg.getGroupId()).add(msg.getMessageId());
        if (reliable) {
            if (msg.getSenderId().equals(myAddress)) {
                return;
            }
            Message m = Message.newBuilder().setChatMessage(msg).build();
            List<String> addresses = groupManagement.getAddresses(msg.getGroupId());
            // incoming so we do not need to send to sender and ourselves
            addresses.remove(msg.getSenderId());
            addresses.remove(myAddress);
            if (addresses.isEmpty()) return;
            int outgoingCount = communicationService.multicast(m, addresses);
            recordOperationPerformance(msg.getGroupId(), msg.getMessageId(), outgoingCount);
        }
    }

    public void deliverIncomingMessage(ChatMessage msg) {
        boolean outgoing = msg.getSenderId().equals(groupManagement.getAddress());
        chatController.receiveMessage(msg.getSenderId(), msg.getGroupId(), msg.getPayload(), outgoing);
        receivePath(new ArrayList<>(msg.getPathList()), msg.getMessageId());
        debugMonitor.recordEvent(
                DebugEventType.MESSAGE_DELIVERED,
                myAddress,
                "Delivered message " + msg.getMessageId()
                        + " from " + msg.getSenderId()
                        + " in group " + msg.getGroupId()
                        + ": " + msg.getPayload()
        );
    }

    public void deliverIncomingMessage(GroupMembership msg) {
        if (msg.getJoining()) {
            if (!groupManagement.isStaticGroup(msg.getGroupId()) || groupManagement.canJoinStaticGroup(msg.getGroupId(), msg.getSenderId())) {
                groupManagement.addNewMember(msg.getGroupId(), msg.getSenderId());
                rememberDebugMember(msg.getGroupId(), msg.getSenderId());
                orderingModule.addMemberToVectorClock(msg.getGroupId(), msg.getSenderId());
                System.out.println("Received join message");
            }
        } else {
            rememberDebugMember(msg.getGroupId(), msg.getSenderId());
            groupManagement.removeMember(msg.getGroupId(), msg.getSenderId());
            orderingModule.leaveGroup(msg.getGroupId(), msg.getSenderId());
            System.out.println("Received leave message");
        }
    }

    public void addUserToGroup(String groupName, String ipAddress, String name, int port) {
        System.out.println("groupName: " + groupName);
        System.out.println("ipAddress: " + ipAddress);
        System.out.println("name: " + name);
        System.out.println("port: " + port);

    }

    public void addGroup(String groupName) {
        groupManagement.createNewGroup(groupName, new ArrayList<>());
        rememberDebugMembers(groupName, groupManagement.getAddresses(groupName));
        groupToMessageMap.put(groupName, new ArrayList<>());
    }

    public void addStaticGroup(String groupName, ArrayList<String> groupMembers) {
        groupManagement.createNewStaticGroup(groupName, groupMembers);
        rememberDebugMembers(groupName, groupManagement.getAddresses(groupName));
        groupToMessageMap.put(groupName, new ArrayList<>());
    }
    public void addGroupToReliablePairing(String groupName, boolean reliable){
        groupReliableMap.put(groupName, reliable);
        communicationService.addGroupToReliablePairing(groupName, reliable);
    }

    private int startServer() {
        return communicationService.start();
    }

    public void joinGroup(String name, String ip) throws StatusRuntimeException {
        ArrayList<String> addresses;
        if (groupManagement.NamingServerIsUp()) {
            addresses = groupManagement.joinGroup(name);
        } else {
            addresses = new ArrayList<>();
            addresses.add(ip);
        }
        GroupMembership g = GroupMembership.newBuilder()
                .setGroupId(name)
                .setSenderId(groupManagement.getAddress())
                .setJoining(true)
                .setMessageId("1")
                .build();
        Message m = Message.newBuilder().setGroupMembership(g).build();
        // Send join request
        System.out.println("Sending join request to first in member list: " + addresses.getFirst());
        Ack ack = communicationService.sendBlocking(addresses.getFirst(), m);

        if (ack.getSuccess() && ack.hasMembership()) {
            MembershipAck membershipAck = ack.getMembership();
            System.out.println("Got membership ack with VC: " + membershipAck.getVectorClockMap());
            System.out.println("Can join group? " + membershipAck.getCanJoinStaticGroup());
            if(!membershipAck.getIsStatic() || membershipAck.getCanJoinStaticGroup()) {;
                OrderingModule.OrderingType groupType;
                if (membershipAck.getIsCausal()) {
                    groupType = OrderingModule.OrderingType.CAUSAL;
                } else {
                    groupType = OrderingModule.OrderingType.UNORDERED;
                }
                orderingModule.setUpGroup(name, groupType);
                // join the group with the vector clock we
                orderingModule.joinGroup(name, membershipAck.getVectorClockMap());
                if (namingServerIsUp()) {
                    ArrayList<String> newAddresses = new ArrayList<>(addresses);
                    newAddresses.remove(addresses.getFirst());
                    newAddresses.remove(myAddress);
                    communicationService.multicast(m, newAddresses);
                } else {
                    ArrayList<String> newAddresses = new ArrayList<>(membershipAck.getMembersList());
                    groupManagement.joinGroup(name, newAddresses);
                    newAddresses.remove(ip);
                    newAddresses.remove(myAddress);
                    communicationService.multicast(m, newAddresses);
                }
                if (membershipAck.getIsStatic()) {
                    if (membershipAck.getCanJoinStaticGroup()) {
                        groupManagement.addStaticGroup(name, new ArrayList<>(membershipAck.getStaticMembersList()));
                        if (groupManagement.canStartSendingMessages(name)) {
                            groupManagement.addCanSendMessages(name);
                        }
                    }
                }
                communicationService.addGroupToReliablePairing(name, membershipAck.getIsReliable());
                groupToMessageMap.put(name, new ArrayList<>());
                groupReliableMap.put(name, membershipAck.getIsReliable());
                rememberDebugMembers(name, groupManagement.getAddresses(name));
            } else {
                groupManagement.leaveGroup(name);
                throw new StatusRuntimeException(Status.PERMISSION_DENIED.withDescription("You are not allowed to join this group"));
            }
        }
    }

    public boolean namingServerIsUp() {
        return groupManagement.NamingServerIsUp();
    }

    public List<String> getMembers(String group) {
        return groupManagement.getAddresses(group);
    }

    public List<String> getGroupNames() {
        return groupManagement.getGroupNames();
    }

    public List<String> getGroupMembers(String group) {
        return groupManagement.getAddresses(group);
    }

    public List<String> getDebugKnownGroupMembers(String group) {
        if (group == null) {
            return Collections.emptyList();
        }

        Set<String> knownMembers = new HashSet<>(debugKnownMembersByGroup.getOrDefault(group, Collections.emptySet()));
        knownMembers.addAll(groupManagement.getAddresses(group));
        return new ArrayList<>(knownMembers);
    }

    public void leaveGroup(String groupName) {
        GroupMembership g = GroupMembership.newBuilder()
                .setGroupId(groupName)
                .setSenderId(groupManagement.getAddress())
                .setJoining(false)
                .setMessageId("1")
                .build();
        Message m = Message.newBuilder().setGroupMembership(g).build();
        ArrayList<String> addresses = new ArrayList<>(groupManagement.getAddresses(groupName));
        orderingModule.removeGroup(groupName);
        groupManagement.leaveGroup(groupName);
        addresses.remove(groupManagement.getAddress());
        System.out.println("Sending Leave to:" + addresses);
        communicationService.multicast(m, addresses);
    }

    public Map<String, Integer> getCurrentVectorClock(String groupName) {
        return orderingModule.getVectorClock(groupName);
    }

    public void setVectorClockValue(String groupName, String process, int value) {
        orderingModule.setVectorClockValue(groupName, process, value);
    }

    public List<ChatMessage> getHoldbackQueue(String groupName) {
        return orderingModule.getHoldbackQueue(groupName);
    }

    public Boolean orderingIsCausal(String groupName) {
        return orderingModule.orderingIsCausal(groupName);
    }

    private void recordOperationPerformance(String groupName, String messageId, int msgCount) {
        boolean reliable = groupReliableMap.getOrDefault(groupName, false);
        OrderingModule.OrderingType orderingType = orderingModule.getOrderingType(groupName);
        int totalMessages = msgCount;

        debugMonitor.recordEvent(
                DebugEventType.OPERATION_PERFORMANCE,
                myAddress,
                "messageId=" + messageId + " group=" + groupName + " ordering=" + orderingType + " multicast=" + (reliable ? "RELIABLE" : "UNRELIABLE") + " total=" + totalMessages
        );
    }

    public void setGroupOrdering(String groupId, OrderingModule.OrderingType type) {
        orderingModule.setUpGroup(groupId, type);
    }

    public boolean isStaticGroup(String groupName) {
        return groupManagement.isStaticGroup(groupName);
    }

    public ArrayList<String> getStaticGroupMembers(String groupName) {
        return groupManagement.getStaticGroupMembers(groupName);
    }

    public boolean canJoinStaticGroup(String group, String address) {
        return groupManagement.canJoinStaticGroup(group, address);
    }

    public void canStartSendingMessagesCheck(String group) {
        if (groupManagement.canStartSendingMessages(group)) {
            groupManagement.addCanSendMessages(group);
        }
    }

    public DebugMonitor getDebugMonitor() {
        return debugMonitor;
    }

    public void leaveAllGroups() {
        List<String> groupNames = groupManagement.getGroupNames();
        for (String groupName : groupNames) {
            leaveGroup(groupName);
        }
    }

    public void removeMember(String groupName, String address) {
        rememberDebugMember(groupName, address);
        groupManagement.removeMember(groupName, address);
    }

    public void addMember(String groupName, String address) {
        groupManagement.addNewMember(groupName, address);
        rememberDebugMember(groupName, address);
    }

    public long getDebugSendDelay(String address) {
        if (address == null) {
            return 0;
        }
        return debugSendDelayByAddress.getOrDefault(address, 0L);
    }

    public void setDebugSendDelay(String address, long delayMillis) {
        if (address == null || address.isBlank()) {
            return;
        }

        if (delayMillis <= 0) {
            debugSendDelayByAddress.remove(address);
        } else {
            debugSendDelayByAddress.put(address, delayMillis);
        }
    }

    public void receivePath(ArrayList<String> path, String messageId) {
        if (!messageToPathMap.containsKey(messageId)) {
            messageToPathMap.put(messageId, new ArrayList<>());
        }
        System.out.println("Received path: " + path);
        for (int i = 0; i < messageToPathMap.get(messageId).size(); i++) {
            ArrayList<String> existingPath = messageToPathMap.get(messageId).get(i);
            if (path.getLast().equals(existingPath.getLast())) {
                if (existingPath.size() >= path.size()) {
                    messageToPathMap.get(messageId).set(i, path);
                    return;
                }
            }
        }
        messageToPathMap.get(messageId).add(path);
    }

    public void handleNodeFailure(String groupName, String failedAddress) {
        System.err.println("Node failure detected, sending leave");
        debugMonitor.recordEvent(
                DebugEventType.NETWORK_FAILURE,
                myAddress,
                "Detected failure of " + failedAddress
        );

        // check that we are a member of the group still
        if (!groupManagement.getAddresses(groupName).contains(myAddress)) {
            return;
        }
        // clean up local state
        rememberDebugMember(groupName, failedAddress);
        groupManagement.removeMember(groupName, failedAddress);
        orderingModule.leaveGroup(groupName, failedAddress);
        // craft leave msg
        GroupMembership g = GroupMembership.newBuilder()
                .setGroupId(groupName)
                .setSenderId(failedAddress)
                .setJoining(false)
                .setMessageId("1")
                .build();
        Message m = Message.newBuilder().setGroupMembership(g).build();
        ArrayList<String> addresses = new ArrayList<>(groupManagement.getAddresses(groupName));
        // noo need to send to ourself
        addresses.remove(myAddress);
        System.out.println("Propagating LEAVE for failed node " + failedAddress
                + " in group '" + groupName + "' to: " + addresses);

        communicationService.multicast(m, addresses);
    }

    public ArrayList<ArrayList<String>> getPaths(String messageId) {
        return messageToPathMap.get(messageId);
    }
    public ArrayList<String> getMessages(String groupName){
        ArrayList<String> messages = groupToMessageMap.get(groupName);
        ArrayList<String> trimmedMessages = new ArrayList<>();
        for (String message : messages){
            if(messageToPathMap.containsKey(message) && !trimmedMessages.contains(message)){
                trimmedMessages.add(message);
            }
        }
        return trimmedMessages;
    }
    public HashMap<String, Integer> getMessageCountMap(String group){
        HashMap<String, Integer> actualMap = communicationService.getMessageCountMap();
        HashMap<String, Integer> trimmedMap = new HashMap<>();
        for(String message : actualMap.keySet()){
            if(groupToMessageMap.get(group).contains(message)){
                trimmedMap.put(message, actualMap.get(message));
            }
        }
        return trimmedMap;
    }

    private void rememberDebugMember(String groupName, String address) {
        if (groupName == null || address == null || address.isBlank()) {
            return;
        }
        debugKnownMembersByGroup.computeIfAbsent(groupName, ignored -> ConcurrentHashMap.newKeySet()).add(address);
    }

    private void rememberDebugMembers(String groupName, List<String> addresses) {
        if (addresses == null) {
            return;
        }
        for (String address : addresses) {
            rememberDebugMember(groupName, address);
        }
    }
}
