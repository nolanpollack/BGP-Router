package messages;

/**
 * Represents a dump message.
 */
public class DumpMessage extends Message {
    public DumpMessage(String src, String dst) {
        super(MessageType.dump, src, dst, null);
    }
}
