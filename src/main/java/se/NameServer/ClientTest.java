package se.NameServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import se.NameServer.grpc.*;

import java.util.ArrayList;
import java.util.List;

public class ClientTest {
    public static void main(String[] args) {
        System.out.println("Started test client");

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 1111)
                .usePlaintext()
                .build();

        NamingServiceGrpc.NamingServiceBlockingStub stub =
                NamingServiceGrpc.newBlockingStub(channel);

        // Add a new group
        try {
            stub.addNewGroup(
                    AddGroupRequest.newBuilder()
                            .setGroupName("group1")
                            .addAddresses("127.0.0.1:5000")
                            .addAddresses("127.0.0.1:5001")
                            .build()
            );
        } catch (StatusRuntimeException e){
            System.out.println(e.getMessage());
        }

        // Add one more address
        try {
            stub.addToGroup(
                    AddToGroupRequest.newBuilder()
                            .setGroupName("group1")
                            .setAddress("127.0.0.1:5002")
                            .build()
            );
        }catch (StatusRuntimeException e){
            System.out.println(e.getMessage());
        }

        // Remove from group
        try {
            stub.removeFromGroup(
                    AddToGroupRequest.newBuilder()
                            .setGroupName("group1")
                            .setAddress("127.0.0.1:5001")
                            .build());
        } catch (StatusRuntimeException e){
            System.out.println(e.getMessage());
        }
        // Get addresses
        List<String> addresses = new ArrayList<>();
        try {
            AddressList response = stub.getAddresses(
                    GroupRequest.newBuilder()
                            .setGroupName("group1")
                            .build()
            );
            addresses = response.getAddressesList();
        } catch (StatusRuntimeException e){
            System.out.println(e.getMessage());
        }

        System.out.println("Addresses:");
        for (String a : addresses) {
            System.out.println(a);
        }

        // Get groups from address
        GroupList response = stub.getGroups(AddressRequest.newBuilder()
                .setAddressName("127.0.0.1:5002")
                .build());

        List<String> groups = response.getGroupsList();
        for (String a : groups){
            System.out.println(a);
        }
        channel.shutdown();
    }
}
