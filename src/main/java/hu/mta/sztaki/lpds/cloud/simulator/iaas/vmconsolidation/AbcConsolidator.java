package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.Random;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

/**
 * VM consolidator using artificial bee colony algorithm.
 * 
 * @author Zoltan Mann
 */
public class AbcConsolidator extends SolutionBasedConsolidator {

	/** For generating random numbers */
	private Random random;

	/** Number of individuals in the population */
	private int populationSize;

	/** Terminate the algorithm after this many generations */
	private int nrIterations;

	/** Maximum number of trials for improvement before a solution is abandoned */
	private int limitTrials;

	/** Population, consisting of solutions for each employed bee */
	private Vector<Solution> population;

	/** For each employed bee, number of trials since last improvement */
	private Vector<Integer> numTrials;

	/** Probabilities for the onlooker bees */
	private Vector<Double> probabilities;

	/** Best solution found so far */
	private Solution bestSolution;

	/** Fitness of the best solution found so far */
	private Fitness bestFitness;

	/**
	 * Creates AbcConsolidator with empty population.
	 */
	public AbcConsolidator(IaaSService toConsolidate, long consFreq) {
		super(toConsolidate, consFreq);
		random = new Random();
		population = new Vector<>();
		numTrials = new Vector<>();
		probabilities = new Vector<>();
	}

	/**
	 * If s is better than the best solution found so far, then bestSolution and
	 * bestFitness are updated.
	 */
	private void checkIfBest(Solution s) {
		Fitness f = s.evaluate();
		if (f.isBetterThan(bestFitness)) {
			bestSolution = s;
			bestFitness = f;
		}
	}

	/**
	 * Initializes the population with populationSize random solutions.
	 */
	private void initializePopulation() {
		population.clear();
		for (int i = 0; i < populationSize; i++) {
			Solution s = new Solution(bins, mutationProb);
			s.fillRandomly();
			population.add(s);
			numTrials.add(0);
			if (i == 0) {
				bestSolution = s;
				bestFitness = s.evaluate();
			} else {
				checkIfBest(s);
			}
		}
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
		for (int i = 0; i < populationSize; i++) {
			Solution s = population.get(i);
			Fitness f = s.evaluate();
			int numWins = 0;
			for (int j = 0; j < 10; j++) {
				Solution s2 = population.get(random.nextInt(populationSize));
				if (f.isBetterThan(s2.evaluate()))
					numWins++;
			}
			double prob = (2.0 + numWins) / 12;
			if (i < probabilities.size())
				probabilities.set(i, prob);
			else
				probabilities.add(prob);
		}
	}

	/**
	 * Mutate the jth member of the population and check whether the new solution is
	 * better than the old one. If it is better, it replaces the old one in the
	 * population, otherwise not.
	 */
	private void mutateAndCheck(int j) {
		Solution s1 = population.get(j);
		Solution s2 = s1.mutate();
		if (s2.evaluate().isBetterThan(s1.evaluate())) {
			population.set(j, s2);
			numTrials.set(j, 0);
			checkIfBest(s2);
		} else {
			numTrials.set(j, numTrials.get(j) + 1);
		}
	}

	/**
	 * Reads the properties file and sets the constant values for consolidation.
	 */
	@Override
	protected void processProps() {
		super.processProps();
		this.populationSize = Integer.parseInt(props.getProperty("abcPopulationSize"));
		this.nrIterations = Integer.parseInt(props.getProperty("abcNrIterations"));
		this.limitTrials = Integer.parseInt(props.getProperty("abcLimitTrials"));
	}

	/**
	 * The actual ABC algorithm.
	 */
	@Override
	protected void optimize() {
		initializePopulation();
		for (int iter = 0; iter < nrIterations; iter++) {
			// employed bees phase
			for (int j = 0; j < populationSize; j++) {
				mutateAndCheck(j);
			}
			// onlooker bees phase
			determineProbabilities();
			int j = 0;
			int t = 0;
			while (t < populationSize) {
				double r = random.nextDouble();
				// Logger.getGlobal().info("r="+r+", prob[j]="+probabilities.get(j));
				if (r < probabilities.get(j)) {
					t++;
					mutateAndCheck(j);
				}
				j++;
				if (j > populationSize - 1)
					j = 0;
			}
			// scout bee phase
			int maxTrials = -1;
			int maxTrialsIndex = 0;
			for (j = 0; j < populationSize; j++) {
				if (numTrials.get(j) > maxTrials) {
					maxTrialsIndex = j;
					maxTrials = numTrials.get(j);
				}
			}
			if (maxTrials >= limitTrials) {
				Solution s = new Solution(bins, mutationProb);
				s.fillRandomly();
				population.set(maxTrialsIndex, s);
				numTrials.set(maxTrialsIndex, 0);
				checkIfBest(s);
			}
		}
		// Implement best solution in the model
		bestSolution.implement();
		adaptPmStates();
	}

	public Fitness getBestFitness() {
		return bestFitness;
	}

	public Solution getBestSolution() {
		return bestSolution;
	}
}
