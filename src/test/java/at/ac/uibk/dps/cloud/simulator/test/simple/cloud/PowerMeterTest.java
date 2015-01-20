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

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.EnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.PhysicalMachineEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.SimpleVMEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.ConstantConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.LinearConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import java.util.ArrayList;
import java.util.List;

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
		final EnergyMeter meter = new SimpleVMEnergyMeter(vm);
		meter.startMeter(aSecond / 10, true);
		Timed.simulateUntil(Timed.getFireCount() + aSecond);
		Assert.assertEquals(
				"The idle machine is not consuming as much as expected",
				totalIdle * aSecond, meter.getTotalConsumption(), 0.1);
		long before = Timed.getFireCount();
		meter.stopMeter();
		Timed.simulateUntilLastEvent();
		long after = Timed.getFireCount();
		Assert.assertEquals(
				"Should not be any events if a meter is not executing", before,
				after);
		meter.startMeter(aSecond, true);
		new DeferredEvent(aSecond) {
			@Override
			protected void eventAction() {
				meter.stopMeter();
			}
		};
		Timed.simulateUntilLastEvent();
		Assert.assertEquals(
				"The idle machine's consumption should not depend on the measurement frequency of the meter",
				totalIdle * aSecond, meter.getTotalConsumption(), 0.1);
		Assert.assertFalse(meter.isSubscribed());
		meter.startMeter(aSecond / 10, true);
		ResourceConstraints rc = vm.getResourceAllocation().allocated;
		final long taskleninms = 10 * aSecond;
		Assert.assertEquals(0, ConsumptionEventAssert.hits.size());
		vm.newComputeTask(rc.requiredCPUs * rc.requiredProcessingPower
				* taskleninms, ResourceConsumption.unlimitedProcessing,
				new ConsumptionEventAssert(Timed.getFireCount() + taskleninms,
						true) {
					@Override
					public void conComplete() {
						super.conComplete();
						meter.stopMeter();
					}
				});
		Timed.simulateUntilLastEvent();
		Assert.assertEquals(1, ConsumptionEventAssert.hits.size());
		Assert.assertEquals(
				"The consumption is not properly reported if there is a task processed",
				taskleninms * (maxpower - idlepower + totalIdle),
				meter.getTotalConsumption(), 0.1);
		meter.startMeter(aSecond / 10, true);
		vm.newComputeTask(rc.requiredCPUs * rc.requiredProcessingPower
				* taskleninms, rc.requiredProcessingPower * 0.5,
				new ConsumptionEventAssert());
		Timed.simulateUntil(Timed.getFireCount() + taskleninms);
		Assert.assertEquals(
				"The consumption is not properly reported if there is a task processed",
				taskleninms * (0.5 * (maxpower - idlepower) + totalIdle),
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

	class MeterManager extends Timed {
		IaaSService iaas;
		int expectedVMnum;
		List<? extends EnergyMeter> managed;
		long maxExpected;

		public MeterManager(IaaSService iaas, long meterInterval,
				long managementInterval, int expectedVMs,
				List<? extends EnergyMeter> meters, long maxExpectedTime) {
			this.iaas = iaas;
			expectedVMnum = expectedVMs;
			for (EnergyMeter em : meters) {
				em.startMeter(meterInterval * aSecond, false);
			}
			managed = meters;
			maxExpected = maxExpectedTime;
			subscribe(managementInterval * aSecond);
		}

		@Override
		public void tick(long fires) {
			Assert.assertTrue(
					"Should have finished the execution of all VMs by this time, current time was:"
							+ fires, fires <= maxExpected);
			if (iaas.runningMachines.size() == 0) {
				int totVMcount = 0;
				for (PhysicalMachine pm : iaas.machines) {
					totVMcount += pm.getCompletedVMs();
				}
				if (totVMcount == expectedVMnum) {
					for (EnergyMeter currEm : managed) {
						currEm.stopMeter();
					}
					unsubscribe();
				}
			}
		}
	}

	@Test(timeout = 100)
	public void checkforPMSchedulerConflict() throws Exception {
		final IaaSService iaas = setupIaaS(FirstFitScheduler.class,
				SchedulingDependentMachines.class, 2);
		final int vmNum = 3;
		final int parallelThreads = 2;
		final long startAt = 100;
		for (long i = startAt; i < startAt + vmNum; i++) {
			fireVMat(iaas, i, 400, parallelThreads);
		}
		final ArrayList<EnergyMeter> meters = new ArrayList<EnergyMeter>();
		for (PhysicalMachine pm : iaas.machines) {
			meters.add(new PhysicalMachineEnergyMeter(pm));
		}
		final long managementFrequency = 60;
		new MeterManager(iaas, 5, managementFrequency, vmNum * parallelThreads,
				meters, managementFrequency * 1000);
		Timed.simulateUntilLastEvent();
	}

	@Test(timeout = 150)
	public void prolongedMeterTestThroughIaaS() throws Exception {
		final long[] fireAt = { 1135130133000l, 1135130401000l, 1135130415000l,
				1135130438000l, 1135130471000l, 1135130835000l, 1138120593000l,
				1138120603000l, 1138120664000l, 1138120938000l, 1138120952000l,
				1138121042000l, 1138121107000l };
		final double[] process = { 6, 13, 17, 17, 5, 16, 6, 5, 5, 6, 9, 964, 5 };
		final int[] cores = { 1, 1, 20, 1, 4, 1, 1, 1, 1, 80, 80, 80, 1 };
		final IaaSService iaas = new IaaSService(FirstFitScheduler.class,
				SchedulingDependentMachines.class);
		final ArrayList<EnergyMeter> meters = new ArrayList<EnergyMeter>();
		for (int i = 0; i < 7; i++) {
			final PhysicalMachine pm = new PhysicalMachine(64, 1, 64 * 1024,
					new Repository(vaSize, generateName("M", 1), 1, 1, 1,
							globalLatencyMap), 89000, 29000, defaultTransitions);
			iaas.registerHost(pm);
			meters.add(new PhysicalMachineEnergyMeter(pm));
		}
		iaas.registerRepository(dummyRepoCreator(true));
		for (int i = 0; i < fireAt.length; i++) {
			fireVMat(iaas, fireAt[i], process[i], cores[i]);
		}
		Timed.skipEventsTill(fireAt[0] - 1);
		// FIXME: lower management frequencies should be also possible within
		// the timeout
		new MeterManager(iaas, 1200, 3600, process.length + 3 /*
															 * for the 80 core
															 * vms
															 */, meters,
				1138200000000l);
		Timed.simulateUntilLastEvent();
	}

}
