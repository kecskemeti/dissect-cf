/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.ac.uibk.dps.cloud.simulator.test;

import static at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation.vaSize;
import static at.ac.uibk.dps.cloud.simulator.test.PMRelatedFoundation.defaultHostTransitions;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.iaasscheduling.IaasScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.iaasscheduling.MaxNumberOfPMsReachedException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.iaasscheduling.RoundRobinIaasScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import java.util.ArrayList;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
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
			//System.out.println(i);
			iaas.deregisterHost(iaases.get(0).machines.get(0));
		}

//		Assert.assertEquals(50, iaases.get(0).machines.size());
//		Assert.assertEquals(75, iaases.get(1).machines.size());
		System.out.println("valami");
		iaas.deregisterHost(iaases.get(0).machines.get(0));
		Assert.assertEquals(124, iaases.get(0).machines.size());

	}

	@Test
	public void testHierarchy() throws Exception {
		ArrayList<Class<? extends IaasScheduler>> h = new ArrayList<Class<? extends IaasScheduler>>();
		for (int i = 0; i < 4; i++) {
			h.add(RoundRobinIaasScheduler.class);
		}

		iaas = new IaaSService(h, AlwaysOnMachines.class, 0);

		registerPm(iaas, 11110, "A");
		//gyökér IaaS
		Assert.assertEquals(2, iaas.getIaases().size());
		Assert.assertEquals(IaasScheduler.schedulerType.IAAS_TOP, ((RoundRobinIaasScheduler) iaas.getScheduler()).getType());

		//lvl 1 IaaS (teli)
		IaaSService lvl1_1 = iaas.getIaases().get(0);
		Assert.assertEquals(lvl1_1.getIaases().size(), 10);
		Assert.assertEquals(IaasScheduler.schedulerType.IAAS, ((RoundRobinIaasScheduler) lvl1_1.getScheduler()).getType());

		int i, j;
		for (i = 0; i < lvl1_1.getIaases().size(); i++) {
			IaaSService lvl2 = lvl1_1.getIaases().get(i);
			Assert.assertEquals(IaasScheduler.schedulerType.IAAS_LAST, ((RoundRobinIaasScheduler) lvl2.getScheduler()).getType());
			for (j = 0; j < lvl2.getIaases().size(); j++) {
				IaaSService lvl3 = lvl2.getIaases().get(j);
				Assert.assertEquals(0, lvl3.getIaases().size());
				Assert.assertEquals(100, lvl3.machines.size());
				Assert.assertEquals(IaasScheduler.schedulerType.PM, ((RoundRobinIaasScheduler) lvl3.getScheduler()).getType());
			}
		}

		//lvl1 IaaS (nem teli)
		IaaSService lvl1_2 = iaas.getIaases().get(1);
		Assert.assertEquals(2, lvl1_2.getIaases().size());
		Assert.assertEquals(IaasScheduler.schedulerType.IAAS, ((RoundRobinIaasScheduler) lvl1_2.getScheduler()).getType());
		Assert.assertEquals(10, lvl1_2.getIaases().get(0).getIaases().size());
		Assert.assertEquals(2, lvl1_2.getIaases().get(1).getIaases().size());

		IaaSService lvl1_2_10 = lvl1_2.getIaases().get(0);
		for (i = 0; i < lvl1_2_10.getIaases().size(); i++) {
			Assert.assertEquals(100, lvl1_2_10.getIaases().get(i).machines.size());
		}
		
		IaaSService lvl1_2_2 = lvl1_2.getIaases().get(1);
		Assert.assertEquals(55, lvl1_2_2.getIaases().get(0).machines.size());
		Assert.assertEquals(55, lvl1_2_2.getIaases().get(1).machines.size());

	}

	private ArrayList<PhysicalMachine> createPMs(int numberOfPMs, String prefix) {
		ArrayList<PhysicalMachine> pms = new ArrayList<PhysicalMachine>();

		final String[] names = generateNames(numberOfPMs, prefix, 1);

		int i;
		for (i = 0; i < numberOfPMs; i++) {
			pms.add(new PhysicalMachine(2, 1, vaSize * 40,
					new Repository(vaSize * 200, names[i], 1, 1, 1,
							getGlobalLatencyMapInternal(),defaultStorageTransitions,defaultNetworkTransitions), 1, 1, defaultHostTransitions));
		}

		return pms;

	}

	private void registerPm(IaaSService iaas, int numberOfPMs, String prefix) throws Exception {
		final String[] names = generateNames(numberOfPMs, prefix, 1);

		int i;
		for (i = 0; i < numberOfPMs; i++) {

			iaas.registerHostDinamyc(new PhysicalMachine(2, 1, vaSize * 40,
					new Repository(vaSize * 200, names[i], 1, 1, 1,
							getGlobalLatencyMapInternal(),defaultStorageTransitions,defaultNetworkTransitions), 1, 1, defaultHostTransitions));

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
