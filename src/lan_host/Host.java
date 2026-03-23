package lan_host;

import config.ConfigParser;
import config.ConfigTypes;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

public class Host {

    private final String hostId;

    private String hostIp;
    private int hostPort;

    private String virtualIp;
    private String gatewayVip;
    private String gatewayMac;

    private String switchIp;
    private int switchPort;

    private final DatagramSocket socket;

    public Host(String hostId) throws Exception {
        this.hostId = hostId;
        loadConfig();
        socket = new DatagramSocket(hostPort);

        System.out.println("Host " + hostId + " started on " + hostIp + ":" + hostPort);
        System.out.println("Connected to switch at " + switchIp + ":" + switchPort);
    }

    private void loadConfig() {

        ConfigTypes.HostConfig cfg = ConfigParser.getHostConfig(hostId);
        if (cfg == null) {
            throw new RuntimeException("No host config found for " + hostId);
        }

        hostIp = cfg.realIP();
        hostPort = cfg.realPort();
        virtualIp = cfg.virtualIP();
        gatewayVip = cfg.gatewayVIP();


        gatewayMac = gatewayVip.split("\\.")[1];


        String[] neighbors = cfg.neighbors();
        if (neighbors.length == 0) {
            throw new RuntimeException("Host " + hostId + " has no neighbors in config");
        }

        String switchId = neighbors[0];


        ConfigTypes.SwitchConfig sw = ConfigParser.getSwitchConfig(switchId);
        if (sw == null) {
            throw new RuntimeException("No switch config found for " + switchId);
        }

        switchIp = sw.ipAddress();
        switchPort = sw.port();
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

            } catch (Exception e) {
                System.out.println("Receiver error: " + e.getMessage());
            }
        });

        receiver.setDaemon(true);
        receiver.start();
    }

    private void handleFrame(String frame) {

        String[] parts = frame.split(":", 5);

        if (parts.length != 5) {
            System.out.println("Bad frame received: " + frame);
            return;
        }



        String srcMac = parts[0];
        String dstMac = parts[1];
        String srcIp = parts[2];
        String dstIp = parts[3];
        String msg = parts[4];

        if (srcMac.equals(this.hostId)) {
            return;
        }

        if (dstMac.equals(hostId)) {
            String senderName = parts[2].contains(".") ? parts[2].split("\\.")[1] : parts[2];
            System.out.println("Message from " + senderName + ": " + parts[4]);
        }
    }

    private void startSender() {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Enter destination host ID: ");
            String dstHostId = scanner.nextLine().trim();

            System.out.print("Enter message: ");
            String msg = scanner.nextLine().trim();


            ConfigTypes.HostConfig dstCfg = ConfigParser.getHostConfig(dstHostId);
            if (dstCfg == null) {
                System.out.println("Unknown host ID: " + dstHostId);
                continue;
            }

            String dstVip = dstCfg.virtualIP();
            String mySubnet = virtualIp.split("\\.")[0];
            String dstSubnet = dstVip.split("\\.")[0];

            String targetMac;
            if (mySubnet.equals(dstSubnet)) {
                // Local destination: use the host's ID (e.g., "D")
                targetMac = dstVip.split("\\.")[1];
            } else {
                // Remote destination: use the GATEWAY'S ID (e.g., "R2")
                // gatewayMac was already calculated in loadConfig()!
                targetMac = this.gatewayMac;
            }

            String frame = hostId + ":" + targetMac + ":" + virtualIp + ":" + dstVip + ":" + msg;
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
            System.err.println("Send error: " + e.getMessage());
        }
    }

    public void start() {
        startReceiver();
        startSender();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java lan_host.Host <HOST_ID>");
            return;
        }

        Host host = new Host(args[0]);
        host.start();
    }
}
