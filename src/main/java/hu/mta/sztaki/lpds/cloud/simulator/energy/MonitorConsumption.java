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

package hu.mta.sztaki.lpds.cloud.simulator.energy;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceSpreader;

import java.util.PriorityQueue;

/**
 * This class is an initial framework to collect periodical reports on
 * consumptions. In its current state it is too rigid and needs more
 * customization.
 * 
 * However, if extended further it would allow a single consumption monitor to
 * be added to a particular resourcespreader, eliminating the need for multiple
 * queries on the getTotalProcessed function.
 * 
 * @author 
 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 * 
 */
public class MonitorConsumption extends Timed {
	private static class SpreadingRecord implements Comparable<SpreadingRecord> {
		public long timestamp;
		public double totalProcessed;

		@Override
		public int compareTo(SpreadingRecord o) {
			return Long.signum(timestamp - o.timestamp);
		}
	}

	final ResourceSpreader toMonitor;
	private PriorityQueue<SpreadingRecord> subHourRecords = new PriorityQueue<SpreadingRecord>();
	private PriorityQueue<SpreadingRecord> subDayRecords = new PriorityQueue<SpreadingRecord>();
	private double subHourProcessing = 0;
	private double subDayProcessing = 0;
	private double totalProcessed;
	private double subSecondProcessing = 0;

	public MonitorConsumption(ResourceSpreader toMonitor) {
		this.toMonitor = toMonitor;
		totalProcessed = toMonitor.getTotalProcessed();
		subscribe(1000);
	}

	public double getSubDayProcessing() {
		return subDayProcessing;
	}

	public double getSubHourProcessing() {
		return subHourProcessing;
	}

	public double getSubSecondProcessing() {
		return subSecondProcessing;
	}

	@Override
	public void tick(final long fires) {
		final SpreadingRecord current = new SpreadingRecord();
		current.timestamp = fires;
		current.totalProcessed = toMonitor.getTotalProcessed();
		subSecondProcessing = current.totalProcessed - totalProcessed;
		totalProcessed = current.totalProcessed;
		subHourRecords.add(current);
		while (subHourRecords.peek().timestamp + 3600000 < fires) {
			// 60*60*1000
			// Over the past hour
			subDayRecords.add(subHourRecords.poll());
			while (subDayRecords.peek().timestamp + 86400000 < fires) {
				// Over the past day
				subDayRecords.poll();
			}
		}
		subHourProcessing = totalProcessed
				- subHourRecords.peek().totalProcessed;
		try {
			subDayProcessing = totalProcessed
					- subDayRecords.peek().totalProcessed;
		} catch (NullPointerException ne) {
			// No records over an hour
			subDayProcessing = subHourProcessing;
		}
	}
}
