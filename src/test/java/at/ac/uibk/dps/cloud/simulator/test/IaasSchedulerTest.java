/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ac.uibk.dps.cloud.simulator.test;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.RoundRobinIaasScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Simon Csaba
 */
public class IaasSchedulerTest extends IaaSRelatedFoundation {

	public IaasSchedulerTest() {
	}

	private static IaaSService iaas;

	@BeforeClass
	public static void setUpClass() throws Exception {

	}

	@AfterClass
	public static void tearDownClass() {
	}

	@Before
	public void setUp() {
	}

	@After
	public void tearDown() {
	}

	@Test
	public void testScheduler() throws Exception {
		iaas = new IaaSService(RoundRobinIaasScheduler.class, AlwaysOnMachines.class);
		
		//for (int i = 0; i < 10; i++) {
			iaas.registerHost(dummyPMcreator());
			iaas.registerRepository(dummyRepoCreator(true));
			iaas.registerHost(dummyPMcreator());
			iaas.registerRepository(dummyRepoCreator(true));
		//}
		
		

		iaas.requestVM(
				(VirtualAppliance) iaas.repositories.get(0).contents().iterator().next(),
				iaas.getCapacities(),
				iaas.repositories.get(0),
				1);

	}
}
