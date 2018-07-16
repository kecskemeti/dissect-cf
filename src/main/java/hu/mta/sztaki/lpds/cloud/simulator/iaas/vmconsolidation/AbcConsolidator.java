package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.Arrays;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

/**
 * VM consolidator using artificial bee colony algorithm.
 * 
 * @author Zoltan Mann
 */
public class AbcConsolidator extends IM_ML_Consolidator {
	public static final int probTestCount = 15;
	public static final double probBase = 2;

	/** Maximum number of trials for improvement before a solution is abandoned */
	private int limitTrials;

	/** For each employed bee, number of trials since last improvement */
	private int[] numTrials;

	/** Probabilities for the onlooker bees */
	private double[] probabilities;

	/** Best solution found so far */
	private InfrastructureModel bestSolution;

	private int[] probTestIndexes = new int[probTestCount];
	private int[] wincounts;
	private int[] testcounts;

	private InfChecker currChecker;

	interface InfChecker {
		void checkBest(InfrastructureModel s);
	}

	private final InfChecker regChecker = new InfChecker() {
		public void checkBest(final InfrastructureModel s) {
			if (s.isBetterThan(bestSolution)) {
				bestSolution = s;
			}
		}
	};

	private final InfChecker nullChecker = new InfChecker() {
		@Override
		public void checkBest(final InfrastructureModel s) {
			bestSolution = s;
			currChecker = regChecker;
		}
	};

	/**
	 * Creates AbcConsolidator with empty population.
	 */
	public AbcConsolidator(final IaaSService toConsolidate, final long consFreq) {
		super(toConsolidate, consFreq);
	}

	/**
	 * If s is better than the best solution found so far, then bestSolution and
	 * bestFitness are updated.
	 */
	private void checkIfBest(final InfrastructureModel s) {
		currChecker.checkBest(s);
	}

	/**
	 * Initializes the population with the previously determined solutions. After
	 * that the same mapping as existing before consolidation has started is put
	 * inside a solution.
	 */
	protected void initializePopulation(final InfrastructureModel input) {
		currChecker = nullChecker;
		super.initializePopulation(input);
	}

	private void postRegTasks(int idx) {
		numTrials[idx] = 0;
		checkIfBest(population[idx]);
	}

	protected InfrastructureModel regSolution(final InfrastructureModel toReg) {
		super.regSolution(toReg);
		postRegTasks(getPopFillIndex() - 1);
		return toReg;
	}

	/**
	 * Determine the probabilities for the onlooker bees. For each member of the
	 * population, the better its fitness, the higher the probability should be.
	 * Since our fitness values are not numeric, we employ the following method. We
	 * compare each member of the population with 10 randomly chosen other members
	 * and count the number of times this member was better. This count divided by
	 * 10 will be used as probability.
	 */
	private void determineProbabilities() {
		Arrays.fill(wincounts, 0);
		Arrays.fill(testcounts, 0);
		for (int i = 0; i < population.length; i++) {
			final InfrastructureModel s = population[i];
			final int maxj = Math.min(population.length - 1, probTestCount - testcounts[i] - wincounts[i]);
			for (int j = 0; j < maxj; j++) {
				int popidx;
				int k;
				do {
					// Don't test against the same item..
					while ((popidx = random.nextInt(population.length)) == i)
						;
					// Don't test against something we arleady tested with before..
					for (k = 0; k < j && probTestIndexes[k] != popidx; k++)
						;
				} while (k != j);
				probTestIndexes[j] = popidx;
				final InfrastructureModel s2 = population[popidx];
				if (s.isBetterThan(s2)) {
					wincounts[i]++;
					testcounts[popidx]++;
				} else {
					wincounts[popidx]++;
				}
			}
			probabilities[i] = (probBase + wincounts[i]) / (probBase + probTestCount);
		}
	}

	/**
	 * Mutate the jth member of the population and check whether the new solution is
	 * better than the old one. If it is better, it replaces the old one in the
	 * population, otherwise not.
	 */
	private void mutateAndCheck(final int j) {
		final InfrastructureModel s = population[j].mutate(mutationProb);
		if (s.isBetterThan(population[j])) {
			population[j] = s;
			postRegTasks(j);
		} else {
			numTrials[j]++;
		}
	}

	/**
	 * Reads the properties file and sets the constant values for consolidation.
	 */
	@Override
	protected void processProps() {
		super.processProps();
		this.limitTrials = Integer.parseInt(props.getProperty("abcLimitTrials"));
		numTrials = new int[population.length];
		wincounts = new int[population.length];
		testcounts = new int[population.length];
		probabilities = new double[population.length];
	}

	/**
	 * The actual ABC algorithm.
	 */
	@Override
	protected InfrastructureModel optimize(final InfrastructureModel input) {
		// System.err.println("ABC nrIterations="+nrIterations+",
		// populationSize="+populationSize);
		initializePopulation(input);
		for (int iter = 0; iter < nrIterations; iter++) {
			// employed bees phase
			for (int j = 0; j < population.length; j++) {
//				Logger.getGlobal().info("populationSize: " + populationSize + ", j: " + j);
				mutateAndCheck(j);
			}
			// onlooker bees phase
			determineProbabilities();
			final double rnd = random.nextDouble();
			for (int j = 0; j < population.length; j++) {
				if (rnd <= probabilities[j]) {
					mutateAndCheck(j);
				}
			}
			// scout bee phase
			for (int j = 0; j < population.length; j++) {
				if (numTrials[j] >= limitTrials) {
					population[j] = new InfrastructureModel(input, false, true);
					numTrials[j] = 0;
					checkIfBest(population[j]);
					break;
				}
			}
			// System.err.println("ABC iteration carried out: "+iter);
		}
		// Implement best solution in the model
		return bestSolution;
	}
}
