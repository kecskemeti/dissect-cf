package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.List;
import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.IControllablePmScheduler;

/**
 * This class stores actions, which need to start a PM in the simulator.
 */
public class StartAction extends Action implements PhysicalMachine.StateChangeListener {

	//Reference to the model of the PM, which needs to start
	ModelPM pmToStart;

	/** PM scheduler */
	IControllablePmScheduler pmScheduler;

	/**
	 * Constructor of an action to start a PM.
	 * @param id The ID of this action.
	 * @param pmToStart The modelled PM respresenting the PM which shall start.
	 */
	public StartAction(int id, ModelPM pmToStart, IControllablePmScheduler pmScheduler) {
		super(id);
		this.pmToStart = pmToStart;
		this.pmScheduler = pmScheduler;
	}

	/**
	 * 
	 * @return The modelled PM respresenting the PM which shall start.
	 */
	public ModelPM getPmToStart(){
		return pmToStart;
	}

	/**
	 * There are no predecessors for a starting action.
	 */
	@Override
	public void determinePredecessors(List<Action> actions) {
	}

	@Override
	public Type getType() {
		return Type.START;
	}

	@Override
	public String toString() {
		return "Action: "+getType()+"  :"+getPmToStart().toShortString();
	}

	/**
	 * Method for starting a PM inside the simulator.
	 */
	@Override
	public void execute() {
		Logger.getGlobal().info("Executing: "+toString());
		PhysicalMachine pm = this.getPmToStart().getPM();
		pm.subscribeStateChangeEvents(this);		//observe the PM before turning it on
		pmScheduler.switchOn(pm);
	}

	/**
	 * The stateChanged-logic, if the PM which has been started changes its state to RUNNING,
	 * we can stop observing it.
	 */
	@Override
	public void stateChanged(PhysicalMachine pm, hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State oldState,
			hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State newState) {
		if(newState.equals(PhysicalMachine.State.RUNNING)){
			pm.unsubscribeStateChangeEvents(this);
			finished();
		}
	}
}
