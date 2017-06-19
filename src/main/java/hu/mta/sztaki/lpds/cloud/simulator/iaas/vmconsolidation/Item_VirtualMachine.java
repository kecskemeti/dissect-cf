package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;


/**
 * @author Julian, René
 * 
 * This class represents a VM in our abstract model. It has only the necassary things for consolidation.
 * The energy consumption happens inside the Bin_PhysicalMachine class.
 */


public class Item_VirtualMachine {
	
	VirtualMachine vm;
	Bin_PhysicalMachine hostPM;
	ResourceVector neededResources;

	
	
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
	 */
	
	public Item_VirtualMachine(VirtualMachine vm, Bin_PhysicalMachine pm, double cores, double pCP, long mem) {
		
		this.vm = vm;
		hostPM = pm;
		neededResources = new ResourceVector(cores, pCP, mem);
		
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
		return neededResources.getCPUs();
	}
	
	/** Getter
	 * @return perCoreProcessing of the VM.
	 */
	
	public double getRequiredProcessingPower() {
		return neededResources.getProcessingPower();
	}
	
	/** Getter
	 * @return memory of the VM.
	 */
	public long getRequiredMemory() {
		return neededResources.getMemory();
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
