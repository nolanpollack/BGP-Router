package messages;

public class DumpMessage extends Message {
    public DumpMessage(String src, String dst) {
        super(MessageType.dump, src, dst, null);
    }
}
