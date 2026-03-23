package lan_host;

import config.ConfigParser;
import config.ConfigTypes.HostConfig;
import config.ConfigTypes.SwitchConfig;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;


public class Host {
    private String virtualIp;
    private String gatewayIp;
    private String gatewayMac;
    private final String hostId;
    private String hostIp;
    private int hostPort;

    private String switchIp;
    private int switchPort;

    private final DatagramSocket socket;

    public Host(String hostId) throws Exception {
        this.hostId = hostId;
        Config();
        socket = new DatagramSocket(hostPort);
        System.out.println("lan_host.Host " + hostId + " started on " + hostIp + ":" + hostPort);
        System.out.println("Connected to switch at " + switchIp + ":" + switchPort);
    }

    private void Config() {
        HostConfig deviceConfig = ConfigParser.getHostConfig(hostId);
        if (deviceConfig == null) {
            throw new RuntimeException("No config found for host " + hostId);
        }

        hostIp = deviceConfig.realIP();
        hostPort = deviceConfig.realPort();

        virtualIp = deviceConfig.virtualIP();
        gatewayIp = deviceConfig.gatewayVIP();
        gatewayMac = gatewayIp.split("\\.")[1];

        String[] neighbors = deviceConfig.neighbors();
        if (neighbors.length == 0) {
            throw new RuntimeException("lan_host.Host has no neighbors in config");
        }

        String switchId = neighbors[0];

        SwitchConfig switchConfig = ConfigParser.getSwitchConfig(switchId);
        if (switchConfig == null) {
            throw new RuntimeException("No config found for switch " + switchId);
        }

        switchIp = switchConfig.ipAddress();
        switchPort = switchConfig.port();
    }

    public void startReceiver() {
        Thread receiver = new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String frame = new String(packet.getData(), 0, packet.getLength());
                    handleFrame(frame);
                }
            }catch (Exception e) {
                System.out.println(e.getMessage());
            }
        });
        receiver.setDaemon(true);
        receiver.start();
    }

    // handleFrame - Cam
    // frame looks like SRC:DST:MSG
    // break it up
    // make sure it has 3 parts
    // if for me -> show msg
    // if not -> debug print
    private void handleFrame(String frame) {

        String[] parts = frame.split(":");

        if (parts.length < 6) {   // 5 fields
            System.out.println("Bad frame received: " + frame);
            return;

        }

        boolean isLSA = parts[0].equals("1");
        String srcMac = parts[1];
        String dstMac = parts[2];
        String srcIp = parts[3];
        String dstIp = parts[4];
        String msg = parts[5];

        // if the frame is for a regular message
        if (!isLSA) {
            if (dstMac.equals(hostId))
                System.out.println("Message from " + srcMac + ": " + msg);
            else
                System.out.println("Frame for " + dstMac + " received at " + hostId + " (MAC mismatch)");
        }
        // if the frame is for an LSA
        else {
            // <...>
        }
    }




    private void startSender() {
        Scanner scanner = new Scanner(System.in);
        // loop is manually interrupted
		//noinspection InfiniteLoopStatement
		while (true) {
            System.out.print("Enter destination host ID: ");
            String dst = scanner.nextLine().trim();

            System.out.print("Enter message: ");
            String msg = scanner.nextLine().trim();

            String frame = String.format(
                    "%s:%s:%s:%s:%s:%s",
                    "0", hostId, gatewayMac, virtualIp, dst, msg
            );

            sendFrame(frame);
        }
    }

    private void sendFrame(String frame) {
        try {
            byte[] data = frame.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName(switchIp),
                    switchPort
            );
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public void start(){
        startReceiver();
        startSender();
    }


    static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java lan_host.Host <HOST_ID>");
            return;
        }
        Host host = new Host(args[0]);
        host.start();
    }
}