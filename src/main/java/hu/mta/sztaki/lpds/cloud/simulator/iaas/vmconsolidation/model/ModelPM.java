package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model;

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

	public final ImmutablePMComponents basedetails;

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
	public ModelPM(final PhysicalMachine pm, final double lowerThreshold, final int number,
			final double upperThreshold) {
		basedetails = new ImmutablePMComponents(pm, lowerThreshold, number, upperThreshold);

		vmList = new ArrayList<>(pm.publicVms.size());
		consumedResources = genEmptyConsumed();
		freeResources = genCompletelyFree();
		consumed = new UnalterableConstraintsPropagator(consumedResources);
		free = new UnalterableConstraintsPropagator(freeResources);
		// Logger.getGlobal().info("Created PM: "+toString());
	}

	private AlterableResourceConstraints genEmptyConsumed() {
		return new AlterableResourceConstraints(0, basedetails.pm.getCapacities().getRequiredProcessingPower(), 0);
	}

	private AlterableResourceConstraints genCompletelyFree() {
		return new AlterableResourceConstraints(basedetails.pm.getCapacities());
	}

	/**
	 * ModelVMs are not copied!
	 */
	public ModelPM(final ModelPM toCopy) {
		// Shallow copy on basedetails:
		this.basedetails = toCopy.basedetails;
		// Proper copy (except VMlist on the rest)
		final int ll = toCopy.vmList.size();
		this.vmList = new ArrayList<>(ll);
		this.consumedResources = genEmptyConsumed();
		this.freeResources = genCompletelyFree();
		this.consumed = new UnalterableConstraintsPropagator(consumedResources);
		this.free = new UnalterableConstraintsPropagator(freeResources);
	}

	/**
	 * toString() is used for debugging and contains the number of the pm in the
	 * IaaS and its actual vms.
	 * 
	 * @return A string containing the pms number, the resources (cap and load), the
	 *         state and its vms.
	 */
	public String toString() {
		String result = "PM " + basedetails.number + ",\n cap=" + basedetails.pm.getCapacities().toString() + ",\n curr="
				+ consumedResources.toString() + ",\n state=" + (isHostingVMs() ? "ON" : "OFF") + ",\n VMs=";
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
	 * Checks if there are any VMs on this PM.
	 * 
	 * @return true if VMs are running on this PM.
	 */
	public boolean isHostingVMs() {
		return !vmList.isEmpty();
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
		// Logger.getGlobal().info("available: "+available.toString());
		return toAdd.getResources().getTotalProcessingPower() <= available.getTotalProcessingPower()
				&& toAdd.getResources().getRequiredMemory() <= available.getRequiredMemory();
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
