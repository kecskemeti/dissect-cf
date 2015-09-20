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
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService.IaaSHandlingException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import java.util.HashSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;

public class SchedulerDependentTest extends IaaSRelatedFoundation {
	IaaSService basic;
	Repository repo;

	@Before
	public void resetSim() throws Exception {
		basic = setupIaaS(FirstFitScheduler.class,
				SchedulingDependentMachines.class, 2, 1);
		repo = basic.repositories.get(0);
	}

	@Test(timeout = 100)
	public void withoutLoadTest() {
		Assert.assertEquals(
				"Should not have machines running at the beginning of the simulation",
				0, basic.runningMachines.size());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals(
				"Should not switch any machine on because there is no load", 0,
				basic.runningMachines.size());
	}

	@Test(timeout = 100)
	public void simpleLoadTest() throws VMManagementException, NetworkException {
		final HashSet<PhysicalMachine> affectedpms = new HashSet<PhysicalMachine>();
		for (final PhysicalMachine pm : basic.machines) {
			pm.subscribeStateChangeEvents(new PhysicalMachine.StateChangeListener() {
				@Override
				public void stateChanged(PhysicalMachine pm, State oldState,
						State newState) {
					affectedpms.add(pm);
				}
			});
		}
		VirtualMachine vm = basic.requestVM((VirtualAppliance) repo.contents()
				.iterator().next(), basic.machines.get(0).getCapacities(),
				repo, 1)[0];
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should only affect a single PM", 1,
				affectedpms.size());
		Assert.assertEquals("Should only switch on a single PM", 1,
				basic.runningMachines.size());
		Assert.assertEquals("VM should be able to switch on",
				VirtualMachine.State.RUNNING, vm.getState());
		vm.destroy(false);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should switch off all PMs if there is no load", 0,
				basic.runningMachines.size());
	}

	@Test(timeout = 100)
	public void overLoadTest() throws VMManagementException, NetworkException {
		VirtualMachine[] vms = basic.requestVM((VirtualAppliance) repo
				.contents().iterator().next(), basic.machines.get(0)
				.getCapacities(), repo, basic.machines.size());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should switch all PMs", basic.machines.size(),
				basic.runningMachines.size());
		VirtualMachine extraVM = basic.requestVM((VirtualAppliance) repo
				.contents().iterator().next(), basic.machines.get(0)
				.getCapacities(), repo, 1)[0];
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should not allow the extra VM to run",
				VirtualMachine.State.DESTROYED, extraVM.getState());
		vms[0].destroy(false);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Now the extra VM should run",
				VirtualMachine.State.RUNNING, extraVM.getState());
		for (VirtualMachine vm : vms) {
			vm.destroy(false);
		}
		extraVM.destroy(false);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should switch off all PMs if there is no load", 0,
				basic.runningMachines.size());
	}

	@Test(timeout = 100)
	public void smallVMLoadTest() throws VMManagementException,
			NetworkException {
		AlterableResourceConstraints rc = new AlterableResourceConstraints(
				basic.machines.get(0).getCapacities());
		rc.multiply(0.5);
		VirtualMachine[] vms = basic.requestVM((VirtualAppliance) repo
				.contents().iterator().next(), rc, repo, 2);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should only switch on a single PM", 1,
				basic.runningMachines.size());
		vms[0].destroy(false);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should keep the single PM running", 1,
				basic.runningMachines.size());
		Assert.assertArrayEquals(
				"VMs not in the expected states",
				new VirtualMachine.State[] { VirtualMachine.State.DESTROYED,
						VirtualMachine.State.RUNNING },
				new VirtualMachine.State[] { vms[0].getState(),
						vms[1].getState() });
		vms[1].destroy(false);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should switch off all PMs if there is no load", 0,
				basic.runningMachines.size());
	}

	@Test(timeout = 100)
	public void decreasingCapacityTest() throws IaaSHandlingException,
			VMManagementException, NetworkException {
		basic.deregisterHost(basic.machines.get(0));
		VirtualMachine vm = basic.requestVM((VirtualAppliance) repo.contents()
				.iterator().next(), basic.machines.get(0).getCapacities(),
				repo, 1)[0];
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("VM should be able to switch on",
				VirtualMachine.State.RUNNING, vm.getState());
		vm.destroy(false);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should not have any running PMs", 0,
				basic.runningMachines.size());
	}

	@Test(timeout = 100)
	public void deregistrationTest() throws VMManagementException,
			NetworkException, IaaSHandlingException {
		PhysicalMachine pm = basic.machines.get(0);
		VirtualMachine[] vms = basic.requestVM((VirtualAppliance) repo
				.contents().iterator().next(), pm.getCapacities(), repo,
				basic.machines.size());
		Timed.simulateUntilLastEvent();
		for (VirtualMachine vm : vms) {
			vm.destroy(false);
		}
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should be switched off",
				PhysicalMachine.State.OFF, pm.getState());
		basic.deregisterHost(pm);
		pm.turnon();
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should be running", PhysicalMachine.State.RUNNING,
				pm.getState());

	}
}
