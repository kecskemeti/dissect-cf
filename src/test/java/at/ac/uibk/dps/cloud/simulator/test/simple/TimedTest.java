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

package at.ac.uibk.dps.cloud.simulator.test.simple;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.TestFoundation;

public class TimedTest extends TestFoundation {
	final static long setFrequency = 20;
	final static long expectedFires = 10;

	public class SingleFire extends Timed {
		public long expectedFire;
		public int myfires = 0;

		public void cancel() {
			unsubscribe();
		}

		public SingleFire() {
			subscribe(setFrequency);
			calcExpectedFire(setFrequency);
		}

		protected void doCancel() {
			cancel();
		}

		public void changeFreq(long newFreq) {
			updateFrequency(newFreq);
			calcExpectedFire(newFreq);
		}

		public void calcExpectedFire(long freq) {
			expectedFire = Timed.getFireCount() + freq;
		}

		@Override
		public void tick(final long fires) {
			Assert.assertEquals("Timed event on the wrong time", expectedFire, fires);
			myfires++;
			doCancel();
		}
	}

	public class RepeatedFire extends SingleFire {
		public final long maxTime;

		public RepeatedFire() {
			super();
			maxTime = expectedFire + (expectedFires - 1) * setFrequency;
		}

		protected void doCancel() {

		}

		@Override
		public void tick(long fires) {
			super.tick(fires);
			calcExpectedFire(setFrequency);
			if (maxTime == fires) {
				unsubscribe();
			}
		}
	}

	@Test(timeout = 100)
	public void singleEventFire() {
		SingleFire fire = new SingleFire();
		Assert.assertTrue("ToString does not detail next event ", fire.toString().contains("" + fire.expectedFire));
		Assert.assertEquals("Event distance is not correct", setFrequency, fire.nextEventDistance());
		Assert.assertTrue("Should be subscribed", fire.isSubscribed());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Timed event did not arrive", fire.myfires, 1);
		Assert.assertEquals("Timed frequency is not the one expected", setFrequency, fire.getFrequency());
		Assert.assertEquals("Event distance is not correct", Long.MAX_VALUE, fire.nextEventDistance());
		Assert.assertTrue("Should not be subscribed", !fire.isSubscribed());
	}

	@Test(timeout = 100)
	public void repeatedEventFire() {
		RepeatedFire fire = new RepeatedFire();
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Not enough timed events", expectedFires, fire.myfires);
	}

	@Test(timeout = 100)
	public void overLappingFires() {
		final ArrayList<SingleFire> sf = new ArrayList<SingleFire>();
		RepeatedFire fire = new RepeatedFire() {
			@Override
			public void tick(long fires) {
				super.tick(fires);
				if (myfires == 2) {
					sf.add(new SingleFire());
				}
			}
		};
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Not enough timed events", expectedFires, fire.myfires);
		Assert.assertEquals("Overlapped timed event did not arrive", sf.get(0).myfires, 1);
	}

	@Test(timeout = 100)
	public void overLappingRepeatedFires() {
		RepeatedFire[] fireobjects = new RepeatedFire[10];
		for (int i = 0; i < fireobjects.length; i++) {
			fireobjects[i] = new RepeatedFire();
			Timed.fire();
		}
		Timed.simulateUntilLastEvent();
		for (int i = 0; i < fireobjects.length; i++) {
			Assert.assertEquals("Not enough timed events", expectedFires, fireobjects[i].myfires);
		}
	}

	@Test(timeout = 100)
	public void eventSkipper() {
		RepeatedFire fire = new RepeatedFire();
		long reducedFires = expectedFires - 3;
		Timed.skipEventsTill(fire.maxTime - reducedFires * setFrequency);
		fire.expectedFire = fire.getNextEvent();
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Not the reduced amount of fires arrived", reducedFires, fire.myfires);
	}

	@Test(timeout = 100)
	public void eventJumper() {
		SingleFire fire = new SingleFire();
		long jump = Timed.jumpTime(setFrequency - 10);
		Assert.assertEquals("Jump should have had success", 0, jump);
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("Timed event did not arrive", fire.myfires == 1);
		long timebefore = Timed.getFireCount();
		long expectedAfter = timebefore + setFrequency;
		jump = Timed.jumpTime(setFrequency);
		Assert.assertEquals("Jump should have had success", 0, jump);
		Assert.assertEquals("Time after jump is not correct", expectedAfter, Timed.getFireCount());
	}

	@Test(timeout = 100)
	public void prematureStopper() {
		RepeatedFire fire = new RepeatedFire();
		Timed.simulateUntil(Timed.getFireCount() + (expectedFires - 3) * setFrequency);
		Assert.assertEquals("Not the reduced amount of fires arrived", 7, fire.myfires);
		Timed.simulateUntil(Long.MAX_VALUE);
		Assert.assertEquals("Not all events arrived", expectedFires, fire.myfires);
		Assert.assertTrue("The simulation finshed way into the future", Timed.getFireCount() == fire.maxTime + 1);
		SingleFire sf = new SingleFire();
		long expectedTime = Timed.getFireCount() + 2;
		Timed.simulateUntil(expectedTime);
		Assert.assertTrue("Fired events that should not arrive yet", sf.myfires == 0);
		Assert.assertEquals("The simulation did not finish at the specified time", expectedTime, Timed.getFireCount());
	}

	@Test(timeout = 100)
	public void multiSubscribe() {
		RepeatedFire fire = new RepeatedFire() {
			@Override
			public void tick(long fires) {
				Assert.assertFalse("Unexpected success of subscription", subscribe(3));
				super.tick(fires);
				if (maxTime == fires) {
					Assert.assertFalse("Unsubscribe should be unsuccessful", unsubscribe());
				}
			}
		};
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Not enough timed events", expectedFires, fire.myfires);
	}

	@Test(timeout = 100)
	public void unSubscribeWhileQueued() {
		RepeatedFire fire = new RepeatedFire();
		long reduction = 3;
		long reducedFires = expectedFires - reduction;
		while (Timed.getFireCount() < fire.maxTime - reducedFires * setFrequency) {
			Timed.jumpTime(Long.MAX_VALUE);
			Timed.fire();
		}
		Assert.assertEquals("Incorrect number of timed events before cancel", reduction, fire.myfires);
		fire.cancel();
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Incorrect number of timed events after cancel", reduction, fire.myfires);
	}

	@Test(timeout = 100)
	public void freqUpdater() {
		final long changedFreq = 15;

		RepeatedFire fire = new RepeatedFire() {
			@Override
			public void tick(long fires) {
				super.tick(fires);
				if (isSubscribed()) {
					expectedFire = updateFrequency(changedFreq);
				}
			}
		};
		long adjustedFires = 1 + ((fire.maxTime - setFrequency) - Timed.getFireCount()) / changedFreq;
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Not enough timed events", adjustedFires, fire.myfires);

		SingleFire f = new SingleFire();
		Timed.fire();
		f.changeFreq(changedFreq);
		Timed.fire();
		f.changeFreq(changedFreq - 1);
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("Frequency change unsuccessful if not changed during the tick function", f.myfires == 1);
	}

	@Test(expected = IllegalStateException.class, timeout = 100)
	public void negativeFreqTester() {
		class NFT extends Timed {
			public NFT() {
				subscribe(-1);
			}

			@Override
			public void tick(long fires) {
				Assert.fail("This event should never come!");
			}
		}
		new NFT();
		Timed.simulateUntilLastEvent();
		Assert.fail("We should never reach the end of this test!");
	}

	@Test(timeout = 100)
	public void zeroFreqTester() {
		SingleFire f = new SingleFire();
		f.changeFreq(0);
		Timed.fire();
		Assert.assertEquals("Even a zero length event should arrive", 1, f.myfires);
	}

	@Test(expected = IllegalStateException.class, timeout = 100)
	public void hugeFreqTester() {
		SingleFire f = new SingleFire();
		f.changeFreq(Long.MAX_VALUE);
	}

	@Test(timeout = 100)
	public void updateFreqWhileUnsubscribed() {
		SingleFire f = new SingleFire();
		f.cancel();
		Assert.assertFalse(f.isSubscribed());
		f.changeFreq(setFrequency);
		Assert.assertTrue("Should be subscribed just as if we did not cancel the fire", f.isSubscribed());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should receive the event even after cancel if an update is done afterwards", 1, f.myfires);
	}

	@Test(timeout = 100)
	public void emptyTimedlistTests() {
		Assert.assertEquals(Timed.getNextFire(), -1);
		Timed.fire();
		Assert.assertEquals(1, Timed.getFireCount());
		Timed.jumpTime(50);
		Assert.assertEquals(51, Timed.getFireCount());
		Timed.skipEventsTill(500);
		Assert.assertEquals(500, Timed.getFireCount());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals(500, Timed.getFireCount());
		Timed.simulateUntil(1000);
		Assert.assertEquals(500, Timed.getFireCount());
	}

	@Test(timeout = 100)
	public void zeroFrequencySkipper() {
		SingleFire sf = new SingleFire();
		sf.changeFreq(0);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should fire the event only once", 1, sf.myfires);
		sf.changeFreq(0); // resubscription
		final long targetTime=1000;
		Timed.skipEventsTill(targetTime); // skipping current events
		sf.expectedFire=targetTime;
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should receive the second fire after the skip events complete", 2, sf.myfires);
	}
	
	@Test(timeout = 100)
	public void negativeTimeIncrementStopper() {
		Timed.skipEventsTill(100);
		Assert.assertEquals("Should allow positive increments", 100, Timed.getFireCount());
		Timed.skipEventsTill(10);
		Assert.assertEquals("Should not allow negative time jumps", 100, Timed.getFireCount());
	}
}
