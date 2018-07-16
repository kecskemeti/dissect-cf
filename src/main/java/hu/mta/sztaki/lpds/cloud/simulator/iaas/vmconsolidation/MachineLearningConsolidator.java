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
 *  (C) Copyright 2017, Gabor Kecskemeti (g.kecskemeti@ljmu.ac.uk)
 */
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.Random;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

/**
 * This class is to be sub-classed by all workload consolidators that use the
 * Solution class. It allows the loading of the mutation probability
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2017"
 */
public abstract class MachineLearningConsolidator<T extends InfrastructureModel> extends ModelBasedConsolidator {
	protected double mutationProb;

	protected int randomCreations;
	protected int unchangedCreations;
	protected int firstFitCreations;
	/** Terminate the GA after this many generations */
	protected int nrIterations;
	/** Population for the GA, consisting of solutions=individuals */
	protected T[] population;
	private int popFillIndex;
	/** For generating random numbers */
	static protected Random random;
	/**
	 * Controls whether new solutions (created by mutation or recombination) should
	 * be improved with a local search
	 */
	static protected boolean doLocalSearch1 = false;
	/** simple consolidator local search */
	static protected boolean doLocalSearch2 = false;

	public MachineLearningConsolidator(final IaaSService toConsolidate, final long consFreq) {
		super(toConsolidate, consFreq);
	}

	protected abstract T modelFactory(T input, boolean original, boolean localsearch);
	protected abstract void createPopArray(int len);

	/**
	 * Initializes the population with the previously determined solutions. After
	 * that the same mapping as existing before consolidation has started is put
	 * inside a solution.
	 */
	protected void initializePopulation(final T input) {
		popFillIndex = 0;
		for (int i = 0; i < randomCreations; i++) {
			regSolution(modelFactory(input, false, true));
		}
		if (firstFitCreations != 0) {
			produceClonesOf(regSolution(modelFactory(input, true, true)), firstFitCreations - 1);
		}
		if (unchangedCreations != 0) {
			produceClonesOf(regSolution(modelFactory(input, true, false)), unchangedCreations - 1);
		}
	}

	protected T regSolution(final T toReg) {
		return population[popFillIndex++] = toReg;
	}

	protected void produceClonesOf(final T s0, int clonecount) {
		while (clonecount > 0) {
			regSolution(modelFactory(s0, true, false));
			clonecount--;
		}
	}
	
	protected int getPopFillIndex() {
		return popFillIndex;
	}

	@Override
	protected void processProps() {
		this.mutationProb = Double.parseDouble(props.getProperty("mutationProb"));
		random = new Random(Long.parseLong(props.getProperty("seed")));
		doLocalSearch1 = Boolean.parseBoolean(props.getProperty("doLocalSearch1"));
		doLocalSearch2 = Boolean.parseBoolean(props.getProperty("doLocalSearch2"));
		createPopArray(Integer.parseInt(props.getProperty("populationSize")));
		this.nrIterations = Integer.parseInt(props.getProperty("nrIterations"));
		determineCreations(population.length);
	}

	/**
	 * We have to determine how to fill the population/swarm. At the moment there is
	 * going to be one unchanged solution, size * 0.25 first fit solutions and the
	 * rest of the creations is made randomly.
	 * 
	 * Note that there will be only random creations if the populationSize/swarmSize
	 * is less than three.
	 * 
	 * @param numberOfCreations The swarmSize/populationSize.
	 */
	protected void determineCreations(final int numberOfCreations) {
		// if the populationSize is less than 3, we only use random creations
		if (numberOfCreations < 3) {
			randomCreations = numberOfCreations;
			unchangedCreations = 0;
			firstFitCreations = 0;
		} else if (numberOfCreations == 3) {
			randomCreations = 1;
			unchangedCreations = 1;
			firstFitCreations = 1;
		} else {
			unchangedCreations = 1;
			firstFitCreations = numberOfCreations/4;
			randomCreations = numberOfCreations - unchangedCreations - firstFitCreations;
		}
	}

}
