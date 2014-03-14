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

package at.ac.uibk.dps.cloud.simulator.test.complex;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;

public class IaaSPerformanceTest extends IaaSRelatedFoundation {
	IaaSService basic;
	Repository repo;
	VirtualAppliance va;
	ResourceConstraints baseRC;
	final int hostCount = 40;
	final int vmCount = 2000;
	final int maxTaskCount = 5;
	final double maxTaskLen = 50;
	int runningCounter = 0;
	int destroyCounter = 0;


	@Before
	public void resetSim() throws Exception {
		SeedSyncer.resetCentral();
		basic = new IaaSService(FirstFitScheduler.class,
				SchedulingDependentMachines.class);
		for (int i = 0; i < hostCount; i++) {
			final PhysicalMachine pm = dummyPMcreator();
			basic.registerHost(pm);
		}
		baseRC = basic.machines.get(0).getCapacities();
		repo = dummyRepoCreator(true);
		va = (VirtualAppliance) repo.contents().iterator().next();
		basic.registerRepository(repo);
	}

	@Test(timeout = 2500)
	public void performanceTest() throws Exception {
		for (int i = 0; i < vmCount; i++) {
			final VirtualMachine vm = basic.requestVM(va,
					baseRC.multiply(SeedSyncer.centralRnd.nextDouble()), repo,
					1)[0];
			vm.subscribeStateChange(new VirtualMachine.StateChange() {
				@Override
				public void stateChanged(State oldState, State newState) {
					if (newState.equals(VirtualMachine.State.RUNNING)) {
						runningCounter++;
						final int myTaskCount = 1 + SeedSyncer.centralRnd
								.nextInt(maxTaskCount - 1);
						final ArrayList<String> eventCount = new ArrayList<String>();
						ConsumptionEventAdapter cae = new ConsumptionEventAdapter() {
							@Override
							public void conComplete() {
								eventCount.add("");
								if (eventCount.size() == myTaskCount) {
									try {
										vm.destroy(true);
									} catch (Exception e) {
										throw new RuntimeException(e);
									}
								}
							};
						};
						try {
							for (int j = 0; j < myTaskCount; j++) {
								vm.newComputeTask(
										SeedSyncer.centralRnd.nextDouble()
												* maxTaskLen,
										ResourceConsumption.unlimitedProcessing,
										cae);
							}
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}
					if (newState.equals(VirtualMachine.State.DESTROYED)) {
						destroyCounter++;
					}
				}
			});
			Timed.simulateUntil(Timed.getFireCount()
					+ SeedSyncer.centralRnd.nextInt((int) maxTaskLen));
		}
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Not all VMs ran", vmCount, runningCounter);
		Assert.assertEquals("Not all VMs terminated", vmCount, destroyCounter);
		for (PhysicalMachine pm : basic.runningMachines) {
			Assert.assertFalse("Should not have any running VMs registered",
					pm.isHostingVMs());
		}
		Assert.assertEquals("Should not have any running PMs", 0,
				basic.runningMachines.size());
	}
}
