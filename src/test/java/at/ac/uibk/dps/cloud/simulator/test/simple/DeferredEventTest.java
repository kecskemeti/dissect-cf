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
 *  (C) Copyright 2017, Gabor Kecskemeti (g.kecskemeti@ljmu.ac.uk)
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */

package at.ac.uibk.dps.cloud.simulator.test.simple;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.TestFoundation;

public class DeferredEventTest extends TestFoundation {
	final static int limit = 5000;

	final static int[] delayDistribution = new int[limit];

	public static class DeferredTester extends DeferredEvent {
		public boolean eventFired;

		public DeferredTester(final long delay) {
			super(delay);
		}

		@Override
		protected void eventAction() {
			eventFired = true;
		}
	}

	@BeforeClass
	public static void resetSimulation() {
		new DeferredTester(0);
		for (int i = 0; i < limit; i++) {
			delayDistribution[i] = SeedSyncer.centralRnd.nextInt(limit) + 10;
		}
	}

	@Test(timeout = 100)
	public void eventFireTest() {
		DeferredTester dt = new DeferredTester(10);
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("Deferred event was not received", dt.eventFired);
		Assert.assertFalse("Deferred event was cancelled unexpectedly", dt.isCancelled());
		dt.cancel();
		Assert.assertFalse("Deferred event was cancelled unexpectedly", dt.isCancelled());
	}

	@Test(timeout = 100)
	public void eventCancelTest() {
		DeferredTester dt = new DeferredTester(10);
		dt.cancel();
		Assert.assertTrue("Deferred event was not cancelled", dt.isCancelled());
		long beforeTime = Timed.getFireCount();
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Deferred event cancellation have not succeeded", beforeTime, Timed.getFireCount());
		Assert.assertFalse("Deferred event was received unexpectedly", dt.eventFired);
	}

	@Test(timeout = 100)
	public void immediateFireTest() {
		Assert.assertTrue("Deferred event was not received", new DeferredTester(0).eventFired);
	}

	/**
	 * This test ensures that the timed loop remains performant! So the failure of
	 * this test is not the failure of the system but the failure of its
	 * performance!
	 */
	@Test(timeout = 200)
	public void performanceTest() {
		final DeferredTester[] performer = new DeferredTester[limit];
		for (int i = 0; i < limit; i++) {
			performer[i] = new DeferredTester(delayDistribution[i]);
		}
		Timed.simulateUntilLastEvent();
		boolean fired = true;
		for (int i = 0; i < limit; i++) {
			fired &= performer[i].eventFired;
		}
		Assert.assertTrue("Not all events arrived", fired);
	}

	@Test(timeout = 100)
	public void skipEventsEffects() {
		int baseTime = 15;
		new DeferredEvent(baseTime) {
			@Override
			protected void eventAction() {
				Assert.fail("We skipped through this event, thus we don't want to see it arrive!");
			}
		};
		Timed.skipEventsTill(baseTime + 5);
		final boolean[] arr = new boolean[1];
		arr[0] = false;
		new DeferredEvent(baseTime) {
			@Override
			protected void eventAction() {
				arr[0] = true;
			}
		};
		Timed.skipEventsTill(baseTime * 2 - 1);
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("The second event should arrive", arr[0]);
	}
}
