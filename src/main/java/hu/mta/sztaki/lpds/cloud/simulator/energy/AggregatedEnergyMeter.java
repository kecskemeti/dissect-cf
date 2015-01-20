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
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */
package hu.mta.sztaki.lpds.cloud.simulator.energy;

import java.util.List;

public class AggregatedEnergyMeter extends EnergyMeter {

	// Allows aggregator users to update the supervised list any time they like
	public final List<EnergyMeter> supervised;

	public AggregatedEnergyMeter(List<EnergyMeter> toAggregate) {
		supervised = toAggregate;
	}

	/**
	 * Dispatches start meter calls to all supervised meters. If even one of
	 * them fails, it immediately terminates those meters that were started
	 * before the failure.
	 */
	@Override
	public boolean startMeter(long interval, boolean dropPriorReading) {
		final int supSize = supervised.size();
		int i = 0;
		for (; i < supSize; i++) {
			if (!supervised.get(i).startMeter(interval, dropPriorReading)) {
				break;
			}
		}
		if (i != supSize) {
			// Some of the meters did not start because they already did some
			// metering prior to this startmeter request
			for (int j = 0; j < i; j++) {
				supervised.get(j).stopMeter();
			}
			return false;
		}
		return true;
	}

	/**
	 * Dispatches stop meter calls to all supervised meters
	 */
	@Override
	public void stopMeter() {
		final int supSize = supervised.size();
		for (int i = 0; i < supSize; i++) {
			supervised.get(i).stopMeter();
		}
	}

	/**
	 * Calculates the sum of total consumptions returned by each and every
	 * supervised meter
	 */
	@Override
	public double getTotalConsumption() {
		double sum = 0;
		final int supSize = supervised.size();
		for (int i = 0; i < supSize; i++) {
			sum += supervised.get(i).getTotalConsumption();
		}
		return sum;
	}

	@Override
	public void tick(long fires) {
		// Do nothing
	}
}
