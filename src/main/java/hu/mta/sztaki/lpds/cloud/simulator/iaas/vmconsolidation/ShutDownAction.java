package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.List;
//import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

/**
 * This class stores actions, which need to shut down a PM in the simulator.
 */
public class ShutDownAction extends Action {

	//Reference to the model of the PM, which needs to shut down
	ModelPM pmToShutDown;

	/**
	 * Constructor for an action to shut a PM down.
	 * @param id The ID of this action.
	 * @param pmToShutDown The reference to the PM inside the simulator to get shut down.
	 */
	public ShutDownAction(int id, ModelPM pmToShutDown) {
		super(id);
		this.pmToShutDown = pmToShutDown;
		//Logger.getGlobal().info("ShutDownAction created");
	}

	public ModelPM getPmToShutDown(){
		return pmToShutDown;
	}

	/**
	 * This method determines the predecessors of this action. A predecessor of 
	 * a shut-down action is a migration from this PM.
	 */
	@Override
	public void determinePredecessors(List<Action> actions) {		
		//looking for migrations with this PM as source
		for(Action action : actions) {
			if(action.getType().equals(Type.MIGRATION)){
				if((((MigrationAction) action).getSource()).equals(this.getPmToShutDown())){
					this.addPredecessor(action);
				}
			}
		}
	}

	@Override
	public Type getType() {
		return Type.SHUTDOWN;
	}

	@Override
	public String toString() {
		return "Action: "+getType()+"  :"+getPmToShutDown().toString();
	}

	/**
	 * This method shuts the PM inside the simulator down.
	 */
	@Override
	public void execute() {
		//Logger.getGlobal().info("ShutDownAction starts to execute");
		PhysicalMachine pm = this.getPmToShutDown().getPM();
		try {
			pm.switchoff(null);
		} catch (VMManagementException e) {
			e.printStackTrace();
		} catch (NetworkException e) {
			e.printStackTrace();
		}
	}

}