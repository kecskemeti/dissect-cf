package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

/**
 * This class stores actions, that need to commit a migration in the simulator
 *
 */
public class MigrationAction extends Action{

	//Reference to the model of the PM, which contains the VM before migrating it
	ModelPM source;
	
	//Reference to the model of the PM, which contains the VM after migrating it
	ModelPM target;
	
	//Reference to the model of the VM, which needs be migrated
	ModelVM vm;
	
	public MigrationAction(int id, ModelPM source, ModelPM target, ModelVM vm) {
		super(id);
		this.target = target;
		this.source = source;
		this.vm = vm;
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
	 * @return Reference to the model of the PM, which contains the VM before migrating it
	 */
	public ModelPM getSource(){
		return source;
	}
	
	/**
	 * 
	 * @return Reference to the model of the VM, which needs be migrated
	 */
	public ModelVM getItemVM(){
		return vm;
	}

	/**
	 * This Method determines the predecessors of this action. A predecessor of a migration-action
	 * is a starting-action, which starts the PM, this action needs as a target. Another predecessor
	 * of a starting-action is a migration-action, which migrates a VM from the PM this action uses
	 * as a target
	 */
	@Override
	public void determinePredecessors(ArrayList<Action> actions) {
		//looking for actions where a PM gets started, that is the target of this migration
		for(int i = 0; i < actions.size(); i++) {
			if(actions.get(i).getType().equals(Type.START)){
				if((((StartAction) actions.get(i)).getStartPM()).equals(this.getTarget())){
					this.addPrevious(actions.get(i));
				}
			}
			if(actions.get(i).getType().equals(Type.MIGRATION)){
				if((((MigrationAction) actions.get(i)).getSource()).equals(this.getTarget())){
					this.addPrevious(actions.get(i));
				}
			}
		}	
		
	}

	/**
	 * This Method returns the type of the action
	 */
	public Type getType() {
		return Type.MIGRATION;
	}

	@Override
	public String toString() {
		return "Action: "+getType()+" Source:  "+getSource().toString()+" Target: "+getTarget().toString()+" VM: "+getItemVM().toString();
	}

	
	
}