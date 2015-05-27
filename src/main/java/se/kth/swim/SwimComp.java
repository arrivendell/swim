/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.kth.swim;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.Time;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import javax.imageio.stream.IIOByteBuffer;

import javassist.bytecode.Descriptor.Iterator;

import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CaseFormat;

import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.NetPing;
import se.kth.swim.msg.net.NetPingReq;
import se.kth.swim.msg.net.NetPong;
import se.kth.swim.msg.net.NetStatus;
import se.kth.swim.msg.net.PiggyPong;
import se.kth.swim.msg.net.PiggyPongReq;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatType;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class SwimComp extends ComponentDefinition {

	private static int MAX_LIST = 150;
	private static int TIME_OUT = 10;
	private static int PING_MAX = 10;
	private static int DELAY_PONG = 1000;
	private static int DELAY_INDIRECT_PING = 500;
	private static int DELAY_SUSPECTED = 2000;
	private static int K_INDIRECT_PING = 10;
	private enum enumStatus {ALIVE, SUSPECT, DEAD;};
	
	
	private int current_limit = 0;
    private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);

    private final NatedAddress selfAddress;
    private final Set<NatedAddress> bootstrapNodes;
    private final NatedAddress aggregatorAddress;

    private  Set<NatedAddress> aliveNodes ; //start with the bootstraps nodes
    private  Set<NatedAddress> suspectedNodes= new HashSet<NatedAddress>();
    private  Set<NatedAddress> deadNodes= new HashSet<NatedAddress>();
    
    private SortedSet<NodeAndCounter> recentAliveNodes = new TreeSet<NodeAndCounter>();
    private SortedSet<NodeAndCounter> recentSuspectedNodes =  new TreeSet<NodeAndCounter>();
    private SortedSet<NodeAndCounter> recentDeadNodes =  new TreeSet<NodeAndCounter>();
    

    private HashMap<NatedAddress,NatedAddress> indirectToPingNodes = new HashMap<NatedAddress,NatedAddress>();
    private HashMap<UUID,NatedAddress> indirectPingedNodes = new HashMap<UUID,NatedAddress>();
    private HashMap<UUID,NatedAddress> pingedNodes = new HashMap<UUID,NatedAddress>();
    private HashMap<UUID,NatedAddress> timerToSuspectedNodes = new HashMap<UUID,NatedAddress>();

    
    private UUID pingTimeoutId;
    private UUID statusTimeoutId;

    private int receivedPings = 0;
    private int receivedPongs = 0;
    

    private Random rand = new Random();


    public SwimComp(SwimInit init) {
        this.selfAddress = init.selfAddress;
        //log.info("{} initiating...", selfAddress);
        this.bootstrapNodes = init.bootstrapNodes;
        this.aggregatorAddress = init.aggregatorAddress;
        this.aliveNodes = new HashSet<NatedAddress>(this.bootstrapNodes);
        
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handleIndirectPing, network);
        subscribe(handlePingTimeout, timer);
        subscribe(handlePongTimeout, timer);
        subscribe(handleSuspectedTimeout, timer);
        subscribe(handleStatusTimeout, timer);
        subscribe(handleIndirectPongTimeout, timer);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            //log.info("{} starting...", new Object[]{selfAddress.getId()});

            if (!bootstrapNodes.isEmpty()) {
                schedulePeriodicPing();
            }
            schedulePeriodicStatus();
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            //log.info("{} stopping...", new Object[]{selfAddress.getId()});
            if (pingTimeoutId != null) {
                cancelPeriodicPing();
            }
            if (statusTimeoutId != null) {
                cancelPeriodicStatus();
            }
        }

    };

    private Handler<NetPing> handlePing = new Handler<NetPing>() {

        @Override
        public void handle(NetPing event) {
        	NatedAddress nodeSender = event.getHeader().getSource();
           
            //log.info("{} received ping from:{}", new Object[]{selfAddress.getId(), nodeSender});
            receivedPings++;
            aliveNodes.add(nodeSender);
            //update counter to 0 for sender
            recentAliveNodes.remove(new NodeAndCounter(nodeSender, 0));
            recentAliveNodes.add(new NodeAndCounter(nodeSender, 0));
            //Get a number of updates to send
            java.util.Iterator<NodeAndCounter> it = recentAliveNodes.iterator();
            HashSet<NatedAddress> setAlive = new HashSet<NatedAddress>(constructSet(it, recentAliveNodes));
            setAlive.add(selfAddress);
            
            it = recentSuspectedNodes.iterator();
            HashSet<NatedAddress> setSuspect = new HashSet<NatedAddress>(constructSet(it,recentSuspectedNodes));
                        
            it = recentDeadNodes.iterator();
            HashSet<NatedAddress> setDead = new HashSet<NatedAddress>(constructSet(it,recentDeadNodes));
            
            //log.info(" {}: sending pong to {} ",selfAddress,  event.getSource());
         	trigger(new PiggyPong(selfAddress, event.getSource(), setAlive, setSuspect, setDead), network);
            
        }

    };
    
    private Handler<NetPingReq> handleIndirectPing = new Handler<NetPingReq>(){
    	@Override 
    	public void handle(NetPingReq event){
    		indirectToPingNodes.put(event.getNodeToPing(), event.getSource());
    		 log.info("{} sending ping for ind ping to partner:{}", new Object[]{selfAddress.getId(), event.getNodeToPing()});
             trigger(new NetPing(selfAddress, event.getNodeToPing()), network);
    	}
    };
    

    private Handler<PiggyPong> handlePong = new Handler<PiggyPong>() {

        @Override
        public void handle(PiggyPong event) {
        	//if received answer to relay
        	if(indirectToPingNodes.keySet().contains(event.getSource())){
        		log.info("{} received pong for ind pong from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            	trigger(new PiggyPongReq(selfAddress, indirectToPingNodes.get(event.getSource()), event.getAliveNodes(), event.getSuspNodes(), event.getDeadNodes(),event.getSource()), network);
            	indirectToPingNodes.remove(event.getSource());
            	stopTimer(indirectPingedNodes, event.getSource().getId());
            	stopTimer(pingedNodes, event.getSource().getId());
            	return;
        	}
        	else{
        		//log.info("{} received pong from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
                receivedPongs++;
                UUID uuidToRemove;
                //if receives a  pong, remove node from pingedList
                
                stopTimer(pingedNodes, event.getSource().getId());
                stopTimer(indirectPingedNodes, event.getSource().getId());
                mergeUpdateLists(event.getAliveNodes(), event.getSuspNodes(), event.getDeadNodes());
            	suspectedNodes.remove(event.getSource());
            	recentSuspectedNodes.remove(new NodeAndCounter(event.getHeader().getSource(),0));	
        	}
            
        }

    };

    private Handler<PiggyPongReq> handlePongReq = new Handler<PiggyPongReq>() {

        @Override
        public void handle(PiggyPongReq event) {
    		//log.info("{} received pongReq from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            receivedPongs++;
            UUID uuidToRemove;
            //if receives a  pong, remove node from pingedList
            System.out.println("YYYYYYY");
            stopTimer(indirectPingedNodes, event.getContent().nodeRelayed.getId());
            stopTimer(indirectPingedNodes, event.getSource().getId());
            stopTimer(pingedNodes, event.getContent().nodeRelayed.getId());
            stopTimer(pingedNodes, event.getSource().getId());
            mergeUpdateLists(event.getAliveNodes(), event.getSuspNodes(), event.getDeadNodes());
        	suspectedNodes.remove(event.getSource());
        	recentSuspectedNodes.remove(new NodeAndCounter(event.getHeader().getSource(),0));	
        
        }

    };
    
    private Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

        @Override
        public void handle(PingTimeout event) {
        	int max = (aliveNodes.size()-1) >0 ? aliveNodes.size()-1 : 0;
        	int indexRandom = randInt(0,max);
        	int i = 0;
            for (NatedAddress partnerAddress : aliveNodes) {
            	if (i == indexRandom) {
	                //log.info("{} sending ping to partner:{}", new Object[]{selfAddress.getId(), partnerAddress});
	                trigger(new NetPing(selfAddress, partnerAddress), network);
	                launchTimeOutPing(partnerAddress);
	                break;
            	}
            	i++;
            }
        }

    };
    
    private Handler<PongTimeout> handlePongTimeout = new Handler<PongTimeout>() {

        @Override
        public void handle(PongTimeout event) {
        	Set<NatedAddress> tempSet = new HashSet<NatedAddress>(aliveNodes);
        	NatedAddress nodeReceived = pingedNodes.get(event.getTimeoutId());
        	tempSet.remove(nodeReceived);
            //log.info("{} pong timeout, sending indirect  ping for :{}", new Object[]{selfAddress.getId(), nodeReceived.getId()});
            for(int j = 0; j<K_INDIRECT_PING && j<tempSet.size();j++){
            	int indexRandom = randInt(0,tempSet.size()-1);
            	int i = 0;
            	//just to fill it, zwill be changed after in for each
            	NatedAddress itemToRemove= nodeReceived;
                for (NatedAddress partnerAddress : tempSet) {
                	if (i == indexRandom) {
                		itemToRemove = partnerAddress;
                		//log.info("{} sending indirect ping to partner:{}", new Object[]{selfAddress.getId(), partnerAddress});
    	                trigger(new NetPingReq(selfAddress, partnerAddress, nodeReceived), network);
    	                break;
                	}
                	i++;
                }
                tempSet.remove(itemToRemove);
                
            }
            launchTimeOutIndirectPing(nodeReceived);
        }

        /*	NatedAddress nodeReceived = pingedNodes.get(event.getTimeoutId());
            recentSuspectedNodes.add(new NodeAndCounter(nodeReceived, 0));
            suspectedNodes.add(nodeReceived);
          //  //log.info("{} suspected node:{}", new Object[]{selfAddress.getId(), nodeReceived.getId()});
            //pingedNodes.remove(event.getTimeoutId());
            pingedNodes.remove(event.getTimeoutId());
            //Nabil : Juste après avoir ajouté le noeud aux suspects, on lance un timeout (sur selfAddress??)
            launchTimeOutSuspect(nodeReceived);
        }*/
    };
    private Handler<IndirectPongTimeout> handleIndirectPongTimeout = new Handler<IndirectPongTimeout>() {

        @Override
        public void handle(IndirectPongTimeout event) {
        	NatedAddress nodeReceived = indirectPingedNodes.get(event.getTimeoutId());
            recentSuspectedNodes.add(new NodeAndCounter(nodeReceived, 0));
            suspectedNodes.add(nodeReceived);
          //  //log.info("{} suspected node:{}", new Object[]{selfAddress.getId(), nodeReceived.getId()});
            //pingedNodes.remove(event.getTimeoutId());
            indirectPingedNodes.remove(event.getTimeoutId());
            //Nabil : Juste après avoir ajouté le noeud aux suspects, on lance un timeout (sur selfAddress??)
            launchTimeOutSuspect(nodeReceived);
        }

    };
    private void launchTimeOutSuspect(NatedAddress node){
    	ScheduleTimeout spt = new ScheduleTimeout(DELAY_SUSPECTED);
        SuspectedTimeout sc = new SuspectedTimeout(spt);
        spt.setTimeoutEvent(sc);
        timerToSuspectedNodes.put(sc.getTimeoutId(), node) ;
        trigger(spt, timer);
    }
    private void launchTimeOutPing(NatedAddress node){
    	 ScheduleTimeout spt = new ScheduleTimeout(DELAY_PONG);
         PongTimeout sc = new PongTimeout(spt);
         spt.setTimeoutEvent(sc);
         pingedNodes.put(sc.getTimeoutId(), node) ;
         trigger(spt, timer);
    }
    private void launchTimeOutIndirectPing(NatedAddress node){
   	 	ScheduleTimeout spt = new ScheduleTimeout(DELAY_INDIRECT_PING);
   	 	IndirectPongTimeout sc = new IndirectPongTimeout(spt);
        spt.setTimeoutEvent(sc);
        indirectPingedNodes.put(sc.getTimeoutId(), node) ;
        trigger(spt, timer);
   }
    private Handler<SuspectedTimeout> handleSuspectedTimeout = new Handler<SuspectedTimeout>() {

        @Override
        public void handle(SuspectedTimeout event) {
        	NatedAddress nodeTimedOut = timerToSuspectedNodes.get(event.getTimeoutId());
        	log.info("timeout suspect for : {}", nodeTimedOut);
        	for ( UUID uuid : indirectPingedNodes.keySet()){
           		if (indirectPingedNodes.get(uuid).getId() == nodeTimedOut.getId()){
           			//indirectPingedNodes.remove(uuid);
               		cancelSuspectedTimeout(uuid);
               		break;
           		}
           	}
        	for ( UUID uuid : pingedNodes.keySet()){
           		if (pingedNodes.get(uuid).getId() == nodeTimedOut.getId()){
           			//pingedNodes.remove(uuid);
               		cancelSuspectedTimeout(uuid);
               		break;
           		}
           	}
        	recentAliveNodes.remove(new NodeAndCounter(nodeTimedOut, 0));
        	aliveNodes.remove(nodeTimedOut);
        	recentSuspectedNodes.remove(new NodeAndCounter(nodeTimedOut, 0));
        	suspectedNodes.remove(nodeTimedOut);
        	
            recentDeadNodes.add(new NodeAndCounter(nodeTimedOut, 0));
            deadNodes.add(nodeTimedOut);
            //log.info("{} dead node:{}", new Object[]{selfAddress.getId(), nodeTimedOut.getId()});
            timerToSuspectedNodes.remove(event.getTimeoutId());
        }

    };

    private Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {

        @Override
        public void handle(StatusTimeout event) {
            //log.info("{} sending status to aggregator:{}", new Object[]{selfAddress.getId(), aggregatorAddress});
            trigger(new NetStatus(selfAddress, aggregatorAddress, new Status(receivedPings, aliveNodes, suspectedNodes, deadNodes)), network);
        }

    };
    
    private HashSet<NatedAddress> constructSet(java.util.Iterator<NodeAndCounter> it,  SortedSet<NodeAndCounter> recent ){
    	SortedSet<NodeAndCounter> tempNodes = new TreeSet<NodeAndCounter>();
    	HashSet<NatedAddress> setAlive = new HashSet<NatedAddress>();
        int i= 0;
        while(it.hasNext() && i<MAX_LIST){
        	NodeAndCounter address = it.next();
           	int counterAddress = address.getCounter();
           	if ( counterAddress < PING_MAX){
           		setAlive.add(address.getNode());
               	tempNodes.add(new NodeAndCounter(address.getNode(),counterAddress+1));
            	i++;
           	}
        }
        //We change the recent set by incrementing the counters
        for (NodeAndCounter node : tempNodes){
        	recent.remove(node);
        	recent.add(node);
        }
        return setAlive;
    }
    
    private void updateRecentSet(enumStatus status, NodeAndCounter node){
    	switch(status) {
    		case ALIVE :
    			recentAliveNodes.remove(node);
    			recentAliveNodes.add(node);
    			break;
    		case SUSPECT :
    			recentSuspectedNodes.remove(node);
    			recentSuspectedNodes.add(node);
    			break;
    		case DEAD :
    			recentDeadNodes.remove(node);
    			recentDeadNodes.add(node);
    			break;
    	}
    }

    private void schedulePeriodicPing() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 1000);
        PingTimeout sc = new PingTimeout(spt);
        spt.setTimeoutEvent(sc);
        pingTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicPing() {
        CancelTimeout cpt = new CancelTimeout(pingTimeoutId);
        trigger(cpt, timer);
        pingTimeoutId = null;
    }
    
    private void cancelPingTimeout(UUID uuid) {
        CancelTimeout cpt = new CancelTimeout(uuid);
        trigger(cpt, timer);
    }
    
    private void cancelSuspectedTimeout(UUID uuid) {
        CancelTimeout cpt = new CancelTimeout(uuid);
        trigger(cpt, timer);
    }
    

    private void schedulePeriodicStatus() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(2000, 2000);
        StatusTimeout sc = new StatusTimeout(spt);
        spt.setTimeoutEvent(sc);
        statusTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicStatus() {
        CancelTimeout cpt = new CancelTimeout(statusTimeoutId);
        trigger(cpt, timer);
        statusTimeoutId = null;
    }

    
    private void mergeUpdateLists(HashSet<NatedAddress> newAlive, HashSet<NatedAddress> newSuspect, HashSet<NatedAddress> newDead){
    	
        java.util.Iterator<NatedAddress> it = newAlive.iterator();
        while(it.hasNext()){
        	NatedAddress temp = it.next();
        	if (temp != selfAddress && !deadNodes.contains(temp)){
        		recentAliveNodes.remove(new NodeAndCounter(temp, 0));
            	//recentDeadNodes.remove(new NodeAndCounter(temp, 0));
            	recentAliveNodes.add(new NodeAndCounter(temp, 0));
            	//Can start propagate alive message
            	aliveNodes.add(temp);
        	}
        	if(recentSuspectedNodes.remove(new NodeAndCounter(temp, 0))){
        		stopTimer(timerToSuspectedNodes, temp.getId());
        	}
        	suspectedNodes.remove(temp);
        }
        
        it = newSuspect.iterator();
        while(it.hasNext()){
        	NatedAddress temp = it.next();
        	if (temp != selfAddress &&!deadNodes.contains(temp)){
        		//add it to the "fresh" list
            	recentSuspectedNodes.remove(new NodeAndCounter(temp, 0));
            	recentSuspectedNodes.add(new NodeAndCounter(temp, 0));
            	launchTimeOutSuspect(temp);
            	//add it to the permanent list
            	recentAliveNodes.remove(new NodeAndCounter(temp, 0));
            	//log.info("{} receive suspicion of:{}", new Object[]{selfAddress.getId(), temp.getId()});
            	aliveNodes.add(temp); // Add it here to try to ping it after
            	suspectedNodes.add(temp);
        	}
        	
        }
        
        it = newDead.iterator();
        while(it.hasNext()){
        	NatedAddress temp = it.next();
        	recentAliveNodes.remove(new NodeAndCounter(temp, 0));
        	aliveNodes.remove(temp);
        	boolean isOldSuspect = suspectedNodes.remove(temp);
        	if(recentSuspectedNodes.remove(new NodeAndCounter(temp, 0)) || isOldSuspect ){
        		stopTimer(timerToSuspectedNodes, temp.getId());
        		stopTimer(pingedNodes, temp.getId());
        		stopTimer(indirectPingedNodes, temp.getId());
        	}
        	recentDeadNodes.remove(new NodeAndCounter(temp, 0));
        	recentDeadNodes.add(new NodeAndCounter(temp, 0));
        	deadNodes.add(temp);
        	//recentAliveNodes.remove(new NodeAndCounter(temp, 3));
        }
    }
    
    private void stopTimer(HashMap<UUID, NatedAddress> mapTimer, int idNode){
    	UUID uuidToRemove = new UUID(0, 0);
    	for(UUID uuid : mapTimer.keySet()){
        	if (mapTimer.get(uuid).getId() == idNode){
        		uuidToRemove = uuid ;
        		break;
			}
        }
    	if (uuidToRemove.compareTo(new UUID(0, 0)) != 0){
        	mapTimer.remove(uuidToRemove);
    		cancelPingTimeout(uuidToRemove);
    	}
    }
    public static class SwimInit extends Init<SwimComp> {

        public final NatedAddress selfAddress;
        public final Set<NatedAddress> bootstrapNodes;
        public final NatedAddress aggregatorAddress;

        public SwimInit(NatedAddress selfAddress, Set<NatedAddress> bootstrapNodes, NatedAddress aggregatorAddress) {
            this.selfAddress = selfAddress;
            this.bootstrapNodes = bootstrapNodes;
            this.aggregatorAddress = aggregatorAddress;
        }
    }

    private static class StatusTimeout extends Timeout {

        public StatusTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }

    private static class PingTimeout extends Timeout {

        public PingTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }
    
    private static class PongTimeout extends Timeout {

        public PongTimeout(ScheduleTimeout request) {
            super(request);
        }
    }
    
    private static class IndirectPongTimeout extends Timeout {

        public IndirectPongTimeout(ScheduleTimeout request) {
            super(request);
        }
    }
    
    private static class SuspectedTimeout extends Timeout {

        public SuspectedTimeout(ScheduleTimeout request) {
            super(request);
        }
    }
    
    public int randInt(int min, int max) {


        int randomNum = rand.nextInt((max - min) + 1) + min;

        return randomNum;
    }
}
