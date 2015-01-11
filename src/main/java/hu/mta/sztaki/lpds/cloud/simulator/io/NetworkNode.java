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

import java.util.HashMap;

public class NetworkNode {

	public static class NetworkException extends Exception {
		private static final long serialVersionUID = 5173643896341066497L;

		public NetworkException(String msg) {
			super(msg);
		}
	}

	static class SingleTransfer extends ResourceConsumption {

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

	public final MaxMinConsumer inbws;
	public final MaxMinProvider outbws;
	public final MaxMinConsumer diskinbws;
	public final MaxMinProvider diskoutbws;
	private final long diskBW;

	private final String name;
	private final HashMap<String, Integer> latencies;

	public NetworkNode(final String id, final long maxInBW,
			final long maxOutBW, final long diskBW,
			final HashMap<String, Integer> latencymap) {
		name = id;
		outbws = new MaxMinProvider(maxOutBW);
		inbws = new MaxMinConsumer(maxInBW);
		double diskBWhalf = diskBW / 2f;
		this.diskBW = diskBW;
		diskinbws = new MaxMinConsumer(diskBWhalf);
		diskoutbws = new MaxMinProvider(diskBWhalf);
		latencies = latencymap;
	}

	public long getOutputbw() {
		return (long) outbws.getPerSecondProcessingPower();
	}

	public long getInputbw() {
		return (long) inbws.getPerSecondProcessingPower();
	}

	public long getDiskbw() {
		return this.diskBW;
	}

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
				+ " TX:" + outbws.getTotalProcessed() + " --, D:"
				+ getDiskbw() + ")";
	}

}
