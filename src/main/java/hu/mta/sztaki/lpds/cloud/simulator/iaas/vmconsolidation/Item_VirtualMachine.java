package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;


/**
 * @author Julian, René
 * 
 * This class represents a VM in our abstract model. It has only the necassary things for consolidation.
 */


public class Item_VirtualMachine {
	
	Bin_PhysicalMachine hostPM;
	
	double cores;
	double perCorePocessing;
	long memory;
	
	public Item_VirtualMachine(Bin_PhysicalMachine pm, double cores, double pCP, long mem) {
		
		hostPM = pm;
		
		this.cores = cores;
		this.perCorePocessing = pCP;
		this.memory = mem;
	}
	
	
	/**
	 * A String which contains all resources of this VM.
	 * @return
	 * 			cores, perCoreProcessing and memory of the VM in a single String.
	 */
	
	public String Resources() {
		return "ResourceConstraints(C:" + getRequiredCPUs() + " P:" + getRequiredProcessingPower() + " M:"
				+ getRequiredMemory() + ")";
	}
	
	
	/**
	 * Getter
	 * @return
	 * 			cores of the PM.
	 */
	public double getRequiredCPUs() {
		return cores;
	}
	
	/**
	 * Getter
	 * @return
	 * 			perCoreProcessing of the PM.
	 */
	
	public double getRequiredProcessingPower() {
		return perCorePocessing;
	}
	
	/**
	 * Getter
	 * @return
	 * 			memory of the PM.
	 */
	
	public long getRequiredMemory() {
		return memory;
	}


}
