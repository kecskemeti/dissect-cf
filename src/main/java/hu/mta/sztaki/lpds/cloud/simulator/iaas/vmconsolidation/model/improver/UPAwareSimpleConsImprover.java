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
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.improver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.SimpleConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelPM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelVM;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */
public class UPAwareSimpleConsImprover extends SimpleConsImprover {
	public static final UPAwareSimpleConsImprover singleton = new UPAwareSimpleConsImprover();

	final private static Comparator<ModelPM> mpmFreeComp = (pm1, pm2) -> -pm1.free.compareTo(pm2.free);

	public UPAwareSimpleConsImprover() {
		super();
	}

	/**
	 * The algorithm out of the simple consolidator, adjusted to work with the
	 * abstract model.
	 */
	@Override
	public void improve(final InfrastructureModel toImprove) {

		// Cleaning up over-used PMs
		final ArrayList<ModelPM> underProvisioned = new ArrayList<>(toImprove.bins.length);
		final ArrayList<ModelPM> otherPMs = new ArrayList<>(toImprove.bins.length);
		for (final ModelPM curr : toImprove.bins) {
			if (curr.isHostingVMs() && curr.free.getTotalProcessingPower() < 0) {
				underProvisioned.add(curr);
			} else if (curr.free.getTotalProcessingPower() > SimpleConsolidator.pmFullLimit) {
				otherPMs.add(curr);
			}
		}


		boolean didMove;
		final HashSet<ModelVM> alreadyMoved = new HashSet<>();
		do {
			didMove = false;

			// Tries to fill in the heaviest loaded PMs first.
			otherPMs.sort(mpmFreeComp);

			undLoop: for (int i = underProvisioned.size()-1; i >= 0; i--) {
				final ModelPM source = underProvisioned.get(i);
				final ModelVM[] vmList = source.getVMs().toArray(ModelVM.mvmArrSample);
				for (final ModelVM vm : vmList) {
					if (alreadyMoved.contains(vm))
						continue;
					// ModelVMs can only run, so we need not to check the state (there is none
					// either)
					for (int j = otherPMs.size() - 1; j >= 0; j--) {
						final ModelPM target = otherPMs.get(j);

						if (target.isMigrationPossible(vm)) {
							source.migrateVM(vm, target);
							alreadyMoved.add(vm);
							didMove = true;
							if (target.free.getTotalProcessingPower() < SimpleConsolidator.pmFullLimit) {
								otherPMs.remove(j);
							}
							if (source.free.getTotalProcessingPower() > 0) {
								// The source PM is now capable to serve its hosted VMs.
								underProvisioned.remove(i);
								continue undLoop;
							}
							break;
						}
					}
				}
			}
		} while (didMove);
		
		// There are no underprovisioned VMs now. We can head to the simple consolidator's core algorithm.
		super.improve(toImprove);
	}
}
