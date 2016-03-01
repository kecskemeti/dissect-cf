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
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;

public class UnderProvisionTest extends IaaSRelatedFoundation {
	public static final int smallDivider = 10;
	public static final int bigDivider = 5;
	public static final int smallestDivider = 20;
	private PhysicalMachine pm;
	private ResourceConstraints small, bigger, biggerFittingCPU;

	@Before
	public void prepareClass() {
		pm = dummyPMcreator();
		pm.turnon();
		Timed.simulateUntilLastEvent();
		ResourceConstraints total = pm.getCapacities();
		small = new ConstantConstraints(
				total.getRequiredCPUs() / smallDivider,
				total.getRequiredProcessingPower(),
				(long) (total.getRequiredMemory() / smallDivider));
		bigger = new ConstantConstraints(
				total.getRequiredCPUs() / bigDivider,
				total.getRequiredProcessingPower() / smallestDivider, true,
				(long) (total.getRequiredMemory() / smallestDivider));
		biggerFittingCPU = new ConstantConstraints(
				total.getRequiredCPUs() / smallestDivider,
				total.getRequiredProcessingPower() / smallestDivider, true,
				(long) (total.getRequiredMemory() / smallestDivider));
	}

	@Test(timeout = 100)
	public void basicAllocationTest() throws VMManagementException {
		ResourceAllocation smallAll = pm.allocateResources(small, true,
				PhysicalMachine.defaultAllocLen);
		Assert.assertNull("Should not be able to allocate these resources", pm
				.allocateResources(bigger, true,
						PhysicalMachine.defaultAllocLen));
		Assert.assertTrue("Should be unallocable!",
				pm.cancelAllocation(smallAll));
		ResourceAllocation biggerAll;
		Assert.assertNotNull(
				"Should be allocable now",
				biggerAll = pm.allocateResources(bigger, true,
						PhysicalMachine.defaultAllocLen));
		Assert.assertNull("Should not be able to allocate these resources",
				pm.allocateResources(small, true,
						PhysicalMachine.defaultAllocLen));
		Assert.assertTrue("Should be unallocable!",
				pm.cancelAllocation(biggerAll));
	}

	@Test(timeout = 100)
	public void multiAllocationTest() throws VMManagementException {
		ResourceAllocation[] ras = new ResourceAllocation[smallestDivider];
		for (int i = 0; i < smallDivider; i++) {
			ras[i] = pm.allocateResources(small, true,
					PhysicalMachine.defaultAllocLen);
			Assert.assertNotNull("Should be allocable", ras[i]);
		}
		for (int i = 0; i < smallDivider; i++) {
			Assert.assertTrue("Should be cancellable",
					pm.cancelAllocation(ras[i]));
		}
		for (int i = 0; i < smallestDivider; i++) {
			ras[i] = pm.allocateResources(bigger, true,
					PhysicalMachine.defaultAllocLen);
			Assert.assertNotNull("Should be allocable " + i, ras[i]);
		}
		for (int i = 0; i < smallestDivider; i++) {
			Assert.assertTrue("Should be cancellable",
					pm.cancelAllocation(ras[i]));
		}
	}

	@Test(timeout = 100)
	public void fittingAllocationTest() throws VMManagementException {
		ResourceAllocation[] ras = new ResourceAllocation[smallestDivider];
		for (int i = 0; i < smallestDivider; i++) {
			ras[i] = pm.allocateResources(biggerFittingCPU, true,
					PhysicalMachine.defaultAllocLen);
			Assert.assertNotNull("Should be allocable", ras[i]);
		}
	}

	@Test(timeout = 100)
	public void perfectFitAllocation() throws VMManagementException {
		ResourceConstraints pmc = pm.getCapacities();
		ResourceConstraints smaller = new ConstantConstraints(pmc.getRequiredCPUs(),
						pmc.getRequiredProcessingPower() * 0.33,
						pmc.getRequiredMemory() / 2);
		ResourceConstraints bigger = new ConstantConstraints(
						pmc.getRequiredCPUs(),
						pmc.getRequiredProcessingPower()
								- smaller.getRequiredProcessingPower(),
						pmc.getRequiredMemory() / 2);
		ResourceAllocation sa = pm.allocateResources(smaller, true,
				PhysicalMachine.defaultAllocLen);
		ResourceAllocation ba = pm.allocateResources(bigger, true,
				PhysicalMachine.defaultAllocLen);
		Assert.assertNotNull("Should be both allocable", sa);
		Assert.assertNotNull("Should be both allocable", ba);
		Timed.simulateUntilLastEvent();
	}
}
