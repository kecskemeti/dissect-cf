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
package hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class PowerState {
	public static abstract class ConsumptionModel {
		protected final PowerState myPowerState = null;

		protected abstract double evaluateConsumption(final double load);
	}

	public interface PowerCharacteristicsChange {
		void prePowerChangeEvent(final PowerState onMe);
	}

	private double minConsumption;
	private double consumptionRange;
	private long pastNotification = -1;
	private final ConsumptionModel model;
	private final ArrayList<PowerCharacteristicsChange> listeners = new ArrayList<PowerState.PowerCharacteristicsChange>();

	public PowerState(final double minConsumption,
			final double consumptionRange,
			Class<? extends ConsumptionModel> modelclass)
			throws InstantiationException, IllegalAccessException,
			NoSuchFieldException, SecurityException {
		this.minConsumption = minConsumption;
		this.consumptionRange = consumptionRange;
		model = modelclass.newInstance();
		Field f = ConsumptionModel.class.getDeclaredField("myPowerState");
		f.setAccessible(true);
		f.set(model, this);
		f.setAccessible(false);
	}

	public double getCurrentPower(final double load) {
		if (load > 1.01 || load < -0.01) {
			throw new IllegalStateException(
					"Received an out of range load evaluation request:" + load);
		}
		return model.evaluateConsumption(load);
	}

	public double getMinConsumption() {
		return minConsumption;
	}

	public double getConsumptionRange() {
		return consumptionRange;
	}

	private void notifyCharacteristisListeners() {
		final long currentTime = Timed.getFireCount();
		if (currentTime != pastNotification) {
			for (PowerCharacteristicsChange l : listeners) {
				l.prePowerChangeEvent(this);
			}
			pastNotification = currentTime;
		}
	}

	public void setMinConsumption(final double minConsumption) {
		notifyCharacteristisListeners();
		this.minConsumption = minConsumption;
	}

	public void setConsumptionRange(final double cr) {
		notifyCharacteristisListeners();
		this.consumptionRange = cr;
	}

	public void subscribePowerCharacteristicsChanges(
			PowerCharacteristicsChange listener) {
		listeners.add(listener);
	}

	public void unsubscribePowerCharacteristicsChanges(
			PowerCharacteristicsChange listener) {
		listeners.remove(listener);
	}

	@Override
	public String toString() {
		return "PowSt(I: " + minConsumption + " C: " + consumptionRange + " "
				+ model.getClass().getName() + ")";
	}
}
