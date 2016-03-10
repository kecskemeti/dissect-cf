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
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.RoundRobinScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.iaasscheduling.IaasScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.iaasscheduling.RoundRobinIaasScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import java.util.ArrayList;
import java.util.Iterator;
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
	public void testDynamicIaaSListSchedulerAdd() throws Exception {
		ArrayList<Class<? extends IaasScheduler>> h = new ArrayList<Class<? extends IaasScheduler>>();
		h.add(RoundRobinIaasScheduler.class);
		h.add(RoundRobinIaasScheduler.class);

		iaas = new IaaSService(h, AlwaysOnMachines.class, 0);
		ArrayList<IaaSService> iaases = iaas.getIaases();

		registerPm(iaas, 100, "A");

		Assert.assertEquals(1, iaases.size());
		Assert.assertEquals(100, iaases.get(0).machines.size());

		registerPm(iaas, 1, "B");
		Assert.assertEquals(2, iaases.size());
		Assert.assertEquals(51, iaases.get(0).machines.size());
		Assert.assertEquals(50, iaases.get(1).machines.size());

		registerPm(iaas, 99, "C");
		Assert.assertEquals(2, iaases.size());
		Assert.assertEquals(100, iaases.get(0).machines.size());
		Assert.assertEquals(100, iaases.get(1).machines.size());

		registerPm(iaas, 3, "D");
		Assert.assertEquals(3, iaases.size());
		Assert.assertEquals(68, iaases.get(0).machines.size());
		Assert.assertEquals(68, iaases.get(1).machines.size());
		Assert.assertEquals(67, iaases.get(2).machines.size());

		registerPm(iaas, 97, "E");
		Assert.assertEquals(3, iaases.size());
		Assert.assertEquals(100, iaases.get(0).machines.size());
		Assert.assertEquals(100, iaases.get(1).machines.size());
		Assert.assertEquals(100, iaases.get(2).machines.size());

		registerPm(iaas, 1, "F");
		Assert.assertEquals(4, iaases.size());
		Assert.assertEquals(76, iaases.get(0).machines.size());
		Assert.assertEquals(75, iaases.get(1).machines.size());
		Assert.assertEquals(75, iaases.get(2).machines.size());
		Assert.assertEquals(75, iaases.get(3).machines.size());

	}

	@Test
	public void testDynamicIaaSListSchedulerRemove() throws Exception {
		ArrayList<Class<? extends IaasScheduler>> h = new ArrayList<Class<? extends IaasScheduler>>();
		h.add(RoundRobinIaasScheduler.class);
		h.add(RoundRobinIaasScheduler.class);

		iaas = new IaaSService(h, AlwaysOnMachines.class, 0);
		ArrayList<IaaSService> iaases = iaas.getIaases();

		registerPm(iaas, 150, "A");
		Assert.assertEquals(75, iaases.get(0).machines.size());
		Assert.assertEquals(75, iaases.get(1).machines.size());

		int i;
		for (i = 0; i < 25; i++) {
			iaas.deregisterHost(iaases.get(0).machines.get(0));
		}
		
		Assert.assertEquals(50, iaases.get(0).machines.size());
		Assert.assertEquals(75, iaases.get(1).machines.size());
		
		iaas.deregisterHost(iaases.get(0).machines.get(0));
		Assert.assertEquals(124, iaases.get(0).machines.size());
		

	}

	private ArrayList<PhysicalMachine> createPMs(int numberOfPMs, String prefix) {
		ArrayList<PhysicalMachine> pms = new ArrayList<PhysicalMachine>();

		final String[] names = generateNames(numberOfPMs, prefix, 1);

		int i;
		for (i = 0; i < numberOfPMs; i++) {
			pms.add(new PhysicalMachine(2, 1, vaSize * 40,
					new Repository(vaSize * 200, names[i], 1, 1, 1,
							getGlobalLatencyMapInternal()), 1, 1, defaultTransitions));
		}

		return pms;

	}

	private void registerPm(IaaSService iaas, int numberOfPMs, String prefix) {
		final String[] names = generateNames(numberOfPMs, prefix, 1);

		int i;
		for (i = 0; i < numberOfPMs; i++) {
			iaas.registerHost(new PhysicalMachine(2, 1, vaSize * 40,
					new Repository(vaSize * 200, names[i], 1, 1, 1,
							getGlobalLatencyMapInternal()), 1, 1, defaultTransitions));
		}

	}

//	@Ignore
//	@Test
//	public void testScheduler() throws Exception {
//		int numberOfIaaS = 5;
//		int numberOfPMsPerIaaS = 100;
//		int numberOfrequestedVMs = 12;
//		
//		iaas = new IaaSService(RoundRobinIaasScheduler.class, AlwaysOnMachines.class);
//
//		
//		final String[] names = generateNames(numberOfIaaS*numberOfPMsPerIaaS, "M", 1);
//		iaas.registerRepository(dummyRepoCreator(true));
//		for (int i = 0; i < numberOfIaaS; i++) {
//			iaas.registerHost(new PhysicalMachine(2, 1, vaSize * 40,
//					new Repository(vaSize * 200, names[i], 1, 1, 1,
//							getGlobalLatencyMapInternal()), 1, 1, defaultTransitions));
//			
//		}
//		
//		for (int i = 0; i < numberOfrequestedVMs; i++) {
//			iaas.requestVM(
//				(VirtualAppliance) iaas.repositories.get(0).contents().iterator().next(),
//				new AlterableResourceConstraints(1, 1, 512),
//				iaas.repositories.get(0),
//				1);
//		}
//		
//		ArrayList<IaaSService> iaases = iaas.getIaases();
//		
//		int[] vms = new int[iaases.size()];
//		
//		for(int i=0; i<iaases.size(); i++) {
//			vms[i] = iaases.get(i).listVMs().size();
//		}
//		
//		Assert.assertArrayEquals(vms, new int[]{3,3,2,2,2});
//	}
}
