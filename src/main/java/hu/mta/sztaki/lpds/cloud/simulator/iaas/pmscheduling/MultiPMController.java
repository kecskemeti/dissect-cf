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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.CapacityChangeEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class MultiPMController extends PhysicalMachineController {

	private class CapacityChangeManager
			implements VMManager.CapacityChangeEvent<ResourceConstraints>, PhysicalMachine.StateChangeListener {

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
		public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
			if (PhysicalMachine.State.RUNNING.equals(newState)) {
				currentlyStartingPMs.remove(pm);
				if (parent.sched.getQueueLength() == 0) {
					new DeferredEvent(observed.getCurrentOnOffDelay()) {
						// Keeps the just started PM on for a short while to
						// allow some new VMs to arrive, otherwise it seems like
						// we just started the PM for no reason
						@Override
						protected void eventAction() {
							if (!observed.isHostingVMs() && observed.isRunning()) {
								switchoffmachine();
							}
						}
					};
				} else {
					ResourceConstraints runningCapacities = parent.getRunningCapacities();
					if (!parent.runningMachines.contains(observed)) {
						// parent have not recognize this PM's startup yet
						runningCapacities = new AlterableResourceConstraints(runningCapacities);
						((AlterableResourceConstraints) runningCapacities).add(observed.getCapacities());
					}
					if (runningCapacities.compareTo(parent.sched.getTotalQueued()) < 0) {
						// no capacities to handle the currently queued jobs, so
						// we need to start up further machines
						turnOnSomeMachines();
					}
				}
			}
		}
	}

	private final HashMap<PhysicalMachine, CapacityChangeManager> capacityManagers = new HashMap<PhysicalMachine, CapacityChangeManager>();
	private final ArrayList<PhysicalMachine> currentlyStartingPMs = new ArrayList<PhysicalMachine>();

	public MultiPMController(final IaaSService parent) {
		super(parent);
	}

	@Override
	protected VMManager.CapacityChangeEvent<PhysicalMachine> getHostRegEvent() {
		return new CapacityChangeEvent<PhysicalMachine>() {
			@Override
			public void capacityChanged(final ResourceConstraints newCapacity, final List<PhysicalMachine> alteredPMs) {
				final boolean newRegistration = parent.isRegisteredHost(alteredPMs.get(0));
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
				turnOnSomeMachines();
			}
		};
	}

	protected void turnOnSomeMachines() {
		final int pmsize = parent.machines.size();
		if (parent.runningMachines.size() != pmsize) {
			final AlterableResourceConstraints toSwitchOn = new AlterableResourceConstraints(
					parent.sched.getTotalQueued());
			final int startingLen = currentlyStartingPMs.size();
			for (int i = 0; i < startingLen; i++) {
				/*
				 * final PhysicalMachine pm = currentlyStartingPMs.get(i); if
				 * (!pm.isRunning()) {
				 */
				toSwitchOn.subtract(currentlyStartingPMs.get(i).getCapacities());
				// }
			}
			for (int i = 0; i < pmsize && toSwitchOn.compareTo(ConstantConstraints.noResources) > 0; i++) {
				final PhysicalMachine n = parent.machines.get(i);
				if (PhysicalMachine.ToOfforOff.contains(n.getState())) {
					currentlyStartingPMs.add(n);
					n.turnon();
					toSwitchOn.subtract(n.getCapacities());
				}
			}
		}
	}

}
