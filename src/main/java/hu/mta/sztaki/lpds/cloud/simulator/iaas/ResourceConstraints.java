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

/**
 * This class is intentionally non mutable, allowing those users (e.g. the class
 * of IaaSService or Scheduler) who would want to share their current capacities
 * not to create a new instance for every new query.
 * 
 * @author 
 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 */
public abstract class ResourceConstraints implements
		Comparable<ResourceConstraints> {

	@Override
	public String toString() {
		return "ResourceConstraints(C:" + getRequiredCPUs() + " P:"
				+ getRequiredProcessingPower() + " M:" + getRequiredMemory()
				+ ")";
	}

	@Override
	public int compareTo(ResourceConstraints o) {
		return getRequiredCPUs() == o.getRequiredCPUs()
				&& getRequiredMemory() == o.getRequiredMemory()
				&& getRequiredProcessingPower() == o
						.getRequiredProcessingPower() ? 0
				: (getRequiredCPUs() <= o.getRequiredCPUs()
						&& getRequiredMemory() <= o.getRequiredMemory()
						&& getRequiredProcessingPower() <= o
								.getRequiredProcessingPower() ? -1 : 1);
	}

	public abstract double getRequiredCPUs();

	public abstract double getRequiredProcessingPower();

	public abstract boolean isRequiredProcessingIsMinimum();

	public abstract long getRequiredMemory();

	public abstract double getTotalProcessingPower();

	public abstract void multiply(final double times);

	public abstract void add(final ResourceConstraints... toAdd);

	public abstract void subtract(final ResourceConstraints what);
}
