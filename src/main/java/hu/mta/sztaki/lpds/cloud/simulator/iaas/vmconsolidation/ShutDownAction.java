package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.List;
//import java.util.logging.Logger;
import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.IControllablePmScheduler;

/**
 * This class stores actions, which need to shut down a PM in the simulator.
 */
public class ShutDownAction extends Action {

	//Reference to the model of the PM, which needs to shut down
	ModelPM pmToShutDown;

	/** PM scheduler */
	IControllablePmScheduler pmScheduler;

	/**
	 * Constructor for an action to shut a PM down.
	 * @param id The ID of this action.
	 * @param pmToShutDown The reference to the PM inside the simulator to get shut down.
	 * @param pmScheduler Reference to the PM scheduler of the IaaS service
	 */
	public ShutDownAction(int id, ModelPM pmToShutDown, IControllablePmScheduler pmScheduler) {
		super(id);
		this.pmToShutDown = pmToShutDown;
		this.pmScheduler = pmScheduler;
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
		return "Action: "+getType()+"  :"+getPmToShutDown().toShortString();
	}

	/**
	 * This method shuts the PM inside the simulator down.
	 */
	@Override
	public void execute() {
		Logger.getGlobal().info("Executing at "+Timed.getFireCount()+": "+toString());
		PhysicalMachine pm = this.getPmToShutDown().getPM();
		if(pm.isHostingVMs())
			Logger.getGlobal().info("PM not empty -> nothing to do");
		else
			pmScheduler.switchOff(pm);
	}

}