package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
/**
 * @author Julian, Ren�
 *
 * This class represents a PhysicalMachine. It contains the original PM, its VMs in an ArrayList,
 * two ResourceVectors (one for the total resources and one for the available Resources) and the possible
 * States. The States are not the same as inside the simulator, becouse for migrating it is useful to
 * introduce some new States for the load of the given PM.
 */

public class Bin_PhysicalMachine {
	
	private static final String LINE_SEPARATOR = System.getProperty("line.separator");	//for the toString()-method
	
	private PhysicalMachine pm; 
	ArrayList <Item_VirtualMachine> vmList;
	int number;
	
	ConstantConstraints totalResources;
	ResourceVector availableResources;
	
	State state;
	
	/**
	 * This represents a Phsyical Machine of the simulator. It is abstract and inherits only the methods and properties
	 * which are necassary to do the consolidation inside this model.
	 * The defined treshold is between 25 % and 75 % of the total resources. If the load is greater than 75 % or less than 25 %,
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
	 * @param number
	 * 			The number of the PM in its IAAS, used for debugging.
	 */
	public Bin_PhysicalMachine(PhysicalMachine pm, ArrayList <Item_VirtualMachine> vm, double cores, double pCP, long mem, int number) {
		
		this.pm = pm; 
		this.vmList = vm;
		this.number = number;
		
		totalResources = new ConstantConstraints(cores, pCP, mem);
		availableResources = new ResourceVector(cores, pCP, mem);
		
		//the state is set if the vmlist is set
	}
	
	/**
	 * toString() is used for debugging and contains the number of the PM in the IAAS and its actual VMs.
	 */	
	public String toString() {
		String erg =  LINE_SEPARATOR + "PM: " + number + " VMs:   " + LINE_SEPARATOR;
		for(int i = 0; i < getVMs().size(); i++) {
			
			erg = erg + getVM(i).toString() + LINE_SEPARATOR;
		}
		return erg ;
	}
	
	/**
	 * Get the ArrayList which contains all actual running VMs on this PM.
	 * @return vmList
	 */
	public ArrayList <Item_VirtualMachine> getVMs() {
		return vmList;		
	}
	
	/**
	 * Initialize the VMs, the State and the consumption of resources of this PM.
	 * @param vms
	 * 			The ArrayList to overwrite the actual one.
	 */
	public void initializePM(ArrayList <Item_VirtualMachine> vms) {
		this.vmList = vms;
		
		if(pm.getState() == PhysicalMachine.State.SWITCHINGOFF || pm.getState() == PhysicalMachine.State.OFF) {
			changeState(State.EMPTY_OFF);
		}
		else {
			if(pm.getState() == PhysicalMachine.State.RUNNING) {
				changeState(State.NORMAL_RUNNING);
			}
			else if(pm.getState() == PhysicalMachine.State.SWITCHINGON) {
				changeState(State.EMPTY_RUNNING);
			}
		}
		
		for(int i = 0; i < vms.size(); i++) {
			consumeResources(vms.get(i));
		}
		
		checkAllocation();
	}
	
	/**
	 * @return The matching PM inside the simulator.
	 */
	public PhysicalMachine getPM() {
		return pm;
	}

	/**
	 * Checks if there are any VMs on this PM.
	 * @return true if VMs are running on this PM.
	 */
	public boolean isHostingVMs() {
		return !getVMs().isEmpty();
	}
	
	
	/**
	 * This class represents all properties of the PM regarding to the avialable resources.
	 * @return cores, perCoreProcessing and memory of the PM in a ResourceVector.
	 */
	
	public ResourceVector getAvailableResources() {
		return this.availableResources;
	}	
	
	/**
	 * This class represents all properties of the PM regarding to the total resources.
	 * @return cores, perCoreProcessing and memory of the PM in a ResourceVector.
	 */
	
	public ConstantConstraints getTotalResources() {
		return this.totalResources;
	}
	
	/**
	 * The possible States for a PM in this abstract modell.
	 * 
	 * For understanding, we need the 'double'-states becouse of the graph. If we shut down 
	 * a PM and have to restart it again it would be an unnecassary action, so we mark them 
	 * as for example EMPTY_RUNNING or EMPTY_OFF. For the allocation we only have the x_RUNNING 
	 * State because the check of the allocation can only occur if the PM is running, otherwise it 
	 * would be empty.
	 * 
	 * Additionally we have two other States for OVERALLOCATED_RUNNING and UNDERALLOCATED_RUNNING,
	 * STILL_OVERALLOCATED / UNCHANGEABLE_OVERALLOCATED and STILL_UNDERALLOCATED and UNCHANGEABLE_UNDERALLOCATED.
	 * This is important becouse of the possibility to determine how often it has been tried
	 * to migrate this PM or VMs of this PM without success. So the State STILL_x shows that
	 * it was not possible to migrate VMs of this PM once and the UNCHANGEABLE_x stands for
	 * two failures in a row, which symbolizes that it will not be possible to get a succesful
	 * migration in the future. In that case the UNCHANGEABLE_x PM will be skipped inside the
	 * algorithm for now. 
	 */	
	public static enum State {
		
		/**
		 * There are actually no VMs on this PM, PM is not running
		 */
		EMPTY_OFF,
		
		/**
		 * PM is running and empty
		 */
		EMPTY_RUNNING,
		
		/**
		 * allocation is between 25 % and 75 %, PM is Running
		 */
		NORMAL_RUNNING,
		
		/**
		 * allocation is lower than 25 %, PM is Running
		 */
		UNDERALLOCATED_RUNNING,
		
		/**
		 * allocation is higher than 75 %, PM is Running
		 */
		OVERALLOCATED_RUNNING,
		
		/**
		 * at the moment nothing can be done to handle the overallocation,
		 * but there will be another try for it
		 */
		STILL_OVERALLOCATED,
		
		/**
		 * at the moment nothing can be done to handle the underallocation,
		 * but there will be another try for it
		 */
		STILL_UNDERALLOCATED,
		
		/**
		 * after a second time STILL_OVERALLOCATED. This means, the allocation
		 * cannot be changed n any way and no migrations are possible anymore.
		 */
		UNCHANGEABLE_OVERALLOCATED,
		
		/**
		 * after a second time STILL_UNDERALLOCATED. This means, the allocation
		 * cannot be changed in any way and no migrations are possible anymore.
		 */
		UNCHANGEABLE_UNDERALLOCATED
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
	
	protected void switchOff() {
		this.changeState(State.EMPTY_OFF);
	}
	
	/**
	 * changes the state of the PM so the graph can give the switch on information
	 * later to the real simulation
	 */
	
	protected void switchOn() {
		this.changeState(State.EMPTY_RUNNING);
	}
	
	/**
	 * Setter for the state of the PM
	 * @param state
	 * 			the wanted state
	 */
	
	protected void changeState(State state) {
		this.state = state;
	}
	
	/**
	 * In this method the status of this PM is considered. For that, the methods
	 * underAllocated() and overAllocated() are written. It is recognized if the last
	 * migration on this PM was not succesful and in case of that the state remains
	 * unchanged.
	 */
	
	protected void checkAllocation() {
		if(isHostingVMs() == false) {
			changeState(State.EMPTY_RUNNING);
		}
		if((underAllocated() && getState().equals(State.STILL_UNDERALLOCATED)) || (underAllocated() && getState().equals(State.UNCHANGEABLE_UNDERALLOCATED)) ) {
			
		}
		else {
			if((overAllocated() && getState().equals(State.STILL_OVERALLOCATED)) || (overAllocated() && getState().equals(State.UNCHANGEABLE_OVERALLOCATED)) ) {
				
			}
			else {
				if(underAllocated())  {
					changeState(State.UNDERALLOCATED_RUNNING);
				}
				else {
					if(overAllocated()) {
							changeState(State.OVERALLOCATED_RUNNING);
					}
					else
						changeState(State.NORMAL_RUNNING);
					}	
				}
		}
	}
	
	/**
	 * Method for checking if the actual PM is overAllocated.
	 * @return true if overloaded, false otherwise
	 */
	
	private boolean overAllocated() {
		
		if(availableResources.compareToOverAllocated(totalResources)) {
			return true;
		}
		else
			return false;
		
		
		/*
		if(totalResources.compareToOverAllocated(availableResources)) {
			return true;
		}
		else
			return false;*/
	}
	
	/**
	 * Method for checking if the actual PM is underAllocated.
	 * @return true if underloaded, false otherwise	  
	 */
	
	private boolean underAllocated() {
		
		if(availableResources.compareToUnderAllocated(totalResources)) {
			return true;
		}
		else
			return false;
		
		
		/*
		if(totalResources.compareToUnderAllocated(availableResources)) {
			return true;
		}
		else
			return false;*/
	}
	
	/**
	 * Getter for a specific VM.
	 * @param position
	 * 			The desired position where the VM is.
	 * @return The VM on this position.
	 */
	
	public Item_VirtualMachine getVM(int position) {
		return getVMs().get(position);
	}
	
	/**
	 * Standard Migration for one VM.
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
		
		//deleting the VM out of the vmlist
		for(int i = 0; i < getVMs().size(); i++) {
			Item_VirtualMachine x = getVM(i);
			
			if(x.equals(vm)) {
				this.getVMs().remove(i);
			}
		}
	}
	
	/**
	 *  This method handles the consumption of resources of the PM by hosting VMs.
	 *  
	 *  @param x
	 * 			The Item_VirtualMachine which is going to be put on this PM. 
	 */

	protected void consumeResources(Item_VirtualMachine x) {
		availableResources.subtract(x.getResources());
	}
	
	/**
	 * This method is for the migration of an ArrayList. If a VM is moved, the consumed
	 * resources need to be restored to the available resources.
	 * 
	 * @param x
	 * 			The Item_VirtualMachine which is going to be demigrated. 
	 */
	
	protected void deconsumeResources(Item_VirtualMachine x) {
		availableResources.add(x.getResources());
	}
}