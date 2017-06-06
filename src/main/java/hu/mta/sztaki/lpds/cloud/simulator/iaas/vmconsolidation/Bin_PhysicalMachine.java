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
	
	
	public Bin_PhysicalMachine(Item_VirtualMachine[] vm, double cores, double pCP, long mem) {
		
		for(int i = 0; i < vm.length; i ++) {
			vmList.add(vm[i]);
		}
		
		this.cores = cores;
		this.perCorePocessing = pCP;
		this.memory = mem;
		
	}
	
	
	public String Resources() {
		return "ResourceConstraints(C:" + getRequiredCPUs() + " P:" + getRequiredProcessingPower() + " M:"
				+ getRequiredMemory() + ")";
	}
	
	
	private long getRequiredMemory() {
		return memory;
	}


	private double getRequiredProcessingPower() {
		return perCorePocessing;
	}


	private double getRequiredCPUs() {
		return cores;
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
		normal
	};

}
