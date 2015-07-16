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

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.CapacityChangeEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

import java.util.HashMap;
import java.util.List;

public class SchedulingDependentMachines extends PhysicalMachineController {

	private class CapacityChangeManager implements
			VMManager.CapacityChangeEvent<ResourceConstraints>,
			PhysicalMachine.StateChangeListener {

		final PhysicalMachine observed;

		public CapacityChangeManager(final PhysicalMachine pm) {
			observed = pm;
			observed.subscribeToIncreasingFreeapacityChanges(this);
			observed.subscribeStateChangeEvents(this);
		}

		private void switchoffmachine() {
			try {
				observed.switchoff(null);
				// These exceptions below are only relevant if migration
				// is requested now they will never come.
			} catch (VMManagementException e) {
			} catch (NetworkException e) {
			}
		}

		@Override
		public void capacityChanged(final ResourceConstraints newCapacity,
				final List<ResourceConstraints> newlyFreeCapacities) {
			if (observed.getCapacities().compareTo(newCapacity) <= 0
					&& parent.sched.getTotalQueued().getRequiredCPUs() == 0) {
				// Totally free machine and nothing queued
				switchoffmachine();
			}
		}

		@Override
		public void stateChanged(State oldState, State newState) {
			if (PhysicalMachine.State.RUNNING.equals(newState)) {
				currentlyStartingPM = null;
				if (parent.sched.getQueueLength() == 0) {
					new DeferredEvent(observed.getCurrentOnOffDelay()) {
						// Keeps the just started PM on for a short while to
						// allow some new VMs to arrive, otherwise it seems like
						// we just started the PM for no reason
						@Override
						protected void eventAction() {
							if (!observed.isHostingVMs()
									&& observed.isRunning()) {
								switchoffmachine();
							}
						}
					};
				} else {
					ResourceConstraints runningCapacities = parent
							.getRunningCapacities();
					if (!parent.runningMachines.contains(observed)) {
						// parent have not recognize this PM's startup yet
						runningCapacities = new AlterableResourceConstraints(
								runningCapacities);
						runningCapacities.add(observed.getCapacities());
					}
					if (runningCapacities.compareTo(parent.sched
							.getTotalQueued()) < 0) {
						// no capacities to handle the currently queued jobs, so
						// we need to start up further machines
						turnOnAMachine();
					}
				}
			}
		}
	}

	private HashMap<PhysicalMachine, CapacityChangeManager> capacityManagers = new HashMap<PhysicalMachine, CapacityChangeManager>();
	private PhysicalMachine currentlyStartingPM = null;

	public SchedulingDependentMachines(final IaaSService parent) {
		super(parent);
	}

	@Override
	protected VMManager.CapacityChangeEvent<PhysicalMachine> getHostRegEvent() {
		return new CapacityChangeEvent<PhysicalMachine>() {
			@Override
			public void capacityChanged(final ResourceConstraints newCapacity,
					final List<PhysicalMachine> alteredPMs) {
				final boolean newRegistration = parent
						.isRegisteredHost(alteredPMs.get(0));
				final int pmNum = alteredPMs.size();
				if (newRegistration) {
					// Management of capacity increase
					for (int i = pmNum - 1; i >= 0; i--) {
						final PhysicalMachine pm = alteredPMs.get(i);
						capacityManagers.put(pm, new CapacityChangeManager(pm));
					}
				} else {
					// Management of capacity decrease
					for (int i = pmNum - 1; i >= 0; i--) {
						capacityManagers.remove(alteredPMs.get(i));
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
				if (currentlyStartingPM == null
						|| PhysicalMachine.State.RUNNING
								.equals(currentlyStartingPM.getState())) {
					// If there are no machines under their startup procedure,
					// or the currently started up machine is already running
					// and we still receive the queueingstarted event
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
					currentlyStartingPM = n;
					n.turnon();
					break;
				}
			}
		}
	}
}
