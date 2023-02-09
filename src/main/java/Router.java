import com.google.gson.*;
import messages.HandshakeMessage;
import messages.Message;

import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Router {
    static Map<String, String> relations = new HashMap<>();
    static Map<String, DatagramSocket> sockets = new HashMap<>();
    static Map<String, Integer> ports = new HashMap<>();
    private final int asn;
    Selector selector;
    private Gson gson;

    public Router(int asn, String[] connections) throws Exception {
        gson = initGson();

        System.out.println("Router at AS " + asn + " starting up");
        this.asn = asn;
        selector = Selector.open();

        for (String relationship : connections) {
            String[] parts = relationship.split("-");
            String port = parts[0];
            String neighbor = parts[1];
            String relation = parts[2];

            //Register the socket with the selector
            DatagramSocket socket;
            DatagramChannel channel = DatagramChannel.open();
            socket = channel.socket();
            socket.bind(new InetSocketAddress(InetAddress.getLocalHost(), Integer.parseInt(port)));

            sockets.put(neighbor, socket);
            ports.put(neighbor, Integer.parseInt(port));
            relations.put(neighbor, relation);

            String message = gson.toJson(new HandshakeMessage(ourAddr(neighbor), neighbor));
            send(neighbor, message);
        }
    }

    private Gson initGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(HandshakeMessage.class, new MessageSerializer());
        return builder.create();
    }

    public String ourAddr(String dst) throws Exception {
        String[] quads = dst.split("\\.");
        quads[3] = "1";
        return String.join(".", quads);
    }

    public void send(String network, String message) throws Exception {
        System.out.println("Sending message '" + message + "' to " + network);
        DatagramPacket dp = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName(network), ports.get(network));

        DatagramSocket socket = sockets.get(network);
        socket.send(dp);
    }

    public void run() throws Exception {
        Selector selector = Selector.open();
        for (DatagramSocket socket : sockets.values()) {
            socket.getChannel().configureBlocking(false);
            socket.getChannel().register(selector, SelectionKey.OP_READ);
        }

        while (true) {
            int readyChannels = selector.select();
            if (readyChannels == 0) {
                continue;
            }
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                if (key.isReadable()) {
                    // Read the incoming data
                    ByteBuffer buffer = ByteBuffer.allocate(65535);
                    DatagramChannel channel = (DatagramChannel) key.channel();
                    InetSocketAddress source = (InetSocketAddress) channel.receive(buffer);

                    buffer.flip();
                    String msg = new String(buffer.array(), 0, buffer.limit());
                    System.out.println("Received message '" + msg + "' from " + source);
                }
                keys.remove();
            }
        }
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

    class MessageSerializer implements JsonSerializer<Message> {
        @Override
        public JsonElement serialize(Message message, Type type, JsonSerializationContext jsonSerializationContext) {
            Gson gson = new Gson();
            String json = gson.toJson(message);
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
            if (message.msg == null) {
                jsonObject.add("msg", new JsonObject());
            }
            return jsonObject;
        }
    }
}
