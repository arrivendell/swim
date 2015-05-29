package se.kth.swim.msg.net;

import se.kth.swim.msg.Pong;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class HeartBeatResponse extends NetMsg<Pong> {

	    public HeartBeatResponse(NatedAddress src, NatedAddress dst) {
	        super(src, dst, new Pong());
	    }

	    private HeartBeatResponse(Header<NatedAddress> header, Pong content) {
	        super(header, content);
	    }

	    @Override
	    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
	        return new HeartBeatResponse(newHeader, getContent());
	    }

}
