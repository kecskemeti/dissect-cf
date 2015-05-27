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

import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.pmiterators.PMIterator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class FirstFitScheduler extends Scheduler {

    ResourceAllocation[] ras = new ResourceAllocation[5];
    private final PMIterator it;
    
    public FirstFitScheduler(IaaSService parent) {
        super(parent);
        it=new PMIterator(parent.runningMachines);
    }
    
    protected PMIterator getPMIterator() {
        it.reset();
        return it;
    }

    @Override
    protected void scheduleQueued() {
        final PMIterator currIterator=getPMIterator();
        if (currIterator.hasNext()) {
            QueueingData request;
            ResourceAllocation allocation;
            boolean processableRequest = true;
            int vmNum=0;
            while (queue.size() > 0 && processableRequest) {
                request = queue.get(0);
                vmNum = 0;
                do {
                    processableRequest = false;
                    do {
                        final PhysicalMachine pm = currIterator.next();
                        if (pm.localDisk.getFreeStorageCapacity() >= request.queuedVMs[vmNum]
                                .getVa().size) {
                            try {
                                allocation = pm.allocateResources(
                                        request.queuedRC, true,
                                        PhysicalMachine.defaultAllocLen);
                                if (allocation != null) {
                                	currIterator.markLastCollected();
                                        if (vmNum == ras.length) {
                                            ResourceAllocation[] rasnew = new ResourceAllocation[vmNum * 2];
                                            System.arraycopy(ras, 0, rasnew, 0,
                                                    vmNum);
                                            ras = rasnew;
                                        }
                                        ras[vmNum] = allocation;
                                        processableRequest = true;
                                        break;
 
                                }
                            } catch (VMManagementException e) {
                            }
                        }
                    } while(currIterator.hasNext());
                    currIterator.restart(true);
                } while (++vmNum < request.queuedVMs.length
                        && processableRequest);
                if (processableRequest) {
                    try {
                        for (int i = request.queuedVMs.length - 1; i >= 0; i--) {
                            vmNum--;
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
            vmNum--;
            for (int i = 0; i < vmNum; i++) {
                ras[i].cancel();
            }
        }
    }
}
