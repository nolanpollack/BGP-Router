package messages;

public class NoRouteMessage extends Message {
    public NoRouteMessage(String src, String dst) {
        super(MessageType.noRoute, src, dst, null);
    }
}
