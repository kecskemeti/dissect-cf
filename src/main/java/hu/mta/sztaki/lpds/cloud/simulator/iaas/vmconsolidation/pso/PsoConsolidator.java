package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.pso;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.CachingPRNG;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.MachineLearningConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.improver.NonImprover;

/**
 * @author Rene Ponto
 *
 *         This class manages VM consolidation with a particle swarm
 *         optimization algorithm. For that, particles are used to find the best
 *         solution (minimized fitness function) for consolidation. At the
 *         moment the amount of currently active PMs, the number of migrations
 *         and the amount of overAllocated PMs are used for the fitness.
 */

public class PsoConsolidator extends MachineLearningConsolidator<Particle> {

	private ArithmeticVector[] currentLocations;
	private ArithmeticVector[] currentVelocities;
	private ArithmeticVector[] personalBests;

	/** learning factor one */
	private int c1;

	/** learning factor two */
	private int c2;

	/** used to get a new velocity for each particle */
	private static final double w = 0.6;

	/**
	 * The constructor uses its superclass-constructor to create an abstract model
	 * and work on the modelled PMs and VMs. After finding the solution everything
	 * will be done inside the simulator.
	 * 
	 * @param toConsolidate The iaas service with the machines to consolidate.
	 * @param consFreq      This value determines, how often the consolidation
	 *                      should run.
	 */
	public PsoConsolidator(final IaaSService toConsolidate, final long consFreq) {
		super(toConsolidate, consFreq);
		setOmitAllocationCheck(true);
	}

	/**
	 * Reads the properties file and sets the constant values for consolidation.
	 */
	@Override
	protected void processProps() {
		super.processProps();
		this.c1 = Integer.parseInt(props.getProperty("psoC1"));
		this.c2 = Integer.parseInt(props.getProperty("psoC2"));
	}

	/**
	 * The toString-method, used for debugging.
	 */
	public String toString() {
		final int bestSolindex = findBestSolution();
		String erg = "Amount of VMs: " + population[0].items.length + ", GlobalBest: " + population[bestSolindex]
				+ ", GlobalBestLocation: " + currentLocations[bestSolindex];
		return erg;
	}

	@Override
	protected Particle modelFactory(final Particle input, final boolean original,
			final InfrastructureModel.Improver localsearch) {
		final int i = getPopFillIndex();
		final Particle p = new Particle(input, original, localsearch);
		initVelocity(i, input.items.length);
		// adds up the velocity to create the initial location
		personalBests[i] = currentLocations[i] = population[i].createLocationFromMapping().addUp(currentVelocities[i]);
		// adjusts the mappings with the new location
		currentLocations[i] = population[i].updateMappings(currentLocations[i], localSearch);
		population[i].savePBest();
		return p;
	}

	@Override
	protected void createPopArray(final int len) {
		population = new Particle[len];
	}

	@Override
	protected void singleIteration() {
		// step 2 - update gBest
		final int bestParticleIndex = findBestSolution(); // get the position of the minimum fitness value

		for (int i = 0; i < population.length; i++) {

			// step 3 - update velocity

			/**
			 * Comment on the function to update the velocity:
			 * 
			 * At first the actual Velocity of the Particle has to be multiplied with the
			 * user-supplied coefficient w, which is called the inertiaComponent. After this
			 * the cognitiveComponent has to be added to the inertiaComponent, which
			 * inherits the multiplication of the coefficients c1 and r1, multiplied with
			 * the result of the personalBestLocation of the Particle minus the actual
			 * location of it. Afterwards the socialComponent also has to be added to the
			 * parts before. It inherits the multiplication of the coefficients c2 and r2,
			 * multiplied with the result of the subtraction of the globalBestLocation with
			 * the actual location of the Particle.
			 */

			final ArithmeticVector inertiaComponent = currentVelocities[i].multiply(w);
			final ArithmeticVector cognitiveComponent = personalBests[i].subtract(currentLocations[i])
					.multiply(c1 * random.nextDoubleFast());
			final ArithmeticVector socialComponent = currentLocations[bestParticleIndex].subtract(currentLocations[i])
					.multiply(c2 * random.nextDoubleFast());
			currentVelocities[i] = inertiaComponent.addUp(cognitiveComponent).addUp(socialComponent);

			// Logger.getGlobal().info("Particle: " + p.getNumber() + ", new Velocity: " +
			// newVel);

			// step 4 - update location

			// now the mapping has to be converted to an ArithmeticVector
			// adds up the velocity to create the updated location
			// then adjusts the mappings with the new location
			currentLocations[i] = population[i]
					.updateMappings(population[i].createLocationFromMapping().addUp(currentVelocities[i]),localSearch);

			// Logger.getGlobal().info("Iteration " + t + ", Updated Particle " +
			// p.getNumber() + System.getProperty("line.separator") + p.toString());
			if (population[i].improvedOnPersonal()) {
				// the aim is to minimize the function
				population[i].savePBest();
				personalBests[i] = currentLocations[i];
			}
		}
	}

	@Override
	protected Particle transformInput(final InfrastructureModel input) {
		return new Particle(input, true, NonImprover.singleton);
	}

	/**
	 * Creates the initial velocity of a particle. The velocity points randomly in a
	 * positive or negative direction.
	 */
	public void initVelocity(final int i, final int len) {
		currentVelocities[i] = new ArithmeticVector(len);
		for (int j = 0; j < len; j++) {
			// here we make a random chance of getting a lower id or a higher id
			currentVelocities[i].add(CachingPRNG.genBoolean() ? 1.0 : -1.0); // add the random velocity
		}
	}

}