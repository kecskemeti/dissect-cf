package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
/**
 * @author Julian, René
 *
 * This class represents a PhysicalMachine. It abstracts of it and uses only the necassary things for migration.
 */


public class Bin_PhysicalMachine {
	
	private PhysicalMachine pm; 
	ArrayList <Item_VirtualMachine> vmList;
	
	ResourceVector totalResources;
	ResourceVector availableResources;
	
	State state;
	
	/**
	 * This represents a Phsyical Machine of the simulator. It is abstract and inherits only the methods and properties
	 * which are necassary to do the consolidation inside this model.
	 * The defined treshold is between 25 % and 75 % of the total resources. If its greater than 75 % or less than 25 %,
	 * the state of the PM switches to OVERLOADED or UNDERLOADED.
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
		
		this.setPM(pm);  
		setVMs(vm);
		
		totalResources = new ResourceVector(cores, pCP, mem);
		availableResources = new ResourceVector(cores, pCP, mem);
		
		
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
			consumeResources(getVMs().get(i));
		}
		
		checkLoad();
		
	}
	
	/**
	 * @return the real pm
	 */
	public PhysicalMachine getPM() {
		return pm;
	}

	/**
	 * @param pm 
	 * 			the pm to be set
	 */
	public void setPM(PhysicalMachine pm) {
		this.pm = pm;
	}

	/**
	 * Checks if there are any VMs on this PM.
	 * @return true if VMs are running in this PM.
	 */
	
	public boolean isHostingVMs() {
		return !getVMs().isEmpty();
	}
	
	
	/**
	 * A String which contains all available resources of this PM.
	 * @return cores, perCoreProcessing and memory of the PM in a single String.
	 */
	
	public ResourceVector getAvailableResources() {
		return this.availableResources;
	}	
	
	/**
	 * A String which contains all resources of this PM.
	 * @return cores, perCoreProcessing and memory of the PM in a single String.
	 */
	
	public ResourceVector getTotalResources() {
		return this.totalResources;
	}
	
	
	/**
	 * Getter
	 * @return cores of the PM.
	 */
	public double getRequiredCPUs() {
		return totalResources.getCPUs();
	}
	
	/**
	 * Getter
	 * @return perCoreProcessing of the PM.
	 */
	
	public double getRequiredProcessingPower() {
		return totalResources.getProcessingPower();
	}
	
	/**
	 * Getter
	 * @return memory of the PM.
	 */
	
	public long getRequiredMemory() {
		return totalResources.getMemory();
	}
	
	/**
	 * Getter and Setter
	 * @return available cores of the PM.
	 */
	public double getAvailableCPUs() {
		return availableResources.getCPUs();
	}
	public void setAvCPUs(double d) {
		availableResources.setCPUs(d);
	}
	
	/**
	 * Getter and Setter
	 * @return available perCoreProcessing of the PM.
	 */
	
	public double getAvailableProcessingPower() {
		return availableResources.getProcessingPower();
	}
	public void setAvPCP(double d) {
		availableResources.setPCP(d);
	}
	
	/**
	 * Getter and Setter
	 * @return available memory of the PM.
	 */
	
	public long getAvailableMemory() {
		return availableResources.getMemory();
	}
	public void setAvMem(long d) {
		availableResources.setMem(d);
	}
	
	/**
	 * Getter and Setter for VMlist
	 * @return vmList
	 */
	
	public ArrayList <Item_VirtualMachine> getVMs() {
		return vmList;
	}
	public void setVMs(ArrayList <Item_VirtualMachine> vms) {
		this.vmList = vms;
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
	 * Getter for the State.
	 * @return actual State.
	 */
	
	public State getState() {
		return this.state;
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
	 * In this method the status of each PM in the simulation is considered.
	 * For that, the methods underloaded() and overloaded() are written.
	 */
	
	protected void checkLoad() {
		if(isHostingVMs() == false) {
			changeState(State.EMPTY_RUNNING);
		}
		else {
			if(underloaded())  {
				changeState(State.UNDERLOADED_RUNNING);
			}
			else {
				if(overloaded()) {
						changeState(State.OVERLOADED_RUNNING);
				}
				else
					changeState(State.NORMAL_RUNNING);
				}	
			}
		}
	
	/**
	 * Method for checking if the actual PM is overloaded.
	 * @return true if overloaded, false otherwise
	 */
	
	private boolean overloaded() {
		
		if(totalResources.compareToOverloaded(availableResources)) {
			return true;
		}
		else
			return false;
	}
	
	/**
	 * Method for checking if the actual PM is underloaded.
	 * @return true if underloaded, false otherwise	  
	 */
	
	private boolean underloaded() {
		
		if(totalResources.compareToUnderloaded(availableResources)) {
			return true;
		}
		else
			return false;
	}
	
	/**
	 * Getter for a specific VM.
	 * @param position
	 * 			The desired position where the VM is.
	 * @return
	 * 			The desired VM.
	 */
	
	public Item_VirtualMachine getVM(int position) {
		return getVMs().get(position);
	}
	
	/**
	 * Standard Migration for one VM.
	 * 
	 * @param vm
	 * 			The VM which is going to be migrated.
	 * @param target
	 * 			The target PM where to migrate.
	 */
	
	public void migrateVM(Item_VirtualMachine vm, Bin_PhysicalMachine target) {
		
		target.getVMs().add(vm);
		target.consumeResources(vm);
		this.deconsumeResources(vm);
		vm.sethostPM(target);
		
		//löschen der VM aus dieser vmlist
		for(int i = 0; i < getVMs().size(); i++) {
			Item_VirtualMachine x = getVM(i);
			
			if(x.equals(vm)) {
				this.getVMs().remove(i);
			}
		}
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

	public void consumeResources(Item_VirtualMachine x) {
		ResourceVector second = new ResourceVector(x.getRequiredCPUs(), x.getRequiredProcessingPower(), x.getRequiredMemory());
		availableResources.subtract(second);
	}
	
	/**
	 * This method is for the migration of an ArrayList. If a VM is moved, the consumed
	 * resources need to be restored to the available resources.
	 * 
	 * @param x
	 * 			The Item_VirtualMachine which is going to be demigrated. 
	 */
	
	public void deconsumeResources(Item_VirtualMachine x) {
		
		ResourceVector second = new ResourceVector(x.getRequiredCPUs(), x.getRequiredProcessingPower(), x.getRequiredMemory());
		availableResources.add(second);
	}
}