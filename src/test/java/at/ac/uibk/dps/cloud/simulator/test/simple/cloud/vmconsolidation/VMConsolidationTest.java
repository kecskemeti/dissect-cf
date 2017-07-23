package at.ac.uibk.dps.cloud.simulator.test.simple.cloud.vmconsolidation;

import java.util.HashMap;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.OnOffScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.FirstFitConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.NonQueueingScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

public class VMConsolidationTest extends IaaSRelatedFoundation {

	/**
	 * @author Julian Bellendorf, René Ponto
	 * 
	 * Testcases:
	 * 
	 * simulator:			creation of PMs and VMs inside the simulator and their deployment
	 * 			 			deployment of resources to the VMs
	 * 			 			creating overAllocated and underAllocated PMs
	 * 
	 * selection out    	do the abstract version of the PMs fit with the real ones?
	 * of the simulator:	do the deployment of the VMs fit with the deployment inside the simulator?
	 * 						are the resources of the VMs correct?
	 * 
	 * algorithm:			Do the allocation of each abstract PM fit with the consumed resources?  
	 *       				Do the right MigPMs get chosen?
	 *       				Is the algorithm for migration correct and the right one used (allocation-dependant)?
	 *       				Is the shut down method working?
	 *       				Can a not running PM get started if necessary?
	 *       
	 * graph: 				Do actions exist?
	 * 		  				Are the actions bound to their order?
	 * 		  				Is the selection out of the algorithm correct?
	 * 		  				Are the changes done inside the simulator?
	 */

	// Timed.simulateUntil(Timed.getFireCount() + 1000);	maybe use this instead of the actual simulate

	// Creation of all necessary objects and variables

	final double upperThreshold = 0.75;
	final double lowerThreshold = 0.25;	

	IaaSService basic;
	FirstFitConsolidator ffc;
	PhysicalMachine testOverPM1;
	PhysicalMachine testUnderPM2;
	PhysicalMachine testNormalPM3;

	VirtualMachine VM1;
	VirtualMachine VM2;
	VirtualMachine VM3;
	VirtualMachine VM4;

	VirtualAppliance VA1;	
	VirtualAppliance VA2;
	VirtualAppliance VA3;	
	VirtualAppliance VA4;

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
		Repository disk=new Repository(diskMb*1024*1024, id, bandwidth, bandwidth, bandwidth, latmap);
		PhysicalMachine pm=new PhysicalMachine(cores, perCoreProcessing, ramMb*1024*1024, disk, reqond, reqoffd, defaultTransitions);
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

		basic = new IaaSService(NonQueueingScheduler.class, OnOffScheduler.class);

		//create central repository
		long bandwidth=1000000;
		centralRepo = new Repository(100000, "test1", bandwidth, bandwidth, bandwidth, latmap);
		latmap.put("test1", 1);
		basic.registerRepository(centralRepo);

		//create PMs
		testOverPM1 = createPm("pm1", reqcores, reqProcessing, reqmem, reqDisk);
		testUnderPM2 = createPm("pm2", reqcores, reqProcessing, reqmem, reqDisk);
		testNormalPM3 = createPm("pm3", reqcores, reqProcessing, reqmem, reqDisk);

		//register PMs
		basic.registerHost(testOverPM1);
		basic.registerHost(testUnderPM2);
		basic.registerHost(testNormalPM3);

		// The four VMs set the Load of PM1 to overAllocated, PM2 to underoverAllocated and PM3 to normal.				
		VA1 = new VirtualAppliance("VM 1", 1, 0, false, 1);
		VA2 = new VirtualAppliance("VM 2", 1, 0, false, 1);
		VA3 = new VirtualAppliance("VM 3", 1, 0, false, 1);
		VA4 = new VirtualAppliance("VM 4", 1, 0, false, 1);

		//save the VAs in the repository
		centralRepo.registerObject(VA1);
		centralRepo.registerObject(VA2);
		centralRepo.registerObject(VA3);
		centralRepo.registerObject(VA4);		

		VM1 = new VirtualMachine(VA1);
		VM2 = new VirtualMachine(VA2);
		VM3 = new VirtualMachine(VA3);
		VM4 = new VirtualMachine(VA4);
	}


	@Test(timeout = 1000)
	public void overAllocTest() throws VMManagementException, NetworkException {
		testOverPM1.turnon();
		testUnderPM2.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, bigConstraints, testOverPM1, false);
		switchOnVM(VM2, mediumConstraints, testOverPM1, false);
		Timed.simulateUntilLastEvent();

		//Now, both VMs are on PM1, making it overloaded. PM2 is also on but 
		//empty. If we turn on the consolidator, we expect it to move one of the
		//VMs from PM1 to PM2

		ffc=new FirstFitConsolidator(basic, upperThreshold, lowerThreshold, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		Assert.assertEquals(1, testOverPM1.publicVms.size());
		Assert.assertEquals(1, testUnderPM2.publicVms.size());
	}

	@Test(timeout = 1000)
	public void underAllocTest() throws VMManagementException, NetworkException {
		testUnderPM2.turnon();
		testNormalPM3.turnon();
		Timed.simulateUntilLastEvent();
		switchOnVM(VM1, smallConstraints, testUnderPM2, false);
		switchOnVM(VM2, mediumConstraints, testNormalPM3, false);
		Timed.simulateUntilLastEvent();

		//Now, both PMs contain one VM each. If we turn on the consolidator, 
		//we expect it to consolidate the two VMs to a single VM

		ffc=new FirstFitConsolidator(basic, upperThreshold, lowerThreshold, 600);
		Timed.simulateUntil(Timed.getFireCount()+1000);

		Assert.assertEquals(1, basic.runningMachines.size());
	}

}
