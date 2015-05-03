package se.kth.swim.msg.net;

import se.kth.swim.msg.Ping;
import se.kth.swim.msg.Pong;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class NetPong extends NetMsg<Pong> {

    public NetPong(NatedAddress src, NatedAddress dst) {
        super(src, dst, new Pong());
    }

    private NetPong(Header<NatedAddress> header, Pong content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetPong(newHeader, getContent());
    }

}
