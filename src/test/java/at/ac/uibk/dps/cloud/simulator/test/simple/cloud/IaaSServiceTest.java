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
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService.IaaSHandlingException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import java.util.ArrayList;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;

public class IaaSServiceTest extends IaaSRelatedFoundation {
	ArrayList<IaaSService> services = new ArrayList<IaaSService>();

	@Before
	public void resetSim() throws Exception {
		services = getNewServiceArray();
	}

	@Test(timeout = 100)
	public void repoRegistrationTest() throws IaaSHandlingException {
		for (IaaSService iaas : services) {
			Assert.assertTrue(
					"Should not have any repositories before registering one",
					iaas.repositories.isEmpty());
			iaas.registerRepository(dummyRepoCreator(true));
			Assert.assertFalse("Should have got a repository registered",
					iaas.repositories.isEmpty());
			iaas.deregisterRepository(iaas.repositories.get(0));
			Assert.assertTrue(
					"Should not have any repositories after deregistering the last",
					iaas.repositories.isEmpty());
		}
	}

	@Test(timeout = 100)
	public void faultyRegistrationTest() {
		ArrayList<Exception> exs = new ArrayList<Exception>();
		for (IaaSService iaas : services) {
			try {
				iaas.deregisterHost(dummyPMcreator());
			} catch (Exception e) {
				exs.add(e);
			}
		}
		Assert.assertEquals(
				"None of the faulty PM deregistrations should get through",
				services.size(), exs.size());

	}

	@Test(timeout = 100)
	public void capacityMaintenanceTest() throws IaaSHandlingException {
		for (IaaSService iaas : services) {
			final PhysicalMachine pm = dummyPMcreator();
			ResourceConstraints beforeCapacities = iaas.getCapacities();
			iaas.registerHost(pm);
			ResourceConstraints midCapacities = iaas.getCapacities();
			Assert.assertTrue(
					"After registration the iaas should have more capacities",
					midCapacities.compareTo(ResourceConstraints.add(
							beforeCapacities, pm.getCapacities())) == 0);
			iaas.deregisterHost(pm);
			ResourceConstraints afterCapacities = iaas.getCapacities();
			Assert.assertTrue(
					"After deregistration the iaas should have the same capacities as before the registration took place",
					afterCapacities.compareTo(beforeCapacities) == 0);
		}
		Timed.simulateUntilLastEvent();
	}

	abstract class Capchanger implements IaaSService.CapacityChangeEvent {
		public int fired = 0;
		public PhysicalMachine pm = dummyPMcreator();

		@Override
		public void capacityChanged(ResourceConstraints newCapacity) {
			doAssertion(newCapacity);
			fired++;
			Assert.assertTrue(
					"Should not receive events for the same capacity more than once",
					fired < 2);
		}

		protected abstract void doAssertion(ResourceConstraints newCapacity);
	}

	@Test(timeout = 100)
	public void capacityIncreaseSubscriptionTest() throws IaaSHandlingException {
		ArrayList<Capchanger> ccs = new ArrayList<Capchanger>();
		for (int i = 0; i < services.size(); i++) {
			ccs.add(new Capchanger() {
				protected void doAssertion(ResourceConstraints newCapacity) {
					Assert.assertTrue(
							"Should receive capacity update with the just registered PM's size",
							pm.getCapacities().compareTo(newCapacity) == 0);
				}
			});
		}

		int i = 0;
		for (IaaSService iaas : services) {
			Capchanger cs = ccs.get(i++);
			iaas.subscribeToCapacityChanges(cs);
			iaas.registerHost(cs.pm);
			Assert.assertTrue(
					"The registered PM should be listed in the IaaS description",
					iaas.toString().contains(cs.pm.toString()));
			iaas.unsubscribeFromCapacityChanges(cs);
			iaas.deregisterHost(cs.pm);
			Assert.assertEquals(
					"Capacity incresed event should have arrived once", 1,
					cs.fired);
		}
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void capacityDecreaseSubscriptionTest() throws IaaSHandlingException {
		ArrayList<Capchanger> ccs = new ArrayList<Capchanger>();
		for (int i = 0; i < services.size(); i++) {
			ccs.add(new Capchanger() {
				protected void doAssertion(ResourceConstraints newCapacity) {
					Assert.assertTrue(
							"Should receive capacity update with no further capacities remaining",
							newCapacity.requiredCPUs == 0);
				}
			});
		}

		int i = 0;
		for (IaaSService iaas : services) {
			Capchanger cs = ccs.get(i++);
			iaas.registerHost(cs.pm);
			iaas.subscribeToCapacityChanges(cs);
			iaas.deregisterHost(cs.pm);
			iaas.unsubscribeFromCapacityChanges(cs);
			Assert.assertFalse(
					"The registered PM should not be listed in the IaaS description",
					iaas.toString().contains(cs.pm.toString()));
			Assert.assertEquals(
					"Capacity incresed event should have arrived once", 1,
					cs.fired);
		}
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void runningCapacityMaintananceTest() {
		for (IaaSService iaas : services) {
			iaas.registerHost(dummyPMcreator());
			Assert.assertTrue(
					"A newly created IaaS should not have any running capacities",
					iaas.getRunningCapacities().requiredCPUs == 0);
		}
		Timed.simulateUntilLastEvent();
		int runningPMCount = 0;
		int alwaysRunningSchCount = 0;
		for (IaaSService iaas : services) {
			runningPMCount += iaas.runningMachines.size();
			if (iaas.pmcontroller instanceof AlwaysOnMachines) {
				alwaysRunningSchCount++;
				Assert.assertEquals(
						"This iaas should have running PMs already as it switches them on immediately after registration and keeps them running",
						dummyPMCoreCount,
						(long) iaas.getRunningCapacities().requiredCPUs);
			} else {
				Assert.assertEquals(
						"This iaas should not have running PMs yet as it did not receive VMs so far",
						0, (long) iaas.getRunningCapacities().requiredCPUs);
			}
		}
		Assert.assertEquals(
				"Only IaaSs with alwaysrunning pms should have running capacities",
				alwaysRunningSchCount, runningPMCount);
	}

	private void constructMinimalIaaS() {
		for (IaaSService iaas : services) {
			iaas.registerHost(dummyPMcreator());
			iaas.registerRepository(dummyRepoCreator(true));
		}
	}

	private ArrayList<VirtualMachine> requestVMs()
			throws VMManagementException, NetworkException {
		ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>();
		for (IaaSService iaas : services) {
			vms.add(iaas.requestVM((VirtualAppliance) iaas.repositories.get(0)
					.contents().iterator().next(), iaas.getCapacities(),
					iaas.repositories.get(0), 1)[0]);
		}
		return vms;
	}

	@Test(timeout = 100)
	public void vmRequestTest() throws VMManagementException, NetworkException {
		constructMinimalIaaS();
		ArrayList<VirtualMachine> vms = requestVMs();
		Timed.simulateUntilLastEvent();
		int runningvms = 0;
		for (VirtualMachine vm : vms) {
			runningvms += vm.getState() == VirtualMachine.State.RUNNING ? 1 : 0;
		}
		Assert.assertEquals("All IaaSs should have a running VM",
				services.size(), runningvms);
	}

	@Test(timeout = 100)
	public void vmTerminateTest() throws VMManagementException,
			NetworkException {
		constructMinimalIaaS();
		ArrayList<VirtualMachine> vms = requestVMs();
		Timed.simulateUntilLastEvent();
		int i = 0;
		for (IaaSService iaas : services) {
			iaas.terminateVM(vms.get(i++), false);
		}
		Timed.simulateUntilLastEvent();
		int runningMachineCount = 0;
		int aomCount = 0;
		for (IaaSService iaas : services) {
			runningMachineCount += iaas.runningMachines.size();
			aomCount += iaas.pmcontroller instanceof AlwaysOnMachines ? 1 : 0;
		}
		Assert.assertEquals(
				"Only the iaas's that use alwaysOnMachines policy should have actual running PMs",
				aomCount, runningMachineCount);
		int shutDownVMs = 0;
		for (VirtualMachine vm : vms) {
			shutDownVMs += vm.getState() == VirtualMachine.State.SHUTDOWN ? 1
					: 0;
		}
		Assert.assertEquals("All VMs should be destroyed by now", vms.size(),
				shutDownVMs);
	}

	@Test(timeout = 100)
	public void notRunVMTerminationTest() {
		ArrayList<Exception> exs = new ArrayList<Exception>();
		constructMinimalIaaS();
		for (IaaSService iaas : services) {
			try {
				iaas.terminateVM(new VirtualMachine(new VirtualAppliance(
						"FAULTY", 10, 10)), true);
			} catch (VMManagementException e) {
				exs.add(e);
			}
		}
		Assert.assertEquals(
				"Not all IaaSs rejected the VM termination request",
				services.size(), exs.size());
	}

	@Test(timeout = 100)
	public void crossVMFaultyTerminationTest() throws VMManagementException,
			NetworkException {
		constructMinimalIaaS();
		ArrayList<VirtualMachine> vms = requestVMs();
		Timed.simulateUntilLastEvent();
		int i = services.size() - 1;
		ArrayList<Exception> exs = new ArrayList<Exception>();
		for (IaaSService iaas : services) {
			try {
				iaas.terminateVM(vms.get(i--), false);
			} catch (VMManagementException e) {
				exs.add(e);
			}
		}
		Assert.assertEquals(
				"Not all IaaSs rejected the VM termination request",
				(services.size() % 2 == 1) ? services.size() - 1 : services
						.size(), exs.size());
	}

	@Test(timeout = 100)
	public void beforeRegistrationVMCreationTest() throws NetworkException {
		ArrayList<Exception> exs = new ArrayList<Exception>();
		for (IaaSService iaas : services) {
			try {
				iaas.requestVM(new VirtualAppliance("VERYFAULTY", 1, 0),
						dummyPMcreator().getCapacities(),
						dummyRepoCreator(true), 1);
			} catch (VMManagementException e) {
				exs.add(e);
			}
		}
		Assert.assertEquals(
				"Not all IaaSs rejected the VM termination request",
				services.size(), exs.size());
	}

	@Test(timeout = 100)
	public void vmListingTest() throws VMManagementException, NetworkException {
		constructMinimalIaaS();
		HashSet<VirtualMachine> vms = new HashSet<VirtualMachine>(requestVMs());
		Timed.simulateUntilLastEvent();
		HashSet<VirtualMachine> reportedvms = new HashSet<VirtualMachine>();
		for (IaaSService iaas : services) {
			reportedvms.addAll(iaas.listVMs());
		}
		Assert.assertTrue("All running VMs should be reported by IaaSs",
				vms.containsAll(reportedvms) && reportedvms.containsAll(vms));
	}

	@Ignore
	@Test(timeout = 100)
	public void deregisterHostWithMigration() throws VMManagementException,
			NetworkException, IaaSHandlingException {
		constructMinimalIaaS();
		final ArrayList<Integer> markers = new ArrayList<Integer>();
		for (IaaSService iaas : services) {
			iaas.registerHost(dummyPMcreator());
			Repository repo = iaas.repositories.get(0);
			final VirtualMachine vm = iaas.requestVM((VirtualAppliance) repo
					.contents().iterator().next(), iaas.machines.get(0)
					.getCapacities(), repo, 1)[0];
			vm.subscribeStateChange(new VirtualMachine.StateChange() {
				@Override
				public void stateChanged(State oldState, State newState) {
					if (newState.equals(VirtualMachine.State.RUNNING)) {
						markers.add(vm.hashCode());
					}
				}
			});
		}
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("By now all VMs should have been started",
				services.size(), markers.size());
		for (IaaSService iaas : services) {
			iaas.deregisterHost(iaas.runningMachines.get(0));
		}
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("By now all VMs should have been resumed",
				services.size() * 2, markers.size());
	}

}
