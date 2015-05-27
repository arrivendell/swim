package se.kth.swim.msg.net;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.swim.SwimComp;
import se.kth.swim.msg.Pong;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class PiggyPongReq extends NetMsg<Pong> implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
	/**
	 * 
	 */
	private static final long serialVersionUID = -7148938753157677355L;
		
		public HashSet<NatedAddress> getAliveNodes(){
			return getContent().aliveNodes;
		}
		public HashSet<NatedAddress> getSuspNodes(){
			return getContent().suspectedNode;
		}
		public HashSet<NatedAddress> getDeadNodes(){
			return getContent().deadNodes;
		}
		/*public void setRelayedPong(NatedAddress nodeRelayed){
			getContent().nodeRelayed = nodeRelayed;
			
		}*/
	   public PiggyPongReq(NatedAddress src, NatedAddress dst, HashSet<NatedAddress> aliveNodes, HashSet<NatedAddress>  suspectedNodes, HashSet<NatedAddress>  deadNodes) {
		   super(src, dst, new Pong("", aliveNodes, suspectedNodes, deadNodes/*, src*/));
		   // log.info("IN PIGGY {}",this.getContent().aliveNodes.size());
	   }
	   public PiggyPongReq(NatedAddress src, NatedAddress dst, HashSet<NatedAddress> aliveNodes, HashSet<NatedAddress>  suspectedNodes, HashSet<NatedAddress>  deadNodes, NatedAddress relayedNode) {
		   super(src, dst, new Pong("", aliveNodes, suspectedNodes, deadNodes/*, relayedNode*/));
		   // log.info("IN PIGGY {}",this.getContent().aliveNodes.size());
	   }
	    private PiggyPongReq(Header<NatedAddress> header, Pong content) {
	        super(header, content);
	    }

	    @Override
	    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
	        return new PiggyPongReq(newHeader, getContent());
	    }

}
