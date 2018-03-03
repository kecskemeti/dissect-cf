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

import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

/**
 * This class is to be sub-classed by all workload consolidators that use the
 * Solution class. It allows the loading of the mutation probability
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2017"
 */
public abstract class SolutionBasedConsolidator extends ModelBasedConsolidator {
	protected double mutationProb;
	
	protected int randomCreations;
	protected int unchangedCreations;
	protected int firstFitCreations;

	public SolutionBasedConsolidator(IaaSService toConsolidate, long consFreq) {
		super(toConsolidate, consFreq);
	}

	@Override
	protected void processProps() {
		this.mutationProb = Double.parseDouble(props.getProperty("mutationProb"));		
	}
	
	/**
	 * We have to determine how to fill the population/swarm. At the moment there is 
	 * going to be one unchanged solution, size * 0.25 first fit solutions and the 
	 * rest of the creations is made randomly.
	 * 
	 * Note that there will be only random creations if the populationSize/swarmSize
	 * is less than three.
	 * 
	 * @param numberOfCreations
	 * 			The swarmSize/populationSize.
	 */
	protected void determineCreations(int numberOfCreations) {
		// if the populationSize is less than 3, we only use random creations
		if(numberOfCreations < 3) {
			randomCreations = numberOfCreations;
			unchangedCreations = 0;
			firstFitCreations = 0;
		}			
		else if(numberOfCreations == 3) {
			randomCreations = 1;
			unchangedCreations = 1;
			firstFitCreations = 1;
		}
		else {
			unchangedCreations = 1;
			Double randoms = numberOfCreations * 0.25;
			firstFitCreations = randoms.intValue();
			randomCreations = numberOfCreations - unchangedCreations - firstFitCreations;
		}
		Logger.getGlobal().info("Creations: " + numberOfCreations + ", random: " + randomCreations + ", first fit: "
				+ firstFitCreations + ", unchanged: " + unchangedCreations);
	}
	
}
