package se.NameServer;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import se.NameServer.grpc.*;

import java.util.ArrayList;

public class NamingServerGrpcHandler extends NamingServiceGrpc.NamingServiceImplBase {
    NamingServer namingServer = new NamingServer();

    @Override
    public void getAddresses(GroupRequest request, StreamObserver<AddressList> responseObserver) {
        ArrayList<String> addresses;
        try {
            addresses = namingServer.getAddresses(request.getGroupName());;
        } catch (NamingServer.GroupDoesNotExistException e){
            addresses = null;
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
            return;
        }
        if(addresses == null){
            addresses = new ArrayList<>();
        }
        AddressList addressList = AddressList.newBuilder().addAllAddresses(addresses).build();
        responseObserver.onNext(addressList);
        responseObserver.onCompleted();
    }

    @Override
    public void getGroups(AddressRequest request, StreamObserver<GroupList> responseObserver){
        ArrayList<String> groups = namingServer.getGroups(request.getAddressName());
        GroupList groupList = GroupList.newBuilder().addAllGroups(groups).build();
        responseObserver.onNext(groupList);
        responseObserver.onCompleted();
    }

    @Override
    public void addNewGroup(AddGroupRequest request, StreamObserver<Empty> responseObserver) {
        String groupName = request.getGroupName();
        ArrayList<String> addresses = new ArrayList<>(request.getAddressesList());
        try {
            namingServer.addNewGroup(groupName, addresses);

            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();

        }catch (NamingServer.GroupAlreadyExistsException e){
            responseObserver.onError(
                    Status.ALREADY_EXISTS
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        }catch (Exception e) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException()
            );
        }

    }

    @Override
    public void addToGroup(AddToGroupRequest request, StreamObserver<Empty> responseObserver){
        String groupName = request.getGroupName();
        String address = request.getAddress();
        try {
            namingServer.addToGroup(groupName, address);
        } catch (NamingServer.GroupDoesNotExistException e){
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        }
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void removeFromGroup(AddToGroupRequest request, StreamObserver<Empty> responseObserver){
        String groupName = request.getGroupName();
        String address = request.getAddress();
        try {
            namingServer.removeFromGroup(groupName, address);
        } catch (NamingServer.GroupDoesNotExistException e){
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        }
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void isUp(Empty request, StreamObserver<Empty> responseObserver){
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }
}
