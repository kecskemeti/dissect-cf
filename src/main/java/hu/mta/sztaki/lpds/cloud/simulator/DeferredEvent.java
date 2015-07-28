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

import gnu.trove.map.hash.TLongObjectHashMap;

public abstract class DeferredEvent {

	private static final TLongObjectHashMap<DeferredEvent[]> toSweep = new TLongObjectHashMap<DeferredEvent[]>();
	private static final AggregatedEventDispatcher dispatcherSingleton = new AggregatedEventDispatcher();

	private static class AggregatedEventDispatcher extends Timed {
		@Override
		public void tick(long fires) {
			final DeferredEvent[] simultaneousReceivers = toSweep.remove(fires);
			if (simultaneousReceivers != null) {
				for (int i = 0; i < simultaneousReceivers.length; i++) {
					if (simultaneousReceivers[i] != null) {
						simultaneousReceivers[i].eventAction();
						simultaneousReceivers[i].received = true;
					}
				}
			}
			updateDispatcher();
		}

		private void updateDispatcher() {
			if (toSweep.isEmpty()) {
				unsubscribe();
				return;
			}
			final long[] keys = toSweep.keys();
			long minkey = Long.MAX_VALUE;
			for (long key : keys) {
				if (key < minkey) {
					minkey = key;
				}
			}
			updateFrequency(minkey - getFireCount());
		}
	}

	private boolean cancelled = false;
	private boolean received = false;
	private final long eventArrival;

	public DeferredEvent(final long delay) {
		eventArrival = Timed.calcTimeJump(delay);
		if (delay <= 0) {
			eventAction();
			received = true;
			return;
		}
		DeferredEvent[] simultaneousReceivers = toSweep.get(eventArrival);
		if (simultaneousReceivers == null) {
			simultaneousReceivers = new DeferredEvent[5];
			toSweep.put(eventArrival, simultaneousReceivers);
		}
		int pos = 0;
		for (; pos < simultaneousReceivers.length && simultaneousReceivers[pos] != null; pos++)
			;
		if (pos == simultaneousReceivers.length) {
			DeferredEvent[] temp = new DeferredEvent[simultaneousReceivers.length * 2];
			System.arraycopy(simultaneousReceivers, 0, temp, 0, pos);
			simultaneousReceivers = temp;
			toSweep.put(eventArrival, simultaneousReceivers);
		}
		simultaneousReceivers[pos] = this;
		if (!dispatcherSingleton.isSubscribed() || dispatcherSingleton.getNextEvent() > eventArrival) {
			dispatcherSingleton.updateDispatcher();
		}
	}

	public void cancel() {
		if (received)
			return;
		if (!cancelled) {
			cancelled = true;
			final DeferredEvent[] simultaneousReceivers = toSweep.get(eventArrival);
			if (simultaneousReceivers != null) {
				int uselessReceivers = 0;
				for (int i = 0; i < simultaneousReceivers.length; i++) {
					if (simultaneousReceivers[i] == this) {
						simultaneousReceivers[i] = null;
					}
					if (simultaneousReceivers[i] == null) {
						uselessReceivers++;
					}
				}
				if (uselessReceivers == simultaneousReceivers.length) {
					toSweep.remove(eventArrival);
					dispatcherSingleton.updateDispatcher();
				}
			}
		}
	}

	public boolean isCancelled() {
		return cancelled;
	}

	protected abstract void eventAction();

	static void reset() {
		toSweep.clear();
	}
}
