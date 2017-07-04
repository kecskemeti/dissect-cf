package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

/**
 * @author René Ponto
 * 
 * This class manages the resources of the modeled VMs and PMs as an extension of 'AlterableResourceConstraints'.
 * The additional functions are comparisons of the resources (cores, perCoreProcessing and memory) between a ConstantConstraint
 * and a ResourceVector to look if a PM is overAllocated or underAllocated with the actual placed VMs and a method to compare two
 * ResourceVectors.
 */

public class ResourceVector extends AlterableResourceConstraints {	
	
	/**
	 * The constructor for a ResourceVector. This class represents the cores, perCoreProcessingPower
	 * and the memory for either a PM or a VM.
	 * 
	 * @param cores
	 * @param perCoreProcessing
	 * @param memory
	 */
	
	public ResourceVector(double cores, double perCoreProcessing, long memory) {
		super(cores, perCoreProcessing, memory);
	}
	
	
	/**
	 * Comparison for checking if the PM is overAllocated.
	 * @param total
	 * 			The total resources
	 * @param upperThreshold 
	 * 			The defined threshold
	 * @return true if the pm is overAllocated.
	 */
	public boolean compareToOverAllocated(ResourceConstraints total, double upperThreshold) {	
		
		if(this.getTotalProcessingPower() > total.getTotalProcessingPower() * 0.75 || this.getRequiredMemory() > total.getRequiredMemory() * 0.75) {
			return true;
		}
		else
			return false;
	}	
	
	/**
	 * Comparison for checking if the PM is underAllocated.
	 * @param total
	 * 			The total resources
	 * @param lowerThreshold
	 * 			The defined threshold
	 * @return true if the pm is underAllocated.
	 */
	public boolean compareToUnderAllocated(ResourceConstraints total, double lowerThreshold) {
		
		if(this.getTotalProcessingPower() < total.getTotalProcessingPower() * 0.25 && this.getRequiredMemory() < total.getRequiredMemory() * 0.25) {
			return true;
		}
		else
			return false;
	}
	
	/**
	 * Compares the allocation of two ResourceVectors to verfify that the allocation of the first on is smaller than the second one.
	 * @param available
	 * 			The second ResourceVector
	 * @return true if all values are greater.
	 */
	public boolean fitsIn(ResourceConstraints second) {
		
		if(getRequiredCPUs() <= second.getRequiredCPUs() && getRequiredMemory() <= second.getRequiredMemory() && getRequiredProcessingPower() <= second.getRequiredProcessingPower()) {
			return true;
		}
		else {
			return false;
		}
	}
}
