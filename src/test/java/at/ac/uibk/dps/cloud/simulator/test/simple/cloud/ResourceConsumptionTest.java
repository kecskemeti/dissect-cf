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

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinConsumer;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinProvider;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption.ConsumptionEvent;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventFoundation;

public class ResourceConsumptionTest extends ConsumptionEventFoundation {
	public static final double processingTasklen = 1;
	public static final double permsProcessing = processingTasklen / aSecond;
	MaxMinProvider offer;
	MaxMinConsumer utilize;
	ResourceConsumption con;

	public ResourceConsumption createAUnitConsumption(final ConsumptionEvent ce) {
		return new ResourceConsumption(processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer,
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
		Assert.assertEquals("Processing limit mismatch", con.getHardLimit(),
				con.getProcessingLimit(), 0);
		Assert.assertTrue(
				"Real processing power should be always smaller than equal to the processing limit",
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
		Assert.assertFalse("Second registration should not succeed",
				con.registerConsumption());
		Assert.assertTrue("Already consumed",
				con.getUnProcessed() == processingTasklen);
		Assert.assertTrue("Unprocessed quantity is not in string output", con
				.toString().contains("" + processingTasklen));
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Not consumed", 0, con.getUnProcessed(), 0);
		con = new ResourceConsumption(processingTasklen,
				ResourceConsumption.unlimitedProcessing, null, offer,
				new ConsumptionEventAssert());
		con.registerConsumption();
		Assert.assertFalse(
				"Should not be possible to register without consumer",
				con.isRegistered());
		con = new ResourceConsumption(processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, null,
				new ConsumptionEventAssert());
		con.registerConsumption();
		Assert.assertFalse(
				"Should not be possible to register without provider",
				con.isRegistered());
	}

	@Test(expected = IllegalStateException.class, timeout = 100)
	public void testNullEvent() {
		// Below we should not receive a null pointer
		con = new ResourceConsumption(processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer, null);
		Assert.fail("Should not reach tis point because we asked for a consumption with a null event");
	}

	@Test(timeout = 100)
	public void zeroLenConsumption() {
		ConsumptionEventAdapter ce = new ConsumptionEventAssert();
		con = new ResourceConsumption(0,
				ResourceConsumption.unlimitedProcessing, utilize, offer, ce);
		Assert.assertFalse(
				"Consumption should not be complete before registration",
				ce.isCompleted());
		Assert.assertFalse(
				"Consumption should not be cancelled before registration",
				ce.isCancelled());
		con.registerConsumption();
		Assert.assertTrue(
				"Consumption should be complete immediately after registration",
				ce.isCompleted());
		Assert.assertFalse(
				"Consumption should not be cancelled before registration",
				ce.isCancelled());
	}

	private double preSuspendPhase() {
		Assert.assertTrue(
				"Should be able to register an unprocessed consumption",
				con.registerConsumption());
		double before = con.getUnProcessed();
		Timed.simulateUntil(Timed.getFireCount() + 500);
		return before;
	}

	private void pastSuspendChecks(double before) {
		double after = con.getUnProcessed();
		Assert.assertTrue("Should already have some consumption recorded",
				after != before);
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
		Assert.assertEquals("Not consumed the necessary amount", 0,
				con.getUnProcessed(), 0);
	}

	@Test(timeout = 100)
	public void cancelConsumption() {
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		con = createAUnitConsumption(cae);
		double before = preSuspendPhase();
		con.cancel();
		Timed.fire();
		pastSuspendChecks(before);
		Assert.assertFalse(
				"Should not be possible to register after cancellation",
				con.registerConsumption());
		long timeBefore = Timed.getFireCount();
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Cancellation arrived at improper time",
				timeBefore - 1, cae.getArrivedAt());
		Assert.assertTrue("There should be no events after cancellation",
				cae.getArrivedAt() + 1 >= Timed.getFireCount());
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
		Assert.assertTrue("It should not yet arrive",
				ConsumptionEventAssert.hits.isEmpty());
		Timed.fire();
		con.cancel();
		Timed.fire();
		// Ambiguous behavior! This needs to be thinked through
		Assert.assertFalse("It should arrive by now",
				ConsumptionEventAssert.hits.isEmpty());
	}

	@Test(timeout = 100)
	public void failedRegistrationTest() {
		Assert.assertFalse("Provider should not accept this consumption",
				new ResourceConsumption(100000,
						ResourceConsumption.unlimitedProcessing,
						new MaxMinProvider(1) {
							protected boolean isAcceptableConsumption(
									ResourceConsumption con) {
								return false;
							};
						}, utilize, new ConsumptionEventAssert())
						.registerConsumption());
		Assert.assertFalse("Consumer should not accept this consumption",
				new ResourceConsumption(100000,
						ResourceConsumption.unlimitedProcessing,
						new MaxMinConsumer(1) {
							@Override
							protected boolean isAcceptableConsumption(
									ResourceConsumption con) {
								return false;
							}
						}, utilize, new ConsumptionEventAssert())
						.registerConsumption());
	}

	@Test(timeout = 100)
	public void testLessPerformantProvider() {
		offer = new MaxMinProvider(permsProcessing / 2);
		con = new ResourceConsumption(processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer,
				new ConsumptionEventAssert(Timed.getFireCount()
						+ (long) (processingTasklen / offer
								.getPerTickProcessingPower())));
		con.registerConsumption();
		Timed.simulateUntilLastEvent();
	}
	
	@Test(timeout = 100)
	public void testConsumptionState() {
		ResourceConsumption restored;
		ResourceConsumption.ConsumptionState state;
		
		con = createAUnitConsumption(null);
		con.setProvider(null);
		con.setConsumer(null);
		restored = con.getConsumptionState().restore(false);
		Assert.assertFalse(
				"A nonregistered consumption should be nonregistered when restored", 
				restored.isRegistered());
		Assert.assertTrue(
				"A resumable consumption should be resumable when restored",
				restored.isResumable());
		Assert.assertEquals(
				"The unprocessed amount of resources should be the same after restoring",
				con.getUnProcessed(),restored.getUnProcessed(),1e-4);
		Assert.assertNull(
				"If the provider was null, the restored consumption's provider should be null, too", 
				restored.getProvider());
		Assert.assertNull(
				"If the consumer was null, the restored consumption's consumer should be null, too",
				restored.getConsumer());
		Assert.assertEquals(
				"The restored hard limit should match the original",
				con.getHardLimit(), restored.getHardLimit(),1e-4);
		
		state = con.getConsumptionState();
		con.setProvider(offer);
		Assert.assertNotEquals(
				"The consumer must return a new state when the provider is changed",
				con.getConsumptionState(), state);
		
		state = con.getConsumptionState();
		con.setConsumer(utilize);
		Assert.assertNotEquals(
				"The consumer must return a new state when the consumer is changed",
				con.getConsumptionState(), state);
		
		state = con.getConsumptionState();
		con.registerConsumption();
		Assert.assertNotEquals(
				"The consumer must return a new state after registering",
				con.getConsumptionState(), state);
		Assert.assertTrue(
				"The restored consumer should be registered if the original was",
				con.getConsumptionState().restore(false).isRegistered());
		
		state = con.getConsumptionState();
		con.suspend();
		Assert.assertNotEquals(
				"The consumer must return a new state after suspending",
				con.getConsumptionState(), state);
		
		state = con.getConsumptionState();
		con.registerConsumption();
		
		ResourceConsumption con2 = new ResourceConsumption(
				processingTasklen/2,
				ResourceConsumption.unlimitedProcessing,
				utilize,
				offer,
				new ConsumptionEventAdapter());
		con2.registerConsumption();	
	
		restored = con.getConsumptionState().restore(false);
		// Simulate until con2 finishes
		Timed.simulateUntil(
				Math.round(Timed.getFireCount() + processingTasklen/(2*permsProcessing)));
		
		Assert.assertEquals(
				"The restored consumption should process the same amount of " +
		        "resources in the same environment as the original consumption",
		        con.getUnProcessed(), restored.getUnProcessed(), 1e-4);
		
		restored.cancel();
		restored = restored.getConsumptionState().restore(false);
		Assert.assertFalse(
				"The restored consumption should not be registered if the original got canceled",
		        restored.isRegistered());
		Assert.assertFalse(
				"The restored consumption should not be resumable if the original got canceled",
				restored.isResumable());
		
		/* Check if we can get the valid state after processing started */
		Timed.fire();	
		restored = con.getConsumptionState().restore(false);
		// Simulate until con finishes
		Timed.simulateUntilLastEvent();
		Assert.assertEquals(
				"The restored state should be valid when accessed without explicitly " +
				"executing the spreaders processing functions", 
				con.getUnProcessed(), restored.getUnProcessed(), 1e-4);
		
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		con = this.createAUnitConsumption(cae);
		con.registerConsumption();
		restored = con.getConsumptionState().restore(true);
		con.suspend();
		Timed.simulateUntil(Timed.getFireCount() + Math.round(processingTasklen/permsProcessing));
		Assert.assertTrue(
				"The restored consumption should fire the same event as the original one",
				cae.isCompleted());
	}
}
