package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;

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
	 * @return true if the pm is overAllocated.
	 */
	public boolean compareToOverAllocated(ConstantConstraints total) {
		
		if(total.getRequiredCPUs() * 0.25 > this.getRequiredCPUs() || total.getRequiredProcessingPower() * 0.25 > this.getRequiredProcessingPower() 
				|| total.getRequiredMemory() * 0.25 < this.getRequiredMemory() ) {
			return true;
		}
		else {
			return false;
		}
	}
	
	/**
	 * Comparison for checking if the PM is underAllocated.
	 * @param total
	 * 			The total resources
	 * @return true if the pm is underAllocated.
	 */
	public boolean compareToUnderAllocated(ConstantConstraints total) {
		
		if(total.getRequiredCPUs() * 0.75 < this.getRequiredCPUs() && total.getRequiredProcessingPower() * 0.75 < this.getRequiredProcessingPower() 
				&& total.getRequiredMemory() * 0.75 < this.getRequiredMemory() ) {
			return true;
		}
		else {
			return false;
		}
	}
	
	/**
	 * Comparison for checking if the values of the second ResourceVector are smaller than this one.
	 * @param available
	 * 			The second ResourceVector
	 * @return true if all values are greater.
	 */
	public boolean fitsIn(ConstantConstraints second) {
		
		if(getRequiredCPUs() <= second.getRequiredCPUs() && getRequiredMemory() <= second.getRequiredMemory() && getRequiredProcessingPower() <= second.getRequiredProcessingPower()) {
			return true;
		}
		else {
			return false;
		}
	}
	
	/**
	 * Comparison for checking if the values of the second ResourceVector are smaller than this one.
	 * @param available
	 * 			The second ResourceVector
	 * @return true if all values are greater.
	 */
	public boolean isGreater(ResourceVector second) {
		
		if(getRequiredCPUs() >= second.getRequiredCPUs() && getRequiredMemory() >= second.getRequiredMemory() && getRequiredProcessingPower() >= second.getRequiredProcessingPower()) {
			return true;
		}
		else {
			return false;
		}
	}
	
}
