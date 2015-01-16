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

public abstract class Timed implements Comparable<Timed> {

	private static final PriorityQueue<Timed> timedlist = new PriorityQueue<Timed>();
	private static Timed underProcessing = null;
	private static long fireCounter = 0;

	private boolean activeSubscription = false;
	private long nextEvent = 0;
	private long frequency = 0;

	public final boolean isSubscribed() {
		return activeSubscription;
	}

	protected final boolean subscribe(final long freq) {
		if (activeSubscription) {
			return false;
		}
		return realSubscribe(freq);
	}
		
	private boolean realSubscribe(final long freq) {
		activeSubscription = true;
		updateEvent(freq);
		if (this == underProcessing) {
			return true;
		}
		timedlist.offer(this);
		return true;
		
	}

	protected final boolean unsubscribe() {
		if (activeSubscription) {
			activeSubscription = false;
			if (this == underProcessing) {
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
			throw new IllegalStateException(
					"ERROR: Negative event frequency cannot simulate further!");
		}
		frequency = freq;
		nextEvent = calcTimeJump(freq);
	}

	public long getNextEvent() {
		return nextEvent;
	}

	public long getFrequency() {
		return frequency;
	}

	public long nextEventDistance() {
		if (this.activeSubscription) {
			return this.nextEvent - fireCounter;
		}
		return Long.MAX_VALUE;
	}

	@Override
	public int compareTo(final Timed o) {
		if (this.nextEvent < o.nextEvent) {
			return -1;
		}
		if (this.nextEvent == o.nextEvent) {
			return 0;
		}
		return 1;
	}

	public static final void fire() {
		while (!timedlist.isEmpty()
				&& timedlist.peek().nextEvent == fireCounter) {
			final Timed t;
			(t = underProcessing = timedlist.poll()).tick(fireCounter);
			if (t.activeSubscription) {
				t.updateEvent(t.frequency);
				timedlist.offer(t);
			}
		}
		underProcessing = null;
		fireCounter++;
	}

	private static long calcTimeJump(long jump) {
		final long targettime;
		if ((targettime = fireCounter + jump) < 0) {
			return Long.MAX_VALUE;
		}
		return targettime;
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
		while (timedlist.peek().nextEvent < desiredTime) {
			final Timed t;
			final long oldfreq = (t = timedlist.poll()).frequency;
			t.updateFrequency(distance + (oldfreq - distance % oldfreq));
			t.frequency = oldfreq;
		}
		fireCounter = desiredTime;
	}

	public static final long getFireCount() {
		return fireCounter;
	}

	public static final long getNextFire() {
		final Timed head;
		if((head = timedlist.peek()) == null) {
			return -1;
		}
		return head.nextEvent;
	}

	public static final void simulateUntilLastEvent() {
		while (timedlist.peek() != null) {
			jumpTime(Long.MAX_VALUE);
			fire();
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
		return new StringBuilder("Timed(Freq: ").append(frequency).append(" NE:").append(nextEvent).append(")").toString();
		//return "Timed(Freq: "+frequency+" NE:"+nextEvent+")";
	}

	public abstract void tick(long fires);
}
