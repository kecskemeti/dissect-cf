package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;

/**
 * This class stores actions, which need to start a PM in the simulator
 *
 */
public class StartAction extends Action{

	//Reference to the model of the PM, which needs to start
	ModelPM startpm;
	
	public StartAction(int id, ModelPM startpm) {
		super(id);
		this.startpm = startpm;
	}
	
	/**
	 * 
	 * @return Reference to the model of the PM, which needs to shut down
	 */
	public ModelPM getStartPM(){
		return startpm;
	}
	
	/**
	 * There are no predecessors for a starting-action. 
	 */
	@Override
	public void determinePredecessors(ArrayList<Action> actions) {
		
	}
	/**
	 * This Method returns the type of the action
	 */
	public Type getType() {
		return Type.START;
	}
	@Override
	public String toString() {
		return "Action: "+getType()+"  :"+getStartPM().toString();
	}

	@Override
	public void execute() {
		PhysicalMachine pm = this.getStartPM().getPM();
		pm.turnon();
	}
}