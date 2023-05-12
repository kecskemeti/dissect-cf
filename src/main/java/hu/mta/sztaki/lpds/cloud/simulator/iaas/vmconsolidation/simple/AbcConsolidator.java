/*
 *  ========================================================================
 *  DIScrete event baSed Energy Consumption simulaTor
 *    					             for Clouds and Federations (DISSECT-CF)
 *  ========================================================================
 *
 *  This file is part of DISSECT-CF.
 *
 *  DISSECT-CF is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or (at
 *  your option) any later version.
 *
 *  DISSECT-CF is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with DISSECT-CF.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  (C) Copyright 2019-20, Gabor Kecskemeti, Rene Ponto, Zoltan Mann
 */
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.simple;

import java.util.Arrays;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.IM_PB_Consolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.RandomVMassigner;

/**
 * VM consolidator using artificial bee colony algorithm.
 * 
 * @author Zoltan Mann
 */
public class AbcConsolidator extends IM_PB_Consolidator {
	public static final int probTestCount = 15;
	public static final double probBase = 2;

	/** Maximum number of trials for improvement before a solution is abandoned */
	private int limitTrials;

	/** For each employed bee, number of trials since last improvement */
	private int[] numTrials;

	/** Probabilities for the onlooker bees */
	private double[] probabilities;

	private final int[] probTestIndexes = new int[probTestCount + 1];
	private int[] wincounts;
	private int[] testcounts;

	/**
	 * Creates AbcConsolidator with empty population.
	 */
	public AbcConsolidator(final IaaSService toConsolidate, final long consFreq) {
		super(toConsolidate, consFreq);
	}

	private void postRegTasks(final int idx) {
		numTrials[idx] = 0;
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
		popIdxStream().forEach(this::determineSingleProb);
	}

	private void determineSingleProb(int i) {
		final InfrastructureModel s = population[i];
		final int maxj = Math.min(population.length - 1, probTestCount - testcounts[i] - wincounts[i]) + 1;
		// Don't test against the same item.
		probTestIndexes[0] = i;
		for (int j = 1; j < maxj; j++) {
			int popidx;
			int k;
			do {
				popidx = random.nextInt(population.length);
				// Don't test against something we already tested with before.
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

	/**
	 * Mutate the jth member of the population and check whether the new solution is
	 * better than the old one. If it is better, it replaces the old one in the
	 * population, otherwise not.
	 */
	private void mutateAndCheck(final int j) {
		if (checkAndReplace(new InfrastructureModel(population[j], mutator, localSearch), j)) {
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
	protected void singleIteration() {
		// employed bees phase
		popIdxStream().forEach(this::mutateAndCheck);
		// onlooker bees phase
		determineProbabilities();
		final double rnd = random.nextDoubleFast();
		popIdxStream().filter(j -> rnd < probabilities[j]).forEach(this::mutateAndCheck);
		// scout bee phase
		popIdxStream().filter(j -> numTrials[j] >= limitTrials).findFirst().ifPresent(j -> population[j] = new InfrastructureModel(input, RandomVMassigner.globalRandomAssigner, localSearch));
	}
}
