package remote;

import messages.UpdateMessage;

import java.util.List;

public class AggregatedRoute extends Route{
    Route lowerRoute;
    Route upperRoute;

    public AggregatedRoute(String nextHop, String network, int netmask, int localpref, boolean selfOrigin, List<Integer> ASPath, UpdateMessage.UpdateParams.Origin origin,
                           Route lowerRoute, Route upperRoute) {
        super(nextHop, network, netmask, localpref, selfOrigin, ASPath, origin);
        this.lowerRoute = lowerRoute;
        this.upperRoute = upperRoute;
    }
}
