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
 *  (C) Copyright 2015, Gabor Kecskemeti (kecskemeti.gabor@sztaki.mta.hu)
 */
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.pmiterators;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import java.util.List;

public class RoundRobinIterator extends PMIterator {

	int stopIndex = -1;

	public RoundRobinIterator(List<PhysicalMachine> pmList) {
		super(pmList);
	}

	@Override
	public void restart(boolean fromMarked) {
		stopIndex = index + maxIndex;
	};

	@Override
	public void reset() {
		index = maxIndex == 0 ? maxIndex : index % maxIndex;
		super.reset();
	}

	@Override
	public boolean hasNext() {
		return index < stopIndex;
	}

	@Override
	public PhysicalMachine next() {
		return pmList.get(index++ % maxIndex);
	}
}
