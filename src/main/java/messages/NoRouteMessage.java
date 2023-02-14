package messages;

public class NoRouteMessage extends Message{
    public NoRouteMessage(String src, String dst, String msg) {
        super(MessageType.noRoute, src, dst, msg);
    }
}
