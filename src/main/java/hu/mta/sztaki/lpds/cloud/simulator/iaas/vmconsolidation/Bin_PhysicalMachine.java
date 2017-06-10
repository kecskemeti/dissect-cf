package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;

/**
 * @author Julian, René
 *
 * This class represents a PhysicalMachine. It abstracts of it and uses only the necassary things for migration.
 */


public class Bin_PhysicalMachine {
	
	PhysicalMachine pm; // Verweis auf die pm, die hier repräsentiert werden soll. wird für das Speichern der Aktion benötigt
	ArrayList <Item_VirtualMachine> vmList;
	double cores;
	double perCoreProcessing;
	long memory;
	
	State state;
	
	double availableCores;
	double availablePerCoreProcessing;
	long availableMemory;
	
	
	/**
	 * This represents a Phsyical Machine of the simulator. It is abstract and inherits only the methods and properties
	 * which are necassary to do the consolidation inside this model.
	 * 
	 * @param pm
	 * 			The real Physical Machine in the Simulator.
	 * @param vm
	 * 			An array which contains all VMs running on this PM.
	 * @param cores
	 * 			The cores of the PM.
	 * @param pCP
	 * 			The Power of one core.
	 * @param mem
	 * 			The memory of this PM.
	 */
	
	public Bin_PhysicalMachine(PhysicalMachine pm, ArrayList <Item_VirtualMachine> vm, double cores, double pCP, long mem) {
		
		this.pm = pm;  
		setVMs(vm);
		this.cores = cores;
		this.perCoreProcessing = pCP;
		this.memory = mem;
		
		//this.state = state;
		
		if(pm.getState() == PhysicalMachine.State.SWITCHINGOFF || pm.getState() == PhysicalMachine.State.OFF) {
			changeState(State.EMPTY_OFF);
		}
		else {
			if(pm.getState() == PhysicalMachine.State.SWITCHINGON || pm.getState() == PhysicalMachine.State.RUNNING) {
				changeState(State.NORMAL_RUNNING);
			}
		}
		
		for(int i = 0; i < getVMs().size(); i++) {
			consumeResources(getVMs().get(i).getRequiredCPUs(), 
					getVMs().get(i).getRequiredProcessingPower(), getVMs().get(i).getRequiredMemory());
		}
		
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
		return perCoreProcessing;
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
	 * The possible States for a PM in this abstract modell.
	 * For understanding, we need the 'double'-states becouse of the graph.
	 * If we shut down a PM and have to restart it again it would be an unnecassary 
	 * action, so we mark them as for example EMPTY_RUNNING or EMPTY_OFF.
	 * For the load we only have the x_RUNNING State becouse the check of the load
	 * can only occur if the PM is running, otherwise it would be empty.
	 */
	
	public static enum State {
		
		/**
		 * There are actually no VMs running on this PM, PM is not running
		 */
		EMPTY_OFF,
		
		/**
		 * PM is running and empty
		 */
		EMPTY_RUNNING,
		
		/**
		 * load balance is lower than 25 %, PM is Running
		 */
		UNDERLOADED_RUNNING,
		
		/**
		 * load balance is higher than 75 %, PM is Running
		 */
		OVERLOADED_RUNNING,
		
		/**
		 * load balance is between 25 % and 75 %, PM is Running
		 */
		NORMAL_RUNNING
	};
	
	/**
	 * Now all needed methods are going to be created for working with the PM inside the abstract model.
	 * To be exact, we need methods for switching off, switching on, migrating, get the VMs, change the States,
	 * consume resources.
	 */
	
	
	
	
	/**
	 * Standard Migration for one VM.
	 * @param vm
	 * 			The VM which is going to be migrated.
	 * @param target
	 * 			The target PM where to migrate.
	 */
	
	public void migrateVM(Item_VirtualMachine vm, Bin_PhysicalMachine target) /*throws NullPointerException*/ {
		
		while(this.getState() == State.OVERLOADED_RUNNING || this.getState() == State.UNDERLOADED_RUNNING) {
			
			if(target.getAvailableCPUs() >= vm.getRequiredCPUs() && target.getAvailableMemory() >= vm.getRequiredMemory()
					&& target.getAvailableProcessingPower() >= vm.getRequiredProcessingPower())	{
				
				try{
					target.vmList.add(vm);
				}
				catch (NullPointerException ex){
					return; //no PM found to migrate
				}
				
				
				this.vmList.remove(vm);
				vm.sethostPM(target);
			}
			else {
				return; //no migration needed anymore
			}
		}
	}
	
	
	/**
	 * changes the state of the PM so the graph can give the switch off information
	 * later to the real simulation
	 */
	
	public void switchOff() {
		this.changeState(State.EMPTY_OFF);
	}
	
	/**
	 * changes the state of the PM so the graph can give the switch on information
	 * later to the real simulation
	 */
	
	public void switchOn() {
		this.changeState(State.EMPTY_RUNNING);
	}
	
	/**
	 * Setter for the state of the PM
	 * @param state
	 * 			the wanted state
	 */
	
	public void changeState(State state) {
		this.state = state;
	}
	
	/**
	 *  This method handles the consupmtion of resources of the PM by hosting VMs.
	 * 
	 * @param cores
	 * 			The cores which are going to get unavailable.
	 * @param corePower
	 * 			The Power of the cores which is going to get unavailable.
	 * @param mem
	 * 			The memory which is going to get unavailable.
	 */
	
	public void consumeResources(double cores, double corePower, long mem) {
		this.setAvCPUs(cores);
		this.setAvPCP(corePower);
		this.setAvMem(mem);
	}


	/**
	 * Additional methods.
	 */
	
	/**
	 * A String which contains all available resources of this PM.
	 * @return
	 * 			cores, perCoreProcessing and memory of the PM in a single String.
	 */
	
	public String availableResources() {
		return "ResourceConstraints(C:" + getAvailableCPUs() + " P:" + getAvailableProcessingPower() + " M:"
				+ getAvailableMemory() + ")";
	}
	
	/**
	 * Getter and Setter
	 * @return
	 * 			available cores of the PM.
	 */
	public double getAvailableCPUs() {
		return availableCores;
	}
	
	public void setAvCPUs(double d) {
		availableCores = availableCores - d;
	}
	
	/**
	 * Getter and Setter
	 * @return
	 * 			available perCoreProcessing of the PM.
	 */
	
	public double getAvailableProcessingPower() {
		return availablePerCoreProcessing;
	}
	
	public void setAvPCP(double d) {
		availablePerCoreProcessing = availablePerCoreProcessing - d;
	}
	
	/**
	 * Getter and Setter
	 * @return
	 * 			available memory of the PM.
	 */
	
	public long getAvailableMemory() {
		return availableMemory;
	}

	public void setAvMem(long d) {
		availableMemory = availableMemory - d;
	}
	
	
	
	/**
	 * Getter for VMlist
	 * @return vmList
	 */
	
	public ArrayList <Item_VirtualMachine> getVMs() {
		return vmList;
	}
	
	/**
	 * Setter for VMlist
	 */
	
	public void setVMs(ArrayList <Item_VirtualMachine> vms) {
		this.vmList = vms;
	}
	
	/**
	 * Checks if there are any VMs on this PM.
	 * 
	 * @return true if VMs are running in this PM.
	 */
	
	public boolean isHostingVMs() {
		return !getVMs().isEmpty();
	}
	
	public State getState() {
		return this.state;
	}

}
