package se.kth.swim.msg;

import java.util.HashSet;

import se.kth.swim.NodeAndCounter;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class PongSwim extends Pong{
	public HashSet<NodeAndCounter> aliveNodes = new HashSet<NodeAndCounter>();
	public HashSet<NodeAndCounter>  suspectedNode =  new HashSet<NodeAndCounter>();
	public HashSet<NodeAndCounter>  deadNodes =  new HashSet<NodeAndCounter>();

	public NatedAddress nodeRelayed ;
	
	public PongSwim(String messageIn, HashSet<NodeAndCounter> aliveNodes, HashSet<NodeAndCounter> suspectedNode, HashSet<NodeAndCounter> deadNodes){
		this.aliveNodes= aliveNodes;
		this.suspectedNode = suspectedNode;
		this.deadNodes=deadNodes;
		this.message = messageIn;
	}
	public PongSwim(String messageIn, HashSet<NodeAndCounter> aliveNodes, HashSet<NodeAndCounter> suspectedNode, HashSet<NodeAndCounter> deadNodes, NatedAddress relayedNode){
		this.nodeRelayed = relayedNode;
		this.aliveNodes= aliveNodes;
		this.suspectedNode = suspectedNode;
		this.deadNodes=deadNodes;
		
		message = messageIn;
	}
	
	public PongSwim(){
		message = "";
	}
}
