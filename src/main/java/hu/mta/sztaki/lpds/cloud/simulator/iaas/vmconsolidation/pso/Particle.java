package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.pso;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelPM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelVM;

/**
 * @author Rene Ponto
 *
 *         The idea for particles is to have a list of double values where all
 *         hostPMs in order of their hosted VMs are. Therefore the id of the
 *         specific PM is used. On the given list mathematical operations can be
 *         done, like addition and substraction. For that list an
 *         ArithmeticVector is used.
 * 
 *         The ArithmeticVector location contains only the IDs of the PMs while
 *         the mapping contains the specific mapping of PMs to VMs inside
 *         Solution.
 */

public class Particle extends InfrastructureModel {

	final private int number;

	/**
	 * Total amount of PM overloads, aggregated over all PMs and all resource types
	 */
	protected double bestTotalOverAllocated;
	/** Number of PMs that are on */
	protected int bestNrActivePms;
	/** Number of migrations necessary from original placement of the VMs */
	protected int bestNrMigrations;

	/**
	 * Creates a new Particle and sets the bins list with the values out of the
	 * model. For that the constructor of the superclass "Solution" is used.
	 * 
	 * @param bins   The currently existing pms.
	 * @param number The id of this particle.
	 */
	public Particle(final InfrastructureModel base, final int number, final boolean orig, final boolean localsearch) {
		super(base, orig, localsearch);
		this.number = number;
	}

	/**
	 * Has to be called when using the arithmetics or after the mapping has changed.
	 * Note that there is a difference in saving the pms inside the mappings and
	 * inside the location.
	 */
	public ArithmeticVector createLocationFromMapping() {

		// Logger.getGlobal().info("Before updateLocation(), new location: " + location
		// + ", mapping: " + mappingToString());

		ArithmeticVector l=new ArithmeticVector(items.length);

		for (final ModelVM vm: items) {
			// ModelPMs are stored in the bins array indexed with their hashcode
			l.add(vm.getHostID() + 1.0);
		}
		return l;
		// Logger.getGlobal().info("After updateLocation(), new location: " + location +
		// ", mapping: " + mappingToString());
	}

	/**
	 * We use the actual location do update the current mapping. For that we have to
	 * check each of the mappings (loads, mapping, used) and update them depending
	 * on the changeds inside the location. Note that there is a difference in
	 * saving the pms inside the mappings and inside the location.
	 */
	public void updateMappings(ArithmeticVector adjustedLocation) {

		roundValues(adjustedLocation);

		// Logger.getGlobal().info("Before updateMappings(), location: " + location + ",
		// mapping: " + mappingToString());

		// check if the mappings and the location are different, then adjust the
		// mappings
		final int locSize = adjustedLocation.size();
		for (int i = 0; i < locSize; i++) {

			// System.out.println("bins.size="+bins.size()+",
			// location[i]="+location.get(i));
			final ModelPM locPm = bins[adjustedLocation.get(i).intValue() - 1]; // the host of this vm, has to be done
			// because the ids start at one
			final ModelVM mvm = items[i];
			final ModelPM mappedPm = mvm.gethostPM();

			// now we have to check if both hosts are similar, then we can move on with the
			// next.
			// Otherwise we have to adjust the mappings
			if (locPm.hashCode() != mappedPm.hashCode()) {
				// pms are not the same
				mappedPm.migrateVM(mvm, locPm);
			}
		}

		// determine the fitness
		this.calculateFitness();

		// Logger.getGlobal().info("After updateMappings(), location: " + location + ",
		// mapping: " + mappingToString());

	}

	/**
	 * Getter for the number of this particle.
	 * 
	 * @return The number of this particle.
	 */
	public int getNumber() {
		return number;
	}

	public boolean improvedOnPersonal() {
		return betterThan(totalOverAllocated, nrActivePms, nrMigrations, bestTotalOverAllocated, bestNrActivePms,
				bestNrMigrations);
	}

	public void savePBest() {
		this.bestNrActivePms = this.nrActivePms;
		this.bestNrMigrations = this.nrMigrations;
		this.bestTotalOverAllocated = this.totalOverAllocated;
	}

	/**
	 * Replaces the decimals with one zero to round the values. Can be used after
	 * the arithmetics inside the particle swarm optimization to work with round
	 * values.
	 */
	private void roundValues(final ArithmeticVector l) {
		final int locLen = l.size();
		for (int i = 0; i < locLen; i++) {
			l.set(i, (double) l.get(i).intValue());
		}
	}
}