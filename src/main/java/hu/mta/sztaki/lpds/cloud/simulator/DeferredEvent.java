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

import org.apache.commons.lang3.tuple.MutablePair;

import gnu.trove.map.hash.TLongObjectHashMap;

public abstract class DeferredEvent {

	private static final TLongObjectHashMap<MutablePair<Integer, DeferredEvent[]>> toSweep = new TLongObjectHashMap<MutablePair<Integer, DeferredEvent[]>>();
	private static final AggregatedEventDispatcher dispatcherSingleton = new AggregatedEventDispatcher();

	private static class AggregatedEventDispatcher extends Timed {
		@Override
		public void tick(long fires) {
			final MutablePair<Integer, DeferredEvent[]> simultaneousReceiverPairs = toSweep.remove(fires);
			if (simultaneousReceiverPairs != null) {
				final int len = simultaneousReceiverPairs.getLeft();
				final DeferredEvent[] simultaneousReceivers = simultaneousReceiverPairs.getRight();
				for (int i = 0; i < len; i++) {
					simultaneousReceivers[i].eventAction();
					simultaneousReceivers[i].received = true;
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
		MutablePair<Integer, DeferredEvent[]> simultaneousReceiverPairs = toSweep.get(eventArrival);
		if (simultaneousReceiverPairs == null) {
			simultaneousReceiverPairs = new MutablePair<Integer, DeferredEvent[]>(0, new DeferredEvent[5]);
			toSweep.put(eventArrival, simultaneousReceiverPairs);
		}
		int len = simultaneousReceiverPairs.getLeft();
		DeferredEvent[] simultaneousReceivers = simultaneousReceiverPairs.getRight();
		if (len == simultaneousReceivers.length) {
			DeferredEvent[] temp = new DeferredEvent[simultaneousReceivers.length * 2];
			System.arraycopy(simultaneousReceivers, 0, temp, 0, len);
			simultaneousReceivers = temp;
			simultaneousReceiverPairs.setRight(temp);
		}
		simultaneousReceivers[len++] = this;
		simultaneousReceiverPairs.setLeft(len);
		if (!dispatcherSingleton.isSubscribed() || dispatcherSingleton.getNextEvent() > eventArrival) {
			dispatcherSingleton.updateDispatcher();
		}
	}

	public void cancel() {
		if (received)
			return;
		if (!cancelled) {
			cancelled = true;
			MutablePair<Integer, DeferredEvent[]> simultaneousReceiverPairs = toSweep.get(eventArrival);
			if (simultaneousReceiverPairs != null) {
				int len = simultaneousReceiverPairs.getLeft();
				DeferredEvent[] simultaneousReceivers = simultaneousReceiverPairs.getRight();
				for (int i = 0; i < len; i++) {
					if (simultaneousReceivers[i] == this) {
						len--;
						if (len > i) {
							simultaneousReceivers[i] = simultaneousReceivers[len];
						}
						simultaneousReceivers[len] = null;
						break;
					}
				}
				if (len == 0) {
					toSweep.remove(eventArrival);
					dispatcherSingleton.updateDispatcher();
				} else {
					simultaneousReceiverPairs.setLeft(len);
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
