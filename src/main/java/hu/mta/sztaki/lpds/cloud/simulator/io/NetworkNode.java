/*
 *  ========================================================================
 *  DIScrete event baSed Energy Consumption simulaTor 
 *    					             for Clouds and Federations (DISSECT-CF)
 *  ========================================================================
 *  
 *  This file is part of DISSECT-CF.
 *  
 *  DISSECT-CF is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or (at
 *  your option) any later version.
 *  
 *  DISSECT-CF is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 *  General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with DISSECT-CF.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */

package hu.mta.sztaki.lpds.cloud.simulator.io;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinConsumer;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinProvider;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;

import java.util.Map;

/**
 * This class represents a networked element in the system. The class also
 * contains the definitions for the helper classes in the network simulation
 * that together are responsible to introduce and simulate network delays in the
 * system. The instances of this class are always present and represent the
 * general network capabilities in the hosts.
 * 
 * @author 
 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 * 
 */
public class NetworkNode {

	public static class NetworkException extends Exception {
		private static final long serialVersionUID = 5173643896341066497L;

		public NetworkException(String msg) {
			super(msg);
		}
	}

	/**
	 * The instances of this class represent an individual data transfer in the
	 * system. The visibility of the class and its members are defined so the
	 * compiler does not need to generate access methods for the members thus
	 * allowing fast and prompt changes in its contents.
	 * 
	 * To create a new instance of this class, one must use the initTransfer
	 * method of the NetworkNode.
	 * 
	 * WARNING this is an internal representation of the transfer. This class is
	 * not supposed to be used outside of the context of the NetworkNode.
	 * 
	 * @author 
	 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
	 * 
	 */
	static class SingleTransfer extends ResourceConsumption {

		/**
		 * This constructor describes the basic properties of an individual
		 * transfer.
		 * 
		 * @param tottr
		 *            The amount of data to be transferred during the lifetime
		 *            of the just created object
		 * @param e
		 *            Specify here the event to be fired when the just created
		 *            object completes its transfers. With this event it is
		 *            possible to notify the entity who initiated the transfer.
		 */
		private SingleTransfer(final int latency, final long tottr,
				final double limit, final MaxMinConsumer in,
				final MaxMinProvider out,
				final ResourceConsumption.ConsumptionEvent e) {
			super(tottr, limit, in, out, e);
			if (latency != 0) {
				new DeferredEvent(latency) {
					@Override
					protected void eventAction() {
						registerConsumption();
					}
				};
			} else {
				registerConsumption();
			}
		}
	}

	// BW spreaders among pipes:

	public final MaxMinConsumer inbws;
	public final MaxMinProvider outbws;
	public final MaxMinConsumer diskinbws;
	public final MaxMinProvider diskoutbws;
	public final MaxMinConsumer meminbws;
	public final MaxMinProvider memoutbws;

	private final String name;
	private final Map<String, Integer> latencies;

	/**
	 * This function initializes the bandwidth spreaders for the node to ensure
	 * equal network share for each transfer occurring on the node.
	 * 
	 * @param maxInBW
	 *            the input bw of the node
	 * @param maxOutBW
	 *            the output bw of the node
	 * @param diskBW
	 *            the disk bw of the node
	 */
	public NetworkNode(final String id, final long maxInBW,
			final long maxOutBW, final long diskBW,
			final Map<String, Integer> latencymap) {
		name = id;
		outbws = new MaxMinProvider(maxOutBW);
		inbws = new MaxMinConsumer(maxInBW);
		diskinbws = new MaxMinConsumer(diskBW / 2f);
		diskoutbws = new MaxMinProvider(diskBW / 2f);
		// Just making sure we will have enough bandwidht for every operation we
		// could possibly have
		final double memBW = (maxOutBW + maxInBW + diskBW);
		meminbws = new MaxMinConsumer(memBW);
		memoutbws = new MaxMinProvider(memBW);
		latencies = latencymap;
	}

	/**
	 * Determines the total output bandwidth available for the node
	 * 
	 * @return the maximum bandwidth with this network node can send data to the
	 *         outside world
	 */
	public long getOutputbw() {
		return (long) outbws.getPerTickProcessingPower();
	}

	/**
	 * Determines the total input bandwidth available for the node
	 * 
	 * @return the maximum bandwidth with this network node can receive data
	 *         from the outside world
	 */
	public long getInputbw() {
		return (long) inbws.getPerTickProcessingPower();
	}

	/**
	 * The bandwidth available when duplicating local disk contents.
	 * 
	 * @return the maximal bandwidth usable by the network node while copying a
	 *         file or raw disk blocks on its storage subsystem
	 */
	public long getDiskbw() {
		return (long) diskinbws.getPerTickProcessingPower() * 2;
	}

	/**
	 * This function ensures the proper initialization of an individual
	 * transfer.
	 * 
	 * @param size
	 *            defines the size of the transfer to be simulated
	 * @param from
	 *            defines the source of the transfer
	 * @param to
	 *            defines the destination of the transfer
	 * @param e
	 *            defines the way the initiator will be notified upon the
	 *            completion of the transfer
	 */
	public static ResourceConsumption initTransfer(final long size,
			final double limit, final NetworkNode from, final NetworkNode to,
			final ResourceConsumption.ConsumptionEvent e)
			throws NetworkException {
		if (from == to) {
			return new SingleTransfer(0, size, limit, from.diskinbws,
					from.diskoutbws, e);
		} else {
			return new SingleTransfer(checkConnectivity(from, to), size, limit,
					to.inbws, from.outbws, e);
		}
	}

	public ResourceConsumption pushFromMemory(final long size,
			final double limit, boolean toDisk,
			final ResourceConsumption.ConsumptionEvent e) {
		return new SingleTransfer(0, size, limit, toDisk ? diskinbws : inbws,
				memoutbws, e);
	}

	public ResourceConsumption readToMemory(final long size,
			final double limit, boolean fromDisk,
			final ResourceConsumption.ConsumptionEvent e) {
		return new SingleTransfer(0, size, limit, meminbws,
				fromDisk ? diskoutbws : outbws, e);
	}

	public static int checkConnectivity(final NetworkNode from,
			final NetworkNode to) throws NetworkException {
		if (from == to) {
			return 0;
		}
		final Integer lat = from.latencies.get(to.name);
		if (lat == null)
			throw new NetworkException("No connection between: '" + from.name
					+ "' and '" + to.name + "'");
		return lat;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return "NetworkNode(Id:" + name + " NI:" + getInputbw() + ",NO:"
				+ getOutputbw() + " -- RX:" + inbws.getTotalProcessed()
				+ " TX:" + outbws.getTotalProcessed() + " --, D:" + getDiskbw()
				+ ")";
	}

}
