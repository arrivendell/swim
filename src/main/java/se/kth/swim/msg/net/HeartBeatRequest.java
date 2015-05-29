package se.kth.swim.msg.net;

import se.kth.swim.msg.Ping;

import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class HeartBeatRequest extends NetMsg<Ping>{
	public HeartBeatRequest(NatedAddress src, NatedAddress dst) {
        super(src, dst, new Ping());
    }

    private HeartBeatRequest(Header<NatedAddress> header, Ping content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new HeartBeatRequest(newHeader, getContent());
    }
}
