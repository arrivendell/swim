package se.kth.swim.msg;

import java.util.HashSet;

import se.kth.swim.NodeAndCounter;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class Pong {
	protected String message = "";
	
	public Pong(String messageIn){
		message = messageIn;
	}
	
	public Pong(){
		message = "";
	}
}
