package messages;

import java.util.List;

public class UpdateMessage extends Message{
    public UpdateMessage(String src, String dst, PublicUpdateParams msg) {
        super(MessageType.update, src, dst, msg);
    }
    public UpdateParams getUpdateParams() {
        return (UpdateParams) msg;
    }

    public PublicUpdateParams getPublicUpdateParams(int asn) {
        PublicUpdateParams params = new PublicUpdateParams((UpdateParams) msg);
        params.ASPath.add(0, asn);

        return new PublicUpdateParams((UpdateParams) msg);
    }
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
            this.ASPath = params.ASPath;
        }
    }
    public static class UpdateParams extends PublicUpdateParams{
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
