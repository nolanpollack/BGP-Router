package remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import json.GsonTypeAdapters.MessageDeserializer;
import json.GsonTypeAdapters.MessageSerializer;
import messages.*;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static messages.UpdateMessage.UpdateParams.Origin.*;


public class Router {
    static Map<String, String> relations = new HashMap<>();
    static Map<String, DatagramSocket> sockets = new HashMap<>();
    static Map<String, Integer> ports = new HashMap<>();
    List<Route> routingTable = new ArrayList<>();
    private final int asn;
    private final Gson gson;

    /**
     * Create a new router
     * @param asn AS number of this router
     * @param connections List of connections in the form of "port-neighbor-relation"
     * @throws Exception
     */
    public Router(int asn, String[] connections) throws Exception {
        gson = initGson();

        System.out.println("Router at AS " + asn + " starting up");
        this.asn = asn;

        for (String relationship : connections) {
            String[] parts = relationship.split("-");
            String port = parts[0];
            String neighbor = parts[1];
            String relation = parts[2];

            //Create sockets and bind to an address and ephemeral port
            DatagramChannel channel = DatagramChannel.open();
            DatagramSocket socket = channel.socket();
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            sockets.put(neighbor, socket);
            ports.put(neighbor, Integer.parseInt(port));
            relations.put(neighbor, relation);

            String message = gson.toJson(new HandshakeMessage(ourAddr(neighbor), neighbor));
            send(neighbor, message);
        }
    }

    /**
     * Initialize the Gson object by registering the MessageSerializer and MessageDeserializer.
     * @return Gson object.
     */
    private Gson initGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(HandshakeMessage.class, new MessageSerializer());
        builder.registerTypeAdapter(Message.class, new MessageDeserializer());

        return builder.create();
    }

    /**
     * Return the address of this router based on the address of the neighbor.
     * @param dst Address of the neighbor.
     * @return Address of this router.
     */
    public String ourAddr(String dst) {
        String[] quads = dst.split("\\.");
        quads[3] = "1";
        return String.join(".", quads);
    }

    /**
     * Send a message to a network.
     * @param network Network to send the message to.
     * @param message Message to send.
     * @throws Exception If the message could not be sent.
     */
    public void send(String network, String message) throws Exception {
        System.out.println("Sending message '" + message + "' to " + network);
        byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), ports.get(network));
        sockets.get(network).getChannel().send(byteBuffer, address);
    }
    
    public void run() throws Exception {
        Selector selector = Selector.open();
        for (String neighbor : sockets.keySet()) {
            DatagramSocket socket = sockets.get(neighbor);
            socket.getChannel().configureBlocking(false);
            socket.getChannel().register(selector, SelectionKey.OP_READ, neighbor);
        }

        while (true) {
            int readyChannels = selector.select();
            if (readyChannels == 0) {
                continue;
            }
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = keys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if (key.isReadable()) {
                    String msg = readMessage(key);
                    handleMessage(msg);
                }
                keyIterator.remove();
            }
        }
    }

    public void handleMessage(String msg) throws Exception {
        Message message = gson.fromJson(msg, Message.class);
        switch (message.getType()) {
            case update:
                handleUpdate((UpdateMessage) message);
                break;
            case data:
                handleData((DataMessage) message);
                break;
            default:
                System.out.println("Unknown message type");
        }
    }

    public void handleUpdate(UpdateMessage message) throws Exception {
        if (message.dst.equals(ourAddr(message.src))) {
            updateRoutingTable(message);
            if (relations.get(message.src).equals("cust")) {
                broadcastUpdate(message);
            } else {
                updateCustomers(message);
            }
        }
    }

    public void updateRoutingTable(UpdateMessage message) {
        UpdateMessage.UpdateParams params = message.getUpdateParams();
        routingTable.add(new Route(params, message.src));
    }

    private void broadcastUpdate(UpdateMessage message) throws Exception {
        for (String neighbor : ports.keySet()) {
            if (!neighbor.equals(message.src)) {
                sendUpdate(neighbor, message);
            }
        }
    }

    private void updateCustomers(UpdateMessage message) throws Exception {
        for (String neighbor : ports.keySet()) {
            if (!neighbor.equals(message.src) && relations.get(neighbor).equals("cust")) {
                sendUpdate(neighbor, message);
            }
        }
    }

    private void sendUpdate(String destination, UpdateMessage message) throws Exception {
        UpdateMessage update = new UpdateMessage(ourAddr(destination), destination, message.getPublicUpdateParams(asn));
        send(destination, gson.toJson(update));
    }

    public void handleData(DataMessage message) throws Exception {
        Optional<Route> bestRoute = getBestRoute(message.dst);

        if (bestRoute.isEmpty()) {
            send(message.src, gson.toJson(new NoRouteMessage(ourAddr(message.src), message.src, null)));
        } else {
            Optional<Route> srcRoute = getBestRoute(message.src);

            if (srcRoute.isEmpty()) {
                send(message.src, gson.toJson(new NoRouteMessage(ourAddr(message.src), message.src, null)));
            } else {
                String srcRouter = srcRoute.get().nextHop;
                String dstRouter = bestRoute.get().nextHop;

                if (relations.get(srcRouter).equals("cust") || relations.get(dstRouter).equals("cust")) {
                    send(bestRoute.get().nextHop, gson.toJson(message));
                } else {
                    send(message.src, gson.toJson(new NoRouteMessage(ourAddr(message.src), message.src, null)));
                }
            }
        }
    }

    /**
     * Searches the routing table for the best route to the given IP address.
     * @param ip The IP address to search for.
     * @return The best route to the given IP address or an empty optional if no route was found.
     */
    private Optional<Route> getBestRoute(String ip) {
        Optional<Route> bestRoute = Optional.empty();
        for (Route route : routingTable) {
            String binaryIP = toBinary(route.network);
            String prefix = binaryIP.substring(0, route.netmask);
            if (toBinary(ip).startsWith(prefix)) {
                if (bestRoute.isEmpty()) {
                    //If we don't have a best route yet.
                    bestRoute = Optional.of(route);
                } else if (prefix.length() > bestRoute.get().netmask) {
                    //If the matching prefix is longer than the current best route
                    bestRoute = Optional.of(route);
                } else if (prefix.length() == bestRoute.get().netmask) {
                    if (route.localpref > bestRoute.get().localpref) {
                        //If the localpref is higher than the current best route
                        bestRoute = Optional.of(route);
                    } else if (route.localpref == bestRoute.get().localpref) {
                        if (route.selfOrigin && !bestRoute.get().selfOrigin) {
                            //If the route is self-originated and the current best route is not
                            bestRoute = Optional.of(route);
                        } else if (route.selfOrigin == bestRoute.get().selfOrigin) {
                            if (route.ASPath.size() < bestRoute.get().ASPath.size()) {
                                //If the ASPath is shorter than the current best route
                                bestRoute = Optional.of(route);
                            } else if (route.ASPath.size() == bestRoute.get().ASPath.size()) {
                                if (route.origin == bestRoute.get().origin) {
                                    if (Integer.parseInt(toBinary(route.nextHop)) < Integer.parseInt(toBinary(bestRoute.get().nextHop))) {
                                        //If the next hop is lower than the current best route
                                        bestRoute = Optional.of(route);
                                    }
                                } else if (route.origin.equals(IGP) && !bestRoute.get().origin.equals(IGP)) {
                                    //If the route is IGP and the current best route is EGP or UNK
                                    bestRoute = Optional.of(route);
                                } else if (route.origin.equals(EGP) && bestRoute.get().origin.equals(UNK)) {
                                    //If the route is EGP and the current best route is UNK
                                    bestRoute = Optional.of(route);
                                }
                            }
                        }
                    }
                }
            }
        }
        return bestRoute;
    }

    public static String toBinary(String ip) {
        String[] quads = ip.split("\\.");
        StringBuilder binaryIP = new StringBuilder();
        for (int i = 0; i < quads.length; i++) {
            binaryIP.append(Integer.toBinaryString(1 << 8 | Integer.parseInt(quads[i])).substring(1));
        }
        return binaryIP.toString();
    }

    private String readMessage(SelectionKey key) throws IOException {
        // Read the incoming data
        ByteBuffer buffer = ByteBuffer.allocate(65535);
        DatagramChannel channel = (DatagramChannel) key.channel();
        InetSocketAddress source = (InetSocketAddress) channel.receive(buffer);

        buffer.flip();
        String msg = new String(buffer.array(), 0, buffer.limit());
        System.out.println("Received message '" + msg + "' from " + source);
        return msg;
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
