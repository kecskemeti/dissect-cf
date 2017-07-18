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
	 * 			The total resources as ResourceConstraints.
	 * @param upperThreshold
	 * 			The defined upper Threshold.
	 * @return true if the pm is overAllocated.
	 */
	public boolean isOverAllocated(ResourceConstraints total, double upperThreshold) {	
		if(this.getTotalProcessingPower() > total.getTotalProcessingPower() * upperThreshold || this.getRequiredMemory() > total.getRequiredMemory() * upperThreshold) {
			return true;
		}
		else
			return false;
	}
	
	/**
	 * Comparison for checking if the PM is underAllocated.
	 * @param total
	 * 			The total resources as ResourceConstraints.
	 * @param lowerThreshold
	 * 			The defined lower Threshold.
	 * @return true if the pm is underAllocated.
	 */
	public boolean isUnderAllocated(ResourceConstraints total, double lowerThreshold) {
		if(this.getTotalProcessingPower() < total.getTotalProcessingPower() * lowerThreshold && this.getRequiredMemory() < total.getRequiredMemory() * lowerThreshold) {
			return true;
		}
		else
			return false;
	}

	/**
	 * Compares the allocation of two ResourceVectors to verify that the VM which calls this methods on its resources can be 
	 * added to the available resources of the PM in the parameter.
	 * @param availablePMResources
	 * 			The resources of the possible host PM as ResourceConstraints.
	 * @return true if all values are greater.
	 */
	public boolean canBeAdded(ResourceConstraints availablePMResources) {
		
		if(getTotalProcessingPower() <= availablePMResources.getTotalProcessingPower() && getRequiredMemory() <= availablePMResources.getRequiredMemory()) {
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * The toString()-method.
	 */
	public String toString() {
		return "["+getRequiredCPUs()+","+getRequiredProcessingPower()+","+getRequiredMemory()+"]";
	}
}
