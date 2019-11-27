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
 *  (C) Copyright 2015, Vincenzo De Maio (vincenzo@dps.uibk.ac.at)
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */

package at.ac.uibk.dps.cloud.simulator.test.simple.cloud;

import java.util.ArrayList;
import java.util.EnumSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.StateChangeException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

public class VMTest extends IaaSRelatedFoundation {

	PhysicalMachine pm;
	VirtualAppliance va, vaWithBG;
	VirtualMachine centralVM, centralVMwithBG;
	Repository repo;
	final static long defaultMemory = 1000;

	@Before
	public void initializeObject() throws Exception {
		pm = dummyPMcreator();
		va = new VirtualAppliance("BaseVA", 1000, 0, false, pm.localDisk.getMaxStorageCapacity() / 10);
		centralVM = new VirtualMachine(va);
		vaWithBG = new VirtualAppliance("BaseVAWithBG", 1000, 10, false, pm.localDisk.getMaxStorageCapacity() / 10);
		repo = dummyRepoCreator(false);
		repo.registerObject(va);
		repo.registerObject(vaWithBG);
		repo.setState(NetworkNode.State.RUNNING);
		centralVMwithBG = new VirtualMachine(vaWithBG);
		pm.localDisk.registerObject(va);
		pm.localDisk.registerObject(vaWithBG);
		pm.turnon();
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void constructionTest() {
		Assert.assertEquals("The collected VA is not matching", va, centralVM.getVa());
		Assert.assertEquals("The VM is not in the expected state", VirtualMachine.State.DESTROYED,
				centralVM.getState());
		Assert.assertTrue("The VM state should be in its string output",
				centralVM.toString().contains(centralVM.getState().toString()));
	}

	@Test(expected = IllegalStateException.class, timeout = 100)
	public void faultyConstructionTest() {
		new VirtualMachine(null);
	}

	@Test(timeout = 100)
	public void nonServableTest() throws VMManagementException, NetworkException {
		centralVM.setNonservable();
		PhysicalMachine.ResourceAllocation ra = pm.allocateResources(pm.getCapacities(), true,
				PhysicalMachine.defaultAllocLen);
		try {
			centralVM.migrate(ra);
			Assert.fail("It should not be possible to do migration if the VM is non servable");
		} catch (StateChangeException ex) {
			// Correct behavior
		}
		ra.cancel();
		try {
			centralVM.prepare(dummyRepoCreator(false), dummyRepoCreator(false));
			Assert.fail("It should not be possible to do preparation if the VM is non servable");
		} catch (StateChangeException ex) {
			// Correct behavior
		}
		try {
			centralVM.resume();
			Assert.fail("It should not be possible to do resume if the VM is non servable");
		} catch (StateChangeException ex) {
			// Correct behavior
		}
		try {
			centralVM.suspend();
			Assert.fail("It should not be possible to do suspend if the VM is non servable");
		} catch (StateChangeException ex) {
			// Correct behavior
		}
		try {
			centralVM.switchoff(false);
			Assert.fail("It should not be possible to do switchoff if the VM is non servable");
		} catch (StateChangeException ex) {
			// Correct behavior
		}
		try {
			switchOnVMwithMaxCapacity(centralVM, true);
			Assert.fail("It should not be possible to do switchon if the VM is non servable");
		} catch (StateChangeException ex) {
			// Correct behavior
		}
		try {
			centralVM.destroy(false);
		} catch (StateChangeException ex) {
			Assert.fail("It should be possible to do a destroy if the VM is non servable");
		}
	}

	private void switchOnVMwithMaxCapacity(VirtualMachine target, boolean simulate)
			throws VMManagementException, NetworkException {
		target.switchOn(pm.allocateResources(pm.getCapacities(), true, PhysicalMachine.defaultAllocLen),
				target.getVa().getBgNetworkLoad() > 0 ? repo : pm.localDisk);
		if (simulate) {
			Timed.simulateUntilLastEvent();
		}
	}

	@Test(timeout = 100)
	public void simpleVMStartup() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		Assert.assertEquals("Regular VM not in the excpected state after switchon", VirtualMachine.State.RUNNING,
				centralVM.getState());
		centralVM.destroy(false);
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void bgLoadVMStatrup() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVMwithBG, true);
		Assert.assertEquals("BGLoad VM not in the excpected state after switchon", VirtualMachine.State.RUNNING,
				centralVMwithBG.getState());
		centralVMwithBG.destroy(false);
		Timed.simulateUntilLastEvent();
	}

	@Test(expected = VMManagementException.class, timeout = 100)
	public void faultyBgLoadVMStartup() throws VMManagementException, NetworkException {
		centralVMwithBG.switchOn(pm.allocateResources(pm.getCapacities(), true, PhysicalMachine.defaultAllocLen),
				pm.localDisk);
	}

	@Test(timeout = 100)
	public void startupAfterPrepare() throws VMManagementException, NetworkException {
		centralVM.prepare(pm.localDisk, pm.localDisk);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("VM is not in expected state after prepare", VirtualMachine.State.SHUTDOWN,
				centralVM.getState());
		switchOnVMwithMaxCapacity(centralVM, true);
		Assert.assertEquals("Regular VM not in the excpected state after switchon", VirtualMachine.State.RUNNING,
				centralVM.getState());
		centralVM.destroy(false);
	}

	@Test(expected = VMManagementException.class, timeout = 100)
	public void faultyStartupAfterPrepare() throws VMManagementException, NetworkException {
		PhysicalMachine target = dummyPMcreator();
		target.turnon();
		Timed.simulateUntilLastEvent();
		centralVM.prepare(pm.localDisk, target.localDisk);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("VM is not in expected state after prepare", VirtualMachine.State.SHUTDOWN,
				centralVM.getState());
		switchOnVMwithMaxCapacity(centralVM, true);
	}

	@Test(timeout = 100)
	public void prepareOnlyTest() throws VMManagementException, NetworkException {
		long beforeFreeStorage = pm.localDisk.getFreeStorageCapacity();
		centralVM.prepare(null, pm.localDisk);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("VM is not in expected state after prepare", VirtualMachine.State.SHUTDOWN,
				centralVM.getState());
		long afterFreeStorage = pm.localDisk.getFreeStorageCapacity();
		Assert.assertEquals("PM disk free capacity mismatch after prepare", beforeFreeStorage - va.size,
				afterFreeStorage);
		centralVM.destroy(false);
		Assert.assertEquals("PM disk free capacity mismatch after destroy", beforeFreeStorage,
				pm.localDisk.getFreeStorageCapacity());
	}

	@Test(expected = VMManagementException.class, timeout = 100)
	public void nullVATargetTest() throws VMManagementException, NetworkException {
		centralVM.prepare(pm.localDisk, null);
	}

	@Test(expected = VMManagementException.class, timeout = 100)
	public void doubleNullPrepareTest() throws VMManagementException, NetworkException {
		centralVM.prepare(null, null);
	}

	@Test(expected = VMManagementException.class, timeout = 100)
	public void noFreeSpacePrepareTest() throws VMManagementException, NetworkException {
		pm.localDisk
				.registerObject(new StorageObject("MakesLocalDiskFull", pm.localDisk.getFreeStorageCapacity(), false));
		centralVM.prepare(null, pm.localDisk);
	}

	@Test(timeout = 100)
	public void taskKillingSwitchOff() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		ResourceConsumption con = centralVM.newComputeTask(2, ResourceConsumption.unlimitedProcessing, cae);
		Timed.fire();
		centralVM.switchoff(true);
		Timed.fire();
		Assert.assertTrue("The compute task should be cancelled after switchoff", cae.isCancelled());
		Assert.assertFalse("The compute task should be non-resumable after switchoff", con.registerConsumption());
	}

	@Test(timeout = 100)
	public void failingSwitchOff() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		centralVM.newComputeTask(1, ResourceConsumption.unlimitedProcessing, cae);
		Timed.fire();
		try {
			centralVM.switchoff(false);
			Assert.fail(
					"On a VM with compute tasks, switchoff should not succeed if it was not asked to kill the compute tasks");
		} catch (VMManagementException ex) {
			// Expected
		}
		Assert.assertFalse("The compute task should not be cancelled after faulty switchoff", cae.isCancelled());
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("It should be possible to finish the compute task after faulty switchoff", cae.isCompleted());
		centralVM.destroy(false);
	}

	@Test(timeout = 100)
	public void subscriptionTest() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		final ArrayList<VirtualMachine.State> receivedStates = new ArrayList<VirtualMachine.State>();
		VirtualMachine.StateChange sc = new VirtualMachine.StateChange() {
			@Override
			public void stateChanged(VirtualMachine vmInt, State oldState, State newState) {
				receivedStates.add(newState);
			}
		};
		centralVM.subscribeStateChange(sc);
		centralVM.destroy(false);
		centralVM.unsubscribeStateChange(sc);
		Assert.assertArrayEquals("Did not receive the necessary state changes",
				new VirtualMachine.State[] { VirtualMachine.State.SHUTDOWN, VirtualMachine.State.DESTROYED },
				receivedStates.toArray(new VirtualMachine.State[receivedStates.size()]));
	}

	@Test(timeout = 100)
	public void consumptionRejectionTest() throws VMManagementException, NetworkException {
		final ResourceConsumption con = new ResourceConsumption(1, ResourceConsumption.unlimitedProcessing, centralVM,
				pm, new ConsumptionEventAssert());
		double initialUnpr = con.getUnProcessed();
		Assert.assertFalse("Consumption registration should not succeed unless in consuming state",
				con.registerConsumption());
		switchOnVMwithMaxCapacity(centralVM, false);
		Assert.assertFalse("Consumption registration should not succeed in inital transfer phase",
				con.registerConsumption());
		Assert.assertEquals("Unprocessed consumption mismatch", initialUnpr, con.getUnProcessed(), 0);
		centralVM.subscribeStateChange(new VirtualMachine.StateChange() {
			@Override
			public void stateChanged(VirtualMachine vmInt, State oldState, State newState) {
				if (VirtualMachine.consumingStates.contains(newState) && !con.isRegistered()) {
					Assert.assertTrue("Consumption registration should now proceed", con.registerConsumption());
				}
			}
		});
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Unprocessed consumption mismatch", 0, con.getUnProcessed(), 0);
	}

	@Test(timeout = 100)
	public void newConsumptionRejectionTest() throws VMManagementException, NetworkException {
		Assert.assertEquals("VM should not accept consumption while destroyed", null,
				centralVM.newComputeTask(1, 1, new ConsumptionEventAssert()));
		switchOnVMwithMaxCapacity(centralVM, false);
		Assert.assertEquals("VM should not accept consumption in initial transfer phase", null,
				centralVM.newComputeTask(1, 1, new ConsumptionEventAssert()));
	}

	@Test(expected = StateChangeException.class, timeout = 100)
	public void errenousAllocationRequest() throws VMManagementException, NetworkException {
		AlterableResourceConstraints constraints = new AlterableResourceConstraints(pm.getCapacities());
		constraints.multiply(0.1);
		centralVM.switchOn(pm.allocateResources(constraints, true, PhysicalMachine.defaultAllocLen), pm.localDisk);
		centralVM.setResourceAllocation(pm.allocateResources(constraints, true, PhysicalMachine.defaultAllocLen));
	}

	@Test(timeout = 100)
	public void allocationHandlingCorrectness() throws VMManagementException, NetworkException {
		PhysicalMachine.ResourceAllocation alloc = pm.allocateResources(pm.getCapacities(), true,
				PhysicalMachine.defaultAllocLen);
		centralVM.switchOn(alloc, pm.localDisk);
		Assert.assertEquals("Resource allocation mismatch", alloc, centralVM.getResourceAllocation());
		Timed.simulateUntilLastEvent();
		centralVM.destroy(false);
		Assert.assertEquals("Resource allocation should be released", null, centralVM.getResourceAllocation());
	}

	private void simpleSusResume(VirtualMachine vmToUse) throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(vmToUse, true);
		ConsumptionEventAssert cea = new ConsumptionEventAssert();
		PhysicalMachine.ResourceAllocation ra = vmToUse.getResourceAllocation();
		vmToUse.newComputeTask(1, 1, cea);
		long memless = pm.localDisk.getFreeStorageCapacity();
		long memsize = ra.allocated.getRequiredMemory();
		vmToUse.suspend();
		Assert.assertEquals("Not in expected state", VirtualMachine.State.SUSPEND_TR, vmToUse.getState());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("PM's local storage should decrease with memory size", memless - memsize,
				pm.localDisk.getFreeStorageCapacity());
		Assert.assertEquals("Not in expected state", VirtualMachine.State.SUSPENDED, vmToUse.getState());
		Assert.assertFalse("The consumption should not finish because it should be suspended",
				cea.isCancelled() || cea.isCompleted());
		Assert.assertTrue("The allocation should be unused by now", ra.isUnUsed());
		vmToUse.setResourceAllocation(pm.allocateResources(pm.getCapacities(), true, PhysicalMachine.defaultAllocLen));
		vmToUse.resume();
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("The consumption should finish after resume", cea.isCompleted());
		Assert.assertEquals("The PM's local storage should return to the original levels of free capacities", memless,
				pm.localDisk.getFreeStorageCapacity());
	}

	@Test(timeout = 100)
	public void successfulSuspendResumeTest() throws VMManagementException, NetworkException {
		simpleSusResume(centralVM);
	}

	@Test(timeout = 100)
	public void successfulSuspendResumeTestWithBGLoad() throws VMManagementException, NetworkException {
		simpleSusResume(centralVMwithBG);
	}

	@Test(timeout = 100)
	public void ensureSuspendUsesJustEnoughDisk() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		long memless = pm.localDisk.getFreeStorageCapacity();
		long memsize = centralVM.getResourceAllocation().allocated.getRequiredMemory();
		pm.localDisk.registerObject(new StorageObject("SpaceFiller", memless - memsize, false));
		centralVM.suspend();
	}

	@Test(expected = VMManagementException.class, timeout = 100)
	public void ensureSuspendFailsIfNotEnoughDiskSpace() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		pm.localDisk.registerObject(new StorageObject("SpaceFiller", pm.localDisk.getFreeStorageCapacity(), false));
		centralVM.suspend();
	}

	@Test(timeout = 100)
	public void ensureResumeUsesJustEnoughDisk() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		centralVM.suspend();
		Timed.simulateUntilLastEvent();
		pm.localDisk.registerObject(new StorageObject("SpaceFiller", pm.localDisk.getFreeStorageCapacity(), false));
		centralVM
				.setResourceAllocation(pm.allocateResources(pm.getCapacities(), true, PhysicalMachine.defaultAllocLen));
		centralVM.resume();
		Timed.simulateUntilLastEvent();
	}

	private PhysicalMachine createAndExecutePM() {
		PhysicalMachine pmtarget = dummyPMcreator();
		pmtarget.turnon();
		Timed.simulateUntilLastEvent();
		return pmtarget;
	}

	private void doMigration(PhysicalMachine from, PhysicalMachine to, VirtualMachine vm, boolean sim)
			throws VMManagementException, NetworkException {
		from.migrateVM(vm, to);
		if (sim) {
			Timed.simulateUntilLastEvent();
		}
	}

	private PhysicalMachine simpleMigrate(final VirtualMachine toUse) throws VMManagementException, NetworkException {
		final PhysicalMachine pmtarget = createAndExecutePM();
		Assert.assertTrue("The target PM should not have anything in its storage",
				pmtarget.localDisk.getMaxStorageCapacity() == pmtarget.localDisk.getFreeStorageCapacity());
		switchOnVMwithMaxCapacity(toUse, true);
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		final double ctLen = 100 * aSecond;
		toUse.newComputeTask(ctLen, 1, cae);
		Timed.simulateUntil(Timed.getFireCount() + aSecond);
		doMigration(pm, pmtarget, toUse, true);
		Assert.assertTrue("VM is not on its new host", pmtarget.publicVms.contains(toUse));
		Assert.assertFalse("VM is still on its old host", pm.publicVms.contains(toUse));
		Assert.assertEquals("VM is not properly resumed", VirtualMachine.State.RUNNING, toUse.getState());
		Assert.assertTrue("Source VM should have minority of the consumption",
				pm.getTotalProcessed() < pmtarget.getTotalProcessed());
		return pmtarget;
	}

	@Test(timeout = 100)
	public void simpleMigration() throws VMManagementException, NetworkException {
		final long beforeSize = pm.localDisk.getFreeStorageCapacity();
		PhysicalMachine pmtarget = simpleMigrate(centralVM);
		Assert.assertEquals("The source of the migration should not have any storage occupied by the VM's remainders",
				beforeSize, pm.localDisk.getFreeStorageCapacity());
		Assert.assertEquals("The target of the migration should only have the disk of the VM",
				pmtarget.localDisk.getMaxStorageCapacity() - va.size, pmtarget.localDisk.getFreeStorageCapacity());
	}

	@Test(timeout = 100)
	public void simpleLiveMigration() throws VMManagementException, NetworkException {
		final long beforeSize = pm.localDisk.getFreeStorageCapacity();
		PhysicalMachine pmtarget = simpleMigrate(centralVMwithBG);
		Assert.assertEquals("The source of the migration should not have any storage occupied by the VM's remainders",
				beforeSize, pm.localDisk.getFreeStorageCapacity());
		Assert.assertEquals("The target of the migration should not have any storage occupied by the VM's remainders",
				pmtarget.localDisk.getMaxStorageCapacity(), pmtarget.localDisk.getFreeStorageCapacity());
	}

	private void doubleMigrate(final VirtualMachine toUse) throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		final PhysicalMachine pmtarget = createAndExecutePM();
		final double beforePmCon = pm.getTotalProcessed();
		ConsumptionEventAssert cae = new ConsumptionEventAssert();
		final double ctLen = 100 * aSecond;
		centralVM.newComputeTask(ctLen, 1, cae);
		Timed.simulateUntil(Timed.getFireCount() + aSecond);
		doMigration(pm, pmtarget, centralVM, false);
		final boolean[] haveNotBeenThere = new boolean[1];
		haveNotBeenThere[0] = true;
		centralVM.subscribeStateChange(new VirtualMachine.StateChange() {
			@Override
			public void stateChanged(VirtualMachine vmInt, State oldState, State newState) {
				if (newState.equals(VirtualMachine.State.RUNNING) && haveNotBeenThere[0]) {
					haveNotBeenThere[0] = false;
					new DeferredEvent(aSecond) {
						@Override
						protected void eventAction() {
							try {
								doMigration(pmtarget, pm, centralVM, false);
							} catch (Exception e) {
								throw new IllegalStateException("Second migration failed", e);
							}
						}
					};
				}
			}
		});
		Timed.simulateUntilLastEvent();
		Assert.assertFalse("VM is still on its new host", pmtarget.publicVms.contains(centralVM));
		Assert.assertTrue("VM is not on its old host", pm.publicVms.contains(centralVM));
		Assert.assertEquals("VM is not properly resumed", VirtualMachine.State.RUNNING, centralVM.getState());
		Assert.assertEquals("Source VM should have the majority of the consumption", beforePmCon + ctLen - aSecond,
				pm.getTotalProcessed(), 0.01);
		Assert.assertEquals("Target VM should have the minority of the consumption", aSecond,
				pmtarget.getTotalProcessed(), 0.01);

	}

	@Test(timeout = 100)
	public void doubleMigration() throws VMManagementException, NetworkException {
		doubleMigrate(centralVM);
	}

	@Test(timeout = 100)
	public void doubleMigrationWithBgNWL() throws VMManagementException, NetworkException {
		doubleMigrate(centralVMwithBG);
	}

	@Test(timeout = 100)
	public void migrationAfterSuspend() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		ResourceConstraints original = centralVM.getResourceAllocation().allocated;
		centralVM.suspend();
		final PhysicalMachine pmtarget = createAndExecutePM();
		centralVM.migrate(pmtarget.allocateResources(original, true, PhysicalMachine.migrationAllocLen));
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("Source should not host the VM anymore", pm.publicVms.isEmpty());
		Assert.assertTrue("Target should host the VM", pmtarget.publicVms.contains(centralVM));
	}

	private void failMigrationProcedureAfterSuspendPhase(long spaceToLeave)
			throws VMManagementException, NetworkException {
		final PhysicalMachine pmtarget = createAndExecutePM();
		pmtarget.localDisk.registerObject(new StorageObject("TargetStorageFiller",
				pmtarget.localDisk.getFreeStorageCapacity() - spaceToLeave, false));
		switchOnVMwithMaxCapacity(centralVM, true);
		doMigration(pm, pmtarget, centralVM, true);
		Assert.fail("Migration should fail with an exception, should not reach this point");
	}

	@Test(expected = VMManagementException.class, timeout = 100)
	public void failMigrationProcedureAfterSuspendPhaseNoAvalableStorage()
			throws VMManagementException, NetworkException {
		// Does not allow the copying of the disk to the target machine
		failMigrationProcedureAfterSuspendPhase(centralVM.getVa().size - 1);
	}

	@Test(timeout = 100)
	public void migrationFailureandRetry() throws VMManagementException, NetworkException {
		final PhysicalMachine pmnewtarget = createAndExecutePM();
		try {
			failMigrationProcedureAfterSuspendPhase(centralVM.getVa().size - 1);
		} catch (VMManagementException e) {
			// Expected
		}
		centralVM.migrate(
				pmnewtarget.allocateResources(pmnewtarget.getCapacities(), true, PhysicalMachine.migrationAllocLen));
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("The VM is now expected to be running", VirtualMachine.State.RUNNING, centralVM.getState());
	}

	@Test(timeout = 100)
	public void migrationFailureandResume() throws VMManagementException, NetworkException {
		try {
			failMigrationProcedureAfterSuspendPhase(centralVM.getVa().size - 1);
		} catch (VMManagementException e) {
			// Expected
		}
		Assert.assertEquals("The VM should be running just fine after a failed migration attempt",
				VirtualMachine.State.RUNNING, centralVM.getState());
	}

	@Test(timeout = 100)
	public void suspendUseForOtherResume() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, true);
		ConsumptionEventAssert first = new ConsumptionEventAssert(), second = new ConsumptionEventAssert();
		centralVM.newComputeTask(1, 1, first);
		long memless = pm.localDisk.getFreeStorageCapacity();
		centralVM.suspend();
		Timed.simulateUntilLastEvent();
		VirtualMachine otherVM = pm.requestVM(va, new AlterableResourceConstraints(pm.freeCapacities), repo, 1)[0];
		Timed.simulateUntilLastEvent();
		otherVM.newComputeTask(1, 1, second);
		Timed.simulateUntilLastEvent();
		Assert.assertFalse("The first task should not be executed as it is in the suspended VM",
				first.isCompleted() || first.isCancelled());
		Assert.assertTrue("The second task should be complete by now", second.isCompleted());
		otherVM.destroy(false);
		centralVM
				.setResourceAllocation(pm.allocateResources(pm.getCapacities(), true, PhysicalMachine.defaultAllocLen));
		centralVM.resume();
		Timed.simulateUntilLastEvent();
		Assert.assertTrue("The consumption should finish after resume", first.isCompleted());
		Assert.assertEquals("The PM's local storage should return to the original levels of free capacities", memless,
				pm.localDisk.getFreeStorageCapacity());
	}

	@Test(timeout = 100)
	public void consumptionBlocking() throws VMManagementException, NetworkException {
		final VirtualMachine vm = pm.requestVM(va, new AlterableResourceConstraints(pm.availableCapacities),
				pm.localDisk, 1)[0];
		ResourceConsumption conVM = new ResourceConsumption(100000, ResourceConsumption.unlimitedProcessing, vm, pm,
				new ConsumptionEventAssert());
		Assert.assertFalse("A virtual machine should not allow registering a consumption in a non-running state",
				conVM.registerConsumption());
		Timed.simulateUntilLastEvent();
		vm.destroy(false);
		Timed.simulateUntilLastEvent();
	}

	private boolean vmDestroyerinState(VirtualMachine.State st, VirtualMachine vm) throws VMManagementException {
		if (vm.getState().equals(st)) {
			vm.destroy(true);
			return true;
		}
		return false;
	}

	@Test(timeout = 100)
	public void ensuringReleasewithDestroy() throws VMManagementException, NetworkException {
		for (final VirtualMachine.State st : VirtualMachine.State.values()) {
			if (vmDestroyerinState(st, centralVM))
				continue;
			final boolean[] marker = new boolean[1];
			marker[0] = false;
			switchOnVMwithMaxCapacity(centralVM, false);
			try {
				if (!vmDestroyerinState(st, centralVM)) {
					centralVM.subscribeStateChange(new VirtualMachine.StateChange() {
						@Override
						public void stateChanged(VirtualMachine vmInt, State oldState, State newState) {
							try {
								vmDestroyerinState(st, centralVM);
							} catch (VMManagementException ex) {
								marker[0] = true;
							}
						}
					});
				}
			} catch (VMManagementException e) {
				marker[0] = true;
			}
			Timed.simulateUntilLastEvent();
			if (marker[0]) {
				Assert.assertEquals("Should be running from state " + st, VirtualMachine.State.RUNNING,
						centralVM.getState());
				centralVM.destroy(true);
			} else {
				Assert.assertEquals("Should be destroyed from state " + st, VirtualMachine.State.DESTROYED,
						centralVM.getState());
			}
			Timed.simulateUntilLastEvent();
			Assert.assertEquals("After the second destroy it should be destroyed from state " + st,
					VirtualMachine.State.DESTROYED, centralVM.getState());
			Assert.assertNull("Should not have any resource allocations starting from state " + st,
					centralVM.getResourceAllocation());
		}
	}

	@Test(timeout = 100)
	public void ensureVMKeepsItsAllocationDuringInitialMigration() throws VMManagementException, NetworkException {
		final EnumSet<VirtualMachine.State> needsAllocationOnSource = EnumSet.of(VirtualMachine.State.MIGRATING,
				VirtualMachine.State.SUSPEND_TR);
		PhysicalMachine target = createAndExecutePM();
		switchOnVMwithMaxCapacity(centralVM, true);
		final ConstantConstraints needToKeepThisCapacity = new ConstantConstraints(pm.freeCapacities);
		centralVM.subscribeStateChange(new VirtualMachine.StateChange() {
			@Override
			public void stateChanged(VirtualMachine vm, State oldState, State newState) {
				if (needsAllocationOnSource.contains(newState)) {
					Assert.assertTrue("Allocation for source disappeared prematurely, current VM state is " + newState,
							needToKeepThisCapacity.compareTo(pm.freeCapacities) == 0);
				}
			}
		});
		pm.migrateVM(centralVM, target);
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 100)
	public void allowDestroyDuringInitialTransfer() throws VMManagementException, NetworkException {
		switchOnVMwithMaxCapacity(centralVM, false);
		new DeferredEvent(1) {
			@Override
			protected void eventAction() {
				try {
					centralVM.destroy(false);
				} catch (VMManagementException e) {
					throw new RuntimeException(e);
				}
			};
		};
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Should get to destroyed", VirtualMachine.State.DESTROYED, centralVM.getState());
		switchOnVMwithMaxCapacity(centralVM, true);
		Assert.assertEquals("Cancel should not ruin the chance to get to running", VirtualMachine.State.RUNNING,
				centralVM.getState());
	}

	@Test(timeout = 100)
	public void allowDestroyWhileMigrating() throws VMManagementException, NetworkException {
		PhysicalMachine target = createAndExecutePM();
		switchOnVMwithMaxCapacity(centralVM, true);
		pm.migrateVM(centralVM, target);
		Assert.assertEquals("The VM should already be in MIGRATING phase", VirtualMachine.State.MIGRATING,
				centralVM.getState());
		centralVM.destroy(false);
		Timed.simulateUntil(Timed.getFireCount() + aSecond);
		Assert.assertEquals("There should be no allocation kept for the VM on the source", 0,
				pm.getCapacities().compareTo(pm.freeCapacities));
		Assert.assertEquals("There should be no allocation kept for the VM on the target", 0,
				target.getCapacities().compareTo(target.freeCapacities));
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("The VM should be destroyed by now", VirtualMachine.State.DESTROYED, centralVM.getState());
	}

	@Test(expected = StateChangeException.class, timeout = 100)
	public void disallowLiveIfNotRunning() throws VMManagementException, NetworkException {
		PhysicalMachine target = createAndExecutePM();
		switchOnVMwithMaxCapacity(centralVMwithBG, true);
		ConstantConstraints rc = new ConstantConstraints(centralVMwithBG.getResourceAllocation().allocated);
		centralVMwithBG.suspend();
		Timed.simulateUntilLastEvent();
		centralVMwithBG.migrate(target.allocateResources(rc, true, PhysicalMachine.migrationAllocLen), true);
	}

	@Test(expected = VMManagementException.class, timeout = 100)
	public void disallowLiveForNonRemotelyStored() throws VMManagementException, NetworkException {
		PhysicalMachine target = createAndExecutePM();
		switchOnVMwithMaxCapacity(centralVM, true);
		centralVM.migrate(target.allocateResources(centralVM.getResourceAllocation().allocated, true,
				PhysicalMachine.migrationAllocLen), true);
	}

}
