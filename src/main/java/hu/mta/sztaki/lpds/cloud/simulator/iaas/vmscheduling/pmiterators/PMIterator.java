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

import java.util.Iterator;
import java.util.List;

public class PMIterator implements Iterator<PhysicalMachine> {

	protected int index = 0;
	protected int maxIndex = 0;
	protected int marked = 0;
	protected List<PhysicalMachine> pmList;

	public PMIterator(List<PhysicalMachine> pmList) {
		this.pmList = pmList;
	}

	public void reset() {
		maxIndex = pmList.size();
		restart();
	}

	public void restart() {
		restart(false);
	}

	public void restart(boolean fromMarked) {
		index = fromMarked ? marked : 0;
	}

	public void markLastCollected() {
		marked = index-1;
	}

	@Override
	public boolean hasNext() {
		return index < maxIndex;
	}

	@Override
	public PhysicalMachine next() {
		return pmList.get(index++);
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
}
