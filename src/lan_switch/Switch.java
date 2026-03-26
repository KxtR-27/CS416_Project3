package lan_switch;

import config.ConfigParser;
import config.ConfigTypes;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class Switch {
    private final String id;
    private int listeningPort;
    private final Map<String, Integer> switchTable = new HashMap<>();
    private final Map<Integer, String> neighborIPs = new HashMap<>();
    private DatagramSocket listeningSocket;

    private void create_and_update_switch_table(String sourceMAC, int port){
        if(!switchTable.containsKey(sourceMAC) || switchTable.get(sourceMAC) != port){
            if (!switchTable.containsKey(sourceMAC)) {
                System.out.println("NEW MAC LEARNED: " + sourceMAC + " on Port " + port);
            } else {
                System.out.println("HOST MOVED: " + sourceMAC + " moved to Port " + port);
            }
            switchTable.put(sourceMAC, port);
            display_switch_table();
        }
    }

    public void display_switch_table(){
        System.out.println("\n========= SWITCH TABLE (ID: " + this.id + ") =========");
        System.out.printf("%-15s | %-10s%n", "MAC Address", "Port");
        System.out.println("-------------------------------------------");

        for (Map.Entry<String, Integer> entry : switchTable.entrySet()) {
            System.out.printf("%-15s | %-10d%n", entry.getKey(), entry.getValue());
        }
        System.out.println("===========================================\n");
    }

    public Switch(String id){
        this.id = id;
        ConfigTypes.SwitchConfig myConfig = ConfigParser.getSwitchConfig(id);
        if(myConfig != null){
            this.listeningPort = myConfig.port();
            System.out.println("Config loaded for " + id);
            String[] neighbors = myConfig.neighbors();
            for (String neighborID : neighbors) {

                ConfigTypes.HostConfig host = ConfigParser.getHostConfig(neighborID);
                if (host != null) {
                    neighborIPs.put(host.realPort(), host.realIP());
                    continue;
                }

                ConfigTypes.SwitchConfig sw = ConfigParser.getSwitchConfig(neighborID);
                if (sw != null) {
                    neighborIPs.put(sw.port(), sw.ipAddress());
                    continue;
                }

                ConfigTypes.RouterConfig router = ConfigParser.getRouterConfig(neighborID);
                if (router != null) {
                    neighborIPs.put(router.realPort(), router.realIP());
                }
            }
        }
        else {
            System.out.println("No Configuration found with " + id);
        }
    }
    public void processPacket(String sourceMAC, String destinationMAC, String fullFrame, int incomingPort) throws IOException {
        create_and_update_switch_table(sourceMAC, incomingPort);
        if (!switchTable.containsKey(destinationMAC)) {
            System.out.println("FLOODING: Destination " + destinationMAC + " unknown.");
            for (Map.Entry<Integer, String> entry : neighborIPs.entrySet()) {
                int port = entry.getKey();
                String ip = entry.getValue();
                if (port != incomingPort) {
                    sendUDP(listeningSocket, ip, port, fullFrame);
                }
            }
        } else {
            int targetPort = switchTable.get(destinationMAC);
            String targetIP = neighborIPs.get(targetPort);
            if (targetIP != null) {
                System.out.println("FORWARDING: Sending frame to port " + targetPort);
                sendUDP(listeningSocket, targetIP, targetPort, fullFrame);
            } else {
                System.err.println("ERROR: No neighbor found for port " + targetPort);
            }
        }
    }


    public void startListening() {
        try {
            this.listeningSocket = new DatagramSocket(this.listeningPort);
            System.out.println("Switch " + id + " online on port " + listeningPort);
            byte[] buffer = new byte[1024];

            // loop is manually interrupted
            //noinspection InfiniteLoopStatement
            while (true) {
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                this.listeningSocket.receive(p);

                // Expecting -> "SRC:DEST:MSG"
                String frame = new String(p.getData(), 0, p.getLength()).trim();
                String[] parts = frame.split(":", 5);

                if (parts.length == 5) {
                    String src = parts[0];
                    String dest = parts[1];
                    int incomingPort = p.getPort();
                    processPacket(src, dest, frame, incomingPort);
                }
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    private void sendUDP(DatagramSocket socket, String ip, int port, String fullFrame) throws IOException {
        byte[] buffer = fullFrame.getBytes();
        DatagramPacket packet = new DatagramPacket(
                buffer,
                buffer.length,
                InetAddress.getByName(ip),
                port
        );
        socket.send(packet);
    }



    static void main(String[] args) {
        if (args.length > 0){
            try{
                String inputID = args[0];
                Switch lanSwitch = new Switch(inputID);
                lanSwitch.display_switch_table();
                lanSwitch.startListening();
            }
            catch (NumberFormatException e){
                System.err.println("Argument Must Be Device ID");
            }
        }
        else {
            System.out.println("Please provide a Switch ID in the run config");
        }
    }
}