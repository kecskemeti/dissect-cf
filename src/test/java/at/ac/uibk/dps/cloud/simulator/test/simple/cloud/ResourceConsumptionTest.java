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

package at.ac.uibk.dps.cloud.simulator.test.simple.cloud;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinConsumer;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinProvider;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption.ConsumptionEvent;

public class ResourceConsumptionTest extends ConsumptionEventFoundation {
	public static final double processingTasklen = 1;
	public static final double permsProcessing = processingTasklen / aSecond;
	MaxMinProvider offer;
	MaxMinConsumer utilize;
	protected ResourceConsumption con;

	public ResourceConsumption createAUnitConsumption(final ConsumptionEvent ce) {
		return new ResourceConsumption(processingTasklen, ResourceConsumption.unlimitedProcessing, utilize, offer,
				ce == null ? new ConsumptionEventAssert() : ce);
	}

	@Before
	public void setupConsumption() {
		offer = new MaxMinProvider(permsProcessing);
		utilize = new MaxMinConsumer(permsProcessing);
		con = createAUnitConsumption(null);
	}

	@Test(timeout = 100)
	public void testConsumption() {
		con.setConsumer(utilize);
		con.setProvider(offer);
		Assert.assertEquals("Consumer mismatch", utilize, con.getConsumer());
		Assert.assertEquals("Provider mismatch", offer, con.getProvider());
		Assert.assertEquals("Processing limit mismatch", con.getHardLimit(), con.getProcessingLimit(), 0);
		Assert.assertTrue("Real processing power should be always smaller than equal to the processing limit",
				con.getRealLimit() <= con.getProcessingLimit());
		con.registerConsumption();
		try {
			con.setConsumer(utilize);
			Assert.fail("Consumer change is not allowed after registration");
		} catch (IllegalStateException ex) {
			// Expected after registration
		}
		try {
			con.setProvider(offer);
			Assert.fail("Provider change is not allowed after registration");
		} catch (IllegalStateException ex) {
			// Expected after registration
		}
		Assert.assertFalse("Second registration should not succeed", con.registerConsumption());
		Assert.assertTrue("Already consumed", con.getUnProcessed() == processingTasklen);
		Assert.assertTrue("Unprocessed quantity is not in string output",
				con.toString().contains("" + processingTasklen));
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Not consumed", 0, con.getUnProcessed(), 0);
		con = new ResourceConsumption(processingTasklen, ResourceConsumption.unlimitedProcessing, null, offer,
				new ConsumptionEventAssert());
		con.registerConsumption();
		Assert.assertFalse("Should not be possible to register without consumer", con.isRegistered());
		con = new ResourceConsumption(processingTasklen, ResourceConsumption.unlimitedProcessing, utilize, null,
				new ConsumptionEventAssert());
		con.registerConsumption();
		Assert.assertFalse("Should not be possible to register without provider", con.isRegistered());
	}

	@Test(expected = IllegalStateException.class, timeout = 100)
	public void testNullEvent() {
		// Below we should not receive a null pointer
		con = new ResourceConsumption(processingTasklen, ResourceConsumption.unlimitedProcessing, utilize, offer, null);
		Assert.fail("Should not reach tis point because we asked for a consumption with a null event");
	}

	@Test(timeout = 100)
	public void zeroLenConsumption() {
		ConsumptionEventAdapter ce = new ConsumptionEventAssert();
		con = new ResourceConsumption(0, ResourceConsumption.unlimitedProcessing, utilize, offer, ce);
		Assert.assertFalse("Consumption should not be complete before registration", ce.isCompleted());
		Assert.assertFalse("Consumption should not be cancelled before registration", ce.isCancelled());
		con.registerConsumption();
		Assert.assertTrue("Consumption should be complete immediately after registration", ce.isCompleted());
		Assert.assertFalse("Consumption should not be cancelled before registration", ce.isCancelled());
	}

	protected double preSuspendPhase() {
		Assert.assertTrue("Should be able to register an unprocessed consumption", con.registerConsumption());
		double before = con.getUnProcessed();
		Timed.simulateUntil(Timed.getFireCount() + 500);
		return before;
	}

	protected void pastSuspendChecks(double before) {
		double after = con.getUnProcessed();
		Assert.assertTrue("Should already have some consumption recorded", after != before);
		Assert.assertTrue("Consumption should be incomplete", after != 0);
	}

	@Test(timeout = 100)
	public void suspendConsumption() {
		con.suspend();
		double before = preSuspendPhase();
		con.suspend();
		Timed.fire();
		pastSuspendChecks(before);
		con.registerConsumption();
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Not consumed the necessary amount", 0, con.getUnProcessed(), 0);
	}

	@Test(timeout = 100)
	public void cancelConsumption() {
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		con = createAUnitConsumption(cae);
		double before = preSuspendPhase();
		con.cancel();
		Timed.fire();
		pastSuspendChecks(before);
		Assert.assertFalse("Should not be possible to register after cancellation", con.registerConsumption());
		long timeBefore = Timed.getFireCount();
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Cancellation arrived at improper time", timeBefore - 1, cae.getArrivedAt());
		Assert.assertTrue("There should be no events after cancellation",
				cae.getArrivedAt() + 1 >= Timed.getFireCount());
	}

	@Test(timeout = 100)
	public void cancelUnregistered() {
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		con = createAUnitConsumption(cae);
		con.cancel();
		Assert.assertTrue("Should receive cancellation even if not registered", cae.isCancelled());
	}

	@Ignore
	@Test(timeout = 100)
	public void cancellationJustBeforeCompletion() {
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		long before = Timed.getFireCount();
		con = createAUnitConsumption(cae);
		con.registerConsumption();
		Timed.simulateUntilLastEvent();
		long len = cae.getArrivedAt() - before;
		con = createAUnitConsumption(null);
		con.registerConsumption();
		ConsumptionEventAssert.hits.clear();
		Timed.simulateUntil(Timed.getFireCount() + len - 1);
		Assert.assertTrue("It should not yet arrive", ConsumptionEventAssert.hits.isEmpty());
		Timed.fire();
		con.cancel();
		Timed.fire();
		// Ambiguous behavior! This needs to be thinked through
		Assert.assertFalse("It should arrive by now", ConsumptionEventAssert.hits.isEmpty());
	}

	@Test(timeout = 100)
	public void failedRegistrationTest() {
		Assert.assertFalse("Provider should not accept this consumption",
				new ResourceConsumption(100000, ResourceConsumption.unlimitedProcessing, new MaxMinProvider(1) {
					protected boolean isAcceptableConsumption(ResourceConsumption con) {
						return false;
					};
				}, utilize, new ConsumptionEventAssert()).registerConsumption());
		Assert.assertFalse("Consumer should not accept this consumption",
				new ResourceConsumption(100000, ResourceConsumption.unlimitedProcessing, new MaxMinConsumer(1) {
					@Override
					protected boolean isAcceptableConsumption(ResourceConsumption con) {
						return false;
					}
				}, utilize, new ConsumptionEventAssert()).registerConsumption());
	}

	@Test(timeout = 100)
	public void testLessPerformantProvider() {
		offer = new MaxMinProvider(permsProcessing / 2);
		con = new ResourceConsumption(processingTasklen, ResourceConsumption.unlimitedProcessing, utilize, offer,
				new ConsumptionEventAssert(
						Timed.getFireCount() + (long) (processingTasklen / offer.getPerTickProcessingPower())));
		con.registerConsumption();
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100, expected = IllegalArgumentException.class)
	public void testNegativeRC() {
		con = new ResourceConsumption(-1, ResourceConsumption.unlimitedProcessing, utilize, offer,
				new ConsumptionEventAssert());
		con.registerConsumption();
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void testSuspendRightBeforeCompletionNotification() {
		long before = Timed.getFireCount();
		// benchmark consumption
		con.registerConsumption();
		Timed.simulateUntilLastEvent();
		// needed to determine length of benchmark
		long after = ConsumptionEventAssert.hits.get(0);
		// The tested consumption
		con = createAUnitConsumption(null);
		con.registerConsumption();
		new DeferredEvent(after - before) {
			@Override
			protected void eventAction() {
				// This should come just on the same time the consumption
				// finishes = allowing to test if RS handles these situations
				// correctly
				con.suspend();
			}
		};
		Timed.fire();
		Timed.simulateUntilLastEvent();
		// Resume right after the suspend
		con.registerConsumption();
		// By this time we should not receive two notifications...
		Timed.simulateUntilLastEvent();
	}
}
