package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;

/**
 * Saves the VMs and its actual and next PM, which needs to get migrated.
 */
public class MigrateVMNode extends Node{
	
	PhysicalMachine source;
	PhysicalMachine target;
	VirtualMachine vm; 
	
	public MigrateVMNode(int id, PhysicalMachine source, PhysicalMachine target, VirtualMachine vm){
		super(id);
		this.target = target;
		this.source = source;
		this.vm = vm;
	}

}
