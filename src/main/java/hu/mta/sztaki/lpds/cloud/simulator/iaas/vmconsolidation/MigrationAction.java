package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

public class MigrationAction extends Action{

	Bin_PhysicalMachine source;
	Bin_PhysicalMachine target;
	Item_VirtualMachine vm;
	
	public MigrationAction(int id, Bin_PhysicalMachine source, Bin_PhysicalMachine target, Item_VirtualMachine vm) {
		super(id);
		this.target = target;
		this.source = source;
		this.vm = vm;
	}

	public Bin_PhysicalMachine getTarget(){
		return target;
	}
	
	public Bin_PhysicalMachine getSource(){
		return source;
	}
	
	public Item_VirtualMachine getItemVM(){
		return vm;
	}

	@Override
	public void createGraph(ArrayList<Action> actions) {
		//looking for actions where a PM gets started, that is the target of this migration
		for(int i = 0; i < actions.size(); i++) {
			if(actions.get(i).getType().equals(Type.START)){
				if((((StartAction) actions.get(i)).getstartpm()).equals(this.getTarget())){
					this.addPrevious(actions.get(i));
				}
			}
		}	
		
	}

	@Override
	public Type getType() {
		return Type.MIGRATION;
	}

	
	
}
