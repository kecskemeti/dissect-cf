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
package hu.mta.sztaki.lpds.cloud.simulator.iaas;

public class UnalterableConstraints extends ResourceConstraints {
	private final ResourceConstraints whatToPropagate;

	public UnalterableConstraints(final ResourceConstraints toPropagate) {
		whatToPropagate = toPropagate;
	}

	public static ResourceConstraints directUnalterableCreator(
			final double cpu, final double processing, boolean isMinimum,
			final long memory) {
		return new UnalterableConstraints(new AlterableResourceConstraints(cpu,
				processing, isMinimum, memory));
	}

	public static UnalterableConstraints directUnalterableCreator(
			final double cpu, final double processing, final long memory) {
		return new UnalterableConstraints(new AlterableResourceConstraints(cpu,
				processing, memory));
	}

	@Override
	public void add(ResourceConstraints... toAdd) {
		throw new UnsupportedOperationException(
				"Addition is not allowed on unalterable constraints");
	}

	@Override
	public void multiply(double times) {
		throw new UnsupportedOperationException(
				"Multiplication is not allowed on unalterable constraints");
	}

	@Override
	public void subtract(ResourceConstraints what) {
		throw new UnsupportedOperationException(
				"Subtraction is not allowed on unalterable constraints");
	}

	@Override
	public double getRequiredCPUs() {
		return whatToPropagate.getRequiredCPUs();
	}

	@Override
	public long getRequiredMemory() {
		return whatToPropagate.getRequiredMemory();
	}

	@Override
	public double getRequiredProcessingPower() {
		return whatToPropagate.getRequiredProcessingPower();
	}

	@Override
	public double getTotalProcessingPower() {
		return whatToPropagate.getTotalProcessingPower();
	}

	@Override
	public boolean isRequiredProcessingIsMinimum() {
		return whatToPropagate.isRequiredProcessingIsMinimum();
	}
}
