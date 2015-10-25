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

/**
 * Offers the event handling mechanism for non-recurring but time dependent
 * events. Implementors should provide an implementation of the event action
 * function which will be called once the specified ticks pass.
 * 
 * Aggregates the events that should happen at a single time instance. This
 * approach allows that only one Timed event is registered for a bunch of
 * non-recurring events.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2015"
 */
public abstract class DeferredEvent {

	/**
	 * All deferred events that are due in the future are listed here.
	 * 
	 * The map is indexed by expected event arrivals. The stored objects in the
	 * map are MutablePairs where the left item of the pair is the length of the
	 * right item (which is actually the list of events that should be delivered
	 * at the particular time instance identified by the key of the map).
	 */
	private static final TLongObjectHashMap<MutablePair<Integer, DeferredEvent[]>> toSweep = new TLongObjectHashMap<MutablePair<Integer, DeferredEvent[]>>();
	/**
	 * the aggregator that handles the event list stored in toSweep.
	 */
	private static final AggregatedEventDispatcher dispatcherSingleton = new AggregatedEventDispatcher();

	/**
	 * handles the event aggregations, actual subscriptions to timed events and
	 * dispatches the events if Timed notifies for time instance at which the
	 * non-recurring events should be fired
	 * 
	 * Improves the performance of deferred events significantly if multiple
	 * events should occur at once
	 * 
	 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2015"
	 *
	 */
	private static class AggregatedEventDispatcher extends Timed {
		/**
		 * The actual event dispatcher. This function is called by Timed on the
		 * time instance when the first not yet dispatched deferred event is
		 * due.
		 * 
		 * <i>Note:</i> If multiple events must be delivered at a given time
		 * instance, then the order of the dispatched events are undefined.
		 */
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

		/**
		 * after some deferred events are dispatched, this function actually
		 * determines the next occurrence of a deferred event (and readjusts the
		 * notification frequency for Timed) or if there are no further events
		 * registered, the function cancels the notifications
		 */
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

	/**
	 * Shows if the event in question was cancelled by the user
	 */
	private boolean cancelled = false;
	/**
	 * Shows if the event was dispatched already
	 */
	private boolean received = false;
	/**
	 * The time instance at which this event should be delivered
	 */
	private final long eventArrival;

	/**
	 * Allows constructing objects that will receive an eventAction() call from
	 * Timed after delay ticks.
	 * 
	 * @param delay
	 *            the number of ticks that should pass before this deferred
	 *            event object's eventAction() will be called.
	 */
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

	/**
	 * If the call for eventAction() is no longer necessary at the previously
	 * specified time then the user can cancel this call to arrive with this
	 * function.
	 * 
	 * Calling this function will have no effect on events that are already past
	 * due.
	 */
	public void cancel() {
		if (received)
			return;
		if (!cancelled) {
			cancelled = true;
			MutablePair<Integer, DeferredEvent[]> simultaneousReceiverPairs = toSweep.get(eventArrival);
			if (simultaneousReceiverPairs != null) {
				int len = simultaneousReceiverPairs.getLeft();
				DeferredEvent[] simultaneousReceivers = simultaneousReceiverPairs.getRight();
				// For performance reasons this removal operation does not keep
				// the order of the array entries
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

	/**
	 * Allows to determine whether the actual event was cancelled already or
	 * not.
	 * 
	 * @return
	 * 		<ul>
	 *         <li><i>true</i> if the event will not arrive in the future as it
	 *         was cancelled,
	 *         <li><i>false</i> otherwise
	 *         </ul>
	 */
	public boolean isCancelled() {
		return cancelled;
	}

	/**
	 * When creating a deferred event, implement this function for the actual
	 * event handling mechanism of yours.
	 */
	protected abstract void eventAction();

	/**
	 * Allows the cleanup of all events registered with the aggregator.
	 * 
	 * WARNING: This is not supposed to be called by user code directly.
	 */
	static void reset() {
		toSweep.clear();
	}
}
