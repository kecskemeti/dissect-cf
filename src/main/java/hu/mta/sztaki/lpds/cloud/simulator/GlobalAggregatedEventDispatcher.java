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

package hu.mta.sztaki.lpds.cloud.simulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class GlobalAggregatedEventDispatcher extends Timed {
	private static final HashMap<Long, ArrayList<AggregatedEventReceiver>> toSweep = new HashMap<Long, ArrayList<AggregatedEventReceiver>>();
	private static GlobalAggregatedEventDispatcher dispatcherInstance = new GlobalAggregatedEventDispatcher();

	public static long registerSweepable(final AggregatedEventReceiver rcver,
			final long delay) {
		if (delay <= 0) {
			rcver.receiveEvent(Timed.getFireCount());
			return -1;
		}
		final long eventArrival = Timed.calcTimeJump(delay);
		ArrayList<AggregatedEventReceiver> simultaneousReceivers = toSweep
				.get(eventArrival);
		if (simultaneousReceivers == null) {
			simultaneousReceivers = new ArrayList<AggregatedEventReceiver>();
			toSweep.put(eventArrival, simultaneousReceivers);
		}
		simultaneousReceivers.add(rcver);
		if (!dispatcherInstance.isSubscribed()
				|| dispatcherInstance.getNextEvent() > eventArrival) {
			updateDispatcher();
		}
		return eventArrival;
	}

	public static boolean removeSweepable(final AggregatedEventReceiver rcver,
			final long arrival) {
		final ArrayList<AggregatedEventReceiver> simultaneousReceivers = toSweep
				.get(arrival);
		if (simultaneousReceivers == null) {
			return false;
		} else {
			final boolean dropped = simultaneousReceivers.remove(rcver);
			if (simultaneousReceivers.size() == 0) {
				toSweep.remove(arrival);
				if (toSweep.size() == 0) {
					dispatcherInstance.unsubscribe();
				} else if (arrival == dispatcherInstance.getNextEvent()) {
					updateDispatcher();
				}
			}
			return dropped;
		}
	}

	private static void updateDispatcher() {
		dispatcherInstance.updateFrequency(Collections.min(toSweep.keySet())
				- getFireCount());
	}

	@Override
	public void tick(final long fires) {
		final ArrayList<AggregatedEventReceiver> simultaneousReceivers = toSweep
				.remove(fires);
		final int len = simultaneousReceivers.size();
		for (int i = 0; i < len; i++) {
			simultaneousReceivers.get(i).receiveEvent(fires);
		}
		if (toSweep.isEmpty()) {
			dispatcherInstance.unsubscribe();
			return;
		}
		updateDispatcher();
	}
}
