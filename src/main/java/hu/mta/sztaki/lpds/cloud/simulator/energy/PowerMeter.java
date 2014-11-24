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

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceSpreader;

public class PowerMeter extends Timed {

	private ResourceSpreader measuredResource;
	private final double idleCons, consDiff;
	private double idleCInInt, consDInInt;
	private double totalConsumption = 0;
	private double previousProcessingReport;
	private double maxProcessable;
	private long meteringStarted;
	private long lastMetered;
	private long meteringStopped = 0;

	public PowerMeter(final ResourceSpreader spreader, final double idleCons,
			final double maxCons) {
		measuredResource = spreader;
		this.idleCons = idleCons;
		this.consDiff = maxCons - idleCons;
	}

	public boolean startMeter(final int interval, boolean dropPriorReading) {
		if (meteringStopped == -1) {
			return false;
		}
		subscribe(interval);
		lastMetered = Timed.getFireCount();
		if (dropPriorReading) {
			totalConsumption = 0;
		}
		if (totalConsumption == 0) {
			meteringStarted = lastMetered;
		} else {
			meteringStarted += lastMetered - meteringStopped;
		}
		meteringStopped = -1;
		previousProcessingReport = measuredResource.getTotalProcessed();
		double intervalMultiplier = interval / 1000d;
		maxProcessable = intervalMultiplier
				* measuredResource.getPerSecondProcessingPower();
		idleCInInt = intervalMultiplier * idleCons;
		consDInInt = intervalMultiplier * consDiff;
		return true;
	}

	public void stopMeter() {
		unsubscribe();
		meteringStopped = lastMetered;
	}

	public double getTotalConsumption() {
		return totalConsumption;
	}

	public long getMeteringStarted() {
		return meteringStarted;
	}

	public long getMeteringStopped() {
		return meteringStopped;
	}

	@Override
	public void tick(long fires) {
		final double currentProcessingReport = measuredResource
				.getTotalProcessed();
		totalConsumption += idleCInInt
				+ (consDInInt
						* (currentProcessingReport - previousProcessingReport) / maxProcessable);
		previousProcessingReport = currentProcessingReport;
		lastMetered = fires;
	}

	@Override
	public String toString() {
		return "PowerMeter(" + totalConsumption + " Joules consumed by "
				+ measuredResource + ")";
	}

}
