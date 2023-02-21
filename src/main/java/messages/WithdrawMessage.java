package messages;

/**
 * Represents a withdrawal message.
 */
public class WithdrawMessage extends Message {

    public WithdrawMessage(String src, String dst, WithdrawNetwork[] msg) {
        super(MessageType.withdraw, src, dst, msg);
    }

    /**
     * Returns the networks this message indicates to withdraw.
     *
     * @return the networks this message indicates to withdraw.
     */
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
