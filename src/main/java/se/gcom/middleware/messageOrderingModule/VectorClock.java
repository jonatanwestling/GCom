package se.gcom.middleware.messageOrderingModule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VectorClock {
    private final Map<String, Integer> clock = new ConcurrentHashMap<>();
    private final String myAddress;
    public VectorClock(String myAddress){
        this.myAddress = myAddress;
    }

    public void increment(String myAddress){
        // increment own spot with 1
        clock.put(myAddress, clock.getOrDefault(myAddress, 0) + 1);
    }

    public void updateFromMap(Map<String, Integer> incoming){
        if (incoming == null) return;
        // iterate over the map and take the max value of each clock
        incoming.forEach((key, value)-> {
            clock.put(key, Math.max(clock.getOrDefault(key, 0), value));
        });
    }

    public void setValue(String process, int value) {
        clock.put(process, value);
    }

    public boolean canDeliver(Map<String, Integer> incomingClock, String senderID){
        int ourClockCount = clock.getOrDefault(senderID, 0);
        int incomingClockCount = incomingClock.getOrDefault(senderID, 0);

        if (senderID.equals(myAddress)) {
            // already incremented our clock when we sent it, incoming must be the same
            if (incomingClockCount != ourClockCount) {
                System.out.println("CAN NOT DELIVER (own message)");
                return false;
            }
        } else {
            // we can not deliver if it is not the clock that we expect
            if (incomingClockCount != ourClockCount + 1) {
                System.out.println("CAN NOT DELIVER (other)");
                return false;
            }
        }
        // iterate over all other processes clocks to see if they have seen something we have not
        for (Map.Entry<String, Integer> entry : incomingClock.entrySet()) {
            String process = entry.getKey();
            int incomingVal = entry.getValue();

            if (!process.equals(senderID)) {
                // removed/crashed dont wait for it
                if (!clock.containsKey(process)) {
                    continue;
                }
                int ourVal = clock.get(process);
                //if our val is less we can not deliver
                if (ourVal < incomingVal) {
                    return false;
                }
            }
        }

        return true;
    }
    public void removeProcess(String address) {
        // dont remove my own
        if (address != null && !address.equals(myAddress)) {
            clock.remove(address);
        }
    }

    public Map<String, Integer> attachClock(){
        return new ConcurrentHashMap<>(clock);
    }

    public String toString() {
        return clock.toString();
    }
}
