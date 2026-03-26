package router;
import config.ConfigParser;
import config.ConfigTypes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class router {
    private final String id;
    private final int  gatewayPort;
    private final DatagramSocket socket;
    private final Map<String, InetSocketAddress> routingTable = new LinkedHashMap<>();
    private final Map<String, List<String>> globalTopology = new LinkedHashMap<>();
    private final Map<String, String> virtualRoutingTable = new HashMap<>();

    public router(String id){
        this.id = id;
        ConfigTypes.RouterConfig myConfig = ConfigParser.getRouterConfig(id);
        this.gatewayPort = myConfig.realPort();
        try {
            this.socket = new DatagramSocket(gatewayPort);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleLSA(String payload){
        try {
            String[] parts = payload.split("\\.");
            if (parts.length < 2) return;

            String origin = parts[0];
            List<String> neighbors = Arrays.asList(parts[1].split("\\."));

            if(!neighbors.equals(globalTopology.get(origin))){
                globalTopology.put(origin, neighbors);
                System.out.println("[ROUTER " + this.id + "] Learned " + origin);

                sendLSA(payload, origin);

                computeDijkstra();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void sendLSA(String payload, String origin) {

        String frame = "LSA:" + this.id + ":ALL:" + this.id + ":ALL:" + payload;

        byte[] data = frame.getBytes(StandardCharsets.UTF_8);


        ConfigTypes.RouterConfig cfg = ConfigParser.getRouterConfig(id);


        for (String vip : cfg.virtualIPs()) {
            String[] neighbors = cfg.neighborsPerVirtualIP().get(vip);
            if (neighbors == null) continue;

            for (String neighbor : neighbors) {


                if (neighbor.equals(origin)) continue;

                ConfigTypes.RouterConfig nCfg = ConfigParser.getRouterConfig(neighbor);
                if (nCfg == null) continue;

                InetSocketAddress addr =
                        new InetSocketAddress(nCfg.realIP(), nCfg.realPort());

                try {
                    socket.send(new DatagramPacket(data, data.length, addr));
                    System.out.println("[ROUTER " + id + "] Sent LSA to " + neighbor);
                } catch (IOException e) {
                    System.err.println("[ROUTER " + id + "] Error sending LSA to " + neighbor);
                }
            }
        }
    }

    private void computeDijkstra(){
        Map<String, Integer> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        Set<String> visited = new HashSet<>();

        for (String node : globalTopology.keySet()){
            dist.put(node, Integer.MAX_VALUE);
            prev.put(node, null);
        }
        dist.put(this.id, 0);

        PriorityQueue<String[]> pq = new PriorityQueue<>(
                Comparator.comparingInt(a -> Integer.parseInt(a[1]))
        );
        pq.add(new String[]{this.id, "0"});

        while (!pq.isEmpty()){
            String[] current = pq.poll();
            String currentNode = current[0];
            int currentCost  = Integer.parseInt(current[1]);
            if (visited.contains(currentNode)) continue;
            visited.add(currentNode);

            for (String neighbor : globalTopology.get(currentNode)){
                if (visited.contains(neighbor)) continue;
                if (!dist.containsKey(neighbor))continue;

                int newCost = currentCost + 1;

                if (newCost < dist.get(neighbor)){
                    dist.put(neighbor, newCost);
                    prev.put(neighbor, currentNode);
                    pq.add(new String[]{neighbor, String.valueOf(newCost)});
                }
            }
        }

        Map<String, String> firstHop = new HashMap<>();

        for(String destination : dist.keySet()){
            if (destination.equals(this.id)) continue;
            String hop = destination;
            while (prev.get(hop) != null && !prev.get(hop).equals(this.id)){
                hop = prev.get(hop);
            }
            firstHop.put(destination, hop);
        }

        for (String destination : firstHop.keySet()) {
            ConfigTypes.RouterConfig destConfig = ConfigParser.getRouterConfig(destination);
            if (destConfig == null) continue;

            for (String vip : destConfig.virtualIPs()) {
                String subnet = vip.split("\\.")[0];
                virtualRoutingTable.put(subnet, firstHop.get(destination));
            }
        }
    }

    public void print_routing_table(){
        System.out.println("\n========= ROUTING TABLE (ID: " + this.id + ") =========");
        System.out.printf("%-15s | %-10s%n", "Subnet Prefix", "Next Hop");
        System.out.println("-------------------------------------------");
        for (Map.Entry<String, String> entry :virtualRoutingTable.entrySet()){
            System.out.printf("%-15s | %-10s%n", entry.getKey(), entry.getValue());
        }
    }

    private void processFrame(String frame) throws IOException {
        // format: type:srcMac:dstMac:srcIp:dstIp:msg
        String[] parts = frame.split(":", 6);
        if (parts.length != 6) {
            System.out.println("[ROUTER " + this.id + "] bad frame (needs 6 fields): " + frame);
            return;
        }

        if (parts[0].equals("1")) {
            handleLSA(parts[4]);
        } else {
            String srcMac = parts[1].trim();
            String dstMac = parts[2].trim();
            String srcIp = parts[3].trim();
            String dstIp = parts[4].trim();
            String msg = parts[5];
            if (!dstMac.equals(this.id)) {
                System.out.println("[ROUTER " + this.id + "] Received frame for " + dstMac + " - Not for me. Ignoring/Dropping.");
                return;
            }

            System.out.println("[ROUTER " + this.id + "] " +
                    "srcMac=" + srcMac + " dstMac=" + dstMac +
                    " srcIp=" + srcIp + " dstIp=" + dstIp + " msg=" + msg);

            // get "net3" from "net3.D", then turn it into "subnet3"
            String key = "subnet" + dstIp.split("\\.", 2)[0].substring(3);

            InetSocketAddress next = routingTable.get(key);
            String nextHopId = virtualRoutingTable.get(key);

            String dstMacForFrame;
            if (nextHopId.startsWith("S")) {
                dstMacForFrame = dstIp.split("\\.")[1];
            } else {
                // If sending to another router, use the router's ID
                dstMacForFrame = nextHopId;
            }

            String out = this.id + ":" + dstMacForFrame + ":" + srcIp + ":" + dstIp + ":" + msg;
            System.out.println("[ROUTER " + this.id + "] OUTGOING FRAME: " + out);

            byte[] data = out.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(data, data.length, next));
        }
    }

    public void startListening() {
        System.out.println("[ROUTER " + this.id + "] Listening on port " + gatewayPort + "...");
        byte[] buffer = new byte[65535];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String receivedData = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                processFrame(receivedData);
            } catch (IOException e) {
                System.err.println("[ROUTER " + this.id + "] Error receiving packet: " + e.getMessage());
                if (socket.isClosed()) break;
            }
        }
    }



    static void main(String[] args){
        if(args.length != 1){
            System.out.println("Please provide Router ID in Arguments");
        }
        router r1 = new router(args[0]);
        r1.print_routing_table();
        r1.startListening();
    }
}
