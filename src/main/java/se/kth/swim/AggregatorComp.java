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

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.swim.msg.net.NetStatus;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class AggregatorComp extends ComponentDefinition {

    private static final Logger log = LoggerFactory.getLogger(AggregatorComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    
	private int cycle;
	private int nodeWithRightInformation;


    private final NatedAddress selfAddress;

    public AggregatorComp(AggregatorInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} initiating...", new Object[]{selfAddress.getId()});
        this.cycle = 0;
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleStatus, network);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            log.info("{} starting...", new Object[]{selfAddress});
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress});
        }

    };

    private Handler<NetStatus> handleStatus = new Handler<NetStatus>() {

        @Override
        public void handle(NetStatus status) {
        	
        	Set<Integer> setAlive = new HashSet<Integer>() ;
            for(NatedAddress nat : status.getContent().aliveNodes){
            	setAlive.add(nat.getId());
            }
            Set<Integer> setSuspected = new HashSet<Integer>() ;
            for(NatedAddress nat : status.getContent().suspectedNodes){
            	setSuspected.add(nat.getId());
            }
            Set<Integer> setDead = new HashSet<Integer>() ;
            for(NatedAddress nat : status.getContent().deadNodes){
            	setDead.add(nat.getId());
            }
            if(status.getHeader().getSource().getId() == 0){
            	if (setAlive.size() == 7 && setDead.size() == 2){
            		nodeWithRightInformation++;
            	}
            		
        		cycle++; 
        		//DEBUG LOG
        		log.info("CYCLE NUMBER has been incremented: {}, size alive : {}, size dead : {}, node with right information : {}", new Object[]{cycle, setAlive.size(), setDead.size(), nodeWithRightInformation});
        		
        		if(nodeWithRightInformation == 10){
            		log.info("FINAL LOG, CONVERGE DONE cycles required : {}, size alive : {}, size dead : {}, node with right information : {}", new Object[]{cycle, setAlive.size(), setDead.size(), nodeWithRightInformation});

        		}
            }
            
//           log.info("{} status from:{} pings:{} aliveNodes :  suspected nodes : {}, deadNodes : {}", 
//                    new Object[]{selfAddress.getId(), status.getHeader().getSource(), status.getContent().receivedPings, /*setAlive,*/ setSuspected, setDead});
        }
    };

    public static class AggregatorInit extends Init<AggregatorComp> {

        public final NatedAddress selfAddress;

        public AggregatorInit(NatedAddress selfAddress) {
            this.selfAddress = selfAddress;
        }
    }
}
