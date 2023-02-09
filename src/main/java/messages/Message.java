package messages;

public abstract class Message {
    public String type;
    public String src;
    public String dst;
    public Object msg;

    public Message(String type, String src, String dst, Object msg) {
        this.type = type;
        this.src = src;
        this.dst = dst;
        this.msg = msg;
    }

    public String toString() {
        return "{ \"type\": \"" + type + "\", \"src\": \"" + src + "\", \"dst\": \"" + dst + "\", \"msg\": " + msg + " }";
    }
}
