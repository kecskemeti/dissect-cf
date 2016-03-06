/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ac.uibk.dps.cloud.simulator.test;

import static at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation.vaSize;
import static at.ac.uibk.dps.cloud.simulator.test.PMRelatedFoundation.defaultTransitions;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.RoundRobinIaasScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Simon Csaba
 */
public class RoundRobinIaasSchedulerTest extends IaaSRelatedFoundation {

	public RoundRobinIaasSchedulerTest() {
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
		int numberOfIaaS = 5;
		int numberOfPMsPerIaaS = 4;
		int numberOfrequestedVMs = 12;
		
		iaas = new IaaSService(RoundRobinIaasScheduler.class, AlwaysOnMachines.class);

		
		final String[] names = generateNames(numberOfIaaS*numberOfPMsPerIaaS, "M", 1);
		for (int i = 0; i < numberOfIaaS; i++) {
			iaas.registerHost(dummyPMcreator());
			
			iaas.registerHost(new PhysicalMachine(2, 1, vaSize * 40,
					new Repository(vaSize * 200, names[i], 1, 1, 1,
							getGlobalLatencyMapInternal()), 1, 1, defaultTransitions));
			iaas.registerRepository(dummyRepoCreator(true));
		}
		
		for (int i = 0; i < numberOfrequestedVMs; i++) {
			iaas.requestVM(
				(VirtualAppliance) iaas.repositories.get(0).contents().iterator().next(),
				new AlterableResourceConstraints(1, 1, 512),
				iaas.repositories.get(0),
				1);
		}
		
		IaaSService[] iaases = iaas.getIaases();
		
		int[] vms = new int[iaases.length];
		
		for(int i=0; i<iaases.length; i++) {
			vms[i] = iaases[i].listVMs().size();
		}
		
		Assert.assertArrayEquals(vms, new int[]{3,3,2,2,2});
		

	}
}
