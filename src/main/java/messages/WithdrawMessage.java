package messages;

import java.util.List;

public class WithdrawMessage extends Message{

    public WithdrawMessage(String src, String dst, WithdrawNetwork[] msg) {
        super(MessageType.withdraw, src, dst, msg);
    }

    public WithdrawNetwork[] getWithdrawNetworks() {
        return (WithdrawNetwork[]) msg;
    }

    public static class WithdrawNetwork {
        public String network;
        public String netmask;

        public WithdrawNetwork(String network, String netmask) {
            this.network = network;
            this.netmask = netmask;
        }
    }
}
