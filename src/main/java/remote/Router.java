package remote;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import json.GsonTypeAdapters.MessageDeserializer;
import json.GsonTypeAdapters.MessageSerializer;
import messages.*;

import java.io.IOException;
import java.math.BigInteger;
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
    //Map of routes that have been aggregated, to the route they have been aggregated to.
    Map<Route, AggregatedRoute> routesAggregated = new HashMap<>();
    private final int asn;
    private final Gson gson;

    /**
     * Create a new router
     *
     * @param asn         AS number of this router
     * @param connections List of connections in the form of "port-neighbor-relation"
     */
    public Router(int asn, String[] connections) throws Exception {
        System.out.println("Router at AS " + asn + " starting up");

        gson = initGson();
        this.asn = asn;

        for (String relationship : connections) {
            String[] parts = relationship.split("-");
            String port = parts[0];
            String neighbor = parts[1];
            String relation = parts[2];

            DatagramSocket socket = createSocket();

            sockets.put(neighbor, socket);
            ports.put(neighbor, Integer.parseInt(port));
            relations.put(neighbor, relation);

            //Send Handshake Message
            String message = gson.toJson(new HandshakeMessage(ourAddr(neighbor), neighbor));
            send(neighbor, message);
        }
    }

    /**
     * Initialize the Gson object by registering serializers and deserializers.
     *
     * @return Gson object.
     */
    private Gson initGson() {
        GsonBuilder builder = new GsonBuilder();

        builder.registerTypeAdapter(Message.class, new MessageDeserializer());

        builder.registerTypeAdapter(HandshakeMessage.class, new MessageSerializer());
        builder.registerTypeAdapter(NoRouteMessage.class, new MessageSerializer());
        builder.registerTypeAdapter(TableMessage.class, new MessageSerializer());

        return builder.create();
    }

    /**
     * Create a socket and bind it to an address and ephemeral port.
     *
     * @return DatagramSocket
     * @throws IOException If the socket could not be created.
     */
    private DatagramSocket createSocket() throws IOException {
        DatagramChannel channel = DatagramChannel.open();
        DatagramSocket socket = channel.socket();
        socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        return socket;
    }

    /**
     * Return the address of this router based on the address of a neighbor.
     *
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
     *
     * @param network Network to send the message to.
     * @param message Message to send.
     * @throws Exception If the message could not be sent.
     */
    public void send(String network, String message) throws Exception {
        byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), ports.get(network));
        sockets.get(network).getChannel().send(byteBuffer, address);
    }

    /**
     * Initiates loop that listens for incoming messages and handles them.
     *
     * @throws Exception If the router could not be started.
     */
    public void run() throws Exception {
        try (Selector selector = initializeSelector()) {
            while (true) {
                selectReadyChannels(selector);
            }
        }
    }

    /**
     * Initialize the selector by registering all sockets to it in read mode and setting them to non-blocking.
     *
     * @return Selector
     * @throws IOException If the selector could not be initialized.
     */
    private Selector initializeSelector() throws IOException {
        Selector selector = Selector.open();
        for (String neighbor : sockets.keySet()) {
            DatagramSocket socket = sockets.get(neighbor);
            socket.getChannel().configureBlocking(false);
            socket.getChannel().register(selector, SelectionKey.OP_READ, neighbor);
        }
        return selector;
    }

    /**
     * Selects all ready channels, and iterates through them, then handles received messages.
     *
     * @param selector Selector to select from.
     * @throws Exception If a channel could not be handled.
     */
    private void selectReadyChannels(Selector selector) throws Exception {
        int readyChannels = selector.select();
        if (readyChannels == 0) {
            return;
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

    /**
     * Handles a message based on its type.
     *
     * @param msg Message to handle.
     * @throws Exception If the message could not be handled.
     */
    public void handleMessage(String msg) throws Exception {
        Message message = gson.fromJson(msg, Message.class);
        switch (message.getType()) {
            case update:
                handleUpdate((UpdateMessage) message);
                break;
            case data:
                handleData((DataMessage) message);
                break;
            case dump:
                handleDump((DumpMessage) message);
                break;
            case withdraw:
                handleWithdraw((WithdrawMessage) message);
                break;
            default:
                System.out.println("Unknown message type");
        }
    }

    /**
     * Handles an update message by updating the routing table and forwarding the message to neighbors.
     *
     * @param message Message to handle.
     * @throws Exception If the message could not be handled.
     */
    public void handleUpdate(UpdateMessage message) throws Exception {
        if (message.dst.equals(ourAddr(message.src))) {
            updateRoutingTable(message);
            updateAppropriate(message);
        }
    }

    /**
     * Forwards a message to the appropriate neighbors depending on where the message came from and the
     * relationship of the neighbor.
     *
     * @param message message to send to neighbors.
     */
    private void updateAppropriate(Message message) throws Exception {
        if (relations.get(message.src).equals("cust")) {
            broadcastAnnouncement(message);
        } else {
            announceToCustomers(message);
        }
    }

    /**
     * Updates the routing table based on an update message.
     *
     * @param message Message to update the routing table with.
     */
    public void updateRoutingTable(UpdateMessage message) {
        UpdateMessage.UpdateParams params = message.getUpdateParams();
        Route newRoute = new Route(params, message.src);

        if (!checkAggregate(newRoute)) {
            routingTable.add(newRoute);
        }
    }

    /**
     * Checks if a route can be aggregated with an existing route and does so if possible.
     *
     * @param newRoute route to aggregate into the routing table.
     * @return true if route was aggregated, false if not.
     */
    private boolean checkAggregate(Route newRoute) {
        for (Route route : routingTable) {
            if (routesCanBeAggregated(newRoute, route)) {
                aggregate(newRoute, route);
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether a route can be aggregated with an existing route in the table.
     *
     * @param newRoute      route being added.
     * @param existingRoute route already in table.
     * @return true if routes can be aggregated, false if not.
     */
    private boolean routesCanBeAggregated(Route newRoute, Route existingRoute) {
        if (newRoute.attributesEqual(existingRoute)) {
            //Get the low and high range of the new route.
            String[] newRouteRange = getIPRange(newRoute).split("-");
            BigInteger lowRange = toBigInt(newRouteRange[0]);
            BigInteger highRange = toBigInt(newRouteRange[1]);

            //Get the low and high range of the existing route.
            String[] routeRange = getIPRange(existingRoute).split("-");
            BigInteger existingRouteLow = toBigInt(routeRange[0]);
            BigInteger existingRouteHigh = toBigInt(routeRange[1]);

            boolean adjacent = existingRouteLow.equals(highRange.add(BigInteger.ONE))
                    || existingRouteHigh.equals(lowRange.subtract(BigInteger.ONE));
            boolean existingContainsNew = existingRouteLow.compareTo(lowRange) <= 0 && existingRouteHigh.compareTo(highRange) >= 0;
            boolean newContainsExisting = lowRange.compareTo(existingRouteLow) <= 0 && highRange.compareTo(existingRouteHigh) >= 0;

            return adjacent || existingContainsNew || newContainsExisting;
        }
        return false;
    }

    /**
     * Aggregates two routes.
     *
     * @param newRoute      route being added.
     * @param existingRoute route originally in table.
     */
    private void aggregate(Route newRoute, Route existingRoute) {
        AggregatedRoute aggregatedRoute = getAggregatedRoute(newRoute, existingRoute);

        routingTable.remove(existingRoute);
        routingTable.add(aggregatedRoute);
    }

    /**
     * Gets a Route representation of the aggregation between two routes.
     *
     * @param newRoute      route being added to table.
     * @param existingRoute route already in table.
     * @return an AggregatedRoute, containing a list of all routes that have been aggregated to make it.
     */
    private AggregatedRoute getAggregatedRoute(Route newRoute, Route existingRoute) {
        String newPrefix = toBinary(newRoute.network);
        String existingPrefix = toBinary(existingRoute.network);

        int aggregatedNetmask = 0;

        for (int i = 0; i < newPrefix.length(); i++) {
            if (newPrefix.charAt(i) != existingPrefix.charAt(i)) {
                aggregatedNetmask = i;
                break;
            }
        }

        String netmaskBinary = "1".repeat(aggregatedNetmask) + "0".repeat(32 - aggregatedNetmask);

        String aggregatedNetwork = toIP(binaryAnd(newPrefix, netmaskBinary));

        List<Route> routesInside = new ArrayList<>();
        routesInside.add(newRoute);
        if (existingRoute instanceof AggregatedRoute) {
            routesInside.addAll(((AggregatedRoute) existingRoute).getRoutesInside());
        } else {
            routesInside.add(existingRoute);
        }

        AggregatedRoute aggregatedRoute = new AggregatedRoute(newRoute.nextHop, aggregatedNetwork, aggregatedNetmask, newRoute.localpref, newRoute.selfOrigin, newRoute.ASPath, newRoute.origin,
                routesInside);

        //If the two routes are already aggregated, add the new route to the existing AggregatedRoute.
        if (existingRoute.equals(aggregatedRoute)) {
            assert existingRoute instanceof AggregatedRoute;
            AggregatedRoute existingAggregatedRoute = (AggregatedRoute) existingRoute;
            existingAggregatedRoute.includeRoute(newRoute);
            routesAggregated.put(newRoute, existingAggregatedRoute);
        } else {
            routesAggregated.put(newRoute, aggregatedRoute);
            routesAggregated.put(existingRoute, aggregatedRoute);
        }

        return aggregatedRoute;
    }

    /**
     * Performs a binary AND operation on two binary strings.
     *
     * @param binary1 The first binary string.
     * @param binary2 The second binary string.
     * @return The result of the binary AND operation.
     */
    private String binaryAnd(String binary1, String binary2) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < binary1.length(); i++) {
            if (binary1.charAt(i) == '1' && binary2.charAt(i) == '1') {
                result.append("1");
            } else {
                result.append("0");
            }
        }
        return result.toString();
    }

    /**
     * Gets the IP subnet range of a route.
     *
     * @param route route to get range of.
     * @return a string containing the two edges of the range split by a hyphen.
     */
    private String getIPRange(Route route) {
        if (route.netmask == 0) {
            return "0.0.0.0-0.0.0.0";
        } else {
            int[] bitBoundaries = new int[]{24, 16, 8};
            int mask;
            int bitPrefix = 8;
            int fixedOctet = 0;
            if (route.netmask >= 8) {
                mask = route.netmask;
                for (int i = 0; i < bitBoundaries.length; i++) {
                    if (mask >= bitBoundaries[i]) {
                        bitPrefix = bitBoundaries[i];
                        fixedOctet = 3 - i;
                        break;
                    }
                }
            } else {
                mask = 8 + route.netmask;
            }

            int startingOctetMultiple = (int) Math.pow(2, (8 - (mask - bitPrefix)));

            String[] octets = route.network.split("\\.");

            StringBuilder prefixBuilder = new StringBuilder();
            for (int i = 0; i < fixedOctet; i++) {
                prefixBuilder.append(octets[i]);
                prefixBuilder.append(".");
            }

            int startOfRange = (Integer.parseInt(octets[fixedOctet]) / startingOctetMultiple) * startingOctetMultiple;
            int endOfRange = startOfRange + startingOctetMultiple - 1;

            StringBuilder firstIPBuilder = new StringBuilder();
            StringBuilder lastIPBuilder = new StringBuilder();

            firstIPBuilder.append(prefixBuilder);
            firstIPBuilder.append(startOfRange);
            lastIPBuilder.append(prefixBuilder);
            lastIPBuilder.append(endOfRange);

            for (int i = 0; i < 3 - fixedOctet; i++) {
                firstIPBuilder.append(".0");
                lastIPBuilder.append(".255");
            }

            return firstIPBuilder + "-" + lastIPBuilder;
        }
    }

    /**
     * Broadcasts an update or withdraw message to all neighbors.
     *
     * @param message Message to broadcast.
     */
    private void broadcastAnnouncement(Message message) throws Exception {
        for (String neighbor : ports.keySet()) {
            if (!neighbor.equals(message.src)) {
                forwardAnnouncement(neighbor, message);
            }
        }
    }

    /**
     * Sends an update or withdraw message to all customers.
     *
     * @param message Message to send.
     * @throws Exception If the message could not be sent.
     */
    private void announceToCustomers(Message message) throws Exception {
        for (String neighbor : ports.keySet()) {
            if (!neighbor.equals(message.src) && relations.get(neighbor).equals("cust")) {
                forwardAnnouncement(neighbor, message);
            }
        }
    }

    /**
     * Sends an update or withdraw message to a specific destination.
     *
     * @param destination Destination to send the message to.
     * @param message     Message to send.
     * @throws Exception If the message could not be sent.
     */
    private void forwardAnnouncement(String destination, Message message) throws Exception {
        Message update;
        if (message instanceof UpdateMessage) {
            update = new UpdateMessage(ourAddr(destination), destination, ((UpdateMessage) message).getPublicUpdateParams(asn));
        } else {
            update = new WithdrawMessage(ourAddr(destination), destination, ((WithdrawMessage) message).getWithdrawNetworks());
        }
        send(destination, gson.toJson(update));
    }

    /**
     * Handles a data message by forwarding it to the next hop or sending a no route message if no legal route is found.
     *
     * @param message Data message.
     */
    public void handleData(DataMessage message) throws Exception {
        Optional<Route> bestRoute = getBestRoute(message.dst);
        Optional<Route> srcRoute = getBestRoute(message.src);

        if (bestRoute.isEmpty() && srcRoute.isPresent()) {
            send(srcRoute.get().nextHop, gson.toJson(new NoRouteMessage(ourAddr(message.src), message.src)));
        } else if (srcRoute.isPresent()) {
            String srcRouter = srcRoute.get().nextHop;
            String dstRouter = bestRoute.get().nextHop;

            if (relations.get(srcRouter).equals("cust") || relations.get(dstRouter).equals("cust")) {
                send(bestRoute.get().nextHop, gson.toJson(message));
            } else {
                send(srcRouter, gson.toJson(new NoRouteMessage(ourAddr(message.src), message.src)));
            }
        }

    }

    /**
     * Searches the routing table for the best route to the given IP address, based on the 5 rules to selecting
     * a path.
     *
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
                                    if (Integer.parseInt(route.nextHop.replace(".", "")) < Integer.parseInt(bestRoute.get().nextHop.replace(".", ""))) {
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

    /**
     * Converts an IP address to a binary string.
     *
     * @param ip The IP address to convert.
     * @return The binary string representation of the IP address.
     */
    public static String toBinary(String ip) {
        String[] quads = ip.split("\\.");
        StringBuilder binaryIP = new StringBuilder();
        for (String quad : quads) {
            binaryIP.append(Integer.toBinaryString(1 << 8 | Integer.parseInt(quad)).substring(1));
        }
        return binaryIP.toString();
    }

    /**
     * Converts a binary string to an IP address.
     *
     * @param ip The binary string to convert.
     * @return The IP address.
     */
    public static String toIP(String ip) {
        StringBuilder ipString = new StringBuilder();
        for (int i = 0; i <= ip.length() - 8; i += 8) {
            ipString.append(Integer.parseInt(ip.substring(i, i + 8), 2));
            if (i != ip.length() - 8) {
                ipString.append(".");
            }
        }
        return ipString.toString();
    }

    /**
     * Converts an IP address to a BigInteger.
     *
     * @param ip The IP address to convert.
     * @return The BigInteger representation of the IP address.
     */
    public static BigInteger toBigInt(String ip) {
        return new BigInteger("0" + toBinary(ip), 2);
    }

    /**
     * Handles a dump message by sending the routing table to the sender.
     *
     * @param message The dump message to handle.
     * @throws Exception If the message could not be sent.
     */
    private void handleDump(DumpMessage message) throws Exception {
        send(message.src, gson.toJson(new TableMessage(ourAddr(message.src), message.src, routingTable)));
    }

    /**
     * Withdraws all networks listed in a withdrawal message.
     *
     * @param message The withdrawal message to handle.
     * @throws Exception If the message could not be sent.
     */
    private void handleWithdraw(WithdrawMessage message) throws Exception {
        for (WithdrawMessage.WithdrawNetwork withdrawNetwork : message.getWithdrawNetworks()) {
            //Check all the routes that have been aggregated.
            for (Route route : routesAggregated.keySet()) {
                if (route.network.equals(withdrawNetwork.network)
                        && route.getNetmask().equals(withdrawNetwork.netmask)
                        && route.nextHop.equals(message.src)) {
                    disaggregateAndWithdraw(route);
                    break;
                }
            }
            routingTable.removeIf(route -> route.network.equals(withdrawNetwork.network)
                    && route.getNetmask().equals(withdrawNetwork.netmask)
                    && route.nextHop.equals(message.src));
        }

        updateAppropriate(message);
    }

    /**
     * Withdraws a route that has been previously aggregated.
     *
     * @param route The route to withdraw.
     */
    private void disaggregateAndWithdraw(Route route) {
        //Find the route that's actually in the table and remove it
        AggregatedRoute aggregatedRoute = routesAggregated.get(route);
        routingTable.remove(aggregatedRoute);

        //Add the disaggregated routes back to the table
        List<Route> routesToAdd = aggregatedRoute.getRoutesInside();
        routesToAdd.remove(route);

        for (Route r : routesToAdd) {
            routesAggregated.remove(r);
            if (!checkAggregate(r)) {
                routingTable.add(r);
            }
        }
    }

    /**
     * Receives and handles a message from the given selection key.
     *
     * @param key The selection key to receive the message from. Must be readable.
     * @return Message received.
     * @throws IOException If the message could not be read.
     */
    private String readMessage(SelectionKey key) throws IOException {
        // Read the incoming data
        ByteBuffer buffer = ByteBuffer.allocate(65535);
        DatagramChannel channel = (DatagramChannel) key.channel();
        channel.receive(buffer);

        buffer.flip();
        return new String(buffer.array(), 0, buffer.limit());
    }

    /**
     * Creates a router with the given ASN and connections, then runs it.
     *
     * @param args First argument is the ASN, the rest are the connections formatted as port-ip-relationship.
     * @throws Exception If the router could not be created or run.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: ./3700router <asn> <connections>");
            System.exit(1);
        }

        int asn = Integer.parseInt(args[0]);
        String[] connections = Arrays.copyOfRange(args, 1, args.length);

        Router router = new Router(asn, connections);
        router.run();
    }
}
