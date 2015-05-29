package se.kth.swim.msg.net;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.swim.NodeAndCounter;
import se.kth.swim.SwimComp;
import se.kth.swim.msg.PongSwim;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class PiggyPong extends NetMsg<PongSwim> implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
	/**
	 * 
	 */
	private static final long serialVersionUID = -7148938753157677355L;
		
		public HashSet<NodeAndCounter> getAliveNodes(){
			return getContent().aliveNodes;
		}
		public HashSet<NodeAndCounter> getSuspNodes(){
			return getContent().suspectedNode;
		}
		public HashSet<NodeAndCounter> getDeadNodes(){
			return getContent().deadNodes;
		}
		
	   public PiggyPong(NatedAddress src, NatedAddress dst, HashSet<NodeAndCounter> aliveNodes, HashSet<NodeAndCounter>  suspectedNodes, HashSet<NodeAndCounter>  deadNodes) {
	        super(src, dst, new PongSwim("", aliveNodes, suspectedNodes, deadNodes, dst));
	       // log.info("IN PIGGY {}",this.getContent().aliveNodes.size());
	   }
	   public PiggyPong(NatedAddress src, NatedAddress dst, HashSet<NodeAndCounter> aliveNodes, HashSet<NodeAndCounter>  suspectedNodes, HashSet<NodeAndCounter>  deadNodes, NatedAddress relayedNode) {
	        super(src, dst, new PongSwim("", aliveNodes, suspectedNodes, deadNodes, relayedNode));
	       // log.info("IN PIGGY {}",this.getContent().aliveNodes.size());
	   }


	    private PiggyPong(Header<NatedAddress> header, PongSwim content) {
	        super(header, content);
	    }

	    @Override
	    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
	        return new PiggyPong(newHeader, getContent());
	    }

}
