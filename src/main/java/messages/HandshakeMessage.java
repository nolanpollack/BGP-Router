package messages;

/**
 * Represents a data message.
 */
public class HandshakeMessage extends Message {
    public HandshakeMessage(String src, String dst) {
        super(MessageType.handshake, src, dst, null);
    }
}