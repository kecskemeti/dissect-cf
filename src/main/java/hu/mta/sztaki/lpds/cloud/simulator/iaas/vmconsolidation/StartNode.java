package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;

/**
 * Saves the PMs which need to get started.
 */

public class StartNode extends Node{
	
	PhysicalMachine pm; 
	
	public StartNode(int id, PhysicalMachine pm){
		super(id);
		this.pm = pm;
	}

}
