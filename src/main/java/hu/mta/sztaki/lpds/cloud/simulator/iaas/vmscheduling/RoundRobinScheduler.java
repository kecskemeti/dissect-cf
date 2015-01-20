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
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class RoundRobinScheduler extends Scheduler {
	ResourceAllocation[] ras = new ResourceAllocation[5];
	PhysicalMachine nextMachine = null;

	public RoundRobinScheduler(IaaSService parent) {
		super(parent);
	}

	@Override
	protected void scheduleQueued() {
		final int totalMachineNum = parent.runningMachines.size();
		if (totalMachineNum != 0) {
			int startindex = parent.runningMachines.indexOf(nextMachine);
			if (nextMachine == null || startindex < 0) {
				startindex = 0;
			}
			QueueingData request;
			ResourceAllocation allocation;
			boolean processableRequest = true;
			int rasize = 0;
			while (queue.size() > 0 && processableRequest) {
				request = queue.get(0);
				rasize = 0;
				int vmNum = 0;
				do {
					processableRequest = false;
					final int stopIndex = totalMachineNum + startindex;
					for (int i = startindex; i < stopIndex; i++) {
						final PhysicalMachine pm = parent.runningMachines.get(i
								% totalMachineNum);
						if (pm.localDisk.getFreeStorageCapacity() >= request.queuedVMs[vmNum]
								.getVa().size) {
							try {
								allocation = pm.allocateResources(
										request.queuedRC, true,
										PhysicalMachine.defaultAllocLen);
								if (allocation != null) {
									ras[rasize++] = allocation;
									if (rasize == ras.length) {
										ResourceAllocation[] rasnew = new ResourceAllocation[rasize * 2];
										System.arraycopy(ras, 0, rasnew, 0,
												rasize);
										ras = rasnew;
									}
									processableRequest = true;
									startindex = (i + 1) % totalMachineNum;
									break;
								}
							} catch (VMManagementException e) {
							}
						}
					}
				} while (++vmNum < request.queuedVMs.length
						&& processableRequest);
				if (processableRequest) {
					try {
						for (int i = request.queuedVMs.length - 1; i >= 0; i--) {
							rasize--;
							allocation = ras[i];
							allocation.host.deployVM(request.queuedVMs[i],
									allocation, request.queuedRepo);
						}
						manageQueueRemoval(request);
					} catch (VMManagementException e) {
						processableRequest = false;
					} catch (NetworkException e) {
						// Connectivity issues! Should not happen!
						System.err
								.println("WARNING: there are connectivity issues in the system."
										+ e.getMessage());
						processableRequest = false;
					}
				}
			}
			nextMachine = parent.runningMachines.get(startindex);
			for (int i = 0; i < rasize; i++) {
				ras[i].cancel();
			}
		}
	}
}
