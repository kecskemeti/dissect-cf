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
 *  (C) Copyright 2019-20, Gabor Kecskemeti, Rene Ponto, Zoltan Mann
 */
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.actions;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.IControllablePmScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelPM;

/**
 * This class stores actions, which need to start a PM in the simulator.
 */
public class StartAction extends Action implements PhysicalMachine.StateChangeListener {

	// Reference to the model of the PM, which needs to start
	public final ModelPM pmToStart;

	/** PM scheduler */
	public final IControllablePmScheduler pmScheduler;

	/**
	 * Constructor of an action to start a PM.
	 * 
	 * @param pmToStart The modelled PM representing the PM which shall start.
	 */
	public StartAction(final ModelPM pmToStart, final IControllablePmScheduler pmScheduler) {
		super(Type.START);
		this.pmToStart = pmToStart;
		this.pmScheduler = pmScheduler;
	}

	/**
	 * There are no predecessors for a starting action.
	 */
	@Override
	public void determinePredecessors(final Action[] actions) {
	}

	@Override
	public String toString() {
		return super.toString() + pmToStart.toShortString();
	}

	/**
	 * Method for starting a PM inside the simulator.
	 */
	@Override
	public void execute() {
		final PhysicalMachine pm = this.pmToStart.getPM();
		if(PhysicalMachine.ToOnorRunning.contains(pm.getState())) {
			finished();
			return;
		}
		pm.subscribeStateChangeEvents(this); // observe the PM before turning it on
		pmScheduler.switchOn(pm);
	}

	/**
	 * The stateChanged-logic, if the PM which has been started changes its state to
	 * RUNNING, we can stop observing it.
	 */
	@Override
	public void stateChanged(final PhysicalMachine pm, final PhysicalMachine.State oldState,
			final PhysicalMachine.State newState) {
		if (newState.equals(PhysicalMachine.State.RUNNING)) {
			pm.unsubscribeStateChangeEvents(this);
			finished();
		}
	}
}
