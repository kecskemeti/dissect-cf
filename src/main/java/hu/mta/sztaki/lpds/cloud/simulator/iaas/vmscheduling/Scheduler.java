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
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.UnalterableConstraintsPropagator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2012"
 */
public abstract class Scheduler {

	public interface QueueingEvent {

		void queueingStarted();
	}

	protected final IaaSService parent;

	protected List<QueueingData> queue = new LinkedList<QueueingData>();
	protected AlterableResourceConstraints totalQueued = AlterableResourceConstraints.getNoResources();
	protected UnalterableConstraintsPropagator publicTQ = new UnalterableConstraintsPropagator(totalQueued);
	private ArrayList<PhysicalMachine> orderedPMcache = new ArrayList<PhysicalMachine>();
	private int pmCacheLen;
	private ArrayList<QueueingEvent> queueListeners = new ArrayList<Scheduler.QueueingEvent>();
	private ConstantConstraints minimumSchedulerRequirement = ConstantConstraints.noResources;
	private AlterableResourceConstraints freeResourcesSinceLastSchedule = AlterableResourceConstraints.getNoResources();

	public final static Comparator<PhysicalMachine> pmComparator = new Comparator<PhysicalMachine>() {
		@Override
		public int compare(final PhysicalMachine o1, final PhysicalMachine o2) {
			// Ensures inverse order based on capacities
			return -o1.getCapacities().compareTo(o2.getCapacities());
		}
	};

	protected PhysicalMachine.StateChangeListener pmstateChanged = new PhysicalMachine.StateChangeListener() {
		@Override
		public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
			if (newState.equals(PhysicalMachine.State.RUNNING)) {
				freeResourcesSinceLastSchedule.singleAdd(pm.freeCapacities);
				if (freeResourcesSinceLastSchedule.compareTo(minimumSchedulerRequirement) >= 0) {
					invokeRealScheduler();
				}
			}
			if (totalQueued.getRequiredCPUs() != 0) {
				notifyListeners();
			}
		}
	};

	protected VMManager.CapacityChangeEvent<ResourceConstraints> freeCapacity = new VMManager.CapacityChangeEvent<ResourceConstraints>() {
		@Override
		public void capacityChanged(final ResourceConstraints newCapacity,
				final List<ResourceConstraints> newlyFreeResources) {
			freeResourcesSinceLastSchedule.add(newlyFreeResources);
			if (totalQueued.getRequiredCPUs() != 0) {
				if (freeResourcesSinceLastSchedule.compareTo(minimumSchedulerRequirement) >= 0) {
					invokeRealScheduler();
				}
				if (totalQueued.getRequiredCPUs() != 0
						&& queue.get(0).cumulativeRC.compareTo(parent.getRunningCapacities()) > 0) {
					notifyListeners();
				}
			}
		}
	};

	public Scheduler(final IaaSService parent) {
		this.parent = parent;
		parent.subscribeToCapacityChanges(new VMManager.CapacityChangeEvent<PhysicalMachine>() {
			@Override
			public void capacityChanged(final ResourceConstraints newCapacity, final List<PhysicalMachine> alteredPMs) {
				final boolean newRegistration = parent.isRegisteredHost(alteredPMs.get(0));
				final int pmNum = alteredPMs.size();
				if (newRegistration) {
					// Increased pm count
					orderedPMcache.addAll(alteredPMs);
					Collections.sort(orderedPMcache, pmComparator);
					pmCacheLen += pmNum;
					for (int i = 0; i < pmNum; i++) {
						final PhysicalMachine pm = alteredPMs.get(i);
						pm.subscribeStateChangeEvents(pmstateChanged);
						pm.subscribeToIncreasingFreeapacityChanges(freeCapacity);
					}
				} else {
					// Decreased pm count
					for (int i = 0; i < pmNum; i++) {
						final PhysicalMachine pm = alteredPMs.get(i);
						orderedPMcache.remove(pm);
						pm.unsubscribeStateChangeEvents(pmstateChanged);
						pm.unsubscribeFromIncreasingFreeCapacityChanges(freeCapacity);
					}
					pmCacheLen -= pmNum;
				}
			}
		});
	}

	public final void scheduleVMrequest(final VirtualMachine[] vms, final ResourceConstraints rc,
			final Repository vaSource, final HashMap<String, Object> schedulingConstraints)
					throws VMManagementException {
		final long currentTime = Timed.getFireCount();
		final QueueingData qd = new QueueingData(vms, rc, vaSource, schedulingConstraints, currentTime);

		int hostableVMs = 0;
		boolean hostable = false;
		for (int pmid = 0; pmid < pmCacheLen; pmid++) {
			PhysicalMachine machine = orderedPMcache.get(pmid);
			AlterableResourceConstraints biggestHostable = new AlterableResourceConstraints(rc);
			for (int i = 1; i <= vms.length; i++, hostableVMs++) {
				if (!machine.isHostableRequest(biggestHostable)) {
					break;
				}
				biggestHostable.singleAdd(rc);
			}
			if (hostableVMs >= vms.length) {
				hostable = true;
				break;
			}
		}
		if (hostable) {
			boolean wasEmpty = queue.isEmpty();
			queue.add(qd);
			totalQueued.singleAdd(qd.cumulativeRC);
			if (wasEmpty) {
				invokeRealScheduler();
				if (queue.size() == 0) {
					return;
				}
				notifyListeners();
			} else {
				minimumSchedulerRequirement = ConstantConstraints.noResources;
			}
		} else {
			throw new VMManagementException("No physical machine is capable to serve this request: " + qd);
		}
	}

	public final boolean dropVMrequest(final VirtualMachine vm) {
		final Iterator<QueueingData> it = queue.iterator();
		while (it.hasNext()) {
			final QueueingData qd = it.next();
			for (int i = 0; i < qd.queuedVMs.length; i++) {
				if (qd.queuedVMs[i] == vm) {
					it.remove();
					for (i = 0; i < qd.queuedVMs.length; i++) {
						// Mark all VMs in the request to be nonservable
						qd.queuedVMs[i].setNonservable();
					}
					updateTotalQueuedAfterRemoval(qd);
					return true;
				}
			}
		}
		return false;
	}

	public ResourceConstraints getTotalQueued() {
		return publicTQ;
	}

	public int getQueueLength() {
		return queue.size();
	}

	/**
	 * Removes an arbitrary item from the queue
	 * 
	 * @param qd
	 */
	protected void manageQueueRemoval(final QueueingData qd) {
		queue.remove(qd);
		updateTotalQueuedAfterRemoval(qd);
	}

	/**
	 * Removes the head of the queue
	 */
	protected QueueingData manageQueueRemoval() {
		if (queue.isEmpty()) {
			return null;
		}
		QueueingData removed = queue.remove(0);
		updateTotalQueuedAfterRemoval(removed);
		return removed;
	}

	private void updateTotalQueuedAfterRemoval(final QueueingData qd) {
		if (queue.isEmpty()) {
			totalQueued.subtract(totalQueued);
			minimumSchedulerRequirement = ConstantConstraints.noResources;
		} else {
			totalQueued.subtract(qd.cumulativeRC);
		}
	}

	public final void subscribeQueueingEvents(QueueingEvent e) {
		queueListeners.add(e);
		if (queue.size() != 0) {
			e.queueingStarted();
		}
	}

	public final void unsubscribeQueueingEvents(QueueingEvent e) {
		queueListeners.remove(e);
	}

	protected void notifyListeners() {
		final int size = queueListeners.size();
		for (int i = 0; i < size; i++) {
			queueListeners.get(i).queueingStarted();
		}
	}

	public List<VirtualMachine> getQueuedVMs() {
		ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>(queue.size());
		for (QueueingData qd : queue) {
			vms.addAll(Arrays.asList(qd.queuedVMs));
		}
		return vms;
	}

	private void invokeRealScheduler() {
		minimumSchedulerRequirement = scheduleQueued();
		freeResourcesSinceLastSchedule.subtract(freeResourcesSinceLastSchedule);
	}

	protected abstract ConstantConstraints scheduleQueued();
}
