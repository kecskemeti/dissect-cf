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

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.ArrayHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;

public abstract class Scheduler {

	public interface QueueingEvent {
		void queueingStarted();
	}

	public static final long unknownPMStateDelay = 1000;

	protected final IaaSService parent;

	protected LinkedList<QueueingData> queue = new LinkedList<QueueingData>();
	protected ResourceConstraints totalQueued = ResourceConstraints.noResources;
	private ArrayList<PhysicalMachine> orderedPMcache = new ArrayList<PhysicalMachine>();
	private int pmCacheLen;
	private ArrayList<QueueingEvent> queueListeners = new ArrayList<Scheduler.QueueingEvent>();
	protected PhysicalMachine.StateChangeListener pmstateChanged = new PhysicalMachine.StateChangeListener() {
		@Override
		public void stateChanged(State oldState, State newState) {
			if (newState.equals(PhysicalMachine.State.RUNNING)) {
				scheduleQueued();
			}
			if (totalQueued.requiredCPUs != 0) {
				notifyListeners();
			}
		}
	};

	protected VMManager.CapacityChangeEvent freeCapacity = new VMManager.CapacityChangeEvent() {
		@Override
		public void capacityChanged(ResourceConstraints newCapacity) {
			scheduleQueued();
			if (!queue.isEmpty()
					&& queue.peek().cumulativeRC.compareTo(parent
							.getRunningCapacities()) > 0) {
				notifyListeners();
			}
		}
	};

	public Scheduler(final IaaSService parent) {
		this.parent = parent;
		parent.subscribeToCapacityChanges(new IaaSService.CapacityChangeEvent() {
			@Override
			public void capacityChanged(ResourceConstraints newCapacity) {
				orderedPMcache = new ArrayList<PhysicalMachine>(parent.machines);
				pmCacheLen = orderedPMcache.size();
				Collections.sort(orderedPMcache,
						new Comparator<PhysicalMachine>() {
							@Override
							public int compare(final PhysicalMachine o1,
									final PhysicalMachine o2) {
								return -o1.getCapacities().compareTo(
										o2.getCapacities());
							}
						});
				for (int i = 0; i < pmCacheLen; i++) {
					PhysicalMachine pm = orderedPMcache.get(i);
					pm.unsubscribeStateChangeEvents(pmstateChanged);
					pm.subscribeStateChangeEvents(pmstateChanged);
					pm.unsubscribeFromIncreasingFreeCapacityChanges(freeCapacity);
					pm.subscribeToIncreasingFreeapacityChanges(freeCapacity);
				}
			}
		});
	}

	public final void scheduleVMrequest(final VirtualMachine[] vms,
			final ResourceConstraints rc, final Repository vaSource,
			final HashMap<String, Object> schedulingConstraints)
			throws VMManagementException {
		final long currentTime = Timed.getFireCount();
		final QueueingData qd = new QueueingData(vms, rc, vaSource,
				schedulingConstraints, currentTime);

		int hostableVMs = 0;
		boolean hostable = false;
		for (int pmid = 0; pmid < pmCacheLen; pmid++) {
			PhysicalMachine machine = orderedPMcache.get(pmid);
			for (int i = 1; i <= vms.length
					&& machine.isHostableRequest(rc.multiply(i)); i++, hostableVMs++)
				;
			if (hostableVMs >= vms.length) {
				hostable = true;
				break;
			}
		}
		if (hostable) {
			boolean wasEmpty = queue.isEmpty();
			queue.offer(qd);
			totalQueued = ResourceConstraints.add(totalQueued, qd.cumulativeRC);
			if (wasEmpty) {
				scheduleQueued();
				if (queue.isEmpty()) {
					return;
				}
				notifyListeners();
			}
		} else {
			throw new VMManagementException(
					"No physical machine is capable to serve this request: "
							+ qd);
		}
	}

	public ResourceConstraints getTotalQueued() {
		return totalQueued;
	}

	public int getQueueLength() {
		return queue.size();
	}

	protected void manageQueueRemoval(final QueueingData qd) {
		queue.remove(qd);
		updateTotalQueuedAfterRemoval(qd);
	}

	protected QueueingData manageQueueRemoval() {
		if (queue.isEmpty()) {
			return null;
		}
		QueueingData removed = queue.remove();
		updateTotalQueuedAfterRemoval(removed);
		return removed;
	}

	private void updateTotalQueuedAfterRemoval(final QueueingData qd) {
		totalQueued = queue.isEmpty() ? ResourceConstraints.noResources
				: ResourceConstraints.subtract(totalQueued, qd.cumulativeRC);
	}

	public final void subscribeQueueingEvents(QueueingEvent e) {
		queueListeners.add(e);
		if (!queue.isEmpty()) {
			e.queueingStarted();
		}
	}

	public final void unsubscribeQueueingEvents(QueueingEvent e) {
		ArrayHandler.removeAndReplaceWithLast(queueListeners, e);
	}

	private void notifyListeners() {
		final int size = queueListeners.size();
		for (int i = 0; i < size; i++) {
			queueListeners.get(i).queueingStarted();
		}
	}

	protected abstract void scheduleQueued();
}
