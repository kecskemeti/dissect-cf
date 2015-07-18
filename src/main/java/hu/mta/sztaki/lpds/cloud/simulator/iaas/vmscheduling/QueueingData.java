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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.UnalterableConstraintsPropagator;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

import java.util.Collections;
import java.util.Map;

public class QueueingData {
	public final VirtualMachine[] queuedVMs;
	public final ResourceConstraints queuedRC;
	public final ResourceConstraints cumulativeRC;
	public final Repository queuedRepo;
	/**
	 * Data for custom schedulers, if null then there is no data.
	 */
	public final Map<String, Object> schedulingConstraints;
	public final long receivedTime;

	public QueueingData(final VirtualMachine[] vms,
			final ResourceConstraints rc, final Repository vaSource,
			Map<String, Object> schedulingConstraints, final long received) {
		queuedVMs = vms;
		queuedRC = rc;
		queuedRepo = vaSource;
		AlterableResourceConstraints cRC = new AlterableResourceConstraints(rc);
		cRC.multiply(queuedVMs.length);
		cumulativeRC=new UnalterableConstraintsPropagator(cRC);
		receivedTime = received;
		this.schedulingConstraints = schedulingConstraints==null?null:Collections.unmodifiableMap(schedulingConstraints);
	}

	@Override
	public String toString() {
		return "QueueingData(" + queuedVMs.length + " * " + queuedRC + " @"
				+ receivedTime + ")";
	}
}