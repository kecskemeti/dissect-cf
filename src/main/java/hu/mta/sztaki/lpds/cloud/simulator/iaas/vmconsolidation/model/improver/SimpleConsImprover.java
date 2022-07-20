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

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.SimpleConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelPM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelVM;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */
public class SimpleConsImprover extends InfrastructureModel implements InfrastructureModel.Improver {
	public static final SimpleConsImprover singleton = new SimpleConsImprover();

	final private static Comparator<ModelPM> mpmFreeComp = (pm1, pm2) -> -pm1.free.compareTo(pm2.free);

	public SimpleConsImprover() {
		super(new PhysicalMachine[0], 0, false, 0);
	}

	/**
	 * The algorithm out of the simple consolidator, adjusted to work with the
	 * abstract model.
	 */
	@Override
	public void improve(final InfrastructureModel toImprove) {
//		Logger.getGlobal().info("starting to improve with second local search");

		// create an array out of the bins
		ModelPM[] pmList = new ModelPM[toImprove.bins.length];
		int runningLen = 0;
		for (final ModelPM curr : toImprove.bins) {
			if (curr.isHostingVMs() && curr.free.getTotalProcessingPower() > SimpleConsolidator.pmFullLimit) {
				pmList[runningLen++] = curr;
			}
		}

//		Logger.getGlobal().info("size of the pmList: " + pmList.length);

		boolean didMove;
		runningLen--;
		int beginIndex = 0;
		final HashSet<ModelVM> alreadyMoved = new HashSet<>();
		do {
			didMove = false;

			// sort the array from highest to lowest free capacity with an adjusted version
			// of the fitting pm comparator
			Arrays.sort(pmList, beginIndex, runningLen + 1, mpmFreeComp);

//			Logger.getGlobal().info("filtered array: " + Arrays.toString(pmList));

			for (int i = beginIndex; i < runningLen; i++) {
				final ModelPM source = pmList[i];
				final ModelVM[] vmList = source.getVMs().toArray(ModelVM.mvmArrSample);
				int vmc = 0;
				for (final ModelVM vm : vmList) {
					if (alreadyMoved.contains(vm))
						continue;
					// ModelVMs can only run, so we need not to check the state (there is none
					// either)
					for (int j = runningLen; j > i; j--) {
						final ModelPM target = pmList[j];

						if (target.isMigrationPossible(vm)) {
							source.migrateVM(vm, target);
							alreadyMoved.add(vm);

							if (target.free.getTotalProcessingPower() < SimpleConsolidator.pmFullLimit) {
								// Ensures that those PMs that barely have resources will not be
								// considered in future runs of this loop
								if (j != runningLen) {
									if (j == runningLen - 1) {
										pmList[j] = pmList[runningLen];
									} else {
										System.arraycopy(pmList, j + 1, pmList, j, runningLen - j);
									}
								}
								runningLen--;
							}
							vmc++;
							didMove = true;
							break;
						}
					}
				}
				if (vmc == vmList.length) {
					pmList[i] = pmList[beginIndex++];
				}
			}
		} while (didMove);
	}
}
