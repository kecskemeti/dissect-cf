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

package at.ac.uibk.dps.cloud.simulator.test.simple.cloud.pmscheduler;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.NonQueueingScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;

public class AlwaysOnMachinesTest extends IaaSRelatedFoundation {
	IaaSService basic;
	Repository r;

	@Before
	public void resetSim() throws Exception {
		basic = new IaaSService(NonQueueingScheduler.class,
				AlwaysOnMachines.class);
		basic.registerHost(dummyPMcreator());
		basic.registerHost(dummyPMcreator());
		r = dummyRepoCreator(true);
		basic.registerRepository(r);
	}

	@Test(timeout = 100)
	public void turnonTest() {
		Assert.assertEquals(
				"Even alwayson should not have machines running at the beginning of the simulation",
				0, basic.runningMachines.size());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Did not switch on all machines as expected",
				basic.machines.size(), basic.runningMachines.size());
	}

	@Test(timeout = 100)
	public void additionTest() {
		Timed.simulateUntilLastEvent();
		basic.registerHost(dummyPMcreator());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Did not switch on all machines as expected",
				basic.machines.size(), basic.runningMachines.size());
	}

	@Test(timeout = 100)
	public void vmCreationTest() throws VMManagementException, NetworkException {
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Did not switch on all machines as expected",
				basic.machines.size(), basic.runningMachines.size());
		VirtualMachine vm = basic.requestVM((VirtualAppliance) r.contents()
				.iterator().next(), basic.machines.get(0)
				.getCapacities(), r, 1)[0];
		Timed.simulateUntilLastEvent();
		Assert.assertEquals(VirtualMachine.State.RUNNING, vm.getState());
		Assert.assertEquals(
				"An arriving VM should not change the number of running PMs",
				basic.machines.size(), basic.runningMachines.size());
		vm.destroy(false);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals(
				"A departing VM should not change the number of runnning PMs",
				basic.machines.size(), basic.runningMachines.size());
	}

	@Test(timeout = 100)
	public void prematureVMCreationTest() throws VMManagementException,
			NetworkException {
		VirtualMachine vm = basic.requestVM((VirtualAppliance) r.contents()
				.iterator().next(), basic.machines.get(0)
				.getCapacities(), r, 1)[0];
		Timed.simulateUntilLastEvent();
		Assert.assertEquals(
				"A premature VM arrival should not disturb the PM startup processes",
				basic.machines.size(), basic.runningMachines.size());
		Assert.assertEquals(VirtualMachine.State.RUNNING, vm.getState());
		vm.destroy(false);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals(
				"A departing VM should not change the number of runnning PMs",
				basic.machines.size(), basic.runningMachines.size());
	}
}
