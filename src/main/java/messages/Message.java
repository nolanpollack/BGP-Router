package messages;

public abstract class Message {
    public enum MessageType {
        handshake,
        update,
        data,
        noRoute
    }

    private MessageType type;
    public String src;
    public String dst;
    public Object msg;

    public Message(MessageType type, String src, String dst, Object msg) {
        this.type = type;
        this.src = src;
        this.dst = dst;
        this.msg = msg;
    }

    public MessageType getType() {
        return type;
    }
}