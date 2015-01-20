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

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;

import java.util.Arrays;
import java.util.List;

public class PhysicalMachineEnergyMeter extends AggregatedEnergyMeter implements
		VMManager.CapacityChangeEvent<ResourceConstraints> {

	private final PhysicalMachine observed;

	public PhysicalMachineEnergyMeter(final PhysicalMachine pm) {
		super(Arrays.asList(new EnergyMeter[] { new DirectEnergyMeter(pm),
				new DirectEnergyMeter(pm.localDisk.diskinbws),
				new DirectEnergyMeter(pm.localDisk.diskoutbws),
				new DirectEnergyMeter(pm.localDisk.inbws),
				new DirectEnergyMeter(pm.localDisk.outbws) }));
		observed = pm;
	}

	@Override
	public boolean startMeter(long interval, boolean dropPriorReading) {
		boolean returner = super.startMeter(interval, dropPriorReading);
		observed.subscribeToCapacityChanges(this);
		return returner;
	}

	@Override
	public void stopMeter() {
		super.stopMeter();
		observed.unsubscribeFromCapacityChanges(this);
	}

	@Override
	public void capacityChanged(ResourceConstraints newCapacity,
			List<ResourceConstraints> affectedCapacity) {
		readjustMeter();
	}

	public PhysicalMachine getObserved() {
		return observed;
	}
}
