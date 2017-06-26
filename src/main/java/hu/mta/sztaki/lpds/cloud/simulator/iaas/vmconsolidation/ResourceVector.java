package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

public class ResourceVector{
	
	double cores;
	double perCoreProcessing;
	long memory;
	
	/**
	 * The constructor for a ResourceVector. This class represents the cores, perCoreProcessingPower
	 * and the memory for either a PM or a VM.
	 * 
	 * @param cores
	 * @param perCoreProcessing
	 * @param memory
	 */
	
	public ResourceVector(double cores, double perCoreProcessing, long memory) {
		
		this.cores = cores;
		this.perCoreProcessing = perCoreProcessing;
		this.memory = memory;
	}	
	
	/**
	 * Set the cores of this ResourceVector.
	 * @param cores
	 */
	private void setCPUs(double cores) {
		this.cores = cores;
	}
	
	/**
	 * Set the perCoreProcessingPower of this ResourceVector.
	 * @param cores
	 */
	private void setPCP(double pcp) {
		this.perCoreProcessing = pcp;
	}
	
	/**
	 * Set the memory of this ResourceVector.
	 * @param cores
	 */
	private void setMem(long mem) {
		this.memory = mem;
	}	
	
	/**
	 * @return cores of the PM.
	 */
	public double getCPUs() {
		return cores;
	}
	
	/**
	 * @return perCoreProcessing of the PM.
	 */
	
	public double getProcessingPower() {
		return perCoreProcessing;
	}
	
	/**
	 * @return memory of the PM.
	 */
	
	public long getMemory() {
		return memory;
	}
	
	/**
	 * Subtracts a given ResourceVector to this one.
	 * @param second
	 * 			The second ResourceVector to susbtract to this one.
	 * @return actual ResourceVector
	 */
	public ResourceVector subtract(ResourceVector second) {
		ResourceVector erg = new ResourceVector(this.getCPUs(), this.getProcessingPower(), this.getMemory());
		
		erg.setCPUs(this.getCPUs() - second.getCPUs());
		erg.setPCP(this.getProcessingPower() - second.getProcessingPower());
		erg.setMem(this.getMemory() - second.getMemory());
		
		return erg;
	}
	
	/**
	 * Adds a given ResourceVector to this one.
	 * @param second
	 * 			The second ResourceVector to add to this one.
	 * @return actual ResourceVector
	 */
	public ResourceVector add(ResourceVector second) {
		ResourceVector erg = new ResourceVector(this.getCPUs(), this.getProcessingPower(), this.getMemory());
		
		erg.setCPUs(this.getCPUs() + second.getCPUs());
		erg.setPCP(this.getProcessingPower() + second.getProcessingPower());
		erg.setMem(this.getMemory() + second.getMemory());
		
		return erg;
	}
	
	/**
	 * Comparison for checking if the PM is overloaded.
	 * @param available
	 * 			The second ResourceVector
	 * @return true if the pm is overloaded.
	 */
	public boolean compareToOverloaded(ResourceVector available) {
		
		if(available.getCPUs() < this.getCPUs() * 0.25 || available.getProcessingPower() < this.getProcessingPower() * 0.25 
				|| available.getMemory() < this.getMemory() * 0.25) {
			return true;
		}
		else {
			return false;
		}
	}
	
	/**
	 * Comparison for checking if the PM is underloaded.
	 * @param available
	 * 			The second ResourceVector
	 * @return true if the pm is underloaded.
	 */
	public boolean compareToUnderloaded(ResourceVector available) {
		
		if(available.getCPUs() > this.getCPUs() * 0.75 && available.getProcessingPower() > this.getProcessingPower() * 0.75 
				&& available.getMemory() > this.getMemory() * 0.75) {
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
		
		if(getCPUs() >= second.getCPUs() && getMemory() >= second.getMemory() && getProcessingPower() >= second.getProcessingPower()) {
			return true;
		}
		else {
			return false;
		}
	}
	
}
