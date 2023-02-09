import java.util.HashMap;

public class Router {
    HashMap<String, String> relations = new HashMap<>();
    HashMap<String, String> sockets = new HashMap<>();
    HashMap<String, String> ports = new HashMap<>();

    public Router(String[] connections) {
        System.out.println("Router at AS  is up");

        for (String relationship : connections) {
            String[] parts = relationship.split("-");
            relations.put(parts[0], parts[1]);
            sockets.put(parts[0], parts[2]);
            ports.put(parts[0], parts[3]);
        }
    }


}
