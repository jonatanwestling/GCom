package se.gcom.middleware.groupManagementModule;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import se.NameServer.grpc.*;

import java.util.ArrayList;
import java.util.List;

public class NamingServerCommunication {
    private ManagedChannel channel;
    NamingServiceGrpc.NamingServiceBlockingStub stub;
    public NamingServerCommunication(){
        channel = ManagedChannelBuilder
                .forAddress("localhost", 1111)
                .usePlaintext()
                .build();
        stub = NamingServiceGrpc.newBlockingStub(channel);
    }

    public void createNewGroup(String groupName, ArrayList<String> addresses){
            stub.addNewGroup(
                    AddGroupRequest.newBuilder()
                            .setGroupName(groupName)
                            .addAllAddresses(addresses)
                            .build()
            );
    }
    public void addToGroup(String groupName, String address){
        try {
            stub.addToGroup(
                    AddToGroupRequest.newBuilder()
                            .setGroupName(groupName)
                            .setAddress(address)
                            .build()
            );
        }catch (StatusRuntimeException e){
            System.out.println(e.getMessage());
        }
    }

    public ArrayList<String> getAddresses(String groupName) throws StatusRuntimeException{
        List<String> addresses = new ArrayList<>();

        AddressList response = stub.getAddresses(
                GroupRequest.newBuilder()
                        .setGroupName(groupName)
                        .build()
        );
        addresses = response.getAddressesList();

        return new ArrayList<>(addresses);
    }

    public void removeFromGroup(String groupName, String address){
        try {
            stub.removeFromGroup(
                    AddToGroupRequest.newBuilder()
                            .setGroupName(groupName)
                            .setAddress(address)
                            .build());
        } catch (StatusRuntimeException e){
            System.out.println(e.getMessage());
        }
    }
    public boolean isUp(){
        try {
            stub.isUp(Empty.newBuilder().build());
        } catch (StatusRuntimeException e) {
            return false;
        }
        return true;
    }
    public void shutdown(){
        channel.shutdown();
    }
}
