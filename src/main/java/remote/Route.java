package remote;

import messages.UpdateMessage;

import java.util.List;

import static remote.Router.toBinary;

/**
 * Represents a route in the BGP routing table.
 */
public class Route {
    public String network;
    public String nextHop;
    public int netmask;
    public int localpref;
    public boolean selfOrigin;
    public List<Integer> ASPath;
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
        if (params.netmask.equals("0.0.0.0")) {
            this.netmask = 0;
        } else {
            this.netmask = toBinary(params.netmask).split("0")[0].length();
        }
        this.localpref = params.localpref;
        this.selfOrigin = params.selfOrigin;
        this.ASPath = params.ASPath;
        this.origin = params.origin;
    }

    /**
     * Returns the netmask in the format of an IP address.
     *
     * @return the netmask in the format of an IP address.
     */
    public String getNetmask() {
        StringBuilder ipBuilder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            StringBuilder binaryBuilder = new StringBuilder();

            for (int j = 0; j < 8; j++) {
                if ((i) * 8 + j + 1 <= netmask) {
                    binaryBuilder.append("1");
                } else {
                    binaryBuilder.append("0");
                }
            }
            ipBuilder.append(Integer.parseInt(binaryBuilder.toString(), 2));
            if (i < 3) {
                ipBuilder.append(".");
            }
        }
        return ipBuilder.toString();
    }

    public boolean attributesEqual(Route other) {
        return nextHop.equals(other.nextHop)
                && localpref == other.localpref
                && selfOrigin == other.selfOrigin
                && ASPath.equals(other.ASPath)
                && origin == other.origin;
    }

    @Override
    public String toString() {
        return network + "/" + netmask;
    }

    public int compareTo(Route other) {
        return Integer.compare(Integer.parseInt(network.replace(".", "")), Integer.parseInt(other.network.replace(".", "")));
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Route)) {
            return false;
        }
        Route other = (Route) o;
        return network.equals(other.network)
                && netmask == other.netmask
                && attributesEqual(other);
    }
}
