package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.List;
//import java.util.logging.Logger;
import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

/**
 * This class stores actions, that need to commit a migration in the simulator.
 */
public class MigrationAction extends Action implements VirtualMachine.StateChange {

	//Reference to the model of the PM, which contains the VM before migrating it
	ModelPM source;

	//Reference to the model of the PM, which contains the VM after migrating it
	ModelPM target;

	//Reference to the model of the VM, which needs be migrated
	ModelVM mvm;

	/**
	 * Constructor for an action which shall migrate a VM inside the simulator.
	 * @param id The ID of this action.
	 * @param source The PM which is currently hosting the VM.
	 * @param target The PM which shall host this VM after migration.
	 * @param vm The reference to the VM which shall be migrated.
	 */
	public MigrationAction(int id, ModelPM source, ModelPM target, ModelVM vm) {
		super(id);
		this.source = source;
		this.target = target;
		this.mvm = vm;
	}

	/**
	 * 
	 * @return Reference to the model of the PM, which contains the VM before migrating it
	 */
	public ModelPM getSource(){
		return source;
	}
	
	/**
	 * 
	 * @return Reference to the model of the PM, which contains the VM after migrating it
	 */
	public ModelPM getTarget(){
		return target;
	}
	
	/**
	 * 
	 * @return Reference to the model of the VM, which needs be migrated
	 */
	public ModelVM getVm(){
		return mvm;
	}

	/**
	 * This method determines the predecessors of this action. A predecessor of
	 * a migration action is a starting action, which starts the target PM of 
	 * this action. 
	 * Furthermore, migrations from our target PM are also considered 
	 * predecessors, in order to prohibit temporary overloads of the PM.
	 * TODO: this needs improvement, as it can currently lead to deadlocks.
	 */
	@Override
	public void determinePredecessors(List<Action> actions) {
		//looking for actions where a PM gets started, that is the target of this migration
		for(Action action : actions) {
			if(action.getType().equals(Type.START)){
				if((((StartAction) action).getPmToStart()).equals(getTarget())){
					this.addPredecessor(action);
				}
			}
			// If two PMs would like to migrate one VM to each other,
			// there could be a loop. Not solved yet.
			if(action.getType().equals(Type.MIGRATION)){
				if((((MigrationAction) action).getSource()).equals(this.getTarget())){
					this.addPredecessor(action);
				}
			}
		}	
	}

	@Override
	public Type getType() {
		return Type.MIGRATION;
	}

	@Override
	public String toString() {
		return "Action: "+getType()+" Source:  "+getSource().toShortString()+" Target: "+getTarget().toShortString()+" VM: "+getVm().toShortString();
	}

	/**
	 * Method for doing the migration inside the simulator.
	 */
	@Override
	public void execute() {
		Logger.getGlobal().info("Executing at "+Timed.getFireCount()+": "+toString()+", hash="+Integer.toHexString(System.identityHashCode(this)));
		if(! source.getPM().publicVms.contains(mvm.vm)) {
			Logger.getGlobal().info("VM is not on the source PM anymore -> there is nothing to do");
			finished();
		} else if(mvm.vm.getMemSize()>target.getPM().freeCapacities.getRequiredMemory()
		|| mvm.vm.getPerTickProcessingPower()>target.getPM().freeCapacities.getTotalProcessingPower()) {
			Logger.getGlobal().info("Target PM does not have sufficient capacity anymore -> there is nothing to do");
			finished();
		} else if(mvm.vm.getState()!=VirtualMachine.State.RUNNING && mvm.vm.getState()!=VirtualMachine.State.SUSPENDED) {
			Logger.getGlobal().info("State of the VM inappropriate for migration ("+mvm.vm.getState()+") -> there is nothing to do");
			finished();
		} else if(!(target.getPM().isRunning())) {
			Logger.getGlobal().info("Target PM not running -> there is nothing to do");
			finished();
		} else {
			mvm.vm.subscribeStateChange(this);		// observe the VM which shall be migrated
			try {
				source.getPM().migrateVM(mvm.vm, target.getPM());
			} catch (VMManagementException e) {
				e.printStackTrace();
			} catch (NetworkException e) { 
				e.printStackTrace();
			}
		}
	}

	/**
	 * The stateChanged-logic, if the VM changes its state to RUNNING after migrating,
	 * then it do not has to be observed any longer.
	 */
	@Override
	public void stateChanged(VirtualMachine vm, State oldState, State newState) {
		if(newState.equals(VirtualMachine.State.RUNNING)){
			vm.unsubscribeStateChange(this);
			//Logger.getGlobal().info("Migration action finished");
			//Logger.getGlobal().info("Finished at "+Timed.getFireCount()+": "+toString()+", hash="+Integer.toHexString(System.identityHashCode(this)));
			finished();
		}
	}

}
