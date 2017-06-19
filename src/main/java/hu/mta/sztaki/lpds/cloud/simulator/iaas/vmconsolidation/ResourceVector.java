package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

public class ResourceVector{

	
	double cores;
	double perCoreProcessing;
	long memory;
	
	
	public ResourceVector(double cores, double perCoreProcessing, long memory) {
		
		this.cores = cores;
		this.perCoreProcessing = perCoreProcessing;
		this.memory = memory;
	}
	
	
	public void setCPUs(double cores) {
		this.cores = cores;
	}
	
	public void setPCP(double pcp) {
		this.perCoreProcessing = pcp;
	}
	
	public void setMem(long mem) {
		this.memory = mem;
	}
	
	
	
	/**
	 * Getter
	 * @return cores of the PM.
	 */
	public double getCPUs() {
		return cores;
	}
	
	/**
	 * Getter
	 * @return perCoreProcessing of the PM.
	 */
	
	public double getProcessingPower() {
		return perCoreProcessing;
	}
	
	/**
	 * Getter
	 * @return memory of the PM.
	 */
	
	public long getMemory() {
		return memory;
	}
	
	public ResourceVector subtract(ResourceVector second) {
		ResourceVector erg = new ResourceVector(this.getCPUs(), this.getProcessingPower(), this.getMemory());
		
		erg.setCPUs(this.getCPUs() - second.getCPUs());
		erg.setPCP(this.getProcessingPower() - second.getProcessingPower());
		erg.setMem(this.getMemory() - second.getMemory());
		
		
		return erg;
	}
	
	public ResourceVector add(ResourceVector second) {
		ResourceVector erg = new ResourceVector(this.getCPUs(), this.getProcessingPower(), this.getMemory());
		
		erg.setCPUs(this.getCPUs() + second.getCPUs());
		erg.setPCP(this.getProcessingPower() + second.getProcessingPower());
		erg.setMem(this.getMemory() + second.getMemory());
		
		return erg;
	}
	
	public boolean compareToOverloaded(ResourceVector second) {
		if(this.getCPUs() - second.getCPUs() >= 0.75 || this.getMemory() - second.getMemory() >= 0.75 
				|| this.getProcessingPower() - this.getProcessingPower() >= 0.75){
			return true;
		}
		else {
			return false;
		}
	}
	
	public boolean compareToUnderloaded(ResourceVector second) {
		if(this.getCPUs() - second.getCPUs() <= 0.25 && this.getMemory() - second.getMemory() <= 0.25 
				&& this.getProcessingPower() - this.getProcessingPower() <= 0.25){
			return true;
		}
		else {
			return false;
		}
	}
	
	public boolean isGreater(ResourceVector second) {
		
		if(getCPUs() >= second.getCPUs() && getMemory() >= second.getMemory() && getProcessingPower() >= second.getProcessingPower()) {
			return true;
		}
		else {
			return false;
		}
	}
	
}
