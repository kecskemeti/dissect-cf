package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.pso;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.MachineLearningConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.GenHelper;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.PreserveAllocations;
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

	/** learning factor one */
	private double c1;

	/** learning factor two */
	private double c2;

	public static final FitCompare bestComp = new FitCompare() {
		@Override
		public boolean isBetterThan(final InfrastructureModel a, final InfrastructureModel b) {
			return ((Particle) a).isBetterBest((Particle) b);
		}
	};

	public static final FitCompare worstCurrentComp = new FitCompare() {
		@Override
		public boolean isBetterThan(final InfrastructureModel a, final InfrastructureModel b) {
			return b.isBetterThan(a);
		}
	};

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
	}

	/**
	 * Reads the properties file and sets the constant values for consolidation.
	 */
	@Override
	protected void processProps() {
		super.processProps();
		this.c1 = Double.parseDouble(props.getProperty("psoC1"));
		this.c2 = Double.parseDouble(props.getProperty("psoC2"));
	}

	/**
	 * The toString-method, used for debugging.
	 */
	public String toString() {
		return "Amount of VMs: " + population[0].items.length + ", GlobalBest: "
				+ population[findBestSolution(baseComp)];
	}

	@Override
	protected Particle modelFactory(final Particle input, final GenHelper vmAssignment,
			final InfrastructureModel.Improver localsearch) {
		return new Particle(input, vmAssignment, localsearch);
	}

	@Override
	protected void createPopArray(final int len) {
		population = new Particle[len];
	}

	private void updateSingleParticle(final int index, final int bestIndex) {
		// step 3 - update velocity
		population[index].updateVelocity(population[bestIndex], c1 * random.nextDoubleFast(),
				c2 * random.nextDoubleFast());
		// Logger.getGlobal().info("Particle: " + p.getNumber() + ", new Velocity: " +
		// newVel);
		// step 4 - update location
		if (population[index].updateLocation(localSearch))
			improved = true;
	}

	@Override
	protected void singleIteration() {
		// step 2 - update gBest
		final int bestParticleIndex = findBestSolution(bestComp);
		int i;
		for (i = 0; i < bestParticleIndex; i++) {
			updateSingleParticle(i, bestParticleIndex);
		}
		// Skips the best particle
		for (i++; i < population.length; i++) {
			updateSingleParticle(i, bestParticleIndex);
		}
		// The best particle is updated last as all others were directed towards this
		// one in the current iteration
		updateSingleParticle(bestParticleIndex, bestParticleIndex);
		// The selection of the best particle might not be as trivial as it is with ABC
		// and GA. This needs more tweaking.
	}

	@Override
	protected Particle transformInput(final InfrastructureModel input) {
		return new Particle(input, PreserveAllocations.singleton, NonImprover.singleton);
	}

}