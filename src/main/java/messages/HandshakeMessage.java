package messages;

public class HandshakeMessage extends Message{
    public HandshakeMessage(String src, String dst) {
        super("handshake", src, dst, null);
    }
}