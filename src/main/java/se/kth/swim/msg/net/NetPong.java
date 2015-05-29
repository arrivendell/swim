package se.kth.swim.msg.net;

import se.kth.swim.msg.PingSwim;
import se.kth.swim.msg.PongSwim;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class NetPong extends NetMsg<PongSwim> {

    public NetPong(NatedAddress src, NatedAddress dst) {
        super(src, dst, new PongSwim());
    }

    private NetPong(Header<NatedAddress> header, PongSwim content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetPong(newHeader, getContent());
    }

}
