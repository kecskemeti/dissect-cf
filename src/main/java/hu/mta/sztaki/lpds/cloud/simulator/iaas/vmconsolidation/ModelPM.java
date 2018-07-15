package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.UnalterableConstraintsPropagator;

/**
 * @author Julian Bellendorf, Rene Ponto
 *
 *         This class represents a PhysicalMachine. It contains the original PM,
 *         its VMs in an ArrayList, the total resources as ConstantConstraints,
 *         the available resources as a ResourceVector and the possible States.
 *         The States are not the same as inside the simulator, because for
 *         migrating it is useful to introduce some new States for the
 *         allocation of the given PM.
 */
public class ModelPM {
	public static final ModelPM[] mpmArrSample = new ModelPM[0];

	private final List<ModelVM> vmList;

	private final AlterableResourceConstraints consumedResources;
	private final AlterableResourceConstraints freeResources;
	public final UnalterableConstraintsPropagator consumed;
	public final UnalterableConstraintsPropagator free;
	private final AlterableResourceConstraints reserved; // the reserved resources

	public final ImmutablePMComponents basedetails;

	private State state;

	/**
	 * This represents a Physical Machine of the simulator. It is abstract and
	 * inherits only the methods and properties which are necessary to do the
	 * consolidation inside this model. The threshold is defined by the start of the
	 * consolidator and a percentage of the total resources. If the allocation is
	 * greater than the upper bound or less than the lower bound, the state of the
	 * PM switches to OVERALLOCATED or UNDERALLOCATED.
	 * 
	 * @param pm             The real Physical Machine in the Simulator.
	 * @param mvm            An array which contains all VMs running on this PM.
	 * @param cores          The cores of the PM.
	 * @param pCP            The Power of one core.
	 * @param mem            The memory of this PM.
	 * @param number         The number of the PM in its IaaS, used for debugging.
	 * @param upperThreshold The upperThreshold out of the properties.
	 * @param lowerThreshold The lowerThreshold out of the properties.
	 */
	public ModelPM(final PhysicalMachine pm, final int number, final double upperThreshold,
			final double lowerThreshold) {
		basedetails = new ImmutablePMComponents(pm, number, lowerThreshold, upperThreshold);

		if (pm.getState() == PhysicalMachine.State.SWITCHINGOFF || pm.getState() == PhysicalMachine.State.OFF) {
			state = State.EMPTY_OFF;
		} else {
			if (pm.getState() == PhysicalMachine.State.RUNNING) {
				state = State.NORMAL_RUNNING;
			} else if (pm.getState() == PhysicalMachine.State.SWITCHINGON) {
				state = State.EMPTY_RUNNING;
			}
		}

		vmList = new ArrayList<>(pm.publicVms.size());
		consumedResources = new AlterableResourceConstraints(0, pm.getCapacities().getRequiredProcessingPower(), 0);
		freeResources = new AlterableResourceConstraints(pm.getCapacities());
		consumed = new UnalterableConstraintsPropagator(consumedResources);
		free = new UnalterableConstraintsPropagator(freeResources);
		reserved = new AlterableResourceConstraints(ConstantConstraints.noResources);
		// Logger.getGlobal().info("Created PM: "+toString());
	}

	public ModelPM(final ModelPM toCopy) {
		final int ll = toCopy.vmList.size();
		this.vmList = new ArrayList<>(ll);
		for (int i = 0; i < ll; i++) {
			this.vmList.add(new ModelVM(toCopy.vmList.get(i)));
		}
		this.consumedResources = new AlterableResourceConstraints(toCopy.consumedResources);
		this.freeResources = new AlterableResourceConstraints(toCopy.freeResources);
		this.consumed = new UnalterableConstraintsPropagator(consumedResources);
		this.free = new UnalterableConstraintsPropagator(freeResources);
		this.reserved = new AlterableResourceConstraints(toCopy.reserved);
		// Shallow copy from here:
		this.basedetails = toCopy.basedetails;
		this.state = toCopy.state;
	}

	/**
	 * toString() is used for debugging and contains the number of the pm in the
	 * IaaS and its actual vms.
	 * 
	 * @return A string containing the pms number, the resources (cap and load), the
	 *         state and its vms.
	 */
	public String toString() {
		String result = "PM " + basedetails.number + ", cap=" + basedetails.pm.getCapacities().toString() + ", curr="
				+ consumedResources.toString() + ", state=" + state + ", VMs=";
		boolean first = true;
		for (ModelVM vm : vmList) {
			if (!first)
				result = result + " ";
			result = result + vm.toShortString() + vm.getResources().toString();
			first = false;
		}
		// result=result+" (running="+pm.isRunning()+")";
		return result;
	}

	/**
	 * A small representation to get the id of this pm.
	 * 
	 * @return The id of this pm.
	 */
	public String toShortString() {
		return "PM " + basedetails.number;
	}

	/**
	 * Adds a given VM to this PM.
	 * 
	 * @param vm The VM which is going to be put on this PM.
	 */
	public boolean addVM(final ModelVM vm) {
		if (state == State.EMPTY_RUNNING)
			state = State.NORMAL_RUNNING;
		vmList.add(vm);
		final ResourceConstraints rc = vm.getResources();
		consumedResources.singleAdd(rc);
		freeResources.subtract(rc);
		vm.sethostPM(this);

		// adding was succesful
		return true;
	}

	/**
	 * Removes a given VM of this PM.
	 * 
	 * @param vm The VM which is going to be removed of this PM.
	 */
	public boolean removeVM(final ModelVM vm) {
		vmList.remove(vm);
		// adapt the consumed resources
		final ResourceConstraints rc = vm.getResources();
		consumedResources.subtract(rc);
		freeResources.singleAdd(rc);
		vm.sethostPM(null);
		vm.prevPM = this;
		if (isHostingVMs())
			state = State.EMPTY_RUNNING;

		// removing was succesful
		return true;
	}

	/**
	 * Migration of a VM from this PM to another.
	 * 
	 * @param vm     The VM which is going to be migrated.
	 * 
	 * @param target The target PM where to migrate.
	 */
	public void migrateVM(final ModelVM vm, final ModelPM target) {
		this.removeVM(vm);
		target.addVM(vm);
	}

	/**
	 * Reserves resources for possible migrations.
	 * 
	 * @param vm The Virtual Machine which could be migrated.
	 */
	public void reserveResources(final ModelVM vm) {
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
		return !vmList.isEmpty();
	}

	/**
	 * The possible States for a PM in this abstract model.
	 * 
	 * For understanding, we need the 'double'-states because of the graph. If we
	 * shut down a PM and have to restart it again, it would be an unnecessary
	 * action, so we mark them as for example EMPTY_RUNNING or EMPTY_OFF. For the
	 * allocation we only have the x_RUNNING State because the check of the
	 * allocation can only occur if the PM is running, otherwise it would be empty.
	 * 
	 * Additionally we have one other State for OVERALLOCATED_RUNNING and
	 * UNDERALLOCATED_RUNNING, UNCHANGEABLE_OVERALLOCATED and
	 * UNCHANGEABLE_UNDERALLOCATED. This is important becouse of the possibility to
	 * determine how often it has been tried to migrate this PM or VMs of this PM
	 * without success. So the State UNCHANGEABLE_x symbolizes that it will not be
	 * possible to get a succesful migration in the future. In that case the
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
		NORMAL_RUNNING

	};

	/**
	 * Changes the state of the pm so the graph can give the switch off information
	 * later to the simulation.
	 */
	protected void switchOff() {
		state = State.EMPTY_OFF;
	}

	/**
	 * Changes the state of the pm so the graph can give the switch on information
	 * later to the simulation.
	 */
	protected void switchOn() {
		state = State.EMPTY_RUNNING;
	}

	/**
	 * Setter for the state of this pm.
	 * 
	 * @param state The new state for this pm.
	 */
	public void setState(final State state) {
		this.state = state;
	}

	/**
	 * Method for checking if the actual pm is overAllocated.
	 * 
	 * @return True if overAllocated, false otherwise.
	 */
	public boolean isOverAllocated() {
		return consumedResources.getTotalProcessingPower() > basedetails.upperThrResources.getTotalProcessingPower()
				|| consumedResources.getRequiredMemory() > basedetails.upperThrResources.getRequiredMemory();
	}

	/**
	 * Method for checking if the current pm is underAllocated.
	 * 
	 * @return True if it is underAllocated, false otherwise.
	 */
	public boolean isUnderAllocated() {
		return consumedResources.compareTo(basedetails.lowerThrResources) == -1;
	}

	/**
	 * This method checks if a given vm can be hosted on this pm without changing
	 * the state to overAllocated.
	 * 
	 * @param toAdd The vm which shall be added.
	 * 
	 * @return True if the vm can be added.
	 */
	public boolean isMigrationPossible(final ModelVM toAdd) {
		final AlterableResourceConstraints available = new AlterableResourceConstraints(basedetails.upperThrResources);
		available.subtract(consumedResources);
		available.subtract(reserved);
		// Logger.getGlobal().info("available: "+available.toString());
		return toAdd.getResources().getTotalProcessingPower() <= available.getTotalProcessingPower()
				&& toAdd.getResources().getRequiredMemory() <= available.getRequiredMemory();
	}

	/**
	 * Getter for a specific VM.
	 * 
	 * @param position The desired position where the VM is.
	 * 
	 * @return The VM on this position, null if there is none.
	 */
	public ModelVM getVM(final int position) {
		if (vmList.size() <= position) {
			return null;
		} else
			return vmList.get(position);
	}

	/**
	 * Getter.
	 * 
	 * @return The list which contains all actual running vms on this PM.
	 */
	public List<ModelVM> getVMs() {
		return vmList;
	}

	/**
	 * Getter for the non-abstract version of this pm.
	 * 
	 * @return The matching pm inside the simulator.
	 */
	public PhysicalMachine getPM() {
		return basedetails.pm;
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
	public ResourceConstraints getConsumedResources() {
		return consumed;
	}

	/**
	 * This class represents the total resources of this PM.
	 * 
	 * @return cores, perCoreProcessing and memory of the PM as ConstantConstraints.
	 */
	public ResourceConstraints getTotalResources() {
		return basedetails.pm.getCapacities();
	}

	/**
	 * Getter.
	 * 
	 * @return The lower threshold for the pms.
	 */
	public ConstantConstraints getLowerThreshold() {
		return basedetails.lowerThrResources;
	}

	/**
	 * Getter.
	 * 
	 * @return The upper threshold for the pms.
	 */
	public ConstantConstraints getUpperThreshold() {
		return basedetails.upperThrResources;
	}

	/**
	 * Getter.
	 * 
	 * @return The id of this pm.
	 */
	@Override
	public int hashCode() {
		return basedetails.number;
	}
}
