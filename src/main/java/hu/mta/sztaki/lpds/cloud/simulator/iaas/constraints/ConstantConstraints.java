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
package hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints;

public class ConstantConstraints extends ResourceConstraints {
	final private double requiredCPUs;
	final private double requiredProcessingPower;
	final private boolean requiredProcessingIsMinimum;
	final private long requiredMemory;
	final private double totalProcessingPower;

	public ConstantConstraints(final double cpu, final double processing,
			final long memory) {
		this(cpu, processing, false, memory);
	}

	public ConstantConstraints(final double cpu, final double processing,
			boolean isMinimum, final long memory) {
		requiredCPUs = cpu;
		requiredMemory = memory;
		requiredProcessingPower = processing;
		totalProcessingPower = cpu * processing;
		requiredProcessingIsMinimum = isMinimum;
	}

	public ConstantConstraints(final ResourceConstraints toCopy) {
		this(toCopy.getRequiredCPUs(), toCopy.getRequiredProcessingPower(),
				toCopy.isRequiredProcessingIsMinimum(), toCopy
						.getRequiredMemory());
	}

	@Override
	public double getRequiredCPUs() {
		return requiredCPUs;
	}

	@Override
	public double getRequiredProcessingPower() {
		return requiredProcessingPower;
	}

	@Override
	public boolean isRequiredProcessingIsMinimum() {
		return requiredProcessingIsMinimum;
	}

	@Override
	public long getRequiredMemory() {
		return requiredMemory;
	}

	@Override
	public double getTotalProcessingPower() {
		return totalProcessingPower;
	}

}
