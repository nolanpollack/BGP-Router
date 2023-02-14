package remote;

import messages.UpdateMessage;

import java.util.List;

import static remote.Router.toBinary;

public class Route {
    public String network;
    public String nextHop;
    public int netmask;
    public int localpref;
    public boolean selfOrigin;
    List<Integer> ASPath;
    public UpdateMessage.UpdateParams.Origin origin;

    public Route(String nextHop, String network, int netmask, int localpref, boolean selfOrigin, List<Integer> ASPath, UpdateMessage.UpdateParams.Origin origin) {
        this.nextHop = nextHop;
        this.network = network;
        this.netmask = netmask;
        this.localpref = localpref;
        this.selfOrigin = selfOrigin;
        this.ASPath = ASPath;
        this.origin = origin;
    }

    public Route(UpdateMessage.UpdateParams params, String nextHop) {
        this.nextHop = nextHop;
        this.network = params.network;
        this.netmask = toBinary(params.netmask).split("0")[0].length();
        this.localpref = params.localpref;
        this.selfOrigin = params.selfOrigin;
        this.ASPath = params.ASPath;
        this.origin = params.origin;
    }
}
