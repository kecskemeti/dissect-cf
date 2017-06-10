package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;


/**
 * Saves the PMs which need to be shut down.
 */

public class ShutDownNode extends Node {
	
	Bin_PhysicalMachine pm;

	public ShutDownNode(int id, Bin_PhysicalMachine pm) {
		super(id);
		this.pm = pm;
	}
	public Bin_PhysicalMachine getPM(){
		return pm;
	}
}
