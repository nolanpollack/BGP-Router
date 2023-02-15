package messages;

public class DataMessage extends Message {
    public DataMessage(String src, String dst, String msg) {
        super(MessageType.data, src, dst, msg);
    }
}
