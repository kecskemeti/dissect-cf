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

package at.ac.uibk.dps.cloud.simulator.test;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.NonQueueingScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.SmallestFirstScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

public class IaaSRelatedFoundation extends VMRelatedFoundation {
	public final static int dummyPMCoreCount = 1;
	public final static int vaSize = 100;
	public static HashMap<String, Integer> globalLatencyMap = new HashMap<String, Integer>();

	@BeforeClass
	public static void preloadIaaS() throws Exception {
		new IaaSRelatedFoundation().getNewServiceArray();
	}
	
	@Before
	public void resetLatencyMap() {
		globalLatencyMap.clear();
	}

	public static String generateName(String prefix, int latency) {
		String name = prefix + SeedSyncer.centralRnd.nextInt();
		globalLatencyMap.put(name, latency);
		return name;
	}

	public static PhysicalMachine dummyPMcreator() {

		return new PhysicalMachine(dummyPMCoreCount, 1, 1, new Repository(
				vaSize, generateName("M", 1), 1, 1, 1, globalLatencyMap), 1, 1);
	}

	public static Repository dummyRepoCreator(boolean withVA) {
		Repository repo = new Repository(vaSize, generateName("R", 3), 1, 1, 1,
				globalLatencyMap);
		if (withVA) {
			VirtualAppliance va = new VirtualAppliance("VA", 2000, 0, false,
					vaSize / 5);
			Assert.assertTrue("Registration should succeed",
					repo.registerObject(va));
		}
		return repo;
	}
	
	public ArrayList<IaaSService> getNewServiceArray() throws Exception {
		ArrayList<IaaSService> serviceArray = new ArrayList<IaaSService>();
		serviceArray.add(new IaaSService(FirstFitScheduler.class,
				AlwaysOnMachines.class));
		serviceArray.add(new IaaSService(FirstFitScheduler.class,
				SchedulingDependentMachines.class));
		serviceArray.add(new IaaSService(NonQueueingScheduler.class,
				AlwaysOnMachines.class));
		serviceArray.add(new IaaSService(NonQueueingScheduler.class,
				SchedulingDependentMachines.class));
		serviceArray.add(new IaaSService(SmallestFirstScheduler.class,
				AlwaysOnMachines.class));
		serviceArray.add(new IaaSService(SmallestFirstScheduler.class,
				SchedulingDependentMachines.class));
		return serviceArray;
	}
}
