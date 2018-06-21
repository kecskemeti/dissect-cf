package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

/**
 * @author Julian Bellendorf, Rene Ponto
 *
 * This class represents a PhysicalMachine. It contains the original PM, its VMs in an ArrayList,
 * the total resources as ConstantConstraints, the available resources as a ResourceVector and the possible
 * States. The States are not the same as inside the simulator, because for migrating it is useful to
 * introduce some new States for the allocation of the given PM.
 */
public class ModelPM {

	private PhysicalMachine pm;		// the real PM inside the simulator
	private List<ModelVM> vmList;
	private int number;

	private AlterableResourceConstraints consumedResources;
	private AlterableResourceConstraints reserved;		// the reserved resources
	private ConstantConstraints lowerThrResources, upperThrResources;

	private double lowerThreshold, upperThreshold;

	private State state;

	/**
	 * This represents a Physical Machine of the simulator. It is abstract and inherits only the methods and properties
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
	 * @param upperThreshold
	 * 			The upperThreshold out of the properties.
	 * @param lowerThreshold
	 * 			The lowerThreshold out of the properties.
	 */
	public ModelPM(PhysicalMachine pm, int number, double upperThreshold, double lowerThreshold) {
		this.pm = pm;
		vmList = new ArrayList<>();
		this.number = number;
		AlterableResourceConstraints rc=new AlterableResourceConstraints(pm.getCapacities());
		rc.multiply(lowerThreshold);
		this.lowerThrResources=new ConstantConstraints(rc);
		rc=new AlterableResourceConstraints(pm.getCapacities());
		rc.multiply(upperThreshold);
		this.upperThrResources=new ConstantConstraints(rc);

		if(pm.getState() == PhysicalMachine.State.SWITCHINGOFF || pm.getState() == PhysicalMachine.State.OFF) {
			setState(State.EMPTY_OFF);
		}
		else {
			if(pm.getState() == PhysicalMachine.State.RUNNING) {
				setState(State.NORMAL_RUNNING);
			}
			else if(pm.getState() == PhysicalMachine.State.SWITCHINGON) {
				setState(State.EMPTY_RUNNING);
			}
		}

		consumedResources = new AlterableResourceConstraints(0, pm.getCapacities().getRequiredProcessingPower(), 0);
		reserved = new AlterableResourceConstraints(ConstantConstraints.noResources);
		//Logger.getGlobal().info("Created PM: "+toString());
	}

	/**
	 * toString() is used for debugging and contains the number of the pm in the IaaS and its actual vms.
	 * 
	 * @return A string containing the pms number, the resources (cap and load), the state and its vms.
	 */	
	public String toString() {
		String result="PM " + number + ", cap="+pm.getCapacities().toString()+", curr="+consumedResources.toString()+", state="+state+", VMs=";
		boolean first=true;
		for(ModelVM vm : vmList) {
			if(!first)
				result=result+" ";
			result=result+vm.toShortString()+vm.getResources().toString();
			first=false;
		}
		//result=result+" (running="+pm.isRunning()+")";
		return result;
	}

	/**
	 * A small representation to get the id of this pm.
	 * 
	 * @return The id of this pm.
	 */
	public String toShortString() {
		return "PM " + number;
	}

	/**
	 * Adds a given VM to this PM.
	 * 
	 * @param vm The VM which is going to be put on this PM.
	 */
	public boolean addVM(ModelVM vm) {		
		vmList.add(vm);
		consumedResources.add(vm.getResources());		
		checkAllocation();
		
		// adding was succesful
		return true;
	}

	/**
	 * Removes a given VM of this PM.
	 * 
	 * @param vm The VM which is going to be removed of this PM.
	 */
	private boolean removeVM(ModelVM vm) {
		vmList.remove(vm);
		// adapt the consumed resources
		consumedResources.subtract(vm.getResources());		
		checkAllocation();
		
		// removing was succesful
		return true;
	}

	/**
	 * Migration of a VM from this PM to another.
	 * 
	 * @param vm The VM which is going to be migrated.
	 * 
	 * @param target The target PM where to migrate.
	 */	
	public void migrateVM(ModelVM vm, ModelPM target) {
		target.addVM(vm);		
		this.removeVM(vm);
		vm.sethostPM(target);
	}

	/**
	 * Reserves resources for possible migrations. 
	 * 
	 * @param vm The Virtual Machine which could be migrated.
	 */
	public void reserveResources(ModelVM vm) {
		this.reserved.add(vm.getResources());
	}
	
	/**
	 * Resets the reserved resources.
	 */
	public void setResourcesFree() {
		this.reserved.subtract(reserved);
	}

	/**
	 * Checks if there are any VMs on this PM.
	 * 
	 * @return true if VMs are running on this PM.
	 */
	public boolean isHostingVMs() {
		return !getVMs().isEmpty();
	}
	
	/**
	 * The possible States for a PM in this abstract model.
	 * 
	 * For understanding, we need the 'double'-states because of the graph. If we shut down 
	 * a PM and have to restart it again, it would be an unnecessary action, so we mark them 
	 * as for example EMPTY_RUNNING or EMPTY_OFF. For the allocation we only have the x_RUNNING 
	 * State because the check of the allocation can only occur if the PM is running, otherwise it 
	 * would be empty.
	 * 
	 * Additionally we have one other State for OVERALLOCATED_RUNNING and UNDERALLOCATED_RUNNING,
	 * UNCHANGEABLE_OVERALLOCATED and UNCHANGEABLE_UNDERALLOCATED.
	 * This is important becouse of the possibility to determine how often it has been tried
	 * to migrate this PM or VMs of this PM without success. So the State UNCHANGEABLE_x symbolizes 
	 * that it will not be possible to get a succesful migration in the future. In that case the 
	 * UNCHANGEABLE_x PM will be skipped inside the algorithm for now. 
	 */	
	public static enum State {
		
		/**
		 * There are actually no vms on this pm, pm is not running
		 */
		EMPTY_OFF,
		
		/**
		 * pm is running and empty
		 */
		EMPTY_RUNNING,
		
		/**
		 * allocation is between the upper and lower threshold, pm is running
		 */
		NORMAL_RUNNING,
		
		/**
		 * allocation is lower than the lower threshold, pm is running
		 */
		UNDERALLOCATED_RUNNING,
		
		/**
		 * allocation is higher than the upper threshold, pm is running
		 */
		OVERALLOCATED_RUNNING,
		
		/**
		 * allocation cannot be changed in any way and no migrations are possible anymore.
		 */
		UNCHANGEABLE_OVERALLOCATED,
		
		/**
		 * allocation cannot be changed in any way and no migrations are possible anymore.
		 */
		UNCHANGEABLE_UNDERALLOCATED
	};
	
	/**
	 * Checks if this pm is currently running.
	 * 
	 * @return True, if the state of this pm is not EMPTY_OFF, false otherwise.
	 */
	public boolean isRunning() {
		return !getState().equals(ModelPM.State.EMPTY_OFF);
	}
	
	/**
	 * Changes the state of the pm so the graph can give the switch off information
	 * later to the simulation.
	 */	
	protected void switchOff() {
		this.setState(State.EMPTY_OFF);
	}	
	/**
	 * Changes the state of the pm so the graph can give the switch on information
	 * later to the simulation.
	 */	
	protected void switchOn() {
		this.setState(State.EMPTY_RUNNING);
	}
	
	/**
	 * Setter for the state of this pm.
	 * 
	 * @param state The new state for this pm.
	 */	
	public void setState(State state) {
		this.state = state;
	}
	
	/**
	 * In this method the status of this pm is considered. For that, the methods
	 * underAllocated() and overAllocated() are written. It is recognized if the last
	 * migration on this PM was not succesful and in case of that the state remains
	 * unchanged.
	 */	
	protected void checkAllocation() {
		if(isHostingVMs() == false) {
			setState(State.EMPTY_RUNNING);
		}
		else if(isUnderAllocated() && getState().equals(State.UNCHANGEABLE_UNDERALLOCATED)) {
			
		}
		else {
			if(isOverAllocated() && getState().equals(State.UNCHANGEABLE_OVERALLOCATED)) {
				
			}
			else {
				if(isUnderAllocated())  {
					setState(State.UNDERALLOCATED_RUNNING);
				}
				else {
					if(isOverAllocated()) {
							setState(State.OVERALLOCATED_RUNNING);
					}
					else
						setState(State.NORMAL_RUNNING);
					}	
				}
		}
	}
	
	/**
	 * Method for checking if the actual pm is overAllocated.
	 * 
	 * @return True if overAllocated, false otherwise.
	 */	
	public boolean isOverAllocated() {
		return consumedResources.getTotalProcessingPower() > upperThrResources.getTotalProcessingPower() || consumedResources.getRequiredMemory() > upperThrResources.getRequiredMemory();
	}
	
	/**
	 * Method for checking if the current pm is underAllocated.
	 * 
	 * @return True if it is underAllocated, false otherwise.
	 */	
	public boolean isUnderAllocated() {
		return consumedResources.compareTo(lowerThrResources)==-1;
	}
	
	/**
	 * Checks if the pm is in a state where nothing has to be changed.
	 * 
	 * @return True if the state is NORMAL_RUNNING, UNCHANGEABLE_OVERALLOCATED or UNCHANGEABLE_UNDERALLOCATED, false otherwise.
	 */
	public boolean isNothingToChange() {
		
		if(getState() == State.NORMAL_RUNNING || getState() == State.UNCHANGEABLE_OVERALLOCATED || getState() == State.UNCHANGEABLE_UNDERALLOCATED) {
			return true;
		}
		else
			return false;
	}
	
	/**
	 * This method checks if a given vm can be hosted on this pm without changing the state to overAllocated.
	 * 
	 * @param toAdd The vm which shall be added.
	 * 
	 * @return True if the vm can be added.
	 */
	public boolean isMigrationPossible(ModelVM toAdd) {
		
		checkAllocation();
		AlterableResourceConstraints available = new AlterableResourceConstraints(pm.getCapacities());
		available.subtract(consumedResources);
		available.subtract(reserved);
		//Logger.getGlobal().info("available: "+available.toString());
		if(toAdd.getResources().getTotalProcessingPower() <= available.getTotalProcessingPower() && toAdd.getResources().getRequiredMemory() <= available.getRequiredMemory()) {
			//Logger.getGlobal().info("canbeadded");

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
	 * Getter for a specific VM.
	 * 
	 * @param position
	 * 			The desired position where the VM is.
	 * 
	 * @return The VM on this position, null if there is none.
	 */	
	public ModelVM getVM(int position) {
		if(getVMs().size() <= position) {
			return null;
		}
		else
			return getVMs().get(position);
	}
	
	/**
	 * Getter.
	 * 
	 * @return The list which contains all actual running vms on this PM.
	 */
	public List <ModelVM> getVMs() {
		return vmList;		
	}
	
	/**
	 * Getter for the non-abstract version of this pm.
	 * 
	 * @return The matching pm inside the simulator.
	 */
	public PhysicalMachine getPM() {
		return pm;
	}
	
	/**
	 * Getter for the state.
	 * 
	 * @return The current state of this pm.
	 */	
	public State getState() {
		return this.state;
	}
	
	/**
	 * This class represents the consumed resources of this PM.
	 * 
	 * @return cores, perCoreProcessing and memory of the PM in a ResourceVector.
	 */	
	public ConstantConstraints getConsumedResources() {
		return new ConstantConstraints(consumedResources);
	}	
	
	/**
	 * This class represents the total resources of this PM.
	 * 
	 * @return cores, perCoreProcessing and memory of the PM as ConstantConstraints.
	 */	
	public ResourceConstraints getTotalResources() {
		return pm.getCapacities();
	}
	
	/**
	 * Subtracts the occupied resources with the total resources.
	 * 
	 * @return The free resources of this ModelPM without the reserved ones.
	 */
	public ResourceConstraints getFreeResources() {
		AlterableResourceConstraints r=new AlterableResourceConstraints(pm.getCapacities());
		r.subtract(consumedResources);
		return r;
	}
	
	/**
	 * Getter.
	 * 
	 * @return The lower threshold for the pms.
	 */
	public double getLowerThreshold() {
		return lowerThreshold;
	}

	/**
	 * Getter.
	 * 
	 * @return The upper threshold for the pms.
	 */
	public double getUpperThreshold() {
		return upperThreshold;
	}

	/**
	 * Getter.
	 * 
	 * @return The id of this pm.
	 */
	@Override
	public int hashCode() {
		return number;
	}
}