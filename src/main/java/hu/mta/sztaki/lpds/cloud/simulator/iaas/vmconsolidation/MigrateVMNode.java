package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;


/**
 * Saves the VMs and its actual and next PM, which needs to get migrated.
 */
public class MigrateVMNode extends Node{
	
	Bin_PhysicalMachine source;
	Bin_PhysicalMachine target;
	Item_VirtualMachine vm; 
	
	public MigrateVMNode(int id, Bin_PhysicalMachine source, Bin_PhysicalMachine target, Item_VirtualMachine vm){
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
}
