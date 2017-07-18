package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

/**
 * This class stores actions, which need to shut down a PM in the simulator
 *
 */
public class ShutDownAction extends Action{

	//Reference to the model of the PM, which needs to shut down
	ModelPM shutdownpm; 
	
	public ShutDownAction(int id, ModelPM shutdownpm) {
		super(id);
		this.shutdownpm = shutdownpm;
	}

	/**
	 * 
	 * @return Reference to the model of the PM, which needs to shut down
	 */
	public ModelPM getShutDownPM(){
		return shutdownpm;
	}

	/**
	 * This method determines the predecessors of this action. A predecessor of 
	 * a shut-down-action is a migration-action, which migrates a VM from this PM
	 */
	@Override
	public void determinePredecessors(ArrayList<Action> actions) {		
		//looking for migrations with this PM as source, which needs to get shut down
		for(int i = 0; i < actions.size(); i++) {
			if(actions.get(i).getType().equals(Type.MIGRATION)){
				if((((MigrationAction) actions.get(i)).getSource()).equals(this.getShutDownPM())){
					this.addPrevious(actions.get(i));
				}
			}
		}
	}

	/**
	 * This Method returns the type of the action
	 */
	public Type getType() {
		return Type.SHUTDOWN;
	}

	@Override
	public String toString() {
		return "Action: "+getType()+"  :"+getShutDownPM().toString();
	}
}