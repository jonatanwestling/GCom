package se.gcom.middleware.communicationModule;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import se.gcom.middleware.Manager;

import java.util.HashMap;
import java.util.List;

public class CommunicationService {
    private final Manager manager;
    private final CommunicationGrpcSender sender;
    private final CommunicationGrpcHandler handler;
    private Server server;

    public CommunicationService(Manager manager) {
        this.manager = manager;
        this.sender = new CommunicationGrpcSender(manager);
        this.handler = new CommunicationGrpcHandler(manager);
    }

    public int start() {
        try {
            server = ServerBuilder.forPort(0)
                    .addService(handler)
                    .build()
                    .start();
            System.out.println("CommunicationService listening on port " + server.getPort());
            return server.getPort();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start server", e);
        }
    }

    public void stop() {
        if (server != null) {
            // shut down the open channels
            sender.shutdown();
            server.shutdown();
        }
    }

    public void addGroupToReliablePairing(String group, boolean reliable){
        handler.addGroupToReliablePairing(group, reliable);
    }

    public int multicast(Message msg, List<String> addresses) {
        return sender.multicast(msg, addresses);
    }

    // Used for when acks are needed, like when joining a group
    public Ack sendBlocking(String address, Message msg) {
        return sender.sendJoin(address, msg);
    }

    public HashMap<String, Integer> getMessageCountMap(){
        return handler.getMessageCountMap();
    }

}
