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

package hu.mta.sztaki.lpds.cloud.simulator.io;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

/**
 * 
 * @author 
 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 */
public class StorageObject {
	// think about if it would make things easier if we would refer here
	// the repository where this storage object is stored.
	public final String id;
	public final long size;

	public StorageObject(final String myid) {
		id = myid;
		size = 500000000L + (long) (SeedSyncer.centralRnd.nextDouble() * 19500000000L);
	}

	public StorageObject(final String myid, final long mysize, boolean vary) {
		id = myid;
		size = vary ? (1 + 2 * mysize - (long) (2 * SeedSyncer.centralRnd
				.nextDouble() * mysize)) : mysize;
	}

	public StorageObject newCopy(final String myid) {
		return new StorageObject(myid, size, false);
	}

	@Override
	public String toString() {
		return "SO(id:" + id + " size:" + size + ")";
	}
}
