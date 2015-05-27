package se.kth.swim.msg.net;

import se.kth.swim.msg.Ping;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetPingReq extends NetMsg<Ping>  {
	
	public NatedAddress getNodeToPing(){
		return getContent().nodeToPing;
	}
	
    public NetPingReq(NatedAddress src, NatedAddress dst) {
        super(src, dst, new Ping(null));
    }

    private NetPingReq(Header<NatedAddress> header, Ping content) {
        super(header, content);
    }
    public NetPingReq(NatedAddress src, NatedAddress dst, NatedAddress toPing) {
        super(src, dst, new Ping(toPing));
    }

    private NetPingReq(Header<NatedAddress> header, Ping content, NatedAddress toPing) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetPingReq(newHeader, getContent());
    }
}
