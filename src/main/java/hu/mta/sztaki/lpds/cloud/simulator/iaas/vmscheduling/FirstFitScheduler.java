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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

import java.util.LinkedList;

public class FirstFitScheduler extends Scheduler {

	public FirstFitScheduler(IaaSService parent) {
		super(parent);
	}

	@Override
	protected void scheduleQueued() {
		final int totalMachineNum = parent.runningMachines.size();
		if (totalMachineNum != 0) {
			QueueingData request;
			LinkedList<ResourceAllocation> ras = new LinkedList<ResourceAllocation>();
			ResourceAllocation allocation;
			boolean processableRequest = true;
			while ((request = queue.peek()) != null && processableRequest) {
				ras.clear();
				int vmNum = 0;
				do {
					VirtualMachine vm = request.queuedVMs[vmNum];
					processableRequest = false;
					for (int i = 0; i < totalMachineNum; i++) {
						PhysicalMachine pm = parent.runningMachines.get(i);
						if (pm.localDisk.getFreeStorageCapacity() >= vm.getVa().size) {
							try {
								allocation = pm.allocateResources(
										request.queuedRC, true,
										PhysicalMachine.defaultAllocLen);
								if (allocation != null) {
									if (allocation.allocated == request.queuedRC) {
										ras.add(allocation);
										processableRequest = true;
										break;
									} else {
										allocation.cancel();
										System.err
												.println("WARNING: an allocation has had to be cancelled with strict allocation mode.");

									}
								}
							} catch (VMManagementException e) {
							}
						}
					}
				} while (++vmNum < request.queuedVMs.length
						&& processableRequest);
				if (processableRequest) {
					try {
						for (int i = 0; i < request.queuedVMs.length; i++) {
							allocation = ras.poll();
							allocation.host.deployVM(request.queuedVMs[i],
									allocation, request.queuedRepo);
						}
						manageQueueRemoval(request);
					} catch (VMManagementException e) {
						processableRequest = false;
					} catch (NetworkException e) {
						System.err
								.println("WARNING: there are connectivity issues in the system."
										+ e.getMessage());
						processableRequest = false;
					}
				}
			}
			while ((allocation = ras.poll()) != null) {
				allocation.cancel();
			}
		}
	}
}
