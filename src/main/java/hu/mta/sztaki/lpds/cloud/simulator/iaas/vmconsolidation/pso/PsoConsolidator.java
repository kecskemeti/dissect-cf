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
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.pso;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.PopulationBasedConsolidator;
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

public class PsoConsolidator extends PopulationBasedConsolidator<Particle> {

	/** learning factor one */
	private double c1;

	/** learning factor two */
	private double c2;

	private static final FitCompare bestComp = (a, b) -> ((Particle) a).isBetterBest((Particle) b);

	private static final FitCompare worstCurrentComp = (a, b) -> b.isBetterThan(a);

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
		// A bit of non-basic PSO tweaking
		// the worst particle is eliminated and replaced with an average particle
		population[findBestSolution(worstCurrentComp)]=new Particle(population);
	}

	@Override
	protected Particle transformInput(final InfrastructureModel input) {
		return new Particle(input, PreserveAllocations.singleton, NonImprover.singleton);
	}
	
	@Override
	protected Particle getBestResult() {
		final int bestParticleIndex=findBestSolution(bestComp);
		population[bestParticleIndex].replaceMappingWithPersonalBest();
		return population[bestParticleIndex];
	}
}