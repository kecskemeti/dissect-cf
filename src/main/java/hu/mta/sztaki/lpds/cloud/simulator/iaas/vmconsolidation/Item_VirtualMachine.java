package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;


/**
 * @author Julian, René
 * 
 * This class represents a VM in this abstract model. It has only the necassary things for consolidation which
 * means the hosting PM, its needed resources and the id of the original VM.
 * The energy consumption happens inside the Bin_PhysicalMachine class.
 */


public class Item_VirtualMachine {
	
	VirtualMachine vm;
	Bin_PhysicalMachine hostPM;
	ResourceVector neededResources;
	String id;
	
	/**
	 * This represents a Virtual Machine of the simulator. It is abstract and inherits only the methods and properties
	 * which are necassary to do the consolidation inside this model.
	 * 
	 * @param vm
	 * 			The real Virtual Machine in the Simulator.
	 * @param pm
	 * 			The hosting PM.
	 * @param cores
	 * 			The cores of the PM.
	 * @param pCP
	 * 			The Power of one core.
	 * @param mem
	 * 			The memory of this PM.
	 * @param id
	 * 			The ID of the original VM.
	 */
	
	public Item_VirtualMachine(VirtualMachine vm, Bin_PhysicalMachine pm, double cores, double pCP, long mem, String id) {
		
		this.vm = vm;
		hostPM = pm;
		this.id = id;
		neededResources = new ResourceVector(cores, pCP, mem);
		
	}
	
	/**
	 * toString() is just for debugging and returns the ID, cores, perCoreProcessingPower and memory of this VM.
	 */
	
	public String toString() {
		return id + ", " + "Cores: " +getRequiredCPUs() + ", " + "ProcessingPower: " 
				+ getRequiredProcessingPower() + ", " + "Memory: " + getRequiredMemory();
	}
	
	
	/** Getter
	 * @return the ResourceVector
	 */
	public ResourceVector getResources() {
		return neededResources;
	}
	
	/** Getter
	 * @return cores of the VM.
	 */
	public double getRequiredCPUs() {
		return neededResources.getRequiredCPUs();
	}
	
	/** Getter
	 * @return perCoreProcessing of the VM.
	 */
	
	public double getRequiredProcessingPower() {
		return neededResources.getRequiredProcessingPower();
	}
	
	/** Getter
	 * @return memory of the VM.
	 */
	public long getRequiredMemory() {
		return neededResources.getRequiredMemory();
	}
	
	/** Getter
	 * @return The matching VM out of the real Simulator.	
	 *  */
	public VirtualMachine getVM() {
		return vm;
	}
	
	/** Getter
	 * @return The actual host of this VM.	 
	 * */
	public Bin_PhysicalMachine gethostPM() {
		return  hostPM;
	}
	
	/**
	 * Setter for the hostPM
	 * @param bin
	 * 			The new host.
	 */
	public void sethostPM(Bin_PhysicalMachine bin) {
		this.hostPM = bin;
	}
}
