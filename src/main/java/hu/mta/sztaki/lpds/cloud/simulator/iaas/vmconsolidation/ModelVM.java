package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;


	/**
	 * @author Julian Bellendorf, René Ponto
	 * 
	 * This class represents a VM in this abstract model. It has only the necessary things for consolidation which
	 * means the hosting PM, its needed resources and the id of the original VM.
	 * The resource consumption happens inside the ModelPM class.
	 */

public class ModelVM {
	
	VirtualMachine vm;
	ModelPM hostPM;
	String id;
	ResourceVector neededResources;
	
	/**
	 * This represents a VirtualMachine of the simulator. For that this class contains the real VM itself,
	 * the actual abstract host, the resources (cores, perCoreProcessing, memory) and the id for debugging.
	 * With the resources a ResourceVector is created for this VM.
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
	public ModelVM(VirtualMachine vm, ModelPM pm, double cores, double pCP, long mem, String id) {
		
		this.vm = vm;
		hostPM = pm;		// save the host PM
		this.id = id;
		neededResources = new ResourceVector(cores, pCP, mem);		// create the ResourceVector
	}
	
	/**
	 * toString() is just for debugging and returns the ID, cores, perCoreProcessingPower and memory of this VM.
	 */	
	public String toString() {
		return id + ", " + "Cores: " + getResources().getRequiredCPUs() + ", " + "ProcessingPower: " 
				+ getResources().getRequiredProcessingPower() + ", " + "Memory: " + getResources().getRequiredMemory();
	}	
	
	/** Getter
	 * @return the ResourceVector
	 */
	public ResourceVector getResources() {
		return neededResources;
	}
	
	/** Getter
	 * @return The matching VM out of the real Simulator.	
	 */
	public VirtualMachine getVM() {
		return vm;
	}
	
	/** Getter
	 * @return The actual host of this VM.	 
	 */
	public ModelPM gethostPM() {
		return  hostPM;
	}
	
	/**
	 * Setter for the hostPM
	 * @param bin
	 * 			The new host.
	 */
	public void sethostPM(ModelPM bin) {
		this.hostPM = bin;
	}
}
