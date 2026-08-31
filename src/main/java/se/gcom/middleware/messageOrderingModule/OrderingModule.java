package se.gcom.middleware.messageOrderingModule;

import se.gcom.app.debug.DebugEventType;
import se.gcom.middleware.Manager;
import se.gcom.middleware.communicationModule.ChatMessage;
import se.gcom.middleware.communicationModule.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OrderingModule {

    private Manager manager;
    private String myAddress;
    private final ConcurrentHashMap<String, OrderingType> groupOrdering = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VectorClock> vectorClockMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Queue<ChatMessage>> holdbackQueue = new ConcurrentHashMap<>();

    public enum OrderingType {
        UNORDERED,
        CAUSAL
    }
    public OrderingModule(Manager manager, String myAddress){
        this.manager = manager;
        this.myAddress = myAddress;
    }

    public void setUpGroup(String groupName, OrderingType type){
        System.out.println("Created group: " + groupName + "with type: " + type);
        groupOrdering.put(groupName, type);
        if (type == OrderingType.CAUSAL) {
            // create vector clock
            VectorClock vc = new VectorClock(myAddress);
            //vc.increment(myAddress);
            vectorClockMap.put(groupName, vc);
            holdbackQueue.putIfAbsent(groupName, new ConcurrentLinkedQueue<>());
            recordVectorClock(groupName, "created", vc.attachClock());
        }
    }

    public void joinGroup(String groupName, Map<String, Integer> groupVectorClock){
        OrderingType type = groupOrdering.getOrDefault(groupName, OrderingType.UNORDERED);

        if (type == OrderingType.CAUSAL) {
            VectorClock vc = new VectorClock(myAddress);
            if (groupVectorClock != null && !groupVectorClock.isEmpty()) {
                vc.updateFromMap(groupVectorClock);
            }
            vectorClockMap.put(groupName, vc);
            holdbackQueue.putIfAbsent(groupName, new ConcurrentLinkedQueue<>());
            System.out.println("Joined CAUSAL group: " + groupName +" VC: " + vc);
            recordVectorClock(groupName, "joined", vc.attachClock());
        } else {
            System.out.println("Joined UNORDERED group: " + groupName );
        }
    }


    public void leaveGroup(String groupName, String leavingAddress) {
        System.out.println("OrderingModule: handling leave of " + leavingAddress
                + " in group " + groupName);

        VectorClock vc = vectorClockMap.get(groupName);
        if (vc == null) {
            return;
        }
        // Remove the failed process from the vector clock
        vc.removeProcess(leavingAddress);
        recordVectorClock(groupName, "leave" + leavingAddress + " removed", vc.attachClock());
        checkHoldbackQueue(groupName);
    }

    public void removeGroup (String groupName) {
        groupOrdering.remove(groupName);
        vectorClockMap.remove(groupName);
        Queue<ChatMessage> queue = holdbackQueue.remove(groupName);
        int remaining = queue.size();
        System.out.println("Leaving group with " + remaining + "messages in the holdback queue!");
    }



    public ChatMessage handleOutgoingMessage(ChatMessage msg, String groupName){
        OrderingType type = groupOrdering.getOrDefault(groupName, OrderingType.UNORDERED);
        if (type == OrderingType.UNORDERED) {
            return msg;
        } else {
            VectorClock vc = vectorClockMap.get(groupName);
            vc.increment(myAddress);
            Map<String, Integer> updatedClock = vc.attachClock();
            recordVectorClock(groupName, "send", updatedClock);
            return msg.toBuilder().putAllVectorClock(updatedClock).build();
        }

    }


    public void handleIncomingMessage(ChatMessage msg){
        OrderingType type = groupOrdering.get(msg.getGroupId());
        if (type == null){
            System.out.println("No message ordering type set, defaulting to UNORDERED");
            type = OrderingType.UNORDERED;
        }

        if (type == OrderingType.UNORDERED) {
            manager.deliverIncomingMessage(msg);
        } else {
            handleCausalIncoming(msg);
        }
    }

    private void handleCausalIncoming(ChatMessage msg){
        String groupId = msg.getGroupId();
        VectorClock myVC = vectorClockMap.get(groupId);
        Queue<ChatMessage> queue = holdbackQueue.get(groupId);

        if (myVC == null || queue == null) {
            System.err.println("ERROR: myVC or holdback queue is null, we deliver WITHOUT checking tho");
            manager.deliverIncomingMessage(msg);
            return;
        }

        Map<String, Integer> incomingClock = msg.getVectorClockMap();

        System.out.println("From: " + msg.getSenderId() + ", message ID: " + msg.getMessageId());
        System.out.println("Current  VC (my clock): " + myVC);
        System.out.println("Incoming VC: " + incomingClock);
        System.out.println("Can deliver?: " + myVC.canDeliver(incomingClock, msg.getSenderId()));
        System.out.println("Holdback queue size: " + queue.size());

        // add to holdback
        queue.add(msg);
        // now we check the whole queue and deliver
        checkHoldbackQueue(groupId);

    }

    private void checkHoldbackQueue(String groupId) {
        Queue<ChatMessage> queue = holdbackQueue.get(groupId);
        VectorClock myVC = vectorClockMap.get(groupId);
        if (queue == null || myVC == null || queue.isEmpty()) {
            return;
        }

        System.out.println("[Holdback] Checking queue (size=" + queue.size() + ") for " + groupId);

        boolean deliveredSomething;

        do {
            deliveredSomething = false;
            var iterator = queue.iterator();

            while (iterator.hasNext()) {
                ChatMessage m = iterator.next();

                if (myVC.canDeliver(m.getVectorClockMap(), m.getSenderId())) {
                    iterator.remove();

                    System.out.println("[Holdback] DELIVERING: " + m.getMessageId()
                            + " from " + m.getSenderId());

                    manager.deliverIncomingMessage(m);
                    myVC.updateFromMap(m.getVectorClockMap());
                    recordVectorClock(groupId, "released " + m.getMessageId(), myVC.attachClock());

                    deliveredSomething = true;
                    // start over so we keep the order correct
                    break;
                }
            }
        } while (deliveredSomething);
        System.out.println("[Holdback] Done, Remaining in queue: " + queue.size());
    }

    public void addMemberToVectorClock(String groupName, String newMemberAddress) {
        if (!orderingIsCausal(groupName)) return;

        VectorClock vc = vectorClockMap.get(groupName);
        if (vc != null) {
            vc.updateFromMap(Map.of(newMemberAddress, 0));
            System.out.println("Added new member " + newMemberAddress + " to vector clock -> " + vc);
            recordVectorClock(groupName, "member " + newMemberAddress + " added", vc.attachClock());
        }
    }

    public Map<String, Integer> getVectorClock(String groupName) {
        VectorClock vc = vectorClockMap.get(groupName);
        System.out.println("Get vector clock");
        if (vc != null) {
            Map<String, Integer> clock = vc.attachClock();

            System.out.println(clock);

            return clock;        }
        System.out.println("No vector clock for group: " + groupName);
        return new ConcurrentHashMap<>();
    }

    public void setVectorClockValue(String groupName, String process, int value) {
        VectorClock vc = vectorClockMap.get(groupName);
        if (vc == null || process == null || process.isBlank()) {
            return;
        }

        vc.setValue(process, value);
        recordVectorClock(groupName, "edited", vc.attachClock());
    }

    public List<ChatMessage> getHoldbackQueue(String groupName) {
        Queue<ChatMessage> queue = holdbackQueue.get(groupName);
        if (queue == null) {
            return List.of();
        }

        return new ArrayList<>(queue);
    }

    public Boolean orderingIsCausal(String groupName){
        OrderingType type = groupOrdering.get(groupName);
        return type == OrderingType.CAUSAL;
    }

    public OrderingType getOrderingType(String groupName) {
        return groupOrdering.getOrDefault(groupName, OrderingType.UNORDERED);
    }

    private void recordVectorClock(String groupName, String action, Map<String, Integer> clock) {
        manager.getDebugMonitor().recordEvent(
                DebugEventType.VECTOR_CLOCK_UPDATED,
                myAddress,
                "group=" + groupName + " action=" + action + " vc=" + clock
        );
    }
}
