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
	
	double neededCores;
	double neededPerCorePocessing;
	long neededMemory;
	
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
		
		neededCores = cores;
		neededPerCorePocessing = pCP;
		neededMemory = mem;
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
	 * @return cores of the VM.
	 */
	public double getRequiredCPUs() {
		return neededCores;
	}
	
	/**
	 * Getter
	 * @return perCoreProcessing of the VM.
	 */
	
	public double getRequiredProcessingPower() {
		return neededPerCorePocessing;
	}
	
	/**
	 * Getter
	 * @return memory of the VM.
	 */
	
	public long getRequiredMemory() {
		return neededMemory;
	}
	
	/**
	 * Getter
	 * @return The matching VM out of the real Simulator.
	 */
	
	public VirtualMachine getVM() {
		return vm;
	}
	
	public Bin_PhysicalMachine gethostPM() {
		return  hostPM;
	}
	
	public void sethostPM(Bin_PhysicalMachine bin) {
		this.hostPM = bin;
	}
	
}
