package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;


/**
 * @author Julian, René
 * 
 * This class represents a VM in our abstract model. It has only the necassary things for consolidation.
 */


public class Item_VirtualMachine {
	
	Bin_PhysicalMachine hostPM;
	
	
	public Item_VirtualMachine(Bin_PhysicalMachine pm) {
		
		hostPM = pm;
		
	}

}
