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
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;

public class ImmutablePMComponents {
	public final PhysicalMachine pm; // the real PM inside the simulator
	public final int number;
	public final ConstantConstraints lowerThrResources, upperThrResources;

	public ImmutablePMComponents(final PhysicalMachine pm, final double lowerThreshold, final int number,
			final double upperThreshold) {
		this.pm = pm;
		this.number = number;
		final AlterableResourceConstraints rc = new AlterableResourceConstraints(pm.getCapacities());
		rc.multiply(lowerThreshold);
		this.lowerThrResources = new ConstantConstraints(rc);
		if (upperThreshold == 1) {
			this.upperThrResources = new ConstantConstraints(pm.getCapacities());
		} else {
			rc.multiply(upperThreshold / lowerThreshold);
			this.upperThrResources = new ConstantConstraints(rc);
		}
	}
}
