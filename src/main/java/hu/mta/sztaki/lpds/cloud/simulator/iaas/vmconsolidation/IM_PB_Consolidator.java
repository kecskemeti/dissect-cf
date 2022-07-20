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
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.GenHelper;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.RandomVMassigner;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */

public abstract class IM_PB_Consolidator extends PopulationBasedConsolidator<InfrastructureModel> {
	protected GenHelper mutator;

	public IM_PB_Consolidator(final IaaSService toConsolidate, final long consFreq) {
		super(toConsolidate, consFreq);
	}

	@Override
	protected InfrastructureModel modelFactory(final InfrastructureModel input, final GenHelper vmAssignment,
			final InfrastructureModel.Improver localsearch) {
		return new InfrastructureModel(input, vmAssignment, localsearch);
	}

	@Override
	protected InfrastructureModel transformInput(final InfrastructureModel input) {
		return input;
	}

	@Override
	protected void createPopArray(final int len) {
		population = new InfrastructureModel[len];
	}

	@Override
	protected void processProps() {
		super.processProps();
		prepareMutator(Double.parseDouble(props.getProperty("mutationProb")));
	}

	/**
	 * Create a new solution by mutating the current one. Each gene (i.e., the
	 * mapping of each VM) is replaced by a random one with probability mutationProb
	 * and simply copied otherwise. Note that the current solution (this) is not
	 * changed.
	 */
	private void prepareMutator(final double mutationProb) {
		mutator = new RandomVMassigner() {
			@Override
			public boolean shouldUseDifferent() {
				return PopulationBasedConsolidator.random.nextDoubleFast() < mutationProb;
			}
		};
	}

}
