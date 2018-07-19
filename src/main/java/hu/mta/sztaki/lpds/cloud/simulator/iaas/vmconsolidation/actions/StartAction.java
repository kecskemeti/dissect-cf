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
	 * @param pmToStart The modelled PM respresenting the PM which shall start.
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
