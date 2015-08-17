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

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.PowerBehaviorChangeListener;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceSpreader;

/**
 * Direct energy meters can monitor a single resource spreader and can convert
 * their processed consumption values to a continuously updated energy figure.
 * 
 * @author 
 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 * 
 */
public class DirectEnergyMeter extends EnergyMeter implements
		PowerBehaviorChangeListener,
		PowerState.PowerCharacteristicsChange {
	private ResourceSpreader measuredResource;
	private PowerState usedPowerState;
	private double previousProcessingReport;
	private double maxProcessable;

	public DirectEnergyMeter(final ResourceSpreader spreader) {
		measuredResource = spreader;
	}

	@Override
	public String toString() {
		return "EnergyMeter(" + super.toString() + " " + getTotalConsumption()
				+ " W*(ticks) consumed by " + measuredResource + ")";
	}

	@Override
	public boolean startMeter(long interval, boolean dropPriorReading) {
		boolean startResult = super.startMeter(interval, dropPriorReading);
		if (startResult) {
			previousProcessingReport = collectProcessingReport();
			updateFieldsUsingNewInterval(interval);
			usedPowerState = measuredResource.getCurrentPowerBehavior();
			usedPowerState.subscribePowerCharacteristicsChanges(this);
			measuredResource.subscribePowerBehaviorChangeEvents(this);
		}
		return startResult;
	}

	@Override
	public void stopMeter() {
		if (isSubscribed()) {
			super.stopMeter();
			usedPowerState.unsubscribePowerCharacteristicsChanges(this);
			measuredResource.unsubscribePowerBehaviorChangeEvents(this);
		}
	}

	@Override
	public void behaviorChanged(final ResourceSpreader onSpreader,
			final PowerState newState) {
		if (isSubscribed()) {
			usedPowerState.unsubscribePowerCharacteristicsChanges(this);
			usedPowerState = newState;
			usedPowerState.subscribePowerCharacteristicsChanges(this);
			readjustMeter();
		}
	}

	@Override
	public void prePowerChangeEvent(PowerState onMe) {
		readjustMeter();
	}

	private void updateFieldsUsingNewInterval(final long interval) {
		maxProcessable = interval
				* measuredResource.getPerTickProcessingPower();
	}

	private double collectProcessingReport() {
		return measuredResource.getTotalProcessed();
	}

	/**
	 * Maintains the totalconsumption value in every desired time interval
	 */
	@Override
	public void tick(final long fires) {
		if (!isSubscribed()) {
			updateFieldsUsingNewInterval(getFrequency() - getNextEvent()
					+ fires);
		}
		final double currentProcessingReport = collectProcessingReport();
		increaseTotalConsumption(usedPowerState
				.getCurrentPower(((currentProcessingReport - previousProcessingReport) / maxProcessable))
				* (fires - lastMetered));
		previousProcessingReport = currentProcessingReport;
		lastMetered = fires;
	}

}
