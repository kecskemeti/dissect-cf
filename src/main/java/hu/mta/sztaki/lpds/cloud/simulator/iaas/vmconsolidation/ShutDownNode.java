package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;

/**
 * Saves the PMs which need to be shut down.
 */

public class ShutDownNode extends Node {
	
	PhysicalMachine pm;

	public ShutDownNode(int id, PhysicalMachine pm) {
		super(id);
		this.pm = pm;
	}

}
