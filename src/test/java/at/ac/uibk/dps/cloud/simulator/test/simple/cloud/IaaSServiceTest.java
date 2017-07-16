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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService.IaaSHandlingException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.UnalterableConstraintsPropagator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

public class IaaSServiceTest extends IaaSRelatedFoundation {
	ArrayList<IaaSService> services = new ArrayList<IaaSService>();

	@Before
	public void resetSim() throws Exception {
		services = getNewServiceArray();
	}

	@Test(timeout = 100)
	public void repoRegistrationTest() throws IaaSHandlingException {
		for (IaaSService iaas : services) {
			// Repository management
			Assert.assertTrue("Should not have any repositories before registering one", iaas.repositories.isEmpty());
			iaas.registerRepository(dummyRepoCreator(true));
			Assert.assertFalse("Should have got a repository registered", iaas.repositories.isEmpty());
			iaas.deregisterRepository(iaas.repositories.get(0));
			Assert.assertTrue("Should not have any repositories after deregistering the last",
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
		Assert.assertEquals("None of the faulty PM deregistrations should get through", services.size(), exs.size());

	}

	@Test(timeout = 100)
	public void capacityMaintenanceTest() throws IaaSHandlingException {
		for (IaaSService iaas : services) {
			final PhysicalMachine pm = dummyPMcreator();
			AlterableResourceConstraints beforeCapacities = new AlterableResourceConstraints(iaas.getCapacities());
			beforeCapacities.singleAdd(pm.getCapacities());
			iaas.registerHost(pm);
			ResourceConstraints midCapacities = iaas.getCapacities();
			Assert.assertTrue("After registration the iaas should have more capacities",
					midCapacities.compareTo(beforeCapacities) == 0);
			beforeCapacities.subtract(pm.getCapacities());
			iaas.deregisterHost(pm);
			ResourceConstraints afterCapacities = iaas.getCapacities();
			Assert.assertTrue(
					"After deregistration the iaas should have the same capacities as before the registration took place",
					afterCapacities.compareTo(beforeCapacities) == 0);
		}
		Timed.simulateUntilLastEvent();
	}

	abstract class Capchanger implements IaaSService.CapacityChangeEvent<PhysicalMachine> {
		public int fired = 0;
		public PhysicalMachine pm = dummyPMcreator();

		@Override
		public void capacityChanged(ResourceConstraints newCapacity, List<PhysicalMachine> alteredPMs) {
			doAssertion(newCapacity);
			fired++;
			Assert.assertTrue("Should not receive events for the same capacity more than once", fired < 2);
		}

		protected abstract void doAssertion(ResourceConstraints newCapacity);
	}

	@Test(timeout = 100)
	public void capacityIncreaseSubscriptionTest() throws IaaSHandlingException {
		ArrayList<Capchanger> ccs = new ArrayList<Capchanger>();
		for (int i = 0; i < services.size(); i++) {
			ccs.add(new Capchanger() {
				protected void doAssertion(ResourceConstraints newCapacity) {
					Assert.assertTrue("Should receive capacity update with the just registered PM's size",
							pm.getCapacities().compareTo(newCapacity) == 0);
				}
			});
		}

		int i = 0;
		// Addition test
		for (IaaSService iaas : services) {
			// Proper PM registration
			Capchanger cs = ccs.get(i++);
			iaas.subscribeToCapacityChanges(cs);
			iaas.registerHost(cs.pm);
			Assert.assertTrue("The registered PM should be listed in the IaaS description",
					iaas.toString().contains(cs.pm.toString()));
			iaas.unsubscribeFromCapacityChanges(cs);
			iaas.deregisterHost(cs.pm);
			Assert.assertEquals("Capacity incresed event should have arrived once", 1, cs.fired);
		}
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void capacityDecreaseSubscriptionTest() throws IaaSHandlingException {
		ArrayList<Capchanger> ccs = new ArrayList<Capchanger>();
		for (int i = 0; i < services.size(); i++) {
			ccs.add(new Capchanger() {
				protected void doAssertion(ResourceConstraints newCapacity) {
					Assert.assertTrue("Should receive capacity update with no further capacities remaining",
							newCapacity.getRequiredCPUs() == 0);
				}
			});
		}

		int i = 0;
		// removal test
		for (IaaSService iaas : services) {
			// Proper PM registration
			Capchanger cs = ccs.get(i++);
			iaas.registerHost(cs.pm);
			iaas.subscribeToCapacityChanges(cs);
			iaas.deregisterHost(cs.pm);
			iaas.unsubscribeFromCapacityChanges(cs);
			Assert.assertFalse("The registered PM should not be listed in the IaaS description",
					iaas.toString().contains(cs.pm.toString()));
			Assert.assertEquals("Capacity incresed event should have arrived once", 1, cs.fired);
		}
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void runningCapacityMaintananceTest() {
		for (IaaSService iaas : services) {
			iaas.registerHost(dummyPMcreator());
			Assert.assertTrue("A newly created IaaS should not have any running capacities",
					iaas.getRunningCapacities().getRequiredCPUs() == 0);
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
						dummyPMCoreCount, (long) iaas.getRunningCapacities().getRequiredCPUs());
			} else {
				Assert.assertEquals("This iaas should not have running PMs yet as it did not receive VMs so far", 0,
						(long) iaas.getRunningCapacities().getRequiredCPUs());
			}
		}
		Assert.assertEquals("Only IaaSs with alwaysrunning pms should have running capacities", alwaysRunningSchCount,
				runningPMCount);
	}

	private void constructMinimalIaaS() {
		for (IaaSService iaas : services) {
			iaas.registerHost(dummyPMcreator());
			iaas.registerRepository(dummyRepoCreator(true));
		}
	}

	private ArrayList<VirtualMachine> requestVMs() throws VMManagementException, NetworkException {
		ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>();
		for (IaaSService iaas : services) {
			vms.add(iaas.requestVM((VirtualAppliance) iaas.repositories.get(0).contents().iterator().next(),
					iaas.getCapacities(), iaas.repositories.get(0), 1)[0]);
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
		Assert.assertEquals("All IaaSs should have a running VM", services.size(), runningvms);
	}

	@Test(timeout = 100)
	public void vmTerminateTest() throws VMManagementException, NetworkException {
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
		Assert.assertEquals("Only the iaas's that use alwaysOnMachines policy should have actual running PMs", aomCount,
				runningMachineCount);
		int shutDownVMs = 0;
		for (VirtualMachine vm : vms) {
			shutDownVMs += vm.getState() == VirtualMachine.State.SHUTDOWN ? 1 : 0;
		}
		Assert.assertEquals("All VMs should be destroyed by now", vms.size(), shutDownVMs);
	}

	@Test(timeout = 100)
	public void notRunVMTerminationTest() {
		ArrayList<Exception> exs = new ArrayList<Exception>();
		constructMinimalIaaS();
		for (IaaSService iaas : services) {
			try {
				iaas.terminateVM(new VirtualMachine(new VirtualAppliance("FAULTY", 10, 10)), true);
			} catch (VMManagementException e) {
				exs.add(e);
			}
		}
		Assert.assertEquals("Not all IaaSs rejected the VM termination request", services.size(), exs.size());
	}

	@Test(timeout = 100)
	public void crossVMFaultyTerminationTest() throws VMManagementException, NetworkException {
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
		Assert.assertEquals("Not all IaaSs rejected the VM termination request",
				(services.size() % 2 == 1) ? services.size() - 1 : services.size(), exs.size());
	}

	@Test(timeout = 100)
	public void beforeRegistrationVMCreationTest() throws NetworkException {
		final int slen = services.size();
		final ResourceConstraints pmcap = dummyPMcreator().getCapacities();
		final Repository repo = dummyRepoCreator(true);
		final VirtualAppliance va = new VirtualAppliance("VERYFAULTY", 1, 0);
		int excounter = 0;
		for (int i = 0; i < slen; i++) {
			try {
				services.get(i).requestVM(va, pmcap, repo, 1);
			} catch (VMManagementException e) {
				excounter++;
			}
		}
		Assert.assertEquals("Not all IaaSs rejected the VM creation request", slen, excounter);
	}

	@Test(timeout = 100)
	public void vmListingTest() throws VMManagementException, NetworkException {
		constructMinimalIaaS();
		ArrayList<VirtualMachine> vms = requestVMs();
		Timed.simulateUntilLastEvent();
		ArrayList<VirtualMachine> reportedvms = new ArrayList<VirtualMachine>();
		for (IaaSService iaas : services) {
			reportedvms.addAll(iaas.listVMs());
		}
		Assert.assertTrue("All running VMs should be reported by IaaSs",
				vms.containsAll(reportedvms) && reportedvms.containsAll(vms));
	}

	@Test(timeout = 100)
	public void vmQueuedListingTest() throws VMManagementException, NetworkException {
		constructMinimalIaaS();
		ArrayList<VirtualMachine> vms = requestVMs();
		vms.addAll(requestVMs()); // second unfulfillable vm set
		Timed.simulateUntilLastEvent();
		ArrayList<VirtualMachine> reportedvms = new ArrayList<VirtualMachine>(vms.size());
		for (IaaSService iaas : services) {
			reportedvms.addAll(iaas.listVMs());
		}
		Iterator<VirtualMachine> vmit = vms.iterator();
		int queuedCounter = 0;
		int runningCounter = 0;
		while (vmit.hasNext()) {
			switch (vmit.next().getState()) {
			case NONSERVABLE:
				// Nonservable VMs will not have a chance to be queued
				vmit.remove();
				break;
			case DESTROYED:
				queuedCounter++;
				break;
			case RUNNING:
				runningCounter++;
			default:
			}
		}
		Assert.assertTrue("Even queued VMs should be reported by IaaSs",
				vms.containsAll(reportedvms) && reportedvms.containsAll(vms));
		Assert.assertEquals("The count of queued and running VMs should match the num of VMs under or over scheduling",
				vms.size() - runningCounter, queuedCounter);
	}

	@Ignore
	@Test(timeout = 100)
	public void deregisterHostWithMigration() throws VMManagementException, NetworkException, IaaSHandlingException {
		constructMinimalIaaS();
		final ArrayList<Integer> markers = new ArrayList<Integer>();
		for (IaaSService iaas : services) {
			iaas.registerHost(dummyPMcreator());
			Repository repo = iaas.repositories.get(0);
			iaas.requestVM((VirtualAppliance) repo.contents().iterator().next(), iaas.machines.get(0).getCapacities(),
					repo, 1)[0].subscribeStateChange(new VirtualMachine.StateChange() {
						@Override
						public void stateChanged(VirtualMachine vm, State oldState, State newState) {
							if (newState.equals(VirtualMachine.State.RUNNING)) {
								markers.add(vm.hashCode());
							}
						}
					});
		}
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("By now all VMs should have been started", services.size(), markers.size());
		for (IaaSService iaas : services) {
			iaas.deregisterHost(iaas.runningMachines.get(0));
		}
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("By now all VMs should have been resumed", services.size() * 2, markers.size());
	}

	@Test(timeout = 100)
	public void multiVMRequest() throws VMManagementException, NetworkException {
		constructMinimalIaaS();
		final int maxVMs = 16;
		final int servNum = services.size();
		final Repository[] repos = new Repository[servNum];
		final ResourceConstraints[] caps = new ResourceConstraints[servNum];
		final VirtualAppliance[] vas = new VirtualAppliance[servNum];
		for (int i = 0; i < servNum; i++) {
			final IaaSService iaas = services.get(i);
			repos[i] = iaas.repositories.get(0);
			caps[i] = iaas.machines.get(0).getCapacities();
			vas[i] = (VirtualAppliance) repos[i].contents().iterator().next();
		}

		final VirtualMachine[] vms = new VirtualMachine[maxVMs * servNum];
		for (int vmnum = 2; vmnum <= maxVMs; vmnum *= 2) {
			for (int i = 0; i < servNum; i++) {
				AlterableResourceConstraints cap = new AlterableResourceConstraints(caps[i]);
				cap.multiply(1f / vmnum / 2);
				VirtualMachine[] lvms = services.get(i).requestVM(vas[i], cap, repos[i], vmnum);
				System.arraycopy(lvms, 0, vms, i * vmnum, vmnum);
			}
			Timed.simulateUntilLastEvent();
			for (int i = 0; i < vmnum * servNum; i++) {
				vms[i].destroy(false);
			}
			Timed.simulateUntilLastEvent();
		}
	}

	@Test(timeout = 100)
	public void deQueueTest() throws VMManagementException, NetworkException {
		constructMinimalIaaS();
		for (IaaSService s : services) {
			Repository r = s.repositories.get(0);
			VirtualMachine vm = s.requestVM((VirtualAppliance) r.contents().iterator().next(), s.getCapacities(), r,
					1)[0];
			Timed.simulateUntilLastEvent();
			Assert.assertEquals("The first VM should be running", VirtualMachine.State.RUNNING, vm.getState());
			AlterableResourceConstraints caps = new AlterableResourceConstraints(s.getCapacities());
			caps.multiply(0.4);
			VirtualMachine[] vmsToQueue = s.requestVM((VirtualAppliance) r.contents().iterator().next(),
					new UnalterableConstraintsPropagator(caps), r, 2);
			VirtualMachine.State[] states = new VirtualMachine.State[] { vmsToQueue[0].getState(),
					vmsToQueue[1].getState() };
			for (VirtualMachine.State st : states) {
				Assert.assertTrue("The VMs should be queueing or nonservable (in case of non queueing schedulers)",
						VirtualMachine.preScheduleState.contains(st));
			}
			if (states[0].equals(VirtualMachine.State.DESTROYED)) {
				// A queueing scheduler is used
				s.terminateVM(vmsToQueue[1], false);
				states = new VirtualMachine.State[] { vmsToQueue[0].getState(), vmsToQueue[1].getState() };
			}
			for (VirtualMachine.State st : states) {
				Assert.assertEquals("The VMs should be marked as nonservable by all schedulers this time",
						VirtualMachine.State.NONSERVABLE, st);
			}
			s.terminateVM(vm, true);
			Timed.simulateUntilLastEvent();
			vm.destroy(false);
			Timed.simulateUntilLastEvent();
			states = new VirtualMachine.State[] { vm.getState(), vmsToQueue[0].getState(), vmsToQueue[1].getState() };
			for (VirtualMachine.State st : states) {
				Assert.assertTrue("None of the VMs should be running by this time",
						VirtualMachine.preScheduleState.contains(st));
			}
		}
	}

	@Test(timeout = 100)
	public void subClassingTest() throws IllegalArgumentException, SecurityException, InstantiationException,
			IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class) {
			// Anonymous subclass. with no content
		};
	}

	@Test(timeout = 100)
	public void registeringRunningTest() throws VMManagementException, NetworkException, IaaSHandlingException {
		for (IaaSService s : services) {
			PhysicalMachine pm = dummyPMcreator();
			pm.turnon();
			Timed.simulateUntilLastEvent();
			s.registerRepository(dummyRepoCreator(true));
			s.registerHost(pm);
		}
		ArrayList<VirtualMachine> vms = requestVMs();
		Timed.simulateUntilLastEvent();
		for (int i = 0; i < vms.size(); i++) {
			IaaSService s = services.get(i);
			Assert.assertEquals(
					"All VMs should be running but they were not on PMC: " + s.pmcontroller.getClass().getName()
							+ " VMS: " + s.sched.getClass().getName(),
					VirtualMachine.State.RUNNING, vms.get(i).getState());
		}
	}

	@Test(timeout = 100)
	public void maxRequest() throws Exception {
		IaaSService iaas = setupIaaS(FirstFitScheduler.class, AlwaysOnMachines.class, 3, 2);
		Repository repo = iaas.repositories.get(0);
		VirtualAppliance va = (VirtualAppliance) repo.contents().iterator().next();
		double maxCOrig = Double.MAX_VALUE;
		double maxPOrig = Double.MAX_VALUE;
		long maxMOrig = Long.MAX_VALUE;
		for (int i = 0; i < 10; i++) {
			double maxC = 0;
			double maxP = 0;
			long maxM = 0;
			for (PhysicalMachine pm : iaas.machines) {
				maxC = Math.max(pm.freeCapacities.getRequiredCPUs(), maxC);
				maxM = Math.max(pm.freeCapacities.getRequiredMemory(), maxM);
				maxP = Math.max(pm.freeCapacities.getRequiredProcessingPower(), maxP);
			}
			Assert.assertTrue("MaxC", maxC <= maxCOrig);
			maxCOrig = maxC;
			Assert.assertTrue("MaxP", maxP <= maxPOrig);
			maxPOrig = maxP;
			Assert.assertTrue("MaxM", maxM <= maxMOrig);
			maxMOrig = maxM;

			ResourceConstraints rc = new ConstantConstraints(maxC / 10, maxP / 10, maxM / 10);
			iaas.requestVM(va, rc, repo, 1);
			Timed.simulateUntilLastEvent();
		}
	}

	@Test(timeout = 100)
	public void dropARunningPM() throws Exception {
		int pmcount = 3;
		IaaSService iaas = setupIaaS(FirstFitScheduler.class, AlwaysOnMachines.class, pmcount, 1);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should have all machines running", pmcount, iaas.runningMachines.size());
		iaas.deregisterHost(iaas.machines.get(0));
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should have one less machine running", pmcount - 1, iaas.runningMachines.size());
	}

	@Test(timeout = 100)
	public void heterogenousPMCapacityReport() throws Exception {
		IaaSService iaas = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);
		Assert.assertEquals("Should not report any processing power if there are no PMs", 0,
				iaas.getCapacities().getRequiredProcessingPower(), 0.00001);
		PhysicalMachine small = dummyPMcreator();
		ResourceConstraints sRC = small.getCapacities();
		iaas.registerHost(small);
		Assert.assertEquals("Should report the small PM's processing power if it is the only one",
				sRC.getRequiredProcessingPower(), iaas.getCapacities().getRequiredProcessingPower(), 0.00001);
		PhysicalMachine big = dummyPMsCreator(1, (int) sRC.getRequiredCPUs(), sRC.getRequiredProcessingPower() * 2,
				sRC.getRequiredMemory())[0];
		iaas.registerHost(big);
		Assert.assertEquals("Should report the big PM's processing power if the IaaS has more than one type of PM",
				big.getCapacities().getRequiredProcessingPower(), iaas.getCapacities().getRequiredProcessingPower(),
				0.00001);
		iaas.deregisterHost(big);
		Assert.assertEquals("Should report the small PM's processing power if it is the only one",
				sRC.getRequiredProcessingPower(), iaas.getCapacities().getRequiredProcessingPower(), 0.00001);
		iaas.deregisterHost(small);
		Timed.simulateUntilLastEvent();
	}
}
