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
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.CapacityChangeEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

import java.util.HashMap;
import java.util.List;

/**
 * A reactive PM controller that increases/decreases the powered on pm set on
 * demands of the vm scheduler. This controller implementation is always
 * changing the machine set size by one, thus it is not applicable in cases when
 * there are rapid demand changes on the infrastructure.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 */
public class SchedulingDependentMachines extends PhysicalMachineController {

	/**
	 * The main PM control mechanisms are implemented in this class
	 * 
	 * The class basically controls a single PM. It switches off its PM when the
	 * PM's free resources reach its complete resource set and there are no VM
	 * requests queuing at the IaaS service's VM scheduler.
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
	 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
	 */
	private class CapacityChangeManager
			implements VMManager.CapacityChangeEvent<ResourceConstraints>, PhysicalMachine.StateChangeListener {

		/**
		 * the physical machine that this capacity manager will target with its
		 * operations
		 */
		final PhysicalMachine observed;

		/**
		 * This constructor is expected to be used once a PM is registered to
		 * the parent IaaSService. From that point on the newly registered PM
		 * will be considered for switching off/turning on.
		 * 
		 * @param pm
		 *            the PM to be managed to follow the demand patterns of the
		 *            VM requests to the IaaSService.
		 */
		public CapacityChangeManager(final PhysicalMachine pm) {
			observed = pm;
			observed.subscribeToIncreasingFreeapacityChanges(this);
			observed.subscribeStateChangeEvents(this);
		}

		/**
		 * if the PM gets dropped from the parent IaaSService then we no longer
		 * need to control its behavior. So the class's active behavior is
		 * disabled by calling this function.
		 */
		private void cancelEvents() {
			observed.unsubscribeFromIncreasingFreeCapacityChanges(this);
			observed.unsubscribeStateChangeEvents(this);
		}

		/**
		 * Allows the observed PM to be switched off and its exceptions handled
		 */
		private void switchoffmachine() {
			try {
				observed.switchoff(null);
				// These exceptions below are only relevant if migration
				// is requested now they will never come.
			} catch (VMManagementException e) {
			} catch (NetworkException e) {
			}
		}

		/**
		 * This function is called when the observed PM has newly freed up
		 * resources. It ensures that the PM is switched off if the PM is
		 * completely free and there are no more VMs queuing at the VM scheduler
		 * (i.e., there will be no chance to receive a new VM for the capacities
		 * of this PM).
		 */
		@Override
		public void capacityChanged(final ResourceConstraints newCapacity,
				final List<ResourceConstraints> newlyFreeCapacities) {
			if (observed.getCapacities().compareTo(newCapacity) <= 0
					&& parent.sched.getTotalQueued().getRequiredCPUs() == 0) {
				// Totally free machine and nothing queued
				switchoffmachine();
			}
		}

		/**
		 * This function is called when the PM's power state changes. This event
		 * handler manages situations when the PM is turned on but there are no
		 * longer tasks for it. Also, it initiates a new PM's switchon if the
		 * newly switched on machine will not be enough to host the queued VM
		 * requests found at the VM scheduler of the parent IaaS service.
		 */
		@Override
		public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
			if (PhysicalMachine.State.RUNNING.equals(newState)) {
				currentlyStartingPM = null;
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
						((AlterableResourceConstraints) runningCapacities).singleAdd(observed.getCapacities());
					}
					if (runningCapacities.compareTo(parent.sched.getTotalQueued()) < 0) {
						// no capacities to handle the currently queued jobs, so
						// we need to start up further machines
						turnOnAMachine();
					}
				}
			}
		}
	}

	/**
	 * this map lists all the currently controlled PMs and their controllers.
	 */
	private HashMap<PhysicalMachine, CapacityChangeManager> capacityManagers = new HashMap<PhysicalMachine, CapacityChangeManager>();
	/**
	 * ensures that we only have a single machine switching on at a time and
	 * shows what is the actual machine that is switching on.
	 */
	private PhysicalMachine currentlyStartingPM = null;

	/**
	 * Constructs the scheduler and passes the parent IaaSService to the
	 * superclass.
	 * 
	 * @param parent
	 *            the IaaSService to serve
	 */
	public SchedulingDependentMachines(final IaaSService parent) {
		super(parent);
	}

	/**
	 * Defines to do the following when a new host is (de)registered to the
	 * parent IaaSService:
	 * <ul>
	 * <li>if the current event is a registration event then the function
	 * creates and locally registers a new capacity change manager for the newly
	 * registered pms
	 * <li>if the current event is a deregistration event then the function
	 * cancels the capacity management for all deregistered pms
	 * </ul>
	 */
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
						capacityManagers.remove(alteredPMs.get(i)).cancelEvents();
					}
				}
			}
		};
	}

	/**
	 * Defines to do the following when VM requests arrive:
	 * <ol>
	 * <li>check if there is already a PM under preparation (if there is one
	 * then does nothing)
	 * <li>if there are no PM that is currently prepared then starts to prepare
	 * one for accepting VM requests.
	 * </ol>
	 */
	@Override
	protected Scheduler.QueueingEvent getQueueingEvent() {
		return new Scheduler.QueueingEvent() {
			@Override
			public void queueingStarted() {
				if (currentlyStartingPM == null
						|| PhysicalMachine.State.RUNNING.equals(currentlyStartingPM.getState())) {
					// If there are no machines under their startup procedure,
					// or the currently started up machine is already running
					// and we still receive the queueingstarted event
					turnOnAMachine();
				}
			}
		};
	}

	/**
	 * switches on a not yet switched on machine from the parent IaaS's PM set.
	 * if there are no more machines in the IaaS that can be turned on then the
	 * calling of this function is ignored.
	 */
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
