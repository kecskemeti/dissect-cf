package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;


/**
 * @author Julian Bellendorf, Rene Ponto
 * 
 * This class represents a VM in this abstract model. It has only the necessary things for consolidation which
 * means the hosting PM, its needed resources and the id of the original VM.
 * The resource consumption happens inside the ModelPM class.
 */
public class ModelVM {
	public static final ModelVM[] mvmArrSample=new ModelVM[0];

	final public VirtualMachine vm;
	private ModelPM hostPM;
	final public ModelPM initialHost;
	public ModelPM prevPM;
	final public int id;

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
	public ModelVM(final VirtualMachine vm, final ModelPM pm, final int id) {
		
		this.vm = vm;
		hostPM = pm;		// save the host PM
		initialHost = pm;	// save the host as the first host PM
		this.id = id;
	}
	
	public ModelVM(final ModelVM toCopy) {
		this.vm=toCopy.vm;
		this.hostPM=toCopy.hostPM;
		this.initialHost=toCopy.initialHost;
		this.id=toCopy.id;
	}

	/**
	 * toString() is just for debugging and returns the ID, cores, perCoreProcessingPower and memory of this VM.
	 */	
	public String toString() {
		return id + ", " + "Cores: " + getResources().getRequiredCPUs() + ", " + "ProcessingPower: " 
				+ getResources().getRequiredProcessingPower() + ", " + "Memory: " + getResources().getRequiredMemory();
	}	
	
	/**
	 * A small representation to get the id of this vm.
	 * 
	 * @return The id of this vm.
	 */
	public String toShortString() {
		return "VM " + id;
	}

	/** Getter
	 * @return the ResourceVector
	 */
	public ResourceConstraints getResources() {
		return vm.getResourceAllocation().allocated;
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

	/**
	 * Getter.
	 * 
	 * @return The id of this vm.
	 */
	@Override
	public int hashCode() {
		return id;
	}
}
