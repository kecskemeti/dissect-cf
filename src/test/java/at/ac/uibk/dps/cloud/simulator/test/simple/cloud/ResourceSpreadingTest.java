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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinConsumer;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinProvider;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;

public class ResourceSpreadingTest extends ConsumptionEventFoundation {
	MaxMinProvider offer;
	MaxMinConsumer utilize;

	@Before
	public void setup() {
		offer = new MaxMinProvider(ResourceConsumptionTest.permsProcessing);
		utilize = new MaxMinConsumer(ResourceConsumptionTest.permsProcessing);
	}

	@Test(timeout = 100)
	public void constructionTest() {
		Assert.assertTrue("Provider resource tostring failure", offer.toString().contains("Provider"));
		Assert.assertTrue("Consumer resource tostring failure", utilize.toString().contains("Consumer"));
		Assert.assertEquals("Persecond processing power is not correct", ResourceConsumptionTest.permsProcessing,
				offer.getPerTickProcessingPower(), 0);
		Assert.assertEquals("Total processed should be zero in just initialized resource spreaders", 0,
				offer.getTotalProcessed(), 0);
	}

	@Test(timeout = 100)
	public void registrationTest() {
		ResourceConsumption con1 = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer, new ConsumptionEventAssert());
		ResourceConsumption con2 = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer, new ConsumptionEventAssert());
		ResourceConsumption conFaulty = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer, new ConsumptionEventAssert());
		conFaulty.suspend();
		con1.registerConsumption();
		con2.registerConsumption();
		Assert.assertEquals("The next event should come for offer", offer.getSyncer().getNextEvent(),
				Timed.getNextFire());
		Timed.fire();
		Timed.simulateUntil(Timed.getFireCount() - 1 + offer.getSyncer().getFrequency() / 2);
		Assert.assertEquals("The next event should come for offer", offer.getSyncer().getNextEvent(),
				Timed.getNextFire());
		conFaulty.suspend();
		con1.suspend();
		conFaulty.suspend();
		con2.suspend();
		double offerProcessed = offer.getTotalProcessed();
		double utilizeProcessed = utilize.getTotalProcessed();
		Timed.fire();
		Assert.assertEquals("The consumption did not reach the necessary level on the offer side",
				ResourceConsumptionTest.processingTasklen, offerProcessed, 0);
		Assert.assertEquals("The consumption did not reach the necessary level on the utilize side",
				ResourceConsumptionTest.processingTasklen, utilizeProcessed, 0);
		Assert.assertEquals("The next event should come for no one", -1, Timed.getNextFire());
		conFaulty.suspend();
		Assert.assertEquals("The next event should come for no one", -1, Timed.getNextFire());
		Assert.assertEquals("A faulty deregistration should not change consumption on the offer side", offerProcessed,
				offer.getTotalProcessed(), 0);
		Assert.assertEquals("A faulty deregistration should not change consumption on the utilize side",
				utilizeProcessed, utilize.getTotalProcessed(), 0);
	}

	@Test(timeout = 100)
	public void basicConsumptionTest() {
		ResourceConsumption con1 = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer, new ConsumptionEventAssert());
		ResourceConsumption con2 = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer, new ConsumptionEventAssert());
		con1.registerConsumption();
		con2.registerConsumption();
		Timed.simulateUntil(Timed.getFireCount() + 2000);
		Assert.assertEquals("Consumptions should have been finished", 0, con1.getUnProcessed() + con2.getUnProcessed(),
				0);
	}

	@Test(timeout = 100)
	public void eventRelationTest() {
		ConsumptionEventAdapter cea1 = new ConsumptionEventAssert();
		ConsumptionEventAdapter cea2 = new ConsumptionEventAssert();
		ResourceConsumption con1 = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer, cea1);
		ResourceConsumption con2 = new ResourceConsumption(ResourceConsumptionTest.processingTasklen * 2,
				ResourceConsumption.unlimitedProcessing, utilize, offer, cea2);
		con1.registerConsumption();
		con2.registerConsumption();
		long timeBefore = Timed.getFireCount();
		Timed.simulateUntil(timeBefore + 2000);
		long timeMiddle = Timed.getFireCount();
		Assert.assertTrue("The smaller consumption should finish faster", cea1.isCompleted());
		Assert.assertFalse("The larger consumption should not finish faster", cea2.isCompleted());
		Timed.simulateUntil(offer.getSyncer().getNextEvent());
		long timeAfter = Timed.getFireCount();
		Assert.assertTrue("The larger consumption should finish later", cea2.isCompleted());
		Assert.assertEquals(
				"The execution time of the second part of the larger consumption should take half the time of the smaller consumption's execution time",
				(timeAfter - timeMiddle) * 2, timeMiddle - timeBefore - 1);
	}

	@Test(timeout = 100)
	public void simpleAssimetricProcessing() {
		utilize = new MaxMinConsumer(ResourceConsumptionTest.permsProcessing / 2);
		ResourceConsumption con = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer, new ConsumptionEventAssert());
		con.registerConsumption();
		long before = Timed.getFireCount();
		Timed.simulateUntil(before + 2000);
		Assert.assertEquals("Processing should take twice as long as usually",
				(long) (ResourceConsumptionTest.processingTasklen / utilize.getPerTickProcessingPower()),
				Timed.getFireCount() - before - 1);
	}

	@Test(timeout = 100)
	public void multiPartyProcessing() {
		ConsumptionEventAssert c1Ev = new ConsumptionEventAssert();
		ConsumptionEventAssert c2Ev = new ConsumptionEventAssert();
		ConsumptionEventAssert c3Ev = new ConsumptionEventAssert();
		ConsumptionEventAssert c4Ev = new ConsumptionEventAssert();
		offer = new MaxMinProvider(ResourceConsumptionTest.permsProcessing * 2);
		MaxMinProvider of2 = new MaxMinProvider(ResourceConsumptionTest.permsProcessing / 2);
		MaxMinConsumer ut2 = new MaxMinConsumer(ResourceConsumptionTest.permsProcessing / 3);
		ResourceConsumption c1 = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer, c1Ev);
		ResourceConsumption c2 = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, ut2, of2, c2Ev);
		ResourceConsumption c3 = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, ut2, offer, c3Ev);
		ResourceConsumption c4 = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, of2, c4Ev);
		long startTime = Timed.getFireCount();
		c1.registerConsumption();
		Timed.simulateUntil(Timed.getFireCount() + 10);
		c2.registerConsumption();
		Timed.simulateUntil(Timed.getFireCount() + 10);
		c3.registerConsumption();
		Timed.simulateUntil(Timed.getFireCount() + 10);
		c4.registerConsumption();
		Timed.fire();
		Assert.assertFalse("We should already have some processing done by this time",
				c1.getUnProcessed() == ResourceConsumptionTest.processingTasklen);
		Assert.assertFalse("We should already have some processing done by this time",
				c2.getUnProcessed() == ResourceConsumptionTest.processingTasklen);
		Assert.assertFalse("We should already have some processing done by this time",
				c3.getUnProcessed() == ResourceConsumptionTest.processingTasklen);
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("c1 did not complete", c1Ev.isCompleted());
		Assert.assertTrue("c2 did not complete", c2Ev.isCompleted());
		Assert.assertTrue("c3 did not complete", c3Ev.isCompleted());
		Assert.assertTrue("c4 did not complete", c4Ev.isCompleted());
		Assert.assertEquals("c1 finished at improper time", 1485, c1Ev.getArrivedAt() - startTime);
		Assert.assertEquals("c2 finished at improper time", 6000, c2Ev.getArrivedAt() - startTime);
		Assert.assertEquals("c3 finished at improper time", 6010, c3Ev.getArrivedAt() - startTime);
		Assert.assertEquals("c4 finished at improper time", 3030, c4Ev.getArrivedAt() - startTime);
	}

	@Test(timeout = 100)
	public void basicLimitedProcessing() {
		ResourceConsumption c = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumptionTest.permsProcessing / 2, utilize, offer, new ConsumptionEventAssert());
		c.registerConsumption();
		long before = Timed.getFireCount();
		Timed.simulateUntilLastEvent();
		long after = Timed.getFireCount();
		Assert.assertEquals("Resource consumption should be limited",
				(long) (ResourceConsumptionTest.processingTasklen / (offer.getPerTickProcessingPower() / 2)),
				after - before - 1);
	}

	@Test(timeout = 100)
	public void mixedProcessingLimits() {
		ConsumptionEventAssert evLimited = new ConsumptionEventAssert();
		ConsumptionEventAssert evUnlimited = new ConsumptionEventAssert();
		ResourceConsumption cLimited = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumptionTest.permsProcessing / 2, utilize, offer, evLimited);
		ResourceConsumption cUnlimited = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer, evUnlimited);
		cLimited.registerConsumption();
		cUnlimited.registerConsumption();
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("All consumptions should complete successfully",
				evLimited.isCompleted() && evUnlimited.isCompleted());
		Assert.assertEquals(
				"Consumption limits should not influnece arrival time, both consumptions should finish at the same time",
				evLimited.getArrivedAt(), evUnlimited.getArrivedAt());

		evLimited = new ConsumptionEventAssert();
		evUnlimited = new ConsumptionEventAssert();
		cLimited = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumptionTest.permsProcessing / 3, utilize, offer, evLimited);
		cUnlimited = new ResourceConsumption(ResourceConsumptionTest.processingTasklen,
				ResourceConsumption.unlimitedProcessing, utilize, offer, evUnlimited);
		cLimited.registerConsumption();
		cUnlimited.registerConsumption();
		long before = Timed.getFireCount();
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("All consumptions should complete successfully",
				evLimited.isCompleted() && evUnlimited.isCompleted());
		Assert.assertEquals("Limits should cause runtime increase for limited consumption",
				(long) (ResourceConsumptionTest.processingTasklen / (offer.getPerTickProcessingPower() / 3)),
				evLimited.getArrivedAt() - before);
		Assert.assertEquals("Limits should cause runtime reduction for unlimited consumption",
				(long) (ResourceConsumptionTest.processingTasklen / (2 * offer.getPerTickProcessingPower() / 3)),
				evUnlimited.getArrivedAt() - before);
	}

	private ResourceConsumption crCons(ResourceConsumption.ConsumptionEvent ev) {
		return new ResourceConsumption(1, ResourceConsumption.unlimitedProcessing, utilize, offer,
				ev == null ? new ConsumptionEventAssert() : ev);
	}

	@Test(timeout = 100)
	public void immediateConsumptionFollowup() {
		// Immediate
		long startTime = Timed.getFireCount();
		ResourceConsumption first = crCons(new ConsumptionEventAssert() {
			@Override
			public void conComplete() {
				super.conComplete();
				ResourceConsumption second = crCons(null);
				second.registerConsumption();
			}
		});
		first.registerConsumption();
		Timed.simulateUntilLastEvent();
		first = crCons(null);
		first.registerConsumption();
		Timed.simulateUntilLastEvent();
		List<Long> l = new ArrayList<Long>(ConsumptionEventAssert.hits);
		Collections.sort(l);
		long beforeImmediate = l.get(0);
		long afterImmediate = l.get(1);
		long afterSingleCons = l.get(2) - 1;
		Assert.assertEquals("Consumption before its immediate followup is not with the right timing",
				afterSingleCons - afterImmediate, beforeImmediate - startTime);
		Assert.assertEquals("Immediate consumption followup not arriving with the right timing",
				afterSingleCons - afterImmediate, afterImmediate - beforeImmediate);
	}

	@Test(timeout = 100)
	public void groupManagement() {
		MaxMinProvider prov1 = new MaxMinProvider(1);
		MaxMinProvider prov2 = new MaxMinProvider(1);
		MaxMinConsumer cons2 = new MaxMinConsumer(1);

		new ResourceConsumption(1000, 1, new MaxMinConsumer(1), prov1, new ConsumptionEventAssert())
				.registerConsumption();
		new ResourceConsumption(500, 1, cons2, prov1, new ConsumptionEventAssert()).registerConsumption();
		new ResourceConsumption(1000, 1, cons2, prov2, new ConsumptionEventAssert()).registerConsumption();
		new ResourceConsumption(1000, 1, new MaxMinConsumer(1), prov2, new ConsumptionEventAssert())
				.registerConsumption();
		Timed.simulateUntilLastEvent();

	}
}
