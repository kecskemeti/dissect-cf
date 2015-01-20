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

/**
 * 
 * @author 
 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 * 
 */
public class VirtualAppliance extends StorageObject {
	private long bgNetworkLoad;
	private double startupProcessing;

	private void setDetails(final double startupProcess, final long nl) {
		setBgNetworkLoad(nl);
		setStartupProcessing(startupProcess);
	}

	public VirtualAppliance(final String id, final double startupProcess,
			final long nl) {
		super(id);
		setDetails(startupProcess, nl);
	}

	public VirtualAppliance(final String id, final double startupProcess,
			final long nl, boolean vary, final long reqDisk) {
		super(id, reqDisk, vary);
		setDetails(startupProcess, nl);
	}

	@Override
	public VirtualAppliance newCopy(final String myid) {
		return new VirtualAppliance(myid, startupProcessing, bgNetworkLoad,
				false, size);
	}

	public long getBgNetworkLoad() {
		return bgNetworkLoad;
	}

	public double getStartupProcessing() {
		return startupProcessing;
	}

	public void setBgNetworkLoad(long bgNetworkLoad) {
		this.bgNetworkLoad = bgNetworkLoad;
	}

	public void setStartupProcessing(double startupProcessing) {
		this.startupProcessing = startupProcessing;
	}

	@Override
	public String toString() {
		return "VA(" + super.toString() + " sp:" + startupProcessing + " bgnl:"
				+ bgNetworkLoad + ")";
	}
}
