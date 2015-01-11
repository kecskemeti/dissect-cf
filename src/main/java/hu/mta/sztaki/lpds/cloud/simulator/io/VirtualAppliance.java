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

public class VirtualAppliance extends StorageObject {
	private long bgNetworkLoad;
	private long startupdelay;

	public VirtualAppliance(final String id, final long delay, final long nl) {
		super(id);
		this.bgNetworkLoad = nl;
		this.startupdelay = delay;
	}

	public VirtualAppliance(final String id, final long delay, final long nl,
			boolean vary, final long reqDisk) {
		super(id, reqDisk, vary);
		this.bgNetworkLoad = nl;
		this.startupdelay = delay;
	}

	@Override
	public VirtualAppliance newCopy(final String myid) {
		return new VirtualAppliance(myid, startupdelay, bgNetworkLoad, false,
				size);
	}

	public long getBgNetworkLoad() {
		return bgNetworkLoad;
	}

	public long getStartupdelay() {
		return startupdelay;
	}
}
