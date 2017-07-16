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
 *  (C) Copyright 2017, Gabor Kecskemeti (g.kecskemeti@ljmu.ac.uk)
 */
package hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation;

import java.util.Arrays;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.helpers.PMComparators;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

/**
 * Basic consolidator algorithm which tries to compact the used PM pool by
 * allocating VMs to more heavily allocated PMs.
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2017"
 */
public class SimpleConsolidator extends Consolidator {
	/**
	 * This is a simple counter that one can query to determine how many
	 * migrations this algorithm ordered.
	 */
	public static long migrationCount = 0;

	/**
	 * Just passes its parameters to the superclass's constructor
	 * 
	 * @param toConsolidate
	 * @param consFreq
	 */
	public SimpleConsolidator(IaaSService toConsolidate, long consFreq) {
		super(toConsolidate, consFreq);
	}

	/**
	 * The actual consolidation algorithm, note this is rather computational
	 * heavy, and still provides just minimal gains over not consolidating at
	 * all. This is just offered as an example implementation of a consolidator.
	 */
	@Override
	protected void doConsolidation(PhysicalMachine[] pmList) {
		// Filters out the running machines and ignores the rest
		int runningLen = 0;
		for (int i = 0; i < pmList.length; i++) {
			if (pmList[i].isRunning()) {
				pmList[runningLen++] = pmList[i];
			}
		}
		PhysicalMachine[] newList = new PhysicalMachine[runningLen];
		System.arraycopy(pmList, 0, newList, 0, runningLen);
		pmList = newList;
		boolean didMove;
		int firstIndexHoldingAVM = 0;
		do {
			didMove = false;
			Arrays.sort(pmList, PMComparators.highestToLowestFreeCapacity);
			int lastItem = runningLen - 1;
			for (int i = firstIndexHoldingAVM; i < lastItem; i++) {
				PhysicalMachine source = pmList[i];
				if (source.isHostingVMs()) {
					VirtualMachine[] vmList = source.publicVms.toArray(new VirtualMachine[source.publicVms.size()]);
					for (int vmidx = 0; vmidx < vmList.length; vmidx++) {
						VirtualMachine vm = vmList[vmidx];
						if (!VirtualMachine.State.RUNNING.equals(vm.getState())) {
							continue;
						}
						for (int j = lastItem; j > i; j--) {
							PhysicalMachine target = pmList[j];
							if (target.freeCapacities.getTotalProcessingPower() < 0.00000001) {
								// Ensures that those PMs that barely have
								// resources will not be considered in future
								// runs of this loop
								lastItem = j;
								continue;
							}
							ResourceAllocation alloc = null;
							try {
								// TODO: we do not keep the possibility of
								// underprovisioning with
								// strict allocation = true
								alloc = target.allocateResources(vm.getResourceAllocation().allocated, true,
										PhysicalMachine.migrationAllocLen * 100);
								if (alloc != null) {
									vm.migrate(alloc);
									migrationCount++;
									didMove = true;
									break;
								}
							} catch (VMManagementException pmNotRunning) {
								System.err.println("Error while handling vm " + vm.hashCode() + " === "
										+ pmNotRunning.getMessage());
							} catch (NetworkException nex) {
								System.err.println(
										"NW Error while handling vm " + vm.hashCode() + " === " + nex.getMessage());
								if (alloc != null) {
									alloc.cancel();
								}
							}
						}
					}
				}
			}
		} while (didMove);
	}
}
