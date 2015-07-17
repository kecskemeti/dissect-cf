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

package hu.mta.sztaki.lpds.cloud.simulator.energy.specialized;

import hu.mta.sztaki.lpds.cloud.simulator.energy.AggregatedEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.EnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IaaSEnergyMeter extends AggregatedEnergyMeter implements
		VMManager.CapacityChangeEvent<PhysicalMachine> {
	private final IaaSService observed;
	private ResourceConstraints oldCapacity;

	public IaaSEnergyMeter(IaaSService iaas) {
		super(subMeterCreator(iaas.machines));
		observed = iaas;
		observed.subscribeToCapacityChanges(this);
		oldCapacity = observed.getCapacities();
	}

	private static List<EnergyMeter> subMeterCreator(
			List<PhysicalMachine> machines) {
		final int machineCount = machines.size();
		ArrayList<EnergyMeter> meters = new ArrayList<EnergyMeter>(machineCount);
		for (int i = 0; i < machineCount; i++) {
			meters.add(new PhysicalMachineEnergyMeter(machines.get(i)));
		}
		return meters;
	}

	@Override
	public void capacityChanged(ResourceConstraints newCapacity,
			List<PhysicalMachine> affectedCapacity) {
		long freq = -1;
		if (isSubscribed()) {
			freq = getFrequency();
			stopMeter();
		}
		if (newCapacity.compareTo(oldCapacity) < 0) {
			// Decreased
			Iterator<? extends EnergyMeter> meters = supervised.iterator();
			while (meters.hasNext()) {
				PhysicalMachineEnergyMeter m = (PhysicalMachineEnergyMeter) meters
						.next();
				if (affectedCapacity.remove(m.getObserved())) {
					meters.remove();
				}
			}
		} else {
			// Increased
			supervised.addAll(subMeterCreator(affectedCapacity));
		}
		oldCapacity = newCapacity;
		if (freq != -1) {
			startMeter(freq, false);
		}
	}

	public IaaSService getObserved() {
		return observed;
	}
}
