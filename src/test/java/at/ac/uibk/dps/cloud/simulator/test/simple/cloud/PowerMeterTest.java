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
import hu.mta.sztaki.lpds.cloud.simulator.energy.PowerMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.ConstantConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.LinearConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import org.junit.Assert;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;

public class PowerMeterTest extends IaaSRelatedFoundation {

	@Test(timeout = 100)
	public void VMmeasurementTest() throws VMManagementException,
			NetworkException {
		PhysicalMachine pm = dummyPMcreator();
		Repository repo = dummyRepoCreator(true);
		pm.turnon();
		Timed.simulateUntilLastEvent();
		VirtualMachine vm = pm.requestVM((VirtualAppliance) repo.contents()
				.iterator().next(), pm.getCapacities(), repo, 1)[0];
		Timed.simulateUntilLastEvent();
		final double idlepower = 200;
		final double maxpower = 300;
		PowerMeter meter = new PowerMeter(vm, idlepower, maxpower);
		meter.startMeter(100, true);
		Timed.simulateUntil(Timed.getFireCount() + 1000);
		Assert.assertEquals(
				"The idle machine is not consuming as much as expected",
				idlepower, meter.getTotalConsumption(), 0.1);
		long before = Timed.getFireCount();
		meter.stopMeter();
		Timed.simulateUntilLastEvent();
		long after = Timed.getFireCount();
		Assert.assertEquals(
				"Should not be any events if a meter is not executing", before,
				after);
		meter.startMeter(1000, true);
		Timed.simulateUntil(Timed.getFireCount() + 1000);
		meter.stopMeter();
		Assert.assertEquals(
				"The idle machine's consumption should not depend on the measurement frequency of the meter",
				idlepower, meter.getTotalConsumption(), 0.1);
		meter.startMeter(100, true);
		ResourceConstraints rc = vm.getResourceAllocation().allocated;
		final int taskleninsecs = 10;
		vm.newComputeTask(rc.requiredCPUs * rc.requiredProcessingPower
				* taskleninsecs, ResourceConsumption.unlimitedProcessing,
				new ConsumptionEventAssert());
		while (ConsumptionEventAssert.hits.isEmpty()) {
			Timed.jumpTime(Long.MAX_VALUE);
			Timed.fire();
		}
		Assert.assertEquals(
				"The consumption is not properly reported if there is a task processed",
				taskleninsecs * maxpower, meter.getTotalConsumption(), 0.1);
		meter.stopMeter();
		meter.startMeter(100, true);
		vm.newComputeTask(rc.requiredCPUs * rc.requiredProcessingPower
				* taskleninsecs, rc.requiredProcessingPower * 0.5,
				new ConsumptionEventAssert());
		Timed.simulateUntil(Timed.getFireCount() + taskleninsecs * 1000);
		Assert.assertEquals(
				"The consumption is not properly reported if there is a task processed",
				taskleninsecs * (0.5 * (maxpower - idlepower) + idlepower),
				meter.getTotalConsumption(), 0.1);
		meter.stopMeter();
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void PSTest() throws Exception {
		PowerState psLinear = new PowerState(1, 1, LinearConsumptionModel.class);
		PowerState psConstant = new PowerState(1, 1,
				ConstantConsumptionModel.class);
		Assert.assertEquals(
				"Linear consumption model is not behaving as expected", 1.5,
				psLinear.getCurrentPower(0.5), 0.001);
		Assert.assertEquals(
				"Constant consumption model is not behaving as expected", 1,
				psConstant.getCurrentPower(0.5), 0.001);
	}
}
