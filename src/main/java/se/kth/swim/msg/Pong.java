package se.kth.swim.msg;

import java.util.HashSet;

import se.sics.p2ptoolbox.util.network.NatedAddress;

public class Pong {
	private String message = "";
	public HashSet<NatedAddress> aliveNodes = new HashSet<NatedAddress>();
	public HashSet<NatedAddress>  suspectedNode =  new HashSet<NatedAddress>();
	public HashSet<NatedAddress>  deadNodes =  new HashSet<NatedAddress>();

	public NatedAddress nodeRelayed ;
	
	public Pong(String messageIn, HashSet<NatedAddress> aliveNodes, HashSet<NatedAddress> suspectedNode, HashSet<NatedAddress> deadNodes){
		this.aliveNodes= aliveNodes;
		this.suspectedNode = suspectedNode;
		this.deadNodes=deadNodes;
		message = messageIn;
	}
	public Pong(String messageIn, HashSet<NatedAddress> aliveNodes, HashSet<NatedAddress> suspectedNode, HashSet<NatedAddress> deadNodes, NatedAddress relayedNode){
		this.nodeRelayed = relayedNode;
		this.aliveNodes= aliveNodes;
		this.suspectedNode = suspectedNode;
		this.deadNodes=deadNodes;
		
		message = messageIn;
	}
	
	public Pong(){
		message = "";
	}
}
