package at.ac.uibk.dps.cloud.simulator.test.simple.cloud.vmconsolidation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.SimpleConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.ConsolidationFriendlyPmScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.pso.PsoConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.simple.AbcConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.simple.FirstFitConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.simple.GaConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

@Ignore
public class VMConsolidationTest extends IaaSRelatedFoundation {

	/**
	 * @author Julian Bellendorf, Rene Ponto
	 * 
	 * Testcases:
	 * 
	 * Create situation with overAllocated PMs and check if the consolidation is done correctly
	 * Create situation with underAllocated PMs and check if the consolidation is done correctly
	 */

	// Creation of all necessary objects and variables

	IaaSService basic;	
	PhysicalMachine testPM1;
	PhysicalMachine testPM2;
	PhysicalMachine testPM3;
	PhysicalMachine testPM4;

	VirtualMachine VM1;
	VirtualMachine VM2;
	VirtualMachine VM3;
	VirtualMachine VM4;
	VirtualMachine VM5;
	VirtualMachine VM6;
	VirtualMachine VM7;
	VirtualMachine VM8;

	VirtualAppliance VA1;	
	VirtualAppliance VA2;
	VirtualAppliance VA3;	
	VirtualAppliance VA4;
	VirtualAppliance VA5;
	VirtualAppliance VA6;
	VirtualAppliance VA7;
	VirtualAppliance VA8;

	HashMap<String, Integer> latmap = new HashMap<String, Integer>();	
	Repository centralRepo;

	final static int reqcores = 8, reqProcessing = 1, reqmem = 16,
			reqond = 2 * (int) aSecond, reqoffd = (int) aSecond, reqDisk=100000;

	//The different ResourceConstraints to get an other allocation for each PM

	final ResourceConstraints smallConstraints = new ConstantConstraints(1, 1, 2);	
	final ResourceConstraints mediumConstraints = new ConstantConstraints(3, 1, 6);	
	final ResourceConstraints bigConstraints = new ConstantConstraints(5, 1, 8);

	private PhysicalMachine createPm(String id, double cores, double perCoreProcessing, int ramMb, int diskMb) {
		long bandwidth=1000000;
		Repository disk = new Repository(diskMb*1024*1024, id, bandwidth, bandwidth, bandwidth, latmap, defaultStorageTransitions, defaultNetworkTransitions);
		PhysicalMachine pm = new PhysicalMachine(cores, perCoreProcessing, ramMb*1024*1024, disk, reqond, reqoffd, defaultHostTransitions);
		latmap.put(id,1);
		return pm;
	}

	//This method deploys one VM to a target PM	
	private void switchOnVM(VirtualMachine vm, ResourceConstraints cons, PhysicalMachine pm, boolean simulate) throws VMManagementException, NetworkException {
		vm.switchOn(pm.allocateResources(cons, true, PhysicalMachine.defaultAllocLen), centralRepo);
		if(simulate) {
			Timed.simulateUntilLastEvent();
		}
	}

	/**
	 * Now three PMs and four VMs are going to be instantiated.
	 * At first the PMs are created, after that the VMs are created and each of them deployed to one PM. 
	 */	
	@Before
	public void testSim() throws Exception {
		Handler logFileHandler;
		try {
			logFileHandler = new FileHandler("log.txt");
			logFileHandler.setFormatter(new SimpleFormatter());
			Logger.getGlobal().addHandler(logFileHandler);
		} catch (Exception e) {
			System.out.println("Could not open log file for output"+e);
			System.exit(-1);
		}

		basic = new IaaSService(FirstFitScheduler.class, ConsolidationFriendlyPmScheduler.class);

		//create central repository
		long bandwidth=1000000;
		centralRepo = new Repository(100000, "test1", bandwidth, bandwidth, bandwidth, latmap, defaultStorageTransitions, defaultNetworkTransitions);
		latmap.put("test1", 1);
		basic.registerRepository(centralRepo);

		//create PMs
		testPM1 = createPm("pm1", reqcores, reqProcessing, reqmem, reqDisk);
		testPM2 = createPm("pm2", reqcores, reqProcessing, reqmem, reqDisk);
		testPM3 = createPm("pm3", reqcores, reqProcessing, reqmem, reqDisk);
		testPM4 = createPm("pm4", reqcores, reqProcessing, reqmem, reqDisk);
		
		//register PMs
		basic.registerHost(testPM1);
		basic.registerHost(testPM2);
		basic.registerHost(testPM3);
		basic.registerHost(testPM4);

		// The four VMs set the Load of PM1 to overAllocated, PM2 to underoverAllocated and PM3 to normal.				
		VA1 = new VirtualAppliance("VM 1", 1, 0, false, 1);
		VA2 = new VirtualAppliance("VM 2", 1, 0, false, 1);
		VA3 = new VirtualAppliance("VM 3", 1, 0, false, 1);
		VA4 = new VirtualAppliance("VM 4", 1, 0, false, 1);
		VA5 = new VirtualAppliance("VM 5", 1, 0, false, 1);
		VA6 = new VirtualAppliance("VM 6", 1, 0, false, 1);
		VA7 = new VirtualAppliance("VM 7", 1, 0, false, 1);
		VA8 = new VirtualAppliance("VM 7", 1, 0, false, 1);

		//save the VAs in the repository
		centralRepo.registerObject(VA1);
		centralRepo.registerObject(VA2);
		centralRepo.registerObject(VA3);
		centralRepo.registerObject(VA4);		
		centralRepo.registerObject(VA5);
		centralRepo.registerObject(VA6);
		centralRepo.registerObject(VA7);
		centralRepo.registerObject(VA8);	
		
		VM1 = new VirtualMachine(VA1);
		VM2 = new VirtualMachine(VA2);
		VM3 = new VirtualMachine(VA3);
		VM4 = new VirtualMachine(VA4);
		VM5 = new VirtualMachine(VA5);
		VM6 = new VirtualMachine(VA6);
		VM7 = new VirtualMachine(VA7);
		VM8 = new VirtualMachine(VA8);
	}
	
	// VM consolidator using a first fit algorithm
	
	@Test(timeout = 1000)
	public void ffcOverAllocSimpleTest() throws VMManagementException, NetworkException {
		testPM1.turnon();
		testPM2.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, bigConstraints, testPM1, false);
		switchOnVM(VM2, mediumConstraints, testPM1, false);
		Timed.simulateUntilLastEvent();

		//Now, both VMs are on PM1, making it overAllocated. PM2 is also on but 
		//empty. If we turn on the consolidator, we expect it to move one of the
		//VMs from PM1 to PM2

		FirstFitConsolidator ffc = new FirstFitConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);
		
		if(ffc.getUpperThreshold() != 1.0) {
			Assert.assertEquals(1, testPM1.publicVms.size());
			Assert.assertEquals(1, testPM2.publicVms.size());
		}		
	}
	
	@Test(timeout = 1000)
	public void ffcOverAllocComplexTest() throws VMManagementException, NetworkException {
		testPM1.turnon();
		testPM2.turnon();
		Timed.simulateUntilLastEvent();
		
		switchOnVM(VM1, this.smallConstraints, testPM1, true);
		switchOnVM(VM2, this.smallConstraints, testPM1, true);
		switchOnVM(VM3, this.smallConstraints, testPM1, true);
		switchOnVM(VM7, this.smallConstraints, testPM2, true);
		
		//Timed.simulateUntilLastEvent();		
		
		switchOnVM(VM4, this.smallConstraints, testPM1, true);
		switchOnVM(VM5, this.smallConstraints, testPM1, true);
		switchOnVM(VM6, this.mediumConstraints, testPM1, true);
		
		Timed.simulateUntilLastEvent();		
		//Now, PM1 contains 6 VMs and PM2 one VM. If we turn on the consolidator,
		//we expect moving some of the VMs of PM1 to PM2.
		
		FirstFitConsolidator ffc = new FirstFitConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);
		
		if(ffc.getUpperThreshold() != 1.0) {
			Assert.assertEquals(2, basic.runningMachines.size());
			Assert.assertNotEquals(6, testPM1.publicVms.size());
		}		
		else {
			Assert.assertEquals(2, basic.runningMachines.size());
			Assert.assertEquals(6, testPM1.listVMs().size());
			Assert.assertEquals(1, testPM2.listVMs().size());
		}
	}

	@Test(timeout = 1000)
	public void ffcUnderAllocSimpleTest() throws VMManagementException, NetworkException {
		testPM2.turnon();
		testPM3.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, smallConstraints, testPM2, false);
		switchOnVM(VM2, mediumConstraints, testPM3, false);
		Timed.simulateUntilLastEvent();

		//Now, both PMs contain one VM each. If we turn on the consolidator, 
		//we expect it to consolidate the two VMs to a single PM.

		new FirstFitConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		Assert.assertEquals(1, basic.runningMachines.size());
	}
	
	@Test(timeout = 1000)
	public void ffcUnderAllocComplexTest() throws VMManagementException, NetworkException {
		testPM1.turnon();
		testPM2.turnon();
		testPM3.turnon();
		testPM4.turnon();
		
		Timed.simulateUntilLastEvent();
		
		switchOnVM(VM1, this.smallConstraints, testPM1, true);
		switchOnVM(VM3, this.smallConstraints, testPM2, true);
		switchOnVM(VM4, this.smallConstraints, testPM2, true);
		
		Timed.simulateUntilLastEvent();
		//Now, PM1 contains one VM and PM2 two VMs. If we turn on the consolidator, 
		//we expect only one running machine.
		
		new FirstFitConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		Assert.assertEquals(1, basic.runningMachines.size());
		
	}
	
	@Test(timeout = 100)
	public void ffcShutDownTest() throws VMManagementException, NetworkException {
		testPM1.turnon();
		testPM2.turnon();
		testPM3.turnon();
		testPM4.turnon();
		
		Timed.simulateUntilLastEvent();
		
		switchOnVM(VM1, this.smallConstraints, testPM2, true);
		switchOnVM(VM2, this.smallConstraints, testPM3, true);
		switchOnVM(VM3, this.smallConstraints, testPM4, true);
		
		Timed.simulateUntilLastEvent();
		//Now,we have three PMs containing one VM each. If we turn on the consolidator, 
		//we expect it to consolidate all VMs to a single PM.
		
		new FirstFitConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);
		
		Assert.assertEquals(1, basic.runningMachines.size());
		Assert.assertEquals(PhysicalMachine.State.RUNNING, testPM1.getState());
	}
	
	@Test(timeout = 100)
	public void mutualPlacementTest() throws VMManagementException, NetworkException {
		testPM1.turnon();
		testPM2.turnon();
		testPM3.switchoff(null);
		testPM4.switchoff(null);
		
		Timed.simulateUntilLastEvent();
		
		switchOnVM(VM1, this.smallConstraints, testPM1, true);
		switchOnVM(VM2, this.smallConstraints, testPM2, true);
		
		Timed.simulateUntilLastEvent();		
		testPM1.migrateVM(VM1, testPM2);
		testPM2.migrateVM(VM2, testPM1);
		
		//Testing if mutual placement is possible by migrating the VM of PM1 to PM2 and
		//vice versa.
		
		Timed.simulateUntilLastEvent();
		
		Assert.assertEquals(2, basic.runningMachines.size());
		Assert.assertTrue(testPM2.publicVms.contains(VM1));
		Assert.assertTrue(testPM1.publicVms.contains(VM2));
	}
	
	// VM consolidator using a genetic algorithm

	@Test(timeout = 1000)
	public void gaUnderAllocSimpleTest() throws VMManagementException, NetworkException {
		testPM2.turnon();
		testPM3.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, smallConstraints, testPM2, false);
		switchOnVM(VM2, mediumConstraints, testPM3, false);
		Timed.simulateUntilLastEvent();

		//Now, both PMs contain one VM each. If we turn on the consolidator, 
		//we expect it to consolidate the two VMs to a single PM.

		new GaConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		Assert.assertEquals(1, basic.runningMachines.size());
	}
	
	@Test(timeout = 1000)
	public void gaUnderAllocComplexTest() throws VMManagementException, NetworkException {
		testPM1.turnon();
		testPM2.turnon();
		testPM3.turnon();
		testPM4.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, smallConstraints, testPM1, false);
		switchOnVM(VM2, smallConstraints, testPM2, false);
		switchOnVM(VM3, smallConstraints, testPM3, false);
		switchOnVM(VM4, smallConstraints, testPM4, false);
		Timed.simulateUntilLastEvent();

		//Now, all four PMs contain one VM each. After turning on the consolidator,
		//there should be only one running PM, hosting all four VMs.

		new GaConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		Assert.assertEquals(1, basic.runningMachines.size());
	}
	
	@Test(timeout = 1000)
	public void gaOverAllocSimpleTest() throws VMManagementException, NetworkException {
		testPM1.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, bigConstraints, testPM1, false);
		switchOnVM(VM2, mediumConstraints, testPM1, false);
		Timed.simulateUntilLastEvent();

		//Now, PM1 contains two VM, making it overAllocated. If we turn on the consolidator, 
		//we expect it to consolidate one VM to another PM.

		GaConsolidator ga = new GaConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		if(ga.getUpperThreshold() != 1.0) {
			Assert.assertEquals(1, testPM1.listVMs().size());
			Assert.assertEquals(2, basic.runningMachines.size());
		}		
	}
	
	// VM consolidator using artificial bee colony algorithm

	@Test(timeout = 10000)
	public void abcUnderAllocSimpleTest() throws VMManagementException, NetworkException {
		testPM2.turnon();
		testPM3.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, smallConstraints, testPM2, false);
		switchOnVM(VM2, mediumConstraints, testPM3, false);
		Timed.simulateUntilLastEvent();

		//Now, both PMs contain one VM each. If we turn on the consolidator, 
		//we expect it to consolidate the two VMs to a single PM.

		new AbcConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		Assert.assertEquals(1, basic.runningMachines.size());
	}

	@Test(timeout = 1000)
	public void abcUnderAllocComplexTest() throws VMManagementException, NetworkException {
		testPM1.turnon();
		testPM2.turnon();
		testPM3.turnon();
		testPM4.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, smallConstraints, testPM1, false);
		switchOnVM(VM2, smallConstraints, testPM2, false);
		switchOnVM(VM3, smallConstraints, testPM3, false);
		switchOnVM(VM4, smallConstraints, testPM4, false);
		Timed.simulateUntilLastEvent();

		//Now, all four PMs contain one VM each. After turning on the consolidator,
		//there should be only one PM running, hosting all four VMs.

		new AbcConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		Assert.assertEquals(1, basic.runningMachines.size());
	}
	
	@Test(timeout = 1000)
	public void abcOverAllocSimpleTest() throws VMManagementException, NetworkException {
		testPM1.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, bigConstraints, testPM1, false);
		switchOnVM(VM2, mediumConstraints, testPM1, false);
		Timed.simulateUntilLastEvent();

		//Now, PM1 contains twoVM, making it overAllocated. If we turn on the consolidator, 
		//we expect it to consolidate one VM to another PM.

		AbcConsolidator abc = new AbcConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		if(abc.getUpperThreshold() != 1.0) {
			Assert.assertEquals(2, basic.runningMachines.size());
		}		
	}
	
	// VM consolidator using a particle swarm optimization algorithm
	
	@Test(timeout = 2000)
	public void psoUnderAllocSimpleTest() throws VMManagementException, NetworkException {
		testPM2.turnon();
		testPM3.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, smallConstraints, testPM2, false);
		switchOnVM(VM2, mediumConstraints, testPM3, false);
		Timed.simulateUntilLastEvent();

		//Now, both PMs contain one VM each. If we turn on the consolidator, 
		//we expect it to consolidate the two VMs to a single PM.

		new PsoConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		Assert.assertEquals(1, basic.runningMachines.size());
	}
	
	@Test(timeout = 2000)
	public void psoUnderAllocComplexTest() throws VMManagementException, NetworkException {
		testPM1.turnon();
		testPM2.turnon();
		testPM3.turnon();
		testPM4.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, smallConstraints, testPM1, false);
		switchOnVM(VM2, smallConstraints, testPM2, false);
		switchOnVM(VM3, smallConstraints, testPM3, false);
		switchOnVM(VM4, smallConstraints, testPM4, false);
		Timed.simulateUntilLastEvent();

		//Now, all four PMs contain one VM each. After turning on the consolidator,
		//there should be only one PM running, hosting all four VMs.

		new PsoConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		Assert.assertEquals(1, basic.runningMachines.size());
	}
	
	@Test(timeout = 2000)
	public void psoOverAllocSimpleTest() throws VMManagementException, NetworkException {
		testPM1.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, bigConstraints, testPM1, false);
		switchOnVM(VM2, mediumConstraints, testPM1, false);
		Timed.simulateUntilLastEvent();

		//Now, PM1 contains two VM, making it overAllocated. If we turn on the consolidator, 
		//we expect it to consolidate one VM to another PM.

		PsoConsolidator pso = new PsoConsolidator(basic, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		if(pso.getUpperThreshold() != 1.0) {
			Assert.assertEquals(2, basic.runningMachines.size());
		}		
	}
	
	@Test(timeout = 1000)
	public void localSearchTest() throws VMManagementException, NetworkException, InterruptedException,
			IOException {
		
		boolean finished = false;	// used to check if the test was succesful
		
		// we have to set the local search related props
		Properties props1 = new Properties();
		File file1 = new File("consolidationProperties.xml");
		FileInputStream fileInput1 = new FileInputStream(file1);
		props1.loadFromXML(fileInput1);
		fileInput1.close();
		
		props1.setProperty("doLocalSearch1", "false");
		props1.setProperty("doLocalSearch2", "true");
		
		FileOutputStream fileOutput1 = new FileOutputStream(file1);
		props1.storeToXML(fileOutput1, null);
		fileOutput1.close();
		
		try {
			testPM1.turnon();
			testPM2.turnon();
			Timed.simulateUntilLastEvent();
			// switch on two vms
			switchOnVM(VM1, smallConstraints, testPM1, false);
			switchOnVM(VM2, smallConstraints, testPM2, false);
			
			// the aim is to compare the results of the simple consolidator with the results of
			// either the abc, ga or pso to see whether they are identical
			
			@SuppressWarnings("unused")
			SimpleConsolidator sc = new SimpleConsolidator(basic, 600);
			Timed.simulateUntil(Timed.getFireCount() + 1000);
			
			// now we have to save the situation and compare it later with the following one
			PhysicalMachine vm1host1 = VM1.getResourceAllocation().getHost();
			PhysicalMachine vm2host1 = VM2.getResourceAllocation().getHost();
			
			VM1.destroy(true);
			VM2.destroy(true);
			Assert.assertEquals(0, testPM1.numofCurrentVMs());
			Assert.assertEquals(0, testPM2.numofCurrentVMs());
			sc = null;
			
			// switch on another two vms
			switchOnVM(VM1, smallConstraints, testPM1, false);
			switchOnVM(VM2, smallConstraints, testPM2, false);
			
			
			@SuppressWarnings("unused")
			GaConsolidator ga = new GaConsolidator(basic, 600);
			Timed.simulateUntil(Timed.getFireCount() + 1000);
			
			// now we have to save the situation and compare it later with the previous one
			PhysicalMachine vm1host2 = VM1.getResourceAllocation().getHost();
			PhysicalMachine vm2host2 = VM2.getResourceAllocation().getHost();
			
			ga = null;
			
			// if the assertions fail, we have to reset the properties at this point
			Properties props2 = new Properties();
			File file2 = new File("consolidationProperties.xml");
			FileInputStream fileInput2 = new FileInputStream(file2);
			props2.loadFromXML(fileInput2);
			fileInput2.close();
			
			props2.setProperty("doLocalSearch1", "true");
			props2.setProperty("doLocalSearch2", "false");
			
			FileOutputStream fileOutput2 = new FileOutputStream(file2);
			props2.storeToXML(fileOutput2, null);
			fileOutput2.close();
			
			finished = true;
			
			// compare the results
			Assert.assertEquals(vm1host1, vm1host2);
			Assert.assertEquals(vm2host1, vm2host2);
			
			
			
		} catch(Exception e) {
			
		}
		
		// reset the local search values
		Properties props2 = new Properties();
		File file2 = new File("consolidationProperties.xml");
		FileInputStream fileInput2 = new FileInputStream(file2);
		props2.loadFromXML(fileInput2);
		fileInput2.close();
		
		props2.setProperty("doLocalSearch1", "true");
		props2.setProperty("doLocalSearch2", "false");
		
		FileOutputStream fileOutput2 = new FileOutputStream(file2);
		props2.storeToXML(fileOutput2, null);
		fileOutput2.close();
		
		Assert.assertTrue("Test has not been finished correctly.", finished);
		
	}
	
}
