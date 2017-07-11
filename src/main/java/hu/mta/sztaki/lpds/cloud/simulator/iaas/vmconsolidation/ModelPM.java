package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
/**
 * @author Julian Bellendorf, René Ponto
 *
 * This class represents a PhysicalMachine. It contains the original PM, its VMs in an ArrayList,
 * the total resources as ConstantConstraints, the avaiable resources as a ResourceVector and the possible
 * States. The States are not the same as inside the simulator, because for migrating it is useful to
 * introduce some new States for the allocation of the given PM.
 */

public class ModelPM {
	
	private PhysicalMachine pm; 
	private ArrayList <ModelVM> vmList = new ArrayList <ModelVM>();
	private int number;
	
	private ConstantConstraints totalResources;
	private ResourceVector consumedResources;
	
	private State state;
	
	/**
	 * This represents a Phsyical Machine of the simulator. It is abstract and inherits only the methods and properties
	 * which are necessary to do the consolidation inside this model.
	 * The threshold is defined by the start of the consolidator and a percentage of the total resources. If the allocation is 
	 * greater than the upper bound or less than the lower bound, the state of the PM switches to OVERALLOCATED or UNDERALLOCATED.
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
	 * 			The number of the PM in its IaaS, used for debugging.
	 */
	public ModelPM(PhysicalMachine pm, ArrayList <ModelVM> vm, double cores, double pCP, long mem, int number) {
		
		this.pm = pm; 
		this.number = number;
		
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
		
		totalResources = new ConstantConstraints(cores, pCP, mem);
		consumedResources = new ResourceVector(0, 0, 0);
	}
	
	/**
	 * Checks if the PM is in a state where nothing has to be changed.
	 * @return
	 * 			True if the State is NORMAL_RUNNING, UNCHANGEABLE_OVERALLOCATED or UNCHANGEABLE_UNDERALLOCATED
	 */
	public boolean nothingIsToChange() {
		
		if(getState() == State.NORMAL_RUNNING || getState() == State.UNCHANGEABLE_OVERALLOCATED || getState() == State.UNCHANGEABLE_UNDERALLOCATED) {
			return true;
		}
		else
			return false;
	}
	
	/**
	 * Checks if the PM is UnderAllocated and shall be migrated.
	 * @return
	 * 			True if the State is UNDERALLOCATED_RUNNING or STILL_UNDERALLOCATED
	 */
	public boolean isUnderAllocatedChangeable() {
		if(getState() == State.UNDERALLOCATED_RUNNING || getState() == State.STILL_UNDERALLOCATED) {
			return true;
		}
		else
			return false;
	}
	
	/**
	 * Checks if the PM is OverAllocated and shall be migrated.
	 * @return
	 * 			True if the State is OVERALLOCATED_RUNNING or STILL_OVERALLOCATED
	 */
	public boolean isOverAllocatedChangeable() {
		if(getState() ==  State.OVERALLOCATED_RUNNING || getState() == State.STILL_OVERALLOCATED) {
			return true;
		}
		else
			return false;
	}
	
	/**
	 * This method checks if a given VM can be hosted on this PM without changing the State to overAllocated.
	 * 
	 * @param vmResources
	 * 			The resources of the VM which shall be added.
	 * @return
	 * 			True if the VM can be added.
	 */
	public boolean doOrCancelMigration(ModelVM toAdd) {
		
		checkAllocation();
		ResourceVector available = new ResourceVector(totalResources.getRequiredCPUs(), totalResources.getRequiredProcessingPower(), totalResources.getRequiredMemory());
		available.subtract(consumedResources);
		if(toAdd.getResources().canBeAdded(available)) {

			addVM(toAdd);
			checkAllocation();
			
			if(this.getState().equals(State.OVERALLOCATED_RUNNING)) {
				removeVM(toAdd);
				return false;
			}
			else {
				removeVM(toAdd);
				return true;
			}
		}
		else
			return false;
	}
	
	/**
	 * toString() is used for debugging and contains the number of the PM in the IaaS and its actual VMs.
	 */	
	public String toString() {
		String erg =  System.getProperty("line.separator") + "PM: " + number + System.getProperty("line.separator") + 
				"Resources: TotalProcessingPower: " + this.consumedResources.getTotalProcessingPower() + 
				", Memory: " + this.consumedResources.getRequiredMemory() + System.getProperty("line.separator") +
				"VMs:   " + System.getProperty("line.separator");
		for(int i = 0; i < getVMs().size(); i++) {
			
			erg = erg + getVM(i).toString() + System.getProperty("line.separator");
		}
		return erg ;
	}
	
	/**
	 * Instantiates the PM.
	 * @param vm
	 * 			The list with the VMs.
	 */
	public void initializePM(ArrayList<ModelVM> vms) {
		//set the VM list and consumes resources
		for(int i = 0; i < vms.size(); i++) {
			this.addVM(vms.get(i));
		}
	}
	
	/**
	 * Get the ArrayList which contains all actual running VMs on this PM.
	 * @return vmList
	 */
	public ArrayList <ModelVM> getVMs() {
		return vmList;		
	}
	
	/**
	 * Adds a given VM to this PM
	 * @param vm
	 * 			The VM which is going to be put on this PM.
	 */
	public void addVM(ModelVM vm) {
		vmList.add(vm);
		consumedResources.add(vm.getResources());
		
		checkAllocation();
	}
	
	/**
	 * Removes a given VM of this PM
	 * @param vm
	 * 			The VM which is going to be removed of this PM.
	 */
	public void removeVM(ModelVM vm) {
		vmList.remove(vm);
		consumedResources.subtract(vm.getResources());
		
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
	
	public ResourceVector getConsumedResources() {
		return this.consumedResources;
	}	
	
	/**
	 * This class represents all properties of the PM regarding to the total resources.
	 * @return cores, perCoreProcessing and memory of the PM as ConstantConstraints.
	 */
	
	public ConstantConstraints getTotalResources() {
		return this.totalResources;
	}
	
	/**
	 * The possible States for a PM in this abstract modell.
	 * 
	 * For understanding, we need the 'double'-states because of the graph. If we shut down 
	 * a PM and have to restart it again, it would be an unnecessary action, so we mark them 
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
		 * allocation is between the upper and lower threshold, PM is running
		 */
		NORMAL_RUNNING,
		
		/**
		 * allocation is lower than the lower threshold, PM is running
		 */
		UNDERALLOCATED_RUNNING,
		
		/**
		 * allocation is higher than the upper threshold, PM is running
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
		 * cannot be changed in any way and no migrations are possible anymore.
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
		if((isUnderAllocated() && getState().equals(State.STILL_UNDERALLOCATED)) || (isUnderAllocated() && getState().equals(State.UNCHANGEABLE_UNDERALLOCATED)) ) {
			
		}
		else {
			if((isOverAllocated() && getState().equals(State.STILL_OVERALLOCATED)) || (isOverAllocated() && getState().equals(State.UNCHANGEABLE_OVERALLOCATED)) ) {
				
			}
			else {
				if(isUnderAllocated())  {
					changeState(State.UNDERALLOCATED_RUNNING);
				}
				else {
					if(isOverAllocated()) {
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
	private boolean isOverAllocated() {
		
		if(consumedResources.compareToOverAllocated(totalResources)) {
			return true;
		}
		else
			return false;
	}
	
	/**
	 * Method for checking if the actual PM is underAllocated.
	 * @return true if underloaded, false otherwise	  
	 */	
	private boolean isUnderAllocated() {
		
		if(consumedResources.compareToUnderAllocated(totalResources)) {
			return true;
		}
		else
			return false;
	}
	
	/**
	 * Getter for a specific VM.
	 * @param position
	 * 			The desired position where the VM is.
	 * @return The VM on this position.
	 */	
	public ModelVM getVM(int position) {
		return getVMs().get(position);
	}
	
	/**
	 * Standard Migration for one VM.
	 * @param vm
	 * 			The VM which is going to be migrated.
	 * @param target
	 * 			The target PM where to migrate.
	 */	
	public void migrateVM(ModelVM vm, ModelPM target) {
		
		target.getVMs().add(vm);
		target.addVM(vm);
		this.removeVM(vm);
		vm.sethostPM(target);
		
		//deleting the VM out of the vmlist
		for(int i = 0; i < getVMs().size(); i++) {
			ModelVM x = getVM(i);
			
			if(x.equals(vm)) {
				this.getVMs().remove(i);
			}
		}
	}
}