package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

/**
 * VM consolidator using a genetic algorithm.
 * 
 * This is a fairly basic GA. Ideas for further improvement: - Use a local
 * search procedure to improve individuals - Improve performance by caching
 * fitness values (or their components) - Make fitness more sophisticated, e.g.
 * by incorporating the skewness of PMs' load - Make fitness more sophisticated,
 * e.g. by allowing a large decrease in the number of migrations to compensate
 * for a small increase in the number of active PMs. - Every time and again,
 * introduce new random individuals into the population to increase diversity -
 * Use more sophisticated termination criteria
 * 
 * @author Zoltan Mann
 */
public class GaConsolidator extends IM_ML_Consolidator {

	/** Number of recombinations to perform in each generation */
	private int nrCrossovers;
	/** Best solution found so far */
	private InfrastructureModel bestSolution;
	/**
	 * True if at least one individual has improved during the current generation
	 */
	private boolean improved;

	/**
	 * Creates GaConsolidator with empty population.
	 */
	public GaConsolidator(final IaaSService toConsolidate, final long consFreq) {
		super(toConsolidate, consFreq);
		setOmitAllocationCheck(true);
	}

	/**
	 * Take two random individuals of the population and let them recombinate to
	 * create a new individual. If the new individual is better than one of its
	 * parents, then it replaces that parent in the population, otherwise it is
	 * discarded.
	 */
	private void crossover() {
		final int i1 = random.nextInt(population.length);
		final int i2 = random.nextInt(population.length);
		final InfrastructureModel s3 = population[i1].recombinate(population[i2]);
		if (s3.isBetterThan(population[i1])) {
			population[i1] = s3;
			improved = true;
		} else if (s3.isBetterThan(population[i2])) {
			population[i2] = s3;
			improved = true;
		}
	}

	/**
	 * At the end of the GA, update the model of the consolidator to reflect the
	 * mapping corresponding to the best found solution.
	 */
	private void implementBestSolution() {
		// Determine "best" solution (i.e. a solution, compared to which there is no
		// better one)
		bestSolution = population[0];
		for (int i = 1; i < population.length; i++) {
			if (population[i].isBetterThan(bestSolution)) {
				bestSolution = population[i];
			}
		}
		// Implement solution in the model
	}

	/**
	 * Reads the properties file and sets the constant values for consolidation.
	 */
	@Override
	protected void processProps() {
		super.processProps();
		this.nrCrossovers = Integer.parseInt(props.getProperty("gaNrCrossovers"));
	}

	/**
	 * Perform the genetic algorithm to optimize the mapping of VMs to PMs.
	 */
	@Override
	protected InfrastructureModel optimize(final InfrastructureModel input) {
		// System.err.println("GA nrIterations="+nrIterations+",
		// populationSize="+populationSize+", nrCrossovers="+nrCrossovers);
		initializePopulation(input);
		// Logger.getGlobal().info("Population after initialization:
		// "+populationToString());
		for (int iter = 0; iter < nrIterations; iter++) {
			improved = false;
			// From each individual in the population, create an offspring using
			// mutation. If the child is better than its parent, it replaces it
			// in the population, otherwise it is discarded.
			for (int i = 0; i < population.length; i++) {
				final InfrastructureModel parent = population[i];
				final InfrastructureModel child = parent.mutate(mutationProb);
				if (child.isBetterThan(parent)) {
					population[i] = child;
					improved = true;
				}
			}
			// Perform the given number of crossovers.
			for (int i = 0; i < nrCrossovers; i++) {
				crossover();
			}
//			 Logger.getGlobal().info("Population after iteration "+iter+":"+populationToString());
			// System.err.println("GA iteration carried out: "+iter);
			if (!improved)
				break;
		}
		implementBestSolution();
		return bestSolution;
	}

	/**
	 * String representation of the whole population (for debugging purposes).
	 */
	public String populationToString() {
		String result = "";
		boolean first = true;
		for (int i = 0; i < population.length; i++) {
			if (!first)
				result = result + " ";
			result = result + population[i].toString();
			first = false;
		}
		return result;
	}

	public InfrastructureModel getBestSolution() {
		return bestSolution;
	}
}
