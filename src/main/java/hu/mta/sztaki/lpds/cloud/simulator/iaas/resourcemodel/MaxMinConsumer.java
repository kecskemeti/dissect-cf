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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel;


public class MaxMinConsumer extends MaxMinFairSpreader {

	public MaxMinConsumer(final double initialProcessing) {
		super(initialProcessing);
	}

	@Override
	protected void updateConsumptionLimit(final ResourceConsumption con,
			final double limit) {
		con.consumerLimit = limit;
	}

	@Override
	protected ResourceSpreader getCounterPart(final ResourceConsumption con) {
		return con.getProvider();
	}

	@Override
	protected ResourceSpreader getSamePart(final ResourceConsumption con) {
		return con.getConsumer();
	}

	@Override
	protected double processSingleConsumption(final ResourceConsumption con,
			final double secondsPassed) {
		return con.doConsumerProcessing(secondsPassed);
	}

	@Override
	protected boolean isConsumer() {
		return true;
	}

	@Override
	public String toString() {
		return "MaxMinConsumer(Hash-" + hashCode() + " " + super.toString()
				+ ")";
	}
}
