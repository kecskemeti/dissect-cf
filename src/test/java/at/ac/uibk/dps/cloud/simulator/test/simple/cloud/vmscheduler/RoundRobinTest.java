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
package at.ac.uibk.dps.cloud.simulator.test.simple.cloud.vmscheduler;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.RoundRobinScheduler;

import java.lang.reflect.InvocationTargetException;

import org.junit.Assert;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;

public class RoundRobinTest extends IaaSRelatedFoundation {
	public static int maxPMCount = 10;
	public static int VMcount = 1000;
	public static int vmspread = 100;
	public static double proclen = 5;

	@Test(timeout = 100)
	public void regularVMSchedule() throws InstantiationException,
			IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException {
		IaaSService iaas = new IaaSService(RoundRobinScheduler.class,
				SchedulingDependentMachines.class);
		iaas.registerHost(dummyPMcreator(2));
		iaas.registerHost(dummyPMcreator(2));
		iaas.registerRepository(dummyRepoCreator(true));
		Assert.assertTrue("No machines should be running now",
				iaas.runningMachines.isEmpty());
		fireVMat(iaas, 50, 5000, 1);
		fireVMat(iaas, 200, 5000, 1);
		fireVMat(iaas, 500, 5000, 1);
		Timed.simulateUntil(Timed.getFireCount() + 400);
		Assert.assertEquals("Only one machine should be running now",
				1, iaas.runningMachines.size());
		Timed.simulateUntil(Timed.getFireCount() + 300);
		Assert.assertEquals("Both machines should be running now",
				2, iaas.runningMachines.size());
	}
}
