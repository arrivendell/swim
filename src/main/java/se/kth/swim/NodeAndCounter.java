package se.kth.swim;

import se.sics.p2ptoolbox.util.network.NatedAddress;
import java.lang.Comparable;;

public class NodeAndCounter implements Comparable<NodeAndCounter> {
	private NatedAddress nodeAdress;
	private int counter;
	
	public NodeAndCounter(NatedAddress nodeToAdd, int counterNode){
		nodeAdress = nodeToAdd;
		counter = counterNode;
	}
	
	public int compareTo(NodeAndCounter o) {
		if(nodeAdress == o.nodeAdress){
        	return 0;
        }
		else if (counter < o.counter){
        	return -1;
        }
        else if (counter > o.counter){
        	return 1;
        }
        
        else if (nodeAdress.getId()< o.nodeAdress.getId()){
        	return -1;
        }
        else if (nodeAdress.getId()> o.nodeAdress.getId()){
        	return 1;
        }
        return 0;
    
	}
	
	public NatedAddress getNode(){
		return nodeAdress;
	}

	public int getCounter(){
		return counter;
	}
	
}
