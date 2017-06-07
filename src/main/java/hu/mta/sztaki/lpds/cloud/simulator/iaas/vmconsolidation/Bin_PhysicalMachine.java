package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

/**
 * @author Julian, René
 *
 * This class represents a PhysicalMachine. It abstracts of it and uses only the necassary things for migration.
 */


public class Bin_PhysicalMachine {
	
	ArrayList <Item_VirtualMachine> vmList = new ArrayList();
	double cores;
	double perCorePocessing;
	long memory;
	
	
	/**
	 * This represents a Phsyical Machine of the simulator. It is abstract and inherits only the methods and properties
	 * which are necassary to do the consolidation inside this model.
	 * 
	 * 
	 * @param vm
	 * 			An array which contains all VMs running on this PM.
	 * @param cores
	 * 			The cores of the PM.
	 * @param pCP
	 * 			The Power of one core.
	 * @param mem
	 * 			The memory of this PM.
	 */
	
	public Bin_PhysicalMachine(Item_VirtualMachine[] vm, double cores, double pCP, long mem) {
		
		for(int i = 0; i < vm.length; i ++) {
			vmList.add(vm[i]);
		}
		
		this.cores = cores;
		this.perCorePocessing = pCP;
		this.memory = mem;
		
	}
	
	
	/**
	 * A String which contains all resources of this PM.
	 * @return
	 * 			cores, perCoreProcessing and memory of the PM in a single String.
	 */
	
	public String Resources() {
		return "ResourceConstraints(C:" + getRequiredCPUs() + " P:" + getRequiredProcessingPower() + " M:"
				+ getRequiredMemory() + ")";
	}
	
	
	/**
	 * Getter
	 * @return
	 * 			cores of the PM.
	 */
	public double getRequiredCPUs() {
		return cores;
	}
	
	/**
	 * Getter
	 * @return
	 * 			perCoreProcessing of the PM.
	 */
	
	public double getRequiredProcessingPower() {
		return perCorePocessing;
	}
	
	/**
	 * Getter
	 * @return
	 * 			memory of the PM.
	 */
	
	public long getRequiredMemory() {
		return memory;
	}


	/**
	 * The possible States for a PM.
	 */
	
	public static enum State {
		/**
		 * There are actually no VMs running on this PM
		 */
		empty,
		
		/**
		 * load balance is lower than 25 %
		 */
		underloaded,
		
		/**
		 * load balance is higher than 75 %
		 */
		overloaded,
		
		/**
		 * load balance is between 25 % and 75 %
		 */
		normal,
		
		/**
		 * PM switched off
		 */
		off
		
	};
	
	/**
	 * Now all needed methods are going to be created for working with the PM inside the abstract model.
	 * To be exact, we need methods for switching off, switching on, migrating, get the VMs, change the States,
	 * consume resources.
	 */
	
	public void migrateVM(Item_VirtualMachine vm, Bin_PhysicalMachine target) {
		
		if(target.getRequiredCPUs() >= vm.getRequiredCPUs() && target.getRequiredMemory() >= vm.getRequiredMemory()
				&& target.getRequiredProcessingPower() >= vm.getRequiredProcessingPower())	{
			
			
		}	
	}
	
	public void switchOff() {
		
	}
	
	public void switchOn() {
		
	}
	
	public void changeState() {
		
	}
	
	public void consume() {
		
	}
	
	public ArrayList <Item_VirtualMachine> getVMs() {
		return vmList;
	}
	
	/**
	 * Additional methods.
	 */
	
	
	
	

}
