package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.pso;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.CachingPRNG;
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

	private ArithmeticVector velocity; // the actual velocity : in which direction shall the solution go?
	private ArithmeticVector location; // the actual location : possible solution

	/**
	 * Total amount of PM overloads, aggregated over all PMs and all resource types
	 */
	protected double bestTotalOverAllocated;
	/** Number of PMs that are on */
	protected int bestNrActivePms;
	/** Number of migrations necessary from original placement of the VMs */
	protected int bestNrMigrations;
	private ArithmeticVector personalBestLocation; // the personal best location so far

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
		this.location = new ArithmeticVector(bins.length);
	}

	/**
	 * Has to be called when using the arithmetics or after the mapping has changed.
	 * Note that there is a difference in saving the pms inside the mappings and
	 * inside the location.
	 */
	public void updateLocation() {

		// Logger.getGlobal().info("Before updateLocation(), new location: " + location
		// + ", mapping: " + mappingToString());

		location.clear();

		for (final ModelVM vm: items) {
			// ModelPMs are stored in the bins array indexed with their hashcode
			location.add(vm.getHostID() + 1.0);
		}

		// Logger.getGlobal().info("After updateLocation(), new location: " + location +
		// ", mapping: " + mappingToString());
	}

	/**
	 * We use the actual location do update the current mapping. For that we have to
	 * check each of the mappings (loads, mapping, used) and update them depending
	 * on the changeds inside the location. Note that there is a difference in
	 * saving the pms inside the mappings and inside the location.
	 */
	public void updateMappings() {

		roundValues();

		// Logger.getGlobal().info("Before updateMappings(), location: " + location + ",
		// mapping: " + mappingToString());

		// check if the mappings and the location are different, then adjust the
		// mappings
		final int locSize = location.size();
		for (int i = 0; i < locSize; i++) {

			// System.out.println("bins.size="+bins.size()+",
			// location[i]="+location.get(i));
			final ModelPM locPm = bins[location.get(i).intValue() - 1]; // the host of this vm, has to be done
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
	 * Creates the initial velocity of a particle. The velocity points randomly in a
	 * positive or negative direction.
	 */
	public void initVelocity() {
		final ArithmeticVector vel = new ArithmeticVector(bins.length);
		for (int j = 0; j < items.length; j++) {
			// here we make a random chance of getting a lower id or a higher id
			vel.add(CachingPRNG.genBoolean() ? 1.0 : -1.0); // add the random velocity
		}

		this.setVelocity(vel);
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
	 * Getter for the best location achieved so far.
	 * 
	 * @return An ArithmeticVector with the personal best location.
	 */
	public ArithmeticVector getPBestLocation() {
		return this.personalBestLocation;
	}

	/**
	 * Sets the personal best location with the parametrized ArithmeticVector.
	 * 
	 * @param loc The new best location.
	 */
	public void setPBestLocation(final ArithmeticVector loc) {
		this.personalBestLocation = loc;
	}

	/**
	 * Getter for the current velocity.
	 * 
	 * @return The current velocity of this particle.
	 */
	public ArithmeticVector getVelocity() {
		return velocity;
	}

	/**
	 * Sets the velocity of this particle with the parametrized ArithmeticVector.
	 * 
	 * @param velocity The new velocity.
	 */
	public void setVelocity(final ArithmeticVector velocity) {
		this.velocity = velocity;
	}

	/**
	 * Getter for the current location.
	 * 
	 * @return The current location of this particle.
	 */
	public ArithmeticVector getLocation() {
		return location;
	}

	/**
	 * Sets the location of this particle with the parametrized ArithmeticVector.
	 * 
	 * @param location The new location.
	 */
	public void setLocation(final ArithmeticVector location) {
		this.location = location;
	}

	/**
	 * Replaces the decimals with one zero to round the values. Can be used after
	 * the arithmetics inside the particle swarm optimization to work with round
	 * values.
	 */
	private void roundValues() {
		final int locLen = location.size();
		for (int i = 0; i < locLen; i++) {
			location.set(i, (double) location.get(i).intValue());
		}
	}

	/**
	 * The toString-method, used for debugging.
	 * 
	 * @return A String-object containing the current location, velocity, Fitness,
	 *         personal best Fitness and the personal best location.
	 */
	public String toString() {
		String erg = "Location: " + this.getLocation() + System.getProperty("line.separator") + "Velocity: "
				+ this.getVelocity() + System.getProperty("line.separator")
				// + "FitnessValue: " + this.evaluateFitnessFunction() + ", PersonalBestFitness:
				// "
//			+ this.getPBest() + System.getProperty("line.separator")
				+ "PersonalBestLocation: " + this.getPBestLocation() + System.getProperty("line.separator");

		return erg;
	}
}