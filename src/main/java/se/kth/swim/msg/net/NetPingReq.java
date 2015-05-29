package se.kth.swim.msg.net;

import se.kth.swim.msg.PingSwim;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetPingReq extends NetMsg<PingSwim>  {
	
	public NatedAddress getNodeToPing(){
		return getContent().nodeToPing;
	}
	
    public NetPingReq(NatedAddress src, NatedAddress dst, int incarn) {
        super(src, dst, new PingSwim(null, incarn));
    }

    private NetPingReq(Header<NatedAddress> header, PingSwim content) {
        super(header, content);
    }
    public NetPingReq(NatedAddress src, NatedAddress dst, NatedAddress toPing, int incarn) {
        super(src, dst, new PingSwim(toPing, incarn));
    }

    private NetPingReq(Header<NatedAddress> header, PingSwim content, NatedAddress toPing) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetPingReq(newHeader, getContent());
    }
}
