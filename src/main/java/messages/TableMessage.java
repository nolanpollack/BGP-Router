package messages;

import remote.Route;

import java.util.List;

/**
 * Represents a table message.
 */
public class TableMessage extends Message {
    public TableMessage(String src, String dst, List<Route> routingTable) {
        super(MessageType.table, src, dst, routingTable);
    }
}
