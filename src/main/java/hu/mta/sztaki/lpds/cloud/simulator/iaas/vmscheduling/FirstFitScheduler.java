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
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.pmiterators.PMIterator;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class FirstFitScheduler extends Scheduler {

	ResourceAllocation[] ras = new ResourceAllocation[5];
	ResourceAllocation raBiggestNotSuitable = null;
	private final PMIterator it;

	public FirstFitScheduler(IaaSService parent) {
		super(parent);
		it = new PMIterator(parent.runningMachines);
	}

	protected PMIterator getPMIterator() {
		it.reset();
		return it;
	}

	@Override
	protected ConstantConstraints scheduleQueued() {
		final PMIterator currIterator = getPMIterator();
		ConstantConstraints returner = new ConstantConstraints(getTotalQueued());
		if (currIterator.hasNext()) {
			QueueingData request;
			ResourceAllocation allocation;
			boolean processableRequest = true;
			int vmNum = 0;
			while (queue.size() > 0 && processableRequest) {
				request = queue.get(0);
				vmNum = 0;
				do {
					processableRequest = false;
					do {
						final PhysicalMachine pm = currIterator.next();
						if (pm.localDisk.getFreeStorageCapacity() >= request.queuedVMs[vmNum].getVa().size) {
							try {
								allocation = pm.allocateResources(request.queuedRC, false,
										PhysicalMachine.defaultAllocLen);
								if (allocation != null) {
									if (allocation.allocated.compareTo(request.queuedRC) >= 0) {
										// Successful allocation
										currIterator.markLastCollected();
										if (vmNum == ras.length) {
											ResourceAllocation[] rasnew = new ResourceAllocation[vmNum * 2];
											System.arraycopy(ras, 0, rasnew, 0, vmNum);
											ras = rasnew;
										}
										if (raBiggestNotSuitable != null) {
											raBiggestNotSuitable.cancel();
											raBiggestNotSuitable = null;
										}
										ras[vmNum] = allocation;
										processableRequest = true;
										break;
									} else {
										if (raBiggestNotSuitable == null) {
											raBiggestNotSuitable = allocation;
										} else {
											if (allocation.allocated.compareTo(raBiggestNotSuitable.allocated) > 0) {
												raBiggestNotSuitable.cancel();
												raBiggestNotSuitable = allocation;
											} else {
												allocation.cancel();
											}
										}
									}
								}
							} catch (VMManagementException e) {
							}
						}
					} while (currIterator.hasNext());
					currIterator.restart(true);
				} while (++vmNum < request.queuedVMs.length && processableRequest);
				if (processableRequest) {
					try {
						for (int i = request.queuedVMs.length - 1; i >= 0; i--) {
							vmNum--;
							allocation = ras[i];
							allocation.getHost().deployVM(request.queuedVMs[i], allocation, request.queuedRepo);
						}
						manageQueueRemoval(request);
					} catch (VMManagementException e) {
						processableRequest = false;
					} catch (NetworkException e) {
						// Connectivity issues! Should not happen!
						System.err.println("WARNING: there are connectivity issues in the system." + e.getMessage());
						processableRequest = false;
					}
				} else {
					AlterableResourceConstraints arc = new AlterableResourceConstraints(request.queuedRC);
					arc.multiply(vmNum - 1);
					if (raBiggestNotSuitable != null) {
						arc = new AlterableResourceConstraints(request.queuedRC);
						arc.subtract(raBiggestNotSuitable.allocated);
						raBiggestNotSuitable.cancel();
						raBiggestNotSuitable = null;
					}
					returner = new ConstantConstraints(arc);
				}
			}
			vmNum--;
			for (int i = 0; i < vmNum; i++) {
				ras[i].cancel();
			}
		}
		return returner;
	}
}
