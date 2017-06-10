package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

/**
 * Saves the PMs which need to get started.
 */

public class StartNode extends Node{
	
	Bin_PhysicalMachine pm; 
	
	public StartNode(int id, Bin_PhysicalMachine bin_PhysicalMachine){
		super(id);
		this.pm = bin_PhysicalMachine;
	}
	public Bin_PhysicalMachine getPM(){
		return pm;
	}
}
