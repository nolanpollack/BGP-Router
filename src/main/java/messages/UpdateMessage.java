package messages;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an update message.
 */
public class UpdateMessage extends Message {
    public UpdateMessage(String src, String dst, PublicUpdateParams msg) {
        super(MessageType.update, src, dst, msg);
    }

    /**
     * Returns the parameters of this update message.
     *
     * @return the update parameters.
     */
    public UpdateParams getUpdateParams() {
        return (UpdateParams) msg;
    }

    /**
     * Returns the parameters of this update message that would be sent to neighbors, i.e. the ASPath is truncated and
     * some other fields aren't included.
     *
     * @param asn the ASN of the router sending the update (this router).
     * @return the update parameters.
     */
    public PublicUpdateParams getPublicUpdateParams(int asn) {
        PublicUpdateParams params = new PublicUpdateParams((UpdateParams) msg);
        params.ASPath.add(0, asn);

        return params;
    }

    /**
     * Represents the parameters of an update message that would be sent to another router.
     */
    public static class PublicUpdateParams {
        public String network;
        public String netmask;
        public List<Integer> ASPath;

        public PublicUpdateParams(String network, String netmask, List<Integer> ASPath) {
            this.network = network;
            this.netmask = netmask;
            this.ASPath = List.copyOf(ASPath);
        }

        public PublicUpdateParams(UpdateParams params) {
            this.network = params.network;
            this.netmask = params.netmask;
            this.ASPath = new ArrayList<>(params.ASPath);
        }
    }

    /**
     * Represents the parameters of an update message that would be sent to this router. Contains all information
     * about a Route.
     */
    public static class UpdateParams extends PublicUpdateParams {
        public enum Origin {
            IGP,
            EGP,
            UNK
        }

        public int localpref;
        public boolean selfOrigin;
        public Origin origin;

        public UpdateParams(String network, String netmask, int localpref, boolean selfOrigin, List<Integer> ASPath, Origin origin) {
            super(network, netmask, ASPath);
            this.localpref = localpref;
            this.selfOrigin = selfOrigin;
            this.origin = origin;
        }

        public UpdateParams(String network, String netmask, int localpref, boolean selfOrigin, List<Integer> ASPath, String origin) {
            super(network, netmask, ASPath);
            this.localpref = localpref;
            this.selfOrigin = selfOrigin;
            this.origin = Origin.valueOf(origin.toUpperCase());
        }
    }
}
