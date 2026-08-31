package se.NameServer;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        Main main = new Main();
        Server server = ServerBuilder.forPort(1111).addService(new NamingServerGrpcHandler()).build();
        System.out.println("Server started on: " + main.getIpAddress() +  ":1111");
        server.start();
        server.awaitTermination();
    }

    private String getIpAddress(){
        String ip = null;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            ip = socket.getLocalAddress().getHostAddress();;
        } catch (SocketException | UnknownHostException e) {
            System.out.println(e.getMessage());
        }
        return ip;
    }
}
