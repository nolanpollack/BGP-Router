package remote;

import messages.UpdateMessage;

import java.util.List;

public class AggregatedRoute extends Route{
    List<Route> routesInside;

    public AggregatedRoute(String nextHop, String network, int netmask, int localpref, boolean selfOrigin, List<Integer> ASPath, UpdateMessage.UpdateParams.Origin origin,
                           List<Route> routesInside) {
        super(nextHop, network, netmask, localpref, selfOrigin, ASPath, origin);
        this.routesInside = routesInside;
    }

    public void includeRoute(Route route) {
        routesInside.add(route);
    }

    public List<Route> getRoutesInside() {
        return routesInside;
    }
}
