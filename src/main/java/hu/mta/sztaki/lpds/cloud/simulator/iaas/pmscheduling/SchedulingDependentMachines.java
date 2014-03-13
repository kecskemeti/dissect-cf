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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.CapacityChangeEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

import java.util.ArrayList;
import java.util.Iterator;

public class SchedulingDependentMachines extends PhysicalMachineController {
	private class CapacityChangeManager implements
			VMManager.CapacityChangeEvent, PhysicalMachine.StateChangeListener {
		final PhysicalMachine observed;
		boolean turningOn = false;

		public CapacityChangeManager(final PhysicalMachine pm) {
			observed = pm;
			observed.subscribeToIncreasingFreeapacityChanges(this);
			observed.subscribeStateChangeEvents(this);
		}

		private void switchoffmachine() {
			try {
				observed.switchoff(null);
			} catch (VMManagementException e) {
			} catch (NetworkException e) {
			}
		}

		@Override
		public void capacityChanged(final ResourceConstraints newCapacity) {
			if (observed.getCapacities().compareTo(newCapacity) <= 0
					&& parent.sched.getTotalQueued().requiredCPUs == 0) {
				switchoffmachine();
			}
		}

		@Override
		public void stateChanged(State oldState, State newState) {
			switch (newState) {
			case SWITCHINGON:
				turningOn = true;
				noMachineTurningOn = false;
				break;
			case RUNNING:
				turningOn = false;
				noMachineTurningOn = true;
				int cmSize = capacityManagers.size();
				for (int i = 0; i < cmSize && noMachineTurningOn; i++) {
					noMachineTurningOn &= !capacityManagers.get(i).turningOn;
				}
				if (parent.sched.getQueueLength() == 0) {
					if (!observed.isHostingVMs()) {
						switchoffmachine();
					}
				} else if (noMachineTurningOn
						&& parent.getRunningCapacities().compareTo(
								parent.sched.getTotalQueued()) > 0) {
					turnOnAMachine();
				}
			default:
				break;
			}
		}
	}

	private ArrayList<CapacityChangeManager> capacityManagers = new ArrayList<CapacityChangeManager>();
	private boolean noMachineTurningOn = true;

	public SchedulingDependentMachines(final IaaSService parent) {
		super(parent);
	}

	@Override
	protected VMManager.CapacityChangeEvent getHostRegEvent() {
		return new CapacityChangeEvent() {
			@Override
			public void capacityChanged(final ResourceConstraints newCapacity) {
				int cmSize = capacityManagers.size();
				machineloop: for (final PhysicalMachine pm : parent.machines) {
					for (int j = 0; j < cmSize; j++) {
						if (capacityManagers.get(j).observed == pm)
							continue machineloop;
					}
					capacityManagers.add(new CapacityChangeManager(pm));
					cmSize++;
				}
				final Iterator<CapacityChangeManager> it = capacityManagers
						.iterator();
				while (it.hasNext()) {
					final CapacityChangeManager c = it.next();
					if (!parent.machines.contains(c.observed)) {
						c.observed
								.unsubscribeFromIncreasingFreeCapacityChanges(c);
						it.remove();
					}
				}
			}
		};
	}

	@Override
	protected Scheduler.QueueingEvent getQueueingEvent() {
		return new Scheduler.QueueingEvent() {
			@Override
			public void queueingStarted() {
				if (noMachineTurningOn) {
					turnOnAMachine();
				}
			}
		};
	}

	protected void turnOnAMachine() {
		final int pmsize = parent.machines.size();
		if (parent.runningMachines.size() != pmsize) {
			for (int i = 0; i < pmsize; i++) {
				final PhysicalMachine n = parent.machines.get(i);
				if (PhysicalMachine.ToOfforOff.contains(n.getState())) {
					n.turnon();
					break;
				}
			}
		}
	}
}
