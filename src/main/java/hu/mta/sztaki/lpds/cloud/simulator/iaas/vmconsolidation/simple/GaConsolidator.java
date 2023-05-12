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

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.CachingPRNG;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.IM_PB_Consolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.GenHelper;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;

import java.util.stream.IntStream;

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
public class GaConsolidator extends IM_PB_Consolidator {

	/** Number of recombinations to perform in each generation */
	private int nrCrossovers;

	/**
	 * Creates GaConsolidator with empty population.
	 */
	public GaConsolidator(final IaaSService toConsolidate, final long consFreq) {
		super(toConsolidate, consFreq);
		setOmitAllocationCheck(true);
	}

	/**
	 * Take two random individuals of the population and let them recombinate to
	 * create a new individual. Each gene (i.e., the mapping of each VM) is taken
	 * randomly either from this or the other parent. Note that the two parents are
	 * not changed. If the new individual is better than one of its parents, then it
	 * replaces that parent in the population, otherwise it is discarded.
	 */
	private void crossover() {
		final long temp = Math.abs(random.nextLong());
		final int i1 = (int) (temp % population.length);
		final int i2 = (int) ((temp >> 32) % population.length);
		final InfrastructureModel s3 = new InfrastructureModel(population[i1], new GenHelper() {
			@Override
			public boolean shouldUseDifferent() {
				return CachingPRNG.genBoolean();
			}

			@Override
			public int whatShouldWeUse(final InfrastructureModel im, final int vm) {
				return population[i2].items[vm].getHostID();
			}
		}, localSearch);
		if (!checkAndReplace(s3, i1)) {
			checkAndReplace(s3, i2);
		}
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
	protected void singleIteration() {
		// From each individual in the population, create an offspring using
		// mutation. If the child is better than its parent, it replaces it
		// in the population, otherwise it is discarded.
		popIdxStream().forEach(i -> checkAndReplace(new InfrastructureModel(population[i], mutator, localSearch), i));
		// Perform the given number of crossovers.
		IntStream.range(0, nrCrossovers).forEach(i -> crossover());
	}

}
