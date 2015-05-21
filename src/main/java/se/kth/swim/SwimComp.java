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

import javassist.bytecode.Descriptor.Iterator;

import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.NetPing;
import se.kth.swim.msg.net.NetPong;
import se.kth.swim.msg.net.NetStatus;
import se.kth.swim.msg.net.PiggyPong;
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

	private static int MAX_LIST = 100;
	private static int TIME_OUT = 10;
	private static int PING_MAX = 10;
	private static int DELAY_PONG = 1000;
	private static int LIMIT_CRASH = 1;
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
    private HashMap<UUID,NatedAddress> pingedNodes = new HashMap<UUID,NatedAddress>();
    
    private UUID pingTimeoutId;
    private UUID statusTimeoutId;

    private int receivedPings = 0;
    private int receivedPongs = 0;
    

    private Random rand = new Random();


    public SwimComp(SwimInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} initiating...", selfAddress);
        this.bootstrapNodes = init.bootstrapNodes;
        this.aggregatorAddress = init.aggregatorAddress;
        this.aliveNodes = new HashSet<NatedAddress>(this.bootstrapNodes);
        
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handlePingTimeout, timer);
        subscribe(handlePongTimeout, timer);
        subscribe(handleStatusTimeout, timer);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            log.info("{} starting...", new Object[]{selfAddress.getId()});

            if (!bootstrapNodes.isEmpty()) {
                schedulePeriodicPing();
            }
            schedulePeriodicStatus();
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress.getId()});
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
            SortedSet<NodeAndCounter> tempNodes = new TreeSet<NodeAndCounter>();
            
            log.info("{} received ping from:{}", new Object[]{selfAddress.getId(), nodeSender});
            receivedPings++;
            aliveNodes.add(nodeSender);
            //update counter to 0
            recentAliveNodes.remove(new NodeAndCounter(nodeSender, 0));
            recentAliveNodes.add(new NodeAndCounter(nodeSender, 0));
            
            java.util.Iterator<NodeAndCounter> it = recentAliveNodes.iterator();
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

                recentAliveNodes.remove(node);
                recentAliveNodes.add(node);
            }
            setAlive.add(selfAddress);
            
            it = recentSuspectedNodes.iterator();
            HashSet<NatedAddress> setSuspect = new HashSet<NatedAddress>();
            i= 0;
            tempNodes.clear();
            while(it.hasNext() && i<MAX_LIST){
            	NodeAndCounter address = it.next();
               	int counterAddress = address.getCounter();
               	if ( counterAddress < PING_MAX){
               		setSuspect.add(address.getNode());
                   	tempNodes.add(new NodeAndCounter(address.getNode(),counterAddress+1));
                	i++;
               	}
               	
            }
            
            for (NodeAndCounter node : tempNodes){

                recentSuspectedNodes.remove(node);
                recentSuspectedNodes.add(node);
            }
            

            log.info(" {}: sending pong to {} ",selfAddress,  event.getSource());
         	trigger(new PiggyPong(selfAddress, event.getSource(), setAlive, setSuspect,new HashSet<NatedAddress>()), network);
            

            //trigger(new NetPong(selfAddress, event.getSource()), network);
        }

    };
    
    private Handler<PiggyPong> handlePong = new Handler<PiggyPong>() {

        @Override
        public void handle(PiggyPong event) {
            log.info("{} received pong from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            receivedPongs++;
            UUID uuidToRemove;
            //if receives a  pong, remove node from pingedList
            for(UUID uuid : pingedNodes.keySet()){
            	if (pingedNodes.get(uuid).getId() == event.getSource().getId()){
            		pingedNodes.remove(uuid);
            		cancelPingTimeout(uuid);
            	}
            }
        	suspectedNodes.remove(event.getSource());
        	recentSuspectedNodes.remove(new NodeAndCounter(event.getHeader().getSource(),0));
        	
            HashSet<NatedAddress> hs = event.getAliveNodes();
            java.util.Iterator<NatedAddress> it = hs.iterator();
            while(it.hasNext()){
            	NatedAddress temp = it.next();
            	if (temp != selfAddress){
            		recentAliveNodes.remove(new NodeAndCounter(temp, 0));
                	//recentDeadNodes.remove(new NodeAndCounter(temp, 0));
                	recentAliveNodes.add(new NodeAndCounter(temp, 0));
                	//Can start propagate alive message
                	aliveNodes.add(temp);
            	}
            	recentSuspectedNodes.remove(new NodeAndCounter(temp, 0));
            	suspectedNodes.remove(temp);
            }
            hs = event.getSuspNodes();
            it = hs.iterator();
            while(it.hasNext()){
            	NatedAddress temp = it.next();
            	if (temp != selfAddress){
            		//add it to the "fresh" list
                	recentSuspectedNodes.remove(new NodeAndCounter(temp, 0));
                	recentSuspectedNodes.add(new NodeAndCounter(temp, 0));
                	//add it to the permanent list
                	recentAliveNodes.remove(new NodeAndCounter(temp, 0));
                	log.info("{} receive suspicion of:{}", new Object[]{selfAddress.getId(), temp.getId()});
                	aliveNodes.add(temp); // Add it here to try to ping it after
                	suspectedNodes.add(temp);
            	}
            	
            }
            hs = event.getDeadNodes();
            it = hs.iterator();
            while(it.hasNext()){
            	NatedAddress temp = it.next();
            	recentDeadNodes.add(new NodeAndCounter(temp, 0));
            	//recentAliveNodes.remove(new NodeAndCounter(temp, 3));
            }
        }

    };
    
    
    private Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

        @Override
        public void handle(PingTimeout event) {
        	int indexRandom = randInt(0,aliveNodes.size()-1);
        	int i = 0;
            for (NatedAddress partnerAddress : aliveNodes) {
            	if (i == indexRandom) {
	                log.info("{} sending ping to partner:{}", new Object[]{selfAddress.getId(), partnerAddress});
	                trigger(new NetPing(selfAddress, partnerAddress), network);
	                ScheduleTimeout spt = new ScheduleTimeout(DELAY_PONG);
	                PongTimeout sc = new PongTimeout(spt);
	                spt.setTimeoutEvent(sc);
	                pingedNodes.put(sc.getTimeoutId(), partnerAddress) ;
	                trigger(spt, timer);
	                current_limit++;
	                break;
            	}
            	i++;
            }
        }

    };
    
    private Handler<PongTimeout> handlePongTimeout = new Handler<PongTimeout>() {

        @Override
        public void handle(PongTimeout event) {
            recentSuspectedNodes.add(new NodeAndCounter(pingedNodes.get(event.getTimeoutId()), 0));
            suspectedNodes.add(pingedNodes.get(event.getTimeoutId()));
            log.info("{} suspected node:{}", new Object[]{selfAddress.getId(), pingedNodes.get(event.getTimeoutId()).getId()});
            pingedNodes.remove(event.getTimeoutId());
        }

    };

    private Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {

        @Override
        public void handle(StatusTimeout event) {
            log.info("{} sending status to aggregator:{}", new Object[]{selfAddress.getId(), aggregatorAddress});
            trigger(new NetStatus(selfAddress, aggregatorAddress, new Status(receivedPings, aliveNodes, suspectedNodes, deadNodes)), network);
        }

    };

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
    
    public int randInt(int min, int max) {


        int randomNum = rand.nextInt((max - min) + 1) + min;

        return randomNum;
    }
}
