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
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

public class PMTest extends IaaSRelatedFoundation {
	final static int reqcores = 2, reqProcessing = 3, reqmem = 1000, reqond = 2 * (int) aSecond,
			reqoffd = (int) aSecond;
	final static ResourceConstraints smallConstraints = new ConstantConstraints(reqcores / 2, reqProcessing,
			reqmem / 2);
	final static ResourceConstraints overCPUConstraints = new ConstantConstraints(reqcores * 2, reqProcessing, reqmem);
	final static ResourceConstraints overMemoryConstraints = new ConstantConstraints(reqcores, reqProcessing,
			reqmem * 2);
	final static ResourceConstraints overProcessingConstraints = new ConstantConstraints(reqcores, reqProcessing * 2,
			reqmem);
	final static String pmid = "TestingPM";
	PhysicalMachine pm;
	Repository reqDisk;
	HashMap<String, Integer> latmap = new HashMap<String, Integer>();

	@Before
	public void initializeTests() throws Exception {
		latmap.put(pmid, 1);
		reqDisk = new Repository(123, pmid, 456, 789, 12, new HashMap<String, Integer>(), defaultStorageTransitions,
				defaultNetworkTransitions);
		pm = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk, reqond, reqoffd, defaultHostTransitions);
	}

	@Test(timeout = 100)
	public void constructionTest() {
		Assert.assertEquals("Cores mismatch", reqcores, (int) pm.getCapacities().getRequiredCPUs());
		Assert.assertEquals("Memory mismatch", reqmem, (int) pm.getCapacities().getRequiredMemory());
		Assert.assertEquals("Per core processing power mismatch", reqProcessing,
				(int) pm.getCapacities().getRequiredProcessingPower());
		Assert.assertEquals("Total processing power mismatch", reqcores * reqProcessing,
				(int) pm.getPerTickProcessingPower());
		Assert.assertEquals("On delay mismatch", reqond, pm.getCurrentOnOffDelay());
		Assert.assertTrue("Free capacity mismatch", pm.freeCapacities.compareTo(pm.getCapacities()) == 0);
		Assert.assertTrue("Machine's id is not in the machine's toString", pm.toString().contains(pmid));
		Assert.assertEquals("Machine's state is not initial", PhysicalMachine.State.OFF, pm.getState());
		Assert.assertEquals("Machine should not have any completed VMs", 0, pm.getCompletedVMs());
		Assert.assertFalse("Machine should not host any VMs after construction", pm.isHostingVMs());
		Assert.assertTrue("The PM should report this request as hostable", pm.isHostableRequest(smallConstraints));
	}

	@Test(timeout = 100)
	public void regularSwitchOnOffTest() throws VMManagementException, NetworkException {
		final ArrayList<String> list = new ArrayList<String>();
		pm.subscribeStateChangeEvents(new PhysicalMachine.StateChangeListener() {
			@Override
			public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
				list.add(newState.toString());
			}
		});
		Assert.assertFalse("The PM should not be running now", pm.isRunning());
		Assert.assertEquals("So far we should not have any events", 0, list.size());
		pm.turnon();
		Assert.assertEquals("We should have been notified about the switching on event", 1, list.size());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("We should have been notified about the running event", 2, list.size());
		Assert.assertTrue("The PM should be running now", pm.isRunning());
		Assert.assertTrue(pm.switchoff(null));
		Assert.assertEquals("We should have been notified about the switching off event", 3, list.size());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("We should have been notified about the off event", 4, list.size());
		Assert.assertFalse("The PM should not be running now", pm.isRunning());
	}

	private PhysicalMachine.StateChangeListener getFailingListener(final String message) {
		return new PhysicalMachine.StateChangeListener() {
			@Override
			public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
				Assert.fail(message);
			}
		};
	}

	@Test(timeout = 100)
	public void irregularSwithchOnOffTest() throws VMManagementException, NetworkException {
		long before = Timed.getFireCount();
		pm.turnon(); // Turnon request that will be interrupted
		Timed.simulateUntil(Timed.getFireCount() + pm.getCurrentOnOffDelay() - 5);
		Assert.assertFalse("The PM should not be running now", pm.isRunning());
		Assert.assertTrue(pm.switchoff(null)); // while turning on
		Timed.simulateUntil(Timed.getFireCount() + pm.getCurrentOnOffDelay() - 5);
		pm.turnon(); // Turnon request that will succeed
		Assert.assertFalse("The PM should not be running now", pm.isRunning());
		Timed.simulateUntil(Timed.getFireCount() + pm.getCurrentOnOffDelay() - 5);
		Assert.assertFalse("The PM should not be running now", pm.isRunning());
		PhysicalMachine.StateChangeListener sl = getFailingListener(
				"We should not receive any state change events because of a repeated turnon!");
		pm.subscribeStateChangeEvents(sl);
		pm.turnon(); // While turning on
		pm.unsubscribeStateChangeEvents(sl);
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("The PM should be running now", pm.isRunning());
		long after = Timed.getFireCount();
		Assert.assertEquals("Off and on delays are not executed to their full extent", 2 * reqond + reqoffd,
				after - before - 1);
		pm.subscribeStateChangeEvents(sl);
		pm.turnon(); // After already running
		pm.unsubscribeStateChangeEvents(sl);
		Assert.assertTrue(pm.switchoff(null)); // Swithcoff request that will
												// succeed
		Timed.simulateUntil(Timed.getFireCount() + pm.getCurrentOnOffDelay() - 5);
		sl = getFailingListener("We should not receive any state change events because of a repeated switchoff!");
		Assert.assertTrue(pm.switchoff(null)); // Switchoff request while
												// swithcing off
		pm.unsubscribeStateChangeEvents(sl);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Machine could not swithced off properly", PhysicalMachine.State.OFF, pm.getState());
		pm.subscribeStateChangeEvents(sl);
		Assert.assertTrue(pm.switchoff(null)); // Switchoff request to an
												// already switched off machine
		pm.unsubscribeStateChangeEvents(sl);
	}

	private void registerVA(PhysicalMachine pm) {
		VirtualAppliance va = new VirtualAppliance("VAID", 1500, 0, false, pm.localDisk.getMaxStorageCapacity() / 10);
		pm.localDisk.registerObject(va);
	}

	private void preparePM() {
		registerVA(pm);
		pm.turnon();
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void simpleTwoPhasedSmallVMRequest() throws VMManagementException, NetworkException {
		preparePM();
		ResourceAllocation ra = pm.allocateResources(smallConstraints, true, PhysicalMachine.defaultAllocLen);
		Assert.assertTrue("Resource allocation does not have a proper tostring",
				ra.toString().contains(smallConstraints.toString()));
		pm.deployVM(newVMfromLocalDisk(null), ra, pm.localDisk);
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("PM should report that it hosts VMs", pm.isHostingVMs());
		pm.publicVms.iterator().next().destroy(false);
		Timed.simulateUntilLastEvent();
		Assert.assertFalse("PM should report that it does not host VMs", pm.isHostingVMs());
	}

	private VirtualMachine newVMfromLocalDisk(VirtualAppliance va) {
		return new VirtualMachine(va == null ? (VirtualAppliance) pm.localDisk.contents().iterator().next() : va);
	}

	private VirtualMachine[] requestVMs(ResourceConstraints rc, VirtualAppliance va, int count)
			throws VMManagementException, NetworkException {
		return pm.requestVM(va == null ? (VirtualAppliance) pm.localDisk.contents().iterator().next() : va, rc,
				pm.localDisk, count);
	}

	@Test(timeout = 100)
	public void simpleDirectVMrequest() throws VMManagementException, NetworkException {
		preparePM();
		VirtualMachine[] vms = requestVMs(smallConstraints, null, 2);
		Timed.simulateUntilLastEvent();
		for (VirtualMachine vm : vms) {
			Assert.assertEquals("All VMs should be running", VirtualMachine.State.RUNNING, vm.getState());
			vm.destroy(false);
		}
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void failingDirectVMRequest() throws VMManagementException, NetworkException {
		preparePM();
		AlterableResourceConstraints arc = new AlterableResourceConstraints(smallConstraints);
		arc.multiply(2);
		VirtualMachine[] vms = requestVMs(arc, null, 2);
		Assert.assertArrayEquals("If the PM cannot fulfill all VM requests then it should not accept a single one",
				new VirtualMachine[] { null, null }, vms);
	}

	@Test(timeout = 100)
	public void swithchoffWhileRunningVMs() throws VMManagementException, NetworkException {
		preparePM();
		AlterableResourceConstraints arc = new AlterableResourceConstraints(smallConstraints);
		arc.multiply(0.5);
		VirtualMachine[] vms = requestVMs(arc, null, 4);
		Timed.simulateUntilLastEvent();
		Assert.assertFalse("Should not be able to terminate a PM while VMs are running on it", pm.switchoff(null));
		for (VirtualMachine vm : vms) {
			vm.destroy(false);
		}
		Timed.simulateUntilLastEvent();
		Assert.assertTrue(pm.switchoff(null));
	}

	@Test(expected = VMManager.VMManagementException.class, timeout = 100)
	public void failingVMRequestBecauseofMalformedVA() throws VMManagementException, NetworkException {
		preparePM();
		requestVMs(smallConstraints, new VirtualAppliance("MAKEMEFAILED", 10, 10), 2);
		Assert.fail("We should not reach this point as the VMs requested were using an unknown VA for the PM");
	}

	@Test(expected = VMManager.VMManagementException.class, timeout = 100)
	public void failingVMRequestBecauseofExpiredRA() throws VMManagementException, NetworkException {
		preparePM();
		ResourceAllocation ra = pm.allocateResources(smallConstraints, true, PhysicalMachine.defaultAllocLen);
		Timed.simulateUntilLastEvent();
		pm.deployVM(newVMfromLocalDisk(null), ra, pm.localDisk);
		Assert.fail("The PM should not accept a VM request with an already expired allocation");
	}

	@Test(expected = VMManager.VMManagementException.class, timeout = 100)
	public void failingVMRequestBecauseofNotRunningPM() throws VMManagementException, NetworkException {
		registerVA(pm);
		requestVMs(smallConstraints, null, 2);
		Assert.fail("The PM should not accept a VM request while it is not running!");
	}

	@Test(timeout = 100)
	public void failingSwitchoffBecauseofPendingAllocation() throws VMManagementException, NetworkException {
		preparePM();
		ResourceAllocation ra = pm.allocateResources(smallConstraints, true, PhysicalMachine.defaultAllocLen);
		Assert.assertFalse("PM should not be able to switch off while there are pending resource allocations for it",
				pm.switchoff(null));
		pm.cancelAllocation(ra);
	}

	@Test(timeout = 100)
	public void cancelNotownAllocation() throws VMManagementException, NetworkException {
		preparePM();
		PhysicalMachine other = dummyPMcreator();
		other.turnon();
		registerVA(other);
		Timed.simulateUntilLastEvent();
		ResourceAllocation ra = other.allocateResources(other.getCapacities(), true, PhysicalMachine.defaultAllocLen);
		Assert.assertFalse("Should not be possible to cancel an allocation of another machine",
				pm.cancelAllocation(ra));
		VirtualMachine dpl = new VirtualMachine((VirtualAppliance) other.localDisk.contents().iterator().next());
		other.deployVM(dpl, ra, other.localDisk);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("The VM on the other PM is not in the expected state", VirtualMachine.State.RUNNING,
				dpl.getState());
		dpl.destroy(false);
	}

	@Test(timeout = 100)
	public void checkIncreasingFreeCapacityNotifications() throws VMManagementException, NetworkException {
		preparePM();
		ResourceConstraints beforeCreation = new AlterableResourceConstraints(pm.freeCapacities);
		VirtualMachine[] vm = requestVMs(smallConstraints, null, 2);
		ResourceConstraints afterRequest = new AlterableResourceConstraints(pm.freeCapacities);
		Assert.assertTrue("Unallocated resouce capacity maintanance failure", afterRequest.getRequiredCPUs() == 0);
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("PM should report that it hosts VMs", pm.isHostingVMs());
		Assert.assertTrue("Unallocated resource capacity should not change after request",
				afterRequest.compareTo(pm.freeCapacities) == 0);
		final ArrayList<ResourceConstraints> eventReceived = new ArrayList<ResourceConstraints>();
		PhysicalMachine.CapacityChangeEvent<ResourceConstraints> ev = new PhysicalMachine.CapacityChangeEvent<ResourceConstraints>() {
			@Override
			public void capacityChanged(ResourceConstraints newCapacity, List<ResourceConstraints> newlyFreeCapacity) {
				eventReceived.add(newCapacity);
			}
		};
		pm.subscribeToIncreasingFreeapacityChanges(ev);
		vm[0].destroy(false);
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("PM should still report that it hosts VMs", pm.isHostingVMs());
		Assert.assertEquals("Mismach in the expected number of free capacity increase events", 1, eventReceived.size());
		AlterableResourceConstraints upd = new AlterableResourceConstraints(eventReceived.get(0));
		upd.multiply(2);
		Assert.assertTrue("Mismach in the expected free capacity between the event and query",
				beforeCreation.compareTo(upd) == 0);
		pm.unsubscribeFromIncreasingFreeCapacityChanges(ev);
		vm[1].destroy(false);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("After unsubscription we should not receive more events", 1, eventReceived.size());
		Assert.assertFalse("PM should not report any hosted VMs by now", pm.isHostingVMs());
	}

	@Test(timeout = 100)
	public void checkOnOffdelayBehavior() throws VMManagementException, NetworkException {
		Assert.assertEquals("On delay misreported", reqond, pm.getCurrentOnOffDelay());
		pm.turnon();
		Assert.assertEquals("Still the on delay should be reported", reqond, pm.getCurrentOnOffDelay());
		final int timeDiff = 5;
		Timed.simulateUntil(Timed.getFireCount() + timeDiff);
		Assert.assertEquals("We should have the delay reduced a little bit by now", reqond - timeDiff,
				pm.getCurrentOnOffDelay());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Off delay misreported", reqoffd, pm.getCurrentOnOffDelay());
		pm.switchoff(null);
		Assert.assertEquals("Still the off delay should be reported", reqoffd, pm.getCurrentOnOffDelay());
		Timed.simulateUntil(Timed.getFireCount() + timeDiff);
		Assert.assertEquals("We should have the delay reduced a little bit by now", reqoffd - timeDiff,
				pm.getCurrentOnOffDelay());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("On delay misreported after on-off cycle", reqond, pm.getCurrentOnOffDelay());
		long switchOnRequestTime = Timed.getFireCount();
		pm.turnon();
		Timed.simulateUntil(switchOnRequestTime + timeDiff);
		pm.switchoff(null);
		Timed.simulateUntilLastEvent();
		long completeSwithcOffTime = Timed.getFireCount();
		Assert.assertEquals("Delays should add up together", reqoffd + reqond,
				completeSwithcOffTime - switchOnRequestTime - 1);
	}

	@Test(timeout = 100)
	public void checkConsumptionAcceptance() throws VMManagementException, NetworkException {
		preparePM();
		VirtualAppliance va = (VirtualAppliance) pm.localDisk.contents().iterator().next();
		VirtualMachine fraudent = new VirtualMachine(va);
		ResourceConsumption fraudentCon = new ResourceConsumption(1, ResourceConsumption.unlimitedProcessing, fraudent,
				pm, new ConsumptionEventAssert());
		Assert.assertFalse("Registering an unknown should fail", fraudentCon.registerConsumption());
		VirtualMachine proper = pm.requestVM(va, smallConstraints, pm.localDisk, 1)[0];
		Assert.assertEquals("Registration should not succeed since VM is not yet started up", null,
				proper.newComputeTask(1, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAssert()));
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("Registration should succeed since VM is now running",
				proper.newComputeTask(1, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAssert())
						.isRegistered());
		Timed.simulateUntilLastEvent();
		proper.destroy(false);
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void checkVMNumReport() throws VMManagementException, NetworkException {
		preparePM();
		Assert.assertEquals("Should not report any VM yet", 0, pm.numofCurrentVMs());
		Assert.assertEquals("Should not report any past VMs", 0, pm.getCompletedVMs());
		VirtualMachine[] vms = requestVMs(smallConstraints, null, 2);
		Assert.assertEquals("Should not report any past VMs", 0, pm.getCompletedVMs());
		Assert.assertEquals("Should offer the same VM list", pm.publicVms, pm.listVMs());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should report both VMs", 2, pm.numofCurrentVMs());
		Assert.assertEquals("Should not report any past VMs", 0, pm.getCompletedVMs());
		vms[0].destroy(false);
		Assert.assertEquals("Should report the non destroyed VM only", 1, pm.numofCurrentVMs());
		Assert.assertEquals("Should report the destroyed VM only", 1, pm.getCompletedVMs());
		vms[1].destroy(false);
		Assert.assertEquals("Should not report any VM by now", 0, pm.numofCurrentVMs());
		Assert.assertEquals("Should report both destroyed VMs", 2, pm.getCompletedVMs());
	}

	@Test(timeout = 100)
	public void successfulTerminateVMTest() throws VMManagementException, NetworkException {
		preparePM();
		VirtualMachine vm = requestVMs(smallConstraints, null, 1)[0];
		ResourceAllocation ra = vm.getResourceAllocation();
		Assert.assertFalse("The allocation should already be used", ra.isUnUsed());
		Assert.assertFalse("The allocation should not be reported available", ra.isAvailable());
		Timed.simulateUntilLastEvent();
		pm.terminateVM(vm, false);
		Assert.assertEquals("Should reach a proper termination state", VirtualMachine.State.SHUTDOWN, vm.getState());
		Assert.assertTrue("The allocation should be unused", ra.isUnUsed());
		Assert.assertFalse("The allocation should not be reported available", ra.isAvailable());
	}

	@Test(expected = VMManager.NoSuchVMException.class, timeout = 100)
	public void failedTerminateTest() throws VMManagementException, NetworkException {
		preparePM();
		VirtualMachine vm = new VirtualMachine((VirtualAppliance) pm.localDisk.contents().iterator().next());
		pm.terminateVM(vm, false);
	}

	@Test(timeout = 100)
	public void obeseConsumptionRequestTest() throws VMManagementException {
		preparePM();
		AlterableResourceConstraints obese = new AlterableResourceConstraints(smallConstraints);
		obese.multiply(3);
		Assert.assertFalse("The PM should not allow such big requests as hostable", pm.isHostableRequest(obese));
		Assert.assertFalse("The PM should not allow such big requests as hostable",
				pm.isHostableRequest(overCPUConstraints));
		Assert.assertFalse("The PM should not allow such big requests as hostable",
				pm.isHostableRequest(overMemoryConstraints));
		Assert.assertFalse("The PM should not allow such big requests as hostable",
				pm.isHostableRequest(overProcessingConstraints));
		Assert.assertEquals("The PM should not allocate such a big request in strict mode", null,
				pm.allocateResources(obese, true, PhysicalMachine.defaultAllocLen));
		Assert.assertEquals("The PM should not allocate such a big request in strict mode", null,
				pm.allocateResources(overCPUConstraints, true, PhysicalMachine.defaultAllocLen));
		Assert.assertEquals("The PM should not allocate such a big request in strict mode", null,
				pm.allocateResources(overMemoryConstraints, true, PhysicalMachine.defaultAllocLen));
		Assert.assertEquals("The PM should not allocate such a big request in strict mode", null,
				pm.allocateResources(overProcessingConstraints, true, PhysicalMachine.defaultAllocLen));
		Assert.assertEquals("The PM should not allocate a request with not possible to match per core processing power",
				null, pm.allocateResources(overProcessingConstraints, false, PhysicalMachine.defaultAllocLen));
		ResourceAllocation maxed = pm.allocateResources(overCPUConstraints, false, PhysicalMachine.defaultAllocLen);
		Assert.assertTrue("In non strict mode, the PM should allocate its maximum capacities",
				maxed.allocated.compareTo(pm.getCapacities()) == 0);
		Assert.assertTrue("In non strict mode, the PM should allocate less than requested",
				maxed.allocated.compareTo(overCPUConstraints) < 0);
		maxed.cancel();
		maxed = pm.allocateResources(overMemoryConstraints, false, PhysicalMachine.defaultAllocLen);
		Assert.assertTrue("In non strict mode, the PM should allocate its maximum capacities",
				maxed.allocated.compareTo(pm.getCapacities()) == 0);
		Assert.assertTrue("In non strict mode, the PM should allocate less than requested",
				maxed.allocated.compareTo(overMemoryConstraints) < 0);
		maxed.cancel();
		Assert.assertEquals("Should not have anything to do", -1, Timed.getNextFire());
	}

	@Test(expected = VMManagementException.class, timeout = 100)
	public void duplicateVMStartup() throws VMManagementException, NetworkException {
		preparePM();
		ResourceAllocation ra = pm.allocateResources(smallConstraints, true, PhysicalMachine.defaultAllocLen);
		VirtualAppliance va = (VirtualAppliance) pm.localDisk.contents().iterator().next();
		VirtualMachine vmSuccessful = new VirtualMachine(va);
		VirtualMachine vmFail = new VirtualMachine(va);
		vmSuccessful.switchOn(ra, pm.localDisk);
		vmFail.switchOn(ra, pm.localDisk);
	}

	@Test(timeout = 100)
	public void shutdownWithMigrateTest() throws VMManagementException, NetworkException {
		PhysicalMachine first = dummyPMcreator();
		PhysicalMachine second = dummyPMcreator();
		first.turnon();
		second.turnon();
		Timed.simulateUntilLastEvent();
		VirtualAppliance va = new VirtualAppliance("MyVA", 3000, 0, false,
				first.localDisk.getFreeStorageCapacity() / 10);
		first.localDisk.registerObject(va);
		AlterableResourceConstraints rc = new AlterableResourceConstraints(first.getCapacities());
		rc.multiply(0.5);
		VirtualMachine[] vms = first.requestVM(va, rc, first.localDisk, 2);
		Timed.simulateUntilLastEvent();
		vms[0].newComputeTask(1, 1, new ConsumptionEventAssert());
		vms[1].newComputeTask(1, 1, new ConsumptionEventAssert());
		first.switchoff(second);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Both tasks should be finished by now", 2, ConsumptionEventAssert.hits.size());
		Assert.assertEquals("The VM should be on the second machine now", second,
				vms[0].getResourceAllocation().getHost());
		Assert.assertEquals("The first PM should be off now", PhysicalMachine.State.OFF, first.getState());
	}

	@Test(timeout = 100)
	public void unsubscriptionInStateChangeEventHandler() throws VMManagementException, NetworkException {
		final ArrayList<PhysicalMachine.State> changehits = new ArrayList<PhysicalMachine.State>();
		PhysicalMachine.StateChangeListener sl = new PhysicalMachine.StateChangeListener() {
			@Override
			public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
				changehits.add(newState);
				pm.unsubscribeStateChangeEvents(this);
			}
		};
		pm.subscribeStateChangeEvents(sl);
		pm.turnon();
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Unsubscription failed, unexpected number of state changes", 1, changehits.size());
		changehits.clear();
		pm.subscribeStateChangeEvents(sl);
		pm.switchoff(null);
		pm.subscribeStateChangeEvents(sl);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Subscription failed, unexpected number of state changes", 2, changehits.size());
	}

	@Test(timeout = 100)
	public void doubleStateChangeEntry() {
		final ArrayList<PhysicalMachine.State> statelist = new ArrayList<PhysicalMachine.State>();
		pm.subscribeStateChangeEvents(new PhysicalMachine.StateChangeListener() {
			@Override
			public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
				statelist.add(newState);
				try {
					if (newState.equals(PhysicalMachine.State.RUNNING)) {
						pm.switchoff(null);
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
		Assert.assertEquals("Should not have any state updates yet", 0, statelist.size());
		pm.turnon();
		Assert.assertEquals("Should already receive the switchingon state", 1, statelist.size());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should pass through all the states by now", 4, statelist.size());
	}

	@Test(timeout = 100)
	public void consumptionBlocking() {
		PhysicalMachine pm = dummyPMsCreator(1, 8, 2500, 8000000000L)[0];
		VirtualAppliance va = new VirtualAppliance("Test", 1, 0);
		pm.localDisk.registerObject(va);
		VirtualMachine vm = new VirtualMachine(va);
		ResourceConsumption conVM = new ResourceConsumption(100000, ResourceConsumption.unlimitedProcessing, vm, pm,
				new ConsumptionEventAssert());
		Assert.assertFalse("A physical machine should not allow registering consumption from a nonhosted VM",
				conVM.registerConsumption());
	}

	@Test(timeout = 100)
	public void parallelVMMuse() throws VMManagementException, NetworkException {
		preparePM();
		VirtualAppliance va = (VirtualAppliance) pm.localDisk.contents().iterator().next();
		final VirtualMachine vm = pm.requestVM(va, pm.getCapacities(), pm.localDisk, 1)[0];
		Timed.simulateUntilLastEvent();
		long startTime = Timed.getFireCount();
		vm.newComputeTask(aSecond, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAssert());
		Timed.simulateUntilLastEvent();
		vm.newComputeTask(aSecond, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAssert() {
			@Override
			public void conComplete() {
				super.conComplete();
				try {
					vm.destroy(false);
				} catch (VMManagementException e) {
					throw new IllegalStateException(e);
				}
			}
		});
		ResourceConsumption consumption = new ResourceConsumption(2 * aSecond, ResourceConsumption.unlimitedProcessing,
				pm.directConsumer, pm, new ConsumptionEventAdapter());
		consumption.registerConsumption();
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("The second task execution should take longer due to the VMM activity registered",
				ConsumptionEventAssert.hits.get(1) - ConsumptionEventAssert.hits.get(0),
				(ConsumptionEventAssert.hits.get(0) - startTime) * 2);
	}

	@Test(timeout = 100)
	public void terminatePMduringVMMActivity() throws VMManagementException, NetworkException {
		preparePM();
		long startTime = Timed.getFireCount();
		final long totalProcessing = 1000 * aSecond;
		// Check how long the first consumption takes
		ResourceConsumption consumption = new ResourceConsumption(totalProcessing,
				ResourceConsumption.unlimitedProcessing, pm.directConsumer, pm, new ConsumptionEventAssert());
		consumption.registerConsumption();
		Timed.simulateUntilLastEvent();
		// Ensure the second consumption takes that much time as well despite
		// the switchoff
		consumption = new ResourceConsumption(totalProcessing, ResourceConsumption.unlimitedProcessing,
				pm.directConsumer, pm, new ConsumptionEventAssert(
						Timed.getFireCount() + ConsumptionEventAssert.hits.get(0) - startTime, true));
		consumption.registerConsumption();
		Timed.fire();
		long currentTime = Timed.getFireCount();
		Timed.simulateUntil(currentTime + (Timed.getNextFire() - currentTime) / 2);
		pm.switchoff(null);
		final ArrayList<Long> lastHit = new ArrayList<Long>();
		pm.subscribeStateChangeEvents(new PhysicalMachine.StateChangeListener() {
			@Override
			public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
				if (newState.equals(PhysicalMachine.State.OFF)) {
					lastHit.add(Timed.getFireCount());
				}
			}
		});
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("The PM should not get switched off before its VMM finishes its activities",
				lastHit.get(0) > ConsumptionEventAssert.hits.get(1));
	}

	@Test(timeout = 100)
	public void registerWhileSwitchedOff() throws VMManagementException, NetworkException {
		preparePM();
		ResourceConsumption consumption = new ResourceConsumption(aSecond, ResourceConsumption.unlimitedProcessing,
				pm.directConsumer, pm, new ConsumptionEventAdapter());
		Assert.assertTrue("Should be able to register to a running PM", consumption.registerConsumption());
		Timed.simulateUntilLastEvent();
		pm.switchoff(null);
		ConsumptionEventAdapter cae = new ConsumptionEventAdapter();
		consumption = new ResourceConsumption(aSecond, ResourceConsumption.unlimitedProcessing, pm.directConsumer, pm,
				cae);
		Assert.assertFalse("Should not be able to register to a switching off PM", consumption.registerConsumption());
		Timed.simulateUntilLastEvent();
		Assert.assertFalse("Should not ever complete if it was not registered", cae.isCompleted());
		Assert.assertFalse("Should not be able to register to an off PM", consumption.registerConsumption());
		pm.turnon();
		Assert.assertFalse("Should not be able to register to turning on PM", consumption.registerConsumption());
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("Should be able to register to a running PM", consumption.registerConsumption());
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("Should be complete by now", cae.isCompleted());
	}

	@Test(timeout = 100)
	public void complexBootup() throws VMManagementException, NetworkException {
		pm = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk, new double[] { 1, 0.1, 2, 0.05, 3, 3 },
				new double[] { 3, 0.3, 12, ResourceConsumption.unlimitedProcessing }, defaultHostTransitions);
		final long turnOnTime = 10 + 40 + 1;
		final long switchOffTime = 10 + 2;
		long before = Timed.getFireCount();
		pm.turnon();
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Turnon should take this much time: ", turnOnTime, Timed.getFireCount() - before - 1);
		before = Timed.getFireCount();
		pm.switchoff(null);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Switchoff should take this much time: ", switchOffTime, Timed.getFireCount() - before - 1);
		before = Timed.getFireCount();
		pm.turnon();
		Timed.simulateUntil(before + turnOnTime / 2);
		pm.switchoff(null);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("A complete turnon-switchoff cycle should take this much time: ",
				turnOnTime + switchOffTime, Timed.getFireCount() - before - 1);
	}

	@Test(timeout = 100)
	public void testZeroLenDelays() throws VMManagementException, NetworkException {
		pm = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk, 0, 1, defaultHostTransitions);
		pm.turnon();
		pm.switchoff(null);
		Timed.simulateUntilLastEvent();
		pm.turnon();
		Timed.simulateUntilLastEvent();
		pm.switchoff(null);
		Timed.simulateUntilLastEvent();
		pm = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk, 1, 0, defaultHostTransitions);
		pm.turnon();
		Timed.simulateUntilLastEvent();
		pm.switchoff(null);
		pm.turnon();
		Timed.simulateUntilLastEvent();
		pm.switchoff(null);
		pm.turnon();
	}

	@Test(timeout = 100)
	public void testTurnOnWhileInTransition() {
		pm.subscribeStateChangeEvents(new PhysicalMachine.StateChangeListener() {
			boolean needsSORequest = true;

			@Override
			public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
				Assert.assertFalse("Inproper state transition R->O",
						oldState.equals(PhysicalMachine.State.RUNNING) && newState.equals(PhysicalMachine.State.OFF));
				if (newState.equals(PhysicalMachine.State.RUNNING) && needsSORequest) {
					needsSORequest = false;
					try {
						pm.switchoff(null);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
				if (newState.equals(PhysicalMachine.State.SWITCHINGOFF)) {
					pm.turnon();
				}
			}
		});
		pm.turnon();
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void doubleRaCancel() throws VMManagementException {
		pm.turnon();
		Timed.simulateUntilLastEvent();
		PhysicalMachine.ResourceAllocation ra = pm.allocateResources(pm.getCapacities(), true,
				PhysicalMachine.defaultAllocLen);
		ra.cancel();
		ra.cancel();
		Assert.assertEquals("Should have no free capacity change", 0, pm.getCapacities().compareTo(pm.freeCapacities));
	}

	@Test(timeout=100, expected=VMManager.VMManagementException.class)
	public void explainMigrationFailure() throws VMManagementException, NetworkException {
		PhysicalMachine firstPM=dummyPMcreator();
		PhysicalMachine otherPM=dummyPMcreator();
		firstPM.turnon();
		otherPM.turnon();
		Timed.simulateUntilLastEvent();
		VirtualAppliance va=new VirtualAppliance("occupy", 1, 0,false, firstPM.localDisk.getMaxStorageCapacity()/10);
		firstPM.localDisk.registerObject(va);
		firstPM.requestVM(va, firstPM.getCapacities(), firstPM.localDisk, 1);
		VirtualMachine vm=otherPM.requestVM(va, otherPM.getCapacities(), firstPM.localDisk, 1)[0];
		Timed.simulateUntilLastEvent();
		otherPM.migrateVM(vm, firstPM);
	}
}
