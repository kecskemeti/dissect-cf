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

import java.util.PriorityQueue;

/**
 * This is the base class for the simulation, every class that should receive
 * timing events should extend this and implement the function named "tick".
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 * 
 */
public abstract class Timed implements Comparable<Timed> {

	private static final PriorityQueue<Timed> timedlist = new PriorityQueue<Timed>();
	private static Timed underProcessing = null;
	private static long fireCounter = 0;

	private boolean activeSubscription = false;
	private long nextEvent = 0;
	// -1 => unitialized
	private long frequency = -1;
	private boolean backPreference = false;

	public final boolean isSubscribed() {
		return activeSubscription;
	}

	protected final boolean subscribe(final long freq) {
		if (activeSubscription) {
			return false;
		}
		realSubscribe(freq);
		return true;
	}

	private void realSubscribe(final long freq) {
		activeSubscription = true;
		updateEvent(freq);
		timedlist.offer(this);
	}

	protected final boolean unsubscribe() {
		if (activeSubscription) {
			activeSubscription = false;
			if (this == underProcessing) {
				// because of the poll during the fire function there is nothing
				// to remove from the list
				return true;
			}
			timedlist.remove(this);
			return true;
		}
		return false;
	}

	protected final long updateFrequency(final long freq) {
		if (activeSubscription) {
			final long oldNE = nextEvent;
			updateEvent(freq);
			if (underProcessing != this && oldNE != nextEvent) {
				timedlist.remove(this);
				timedlist.offer(this);
			}
		} else {
			realSubscribe(freq);
		}
		return nextEvent;
	}

	private void updateEvent(final long freq) {
		if (freq < 0) {
			throw new IllegalStateException("ERROR: Negative event frequency cannot simulate further!");
		} else {
			frequency = freq;
			nextEvent = calcTimeJump(freq);
			if (nextEvent == Long.MAX_VALUE) {
				throw new IllegalStateException("Event to never occur: " + freq);
			}
		}
	}

	public long getNextEvent() {
		return nextEvent;
	}

	public long getFrequency() {
		return frequency;
	}

	public long nextEventDistance() {
		return activeSubscription ? nextEvent - fireCounter : Long.MAX_VALUE;
	}

	@Override
	public int compareTo(final Timed o) {
		final int unalteredResult = nextEvent < o.nextEvent ? -1 : nextEvent == o.nextEvent ? 0 : 1;
		return unalteredResult == 0 ? (o.backPreference ? (backPreference ? 0 : -1) : (backPreference ? 1 : 0))
				: unalteredResult;
	}

	protected void setBackPreference(final boolean backPreference) {
		this.backPreference = backPreference;
	}

	public static final void fire() {
		while (!timedlist.isEmpty() && timedlist.peek().nextEvent == fireCounter) {
			final Timed t = underProcessing = timedlist.poll();
			t.tick(fireCounter);
			if (t.activeSubscription) {
				t.updateEvent(t.frequency);
				timedlist.offer(t);
			}
		}
		underProcessing = null;
		fireCounter++;
	}

	public static long calcTimeJump(long jump) {
		final long targettime = fireCounter + jump;
		return targettime < 0 ? Long.MAX_VALUE : targettime;
	}

	public static final long jumpTime(long desiredJump) {
		final long targettime = calcTimeJump(desiredJump);
		final long nextFire = getNextFire();
		if (targettime <= nextFire) {
			fireCounter = targettime;
			return 0;
		} else {
			fireCounter = nextFire < 0 ? targettime : nextFire;
			return targettime - fireCounter;
		}
	}

	public static final void skipEventsTill(final long desiredTime) {
		final long distance = desiredTime - fireCounter;
		if (timedlist.peek() != null) {
			while (timedlist.peek().nextEvent < desiredTime) {
				final Timed t = timedlist.poll();
				final long oldfreq = t.frequency;
				long tempFreq = distance;
				if (oldfreq != 0) {
					tempFreq += oldfreq - distance % oldfreq;
				}
				t.updateFrequency(tempFreq);
				t.frequency = oldfreq;
			}
		}
		fireCounter = desiredTime;
	}

	public static final long getFireCount() {
		return fireCounter;
	}

	public static final long getNextFire() {
		final Timed head = timedlist.peek();
		return head == null ? -1 : head.nextEvent;
	}

	public static final void simulateUntilLastEvent() {
		long pnf = -1;
		long cnf = 0;
		while ((cnf = getNextFire()) >= 0 && (cnf > pnf)) {
			jumpTime(Long.MAX_VALUE);
			fire();
			pnf = cnf;
		}
	}

	public static final void simulateUntil(final long time) {
		while (timedlist.peek() != null && fireCounter < time) {
			jumpTime(time - fireCounter);
			if (getNextFire() == fireCounter) {
				fire();
			}
		}
	}

	public static final void resetTimed() {
		timedlist.clear();
		underProcessing = null;
		fireCounter = 0;
	}

	@Override
	public String toString() {
		return new StringBuilder("Timed(Freq: ").append(frequency).append(" NE:").append(nextEvent).append(")")
				.toString();
	}

	public abstract void tick(long fires);
}
