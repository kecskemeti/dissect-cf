package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;

/**
 * This class stores actions, which need to start a PM in the simulator.
 */
public class StartAction extends Action implements PhysicalMachine.StateChangeListener {

	//Reference to the model of the PM, which needs to start
	ModelPM pmToStart;

	public StartAction(int id, ModelPM pmToStart) {
		super(id);
		this.pmToStart = pmToStart;
	}

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
		return "Action: "+getType()+"  :"+getPmToStart().toString();
	}

	@Override
	public void execute() {
		PhysicalMachine pm = this.getPmToStart().getPM();
		pm.subscribeStateChangeEvents(this);
		pm.turnon();
	}

	@Override
	public void stateChanged(PhysicalMachine pm, hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State oldState,
			hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State newState) {
		if(newState.equals(PhysicalMachine.State.RUNNING)){
			pm.unsubscribeStateChangeEvents(this);
			finished();
		}
	}
}
