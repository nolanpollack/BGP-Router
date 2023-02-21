package remote;

import messages.UpdateMessage;

import java.util.List;

/**
 * Represents an aggregated route in the BGP routing table.
 */
public class AggregatedRoute extends Route {
    //Routes that are aggregated into this route
    List<Route> routesInside;

    public AggregatedRoute(String nextHop, String network, int netmask, int localpref, boolean selfOrigin, List<Integer> ASPath, UpdateMessage.UpdateParams.Origin origin,
                           List<Route> routesInside) {
        super(nextHop, network, netmask, localpref, selfOrigin, ASPath, origin);
        this.routesInside = routesInside;
    }

    /**
     * Adds a route to the list of routes that are aggregated into this route.
     *
     * @param route the route to add.
     */
    public void includeRoute(Route route) {
        routesInside.add(route);
    }

    /**
     * Gets the list of routes that are aggregated into this route.
     */
    public List<Route> getRoutesInside() {
        return routesInside;
    }
}
