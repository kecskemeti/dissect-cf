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
 *  (C) Copyright 2016, Gabor Kecskemeti (g.kecskemeti@ljmu.ac.uk)
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */
package hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;

/**
 * A reactive PM controller that increases/decreases the powered on pm set on
 * demands of the vm scheduler. This controller implementation is always
 * changing the machine set size with the amount that matches the size of the VM
 * queue of the VM scheduler. This might cause unrealistic PM number increases
 * which would ruin power lines in real life.
 * 
 * The logic of this controller is very similar to the one in
 * SchedulingDependentMachines, the only difference is how it grows the number
 * of machines needed for the current operation of the infrastructure.
 * 
 * <i>WARNING:</i> this is an experimental class, it is not widely tested,
 * handle with care
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2016"
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2015"
 */

public class MultiPMController extends SchedulingDependentMachines {

	/**
	 * the list of machines that are currently turned on by this controller.
	 */
	private final ArrayList<PhysicalMachine> currentlyStartingPMs = new ArrayList<PhysicalMachine>();

	/**
	 * Removes PMs from the currentlyStartingPMs list if they started properly
	 */
	private final PhysicalMachine.StateChangeListener sweeper = new PhysicalMachine.StateChangeListener() {
		@Override
		public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
			if (newState.equals(PhysicalMachine.State.RUNNING)) {
				currentlyStartingPMs.remove(pm);
				pm.unsubscribeStateChangeEvents(this);
			}
		}
	};

	/**
	 * Constructs the scheduler and passes the parent IaaSService to the
	 * superclass.
	 * 
	 * @param parent
	 *            the IaaSService to serve
	 */
	public MultiPMController(final IaaSService parent) {
		super(parent);
	}

	/**
	 * Implements a reaction to the starting of the VM queue that is capable to
	 * turn on multiple PMs in parallel.
	 */
	@Override
	protected Scheduler.QueueingEvent getQueueingEvent() {
		return new Scheduler.QueueingEvent() {
			@Override
			public void queueingStarted() {
				turnOnSomeMachines();
			}
		};
	}

	/**
	 * Forwards the single PM turnon request to multi PM turnon.
	 */
	@Override
	protected void turnOnAMachine() {
		turnOnSomeMachines();
	}

	/**
	 * Allows the controller to ignore PM requests until the PM startup loop
	 * from a previous PM request is complete
	 */
	private boolean notInTurnonLoop = true;
	
	/**
	 * Turns on as many PMs as many required to fulfill the total resource
	 * requirements of the queued VMs at the VM scheduler of the parent
	 * IaasService
	 */
	protected void turnOnSomeMachines() {
		final int pmsize = parent.machines.size();
		if (parent.runningMachines.size() != pmsize && notInTurnonLoop) {
			notInTurnonLoop = false;
			final AlterableResourceConstraints toSwitchOn = new AlterableResourceConstraints(
					parent.sched.getTotalQueued());
			final double fltfix=toSwitchOn.getRequiredCPUs()/(pmsize*1000);
			final int startingLen = currentlyStartingPMs.size();
			for (int i = 0; i < startingLen; i++) {
				final PhysicalMachine pm = currentlyStartingPMs.get(i);
				if (!pm.isRunning()) {
					toSwitchOn.subtract(currentlyStartingPMs.get(i).getCapacities());
				}
			}
			for (int i = 0; i < pmsize && toSwitchOn.getRequiredCPUs() > fltfix; i++) {
				final PhysicalMachine n = parent.machines.get(i);
				if (PhysicalMachine.ToOfforOff.contains(n.getState())) {
					currentlyStartingPMs.add(n);
					n.turnon();
					n.subscribeStateChangeEvents(sweeper);
					toSwitchOn.subtract(n.getCapacities());
				}
			}
			notInTurnonLoop = true;
		}
	}

}
