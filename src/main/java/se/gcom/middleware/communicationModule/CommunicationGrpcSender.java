package se.gcom.middleware.communicationModule;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import se.gcom.app.debug.DebugEventType;
import se.gcom.middleware.Manager;

public class CommunicationGrpcSender {
    //this class will be used to make grpc calls to send messages to other nodes
    private final ConcurrentHashMap<String, ManagedChannel> channelMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService sendExecutor;
    private final Manager manager;
    public CommunicationGrpcSender(Manager manager) {
        this.manager = manager;
        this.sendExecutor = Executors.newScheduledThreadPool(8);
    }

    public int multicast(Message msg, List<String> addresses) {
        for (String address : addresses) {
            long delayMillis = manager.getDebugSendDelay(address);
            sendExecutor.schedule(
                    () -> sendToNode(address, msg),
                    delayMillis,
                    TimeUnit.MILLISECONDS
            );
        }

        return addresses.size() * 2;
    }

    private void sendToNode(String address, Message msg) {
        ManagedChannel channel = getChannel(address);
        CommunicationServiceGrpc.CommunicationServiceBlockingStub stub =
                CommunicationServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(5, TimeUnit.SECONDS);

        try {
            recordNetworkEvent(DebugEventType.NETWORK_SEND, address, msg);
            Ack ack = stub.sendMessage(msg);
            recordAck(address, ack);

        } catch (StatusRuntimeException e) {
            System.err.println("gRPC error to " + address + ": " + e.getMessage());

            if (msg.hasChatMessage()) {
                String groupId = msg.getChatMessage().getGroupId();
                // Only trigger failure if node is still known in the group
                if (manager.getMembers(groupId).contains(address)) {
                    manager.handleNodeFailure(groupId, address);
                }
            }
            removeChannel(address);

        } catch (Exception e) {
            System.err.println("Unexpected error sending to " + address + ": " + e.getMessage());
            removeChannel(address);
        }
    }

    public Ack sendJoin(String address, Message msg) {
        ManagedChannel channel = getChannel(address);
        CommunicationServiceGrpc.CommunicationServiceBlockingStub stub =
                CommunicationServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(5, TimeUnit.SECONDS);

        try {
            recordNetworkEvent(DebugEventType.NETWORK_SEND, address, msg);
            Ack ack = stub.sendMessage(msg);
            recordAck(address, ack);
            return ack;
        } catch (StatusRuntimeException e) {
            System.err.println("Blocking gRPC error to " + address + ": " + e.getMessage());
            if (msg.hasChatMessage()) {
                manager.handleNodeFailure(msg.getChatMessage().getGroupId(), address);
            }
            removeChannel(address);
            return Ack.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .build();
        } catch (Exception e) {
            System.err.println("Could not reach node: " + e.getMessage());
            removeChannel(address);
            return Ack.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage(e.getMessage())
                    .build();
        }
    }

    private ManagedChannel getChannel(String address){
        return channelMap.computeIfAbsent(address, addr -> {
            String[] parts = addr.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            return ManagedChannelBuilder.forAddress(host, port).usePlaintext().keepAliveWithoutCalls(true).build();
        });
    }

    private void removeChannel(String address){
        ManagedChannel channel = channelMap.remove(address);
        if (channel != null){
            channel.shutdown();
            System.out.println("Removed channel for address:" + address);
        }

    }

    public void shutdown(){
        System.out.println("Shutting down all channels...");
        sendExecutor.shutdownNow();
        channelMap.forEach((addr, ch) -> ch.shutdownNow());
        channelMap.clear();
    }

    private void recordNetworkEvent(DebugEventType type, String address, Message msg) {
        manager.getDebugMonitor().recordEvent(
                type,
                manager.getMyAddress(),
                "to=" + address + " content=" + msg.getContentCase()
        );
    }

    private void recordAck(String address, Ack ack) {
        manager.getDebugMonitor().recordEvent(
                DebugEventType.NETWORK_ACK,
                manager.getMyAddress(),
                "from=" + address + " success=" + ack.getSuccess()
        );
    }

}
