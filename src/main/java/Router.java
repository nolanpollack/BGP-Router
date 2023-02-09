import com.google.gson.Gson;
import messages.HandshakeMessage;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.*;

public class Router {
    static Map<String, String> relations = new HashMap<>();
    static Map<String, DatagramSocket> sockets = new HashMap<>();
    static Map<String, Integer> ports = new HashMap<>();

    private int asn;

    public Router(int asn, String[] connections) throws Exception {
        System.out.println("Router at AS " + asn + " starting up");
        this.asn = asn;
        for (String relationship : connections) {
            String[] parts = relationship.split("-");
            String port = parts[0];
            String neighbor = parts[1];
            String relation = parts[2];

            sockets.put(neighbor, new DatagramSocket(Integer.parseInt(port), InetAddress.getLocalHost()));
            ports.put(neighbor, Integer.parseInt(port));
            relations.put(neighbor, relation);

            Gson gson = new Gson();
            gson.toJson(new HandshakeMessage(ourAddr(neighbor), neighbor));

            send(neighbor, "{ \"type\": \"handshake\", \"src\": " + ourAddr(neighbor) + ", \"dst\": " + neighbor + ", \"msg\": {} }");
        }
    }

    public String ourAddr(String dst) throws Exception {
        String[] quads = dst.split("\\.");
        quads[3] = "1";
        return String.join(".", quads);
    }

    public void send(String network, String message) throws Exception {
        DatagramPacket dp = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getLocalHost(), ports.get(network));
        sockets.get(network).send(dp);
    }

    public void run() throws Exception {
//        while (true) {
//            List<DatagramSocket> socketList = new ArrayList<>(sockets.values());
//            int index = DatagramSocket.getSelector().select(100);
//            if (index > 0) {
//                DatagramSocket conn = socketList.get(index - 1);
//                byte[] buffer = new byte[65535];
//                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
//                conn.receive(dp);
//                String srcif = null;
//                for (Map.Entry<String, DatagramSocket> entry : sockets.entrySet()) {
//                    if (entry.getValue() == conn) {
//                        srcif = entry.getKey();
//                        break;
//                    }
//                }
//                String msg = new String(dp.getData(), 0, dp.getLength());
//                System.out.println("Received message '" + msg + "' from " + srcif);
//            }
//        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java Router <asn> <connections>");
            System.exit(1);
        }

        int asn = Integer.parseInt(args[0]);
        String[] connections = Arrays.copyOfRange(args, 1, args.length);

        Router router = new Router(asn, connections);
        router.run();
    }

    }
