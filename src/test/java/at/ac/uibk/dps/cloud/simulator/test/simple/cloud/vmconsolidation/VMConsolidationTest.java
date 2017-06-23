package at.ac.uibk.dps.cloud.simulator.test.simple.cloud.vmconsolidation;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.Bin_PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.Bin_PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.FirstFitConsolidation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.Item_VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.NonQueueingScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class VMConsolidationTest extends IaaSRelatedFoundation {
	
	/**
	 * @author Julian, René
	 * 
	 * Testcases:
	 * 
	 * simulator:			creation of PMs and VMs inside the simulator and their deployment
	 * 			 			deployment of resources to the VMs
	 * 			 			creating overloaded and underloaded PMs
	 * 
	 * selection out    	do the abstract version of the PMs fit with the real ones?
	 * of the simulator:	do the deployment of the VMs fit with the deployment inside the simulator?
	 * 						are the resources of the VMs correct?
	 * 
	 * algorithm:			Do the load of each abstract PM fit with the consumed resources?  
	 *       				Do the right MigPMs get chosen?
	 *       				Is the algorithm for migration correct and the right one used (load-dependant)?
	 *       				Is the shut down method working?
	 *       				Can a not running PM get started if necessary?
	 *       
	 * graph: 				Do actions exist?
	 * 		  				Are the actions bound to their order?
	 * 		  				Is the selection out of the algorithm correkt?
	 * 		  				Are the changes done inside the simulator?
	 */
	
	/**
	 * Creation of all necassary objects and variables
	 */
	
	IaaSService basic;
	FirstFitConsolidation ffc;
	
	
	
	
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
	
	Repository reqDisk = new Repository(123, "test1", 200, 200, 200, latmap);
	
	final static int reqcores = 8, reqProcessing = 5, reqmem = 16,
			reqond = 2 * (int) aSecond, reqoffd = (int) aSecond;
	
	//The different ResourceConstraints to get an other load for each PM
	
	final ResourceConstraints smallConstraints = new ConstantConstraints(1, 1, 2);
	
	final ResourceConstraints mediumConstraints = new ConstantConstraints(4, 1, 6);
	
	final ResourceConstraints bigConstraints = new ConstantConstraints(4, 2, 8);
	
	/**
	 * Now three PMs and four VMs are going to be instantiated.
	 * At first the PMs are created, after that the VMs are created and each of them deployed to one PM. 
	 */
	
	@Before
	public void testSim() throws Exception {
		basic = new IaaSService(NonQueueingScheduler.class,
				AlwaysOnMachines.class);
		
		latmap.put("test1", 1);
		
		testOverPM1 = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk,
				reqond, reqoffd, defaultTransitions);
		
		testUnderPM2 = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk,
				reqond, reqoffd, defaultTransitions);
		
		testNormalPM3 = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk,
				reqond, reqoffd, defaultTransitions);
		
		//save the PMs inside the register

		basic.registerRepository(reqDisk);
		basic.registerHost(testOverPM1);
		basic.registerHost(testUnderPM2);
		basic.registerHost(testNormalPM3);
		
		
		 // The four VMs set the Load of PM1 to overloaded, PM2 to underloaded and PM3 to normal.
		
		
		VA1 = new VirtualAppliance("VA1", 1000, 0, false, testOverPM1.localDisk.getMaxStorageCapacity() / 20);
		VA2 = new VirtualAppliance("VA2", 1000, 0, false, testOverPM1.localDisk.getMaxStorageCapacity() / 20);
		VA3 = new VirtualAppliance("VA3", 1000, 0, false, testUnderPM2.localDisk.getMaxStorageCapacity() / 20);
		VA4 = new VirtualAppliance("VA4", 1000, 0, false, testNormalPM3.localDisk.getMaxStorageCapacity() / 20);
		
		
		VM1 = new VirtualMachine(VA1);
		VM2 = new VirtualMachine(VA2);
		VM3 = new VirtualMachine(VA3);
		VM4 = new VirtualMachine(VA4);
		
		
		//save the VAs inside the register and the PMs
		
		reqDisk.registerObject(VA1);
		reqDisk.registerObject(VA2);
		reqDisk.registerObject(VA3);
		reqDisk.registerObject(VA4);
		
		
		testOverPM1.localDisk.registerObject(VA1);
		testOverPM1.localDisk.registerObject(VA2);
		testUnderPM2.localDisk.registerObject(VA3);
		testNormalPM3.localDisk.registerObject(VA4);
	}
	
	
    /** 				
	 * 	simulator:			creation of PMs and VMs inside the simulator and their deployment
	 * 			 			deployment of resources to the VMs
	 * 			 			creating overloaded and underloaded PMs
	 */
	
	
	//This test verifies that all PMs are shut down before simulating
	
	@Test(timeout = 100)
	public void turnonTest() {
		Assert.assertEquals(
				"Even alwayson should not have machines running at the beginning of the simulation",
				0, basic.runningMachines.size());
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Did not switch on all machines as expected",
				basic.machines.size(), basic.runningMachines.size());
	}
	
	//This test verifies that the creation and destruction of a VM is possible
	
	@Test(timeout = 100)
	public void vmCreationTest() throws VMManagementException, NetworkException {
		Timed.simulateUntilLastEvent();
		VirtualMachine vm = basic.requestVM((VirtualAppliance) reqDisk.contents()
				.iterator().next(), basic.machines.get(0)
				.getCapacities(), reqDisk, 1)[0];
		Timed.simulateUntilLastEvent();
		Assert.assertEquals(VirtualMachine.State.RUNNING, vm.getState());
		Assert.assertEquals(
				"An arriving VM should not change the number of running PMs",
				basic.machines.size(), basic.runningMachines.size());
		vm.destroy(false);
		Timed.simulateUntilLastEvent();
		Assert.assertEquals(
				"A departing VM should not change the number of runnning PMs",
				basic.machines.size(), basic.runningMachines.size());
	}
	
	//This method deploys one VM to a target PM
	
	private void switchOnVM(VirtualMachine vm, ResourceConstraints cons, PhysicalMachine pm, boolean simulate) throws VMManagementException, NetworkException {
		
		vm.switchOn(pm.allocateResources(cons, true, 1000), reqDisk);
		if(simulate) {
			Timed.simulateUntilLastEvent();
		}
	}
	
	//This test verifies, that it is possible to start and deploy all VMs
	
	@Test(timeout = 100)
	public void simpleVMStartup() throws VMManagementException, NetworkException {
		
		Timed.simulateUntilLastEvent();
		
		switchOnVM(VM1, this.bigConstraints, testOverPM1, true);
		switchOnVM(VM2, this.mediumConstraints, testOverPM1, true);
		switchOnVM(VM3, this.smallConstraints, testUnderPM2, true);
		switchOnVM(VM4, this.bigConstraints, testNormalPM3, true);
		
		Assert.assertEquals("Regular VM not in the excpected state after switchon", VirtualMachine.State.RUNNING,
				VM1.getState());
		Assert.assertEquals("Regular VM not in the excpected state after switchon", VirtualMachine.State.RUNNING,
				VM2.getState());
		Assert.assertEquals("Regular VM not in the excpected state after switchon", VirtualMachine.State.RUNNING,
				VM3.getState());
		Assert.assertEquals("Regular VM not in the excpected state after switchon", VirtualMachine.State.RUNNING,
				VM4.getState());
	}
	
	
	/** 				
	 * selection out    	do the abstract version of the PMs fit with the real ones?
	 * of the simulator:	do the deployment of the VMs fit with the deployment inside the simulator?
	 * 						are the resources of the VMs correct?
	 * 
	 */
	
	//Method to create the abstract working model
	
	public void createAbstractModel() throws Exception {
		
		switchOnVM(VM1, this.bigConstraints, testOverPM1, true);
		switchOnVM(VM2, this.mediumConstraints, testOverPM1, true);
		switchOnVM(VM3, this.smallConstraints, testUnderPM2, true);
		switchOnVM(VM4, this.bigConstraints, testNormalPM3, true);
		
		ffc = new FirstFitConsolidation(basic);
		
		Timed.simulateUntilLastEvent();
	}
	
	// This test verifies that all three abstract PMs were created and fit with the real ones
	
	@Test(timeout = 100)
	public void checkAbstractPMs() throws Exception {
		
		Timed.simulateUntilLastEvent();
		
		createAbstractModel();
		
		Bin_PhysicalMachine abstractPM1 = ffc.getBins().get(0);
		Bin_PhysicalMachine abstractPM2 = ffc.getBins().get(1);
		Bin_PhysicalMachine abstractPM3 = ffc.getBins().get(2);
		
		Assert.assertEquals("The first PM is not matching with the abstract version of it", testOverPM1,
				abstractPM1.getPM());
		Assert.assertEquals("The second PM is not matching with the abstract version of it", testUnderPM2,
				abstractPM2.getPM());
		Assert.assertEquals("The third PM is not matching with the abstract version of it", testNormalPM3,
				abstractPM3.getPM());
	}
	
	// This test verifies that all four abstract VMs were created and deployed to the fitting PM inside the real simulator
	
	@Test(timeout = 100)
	public void checkAbstractVMs() throws Exception {

		Timed.simulateUntilLastEvent();
		
		createAbstractModel();
		
		Item_VirtualMachine abstractVM1 = ffc.getBins().get(0).getVM(0);
		Item_VirtualMachine abstractVM2 = ffc.getBins().get(0).getVM(1);
		Item_VirtualMachine abstractVM3 = ffc.getBins().get(1).getVM(0);
		Item_VirtualMachine abstractVM4 = ffc.getBins().get(2).getVM(0);
		
		Assert.assertEquals("The 1. VM is not matching with the abstract version of it", ffc.getBins().get(0),
				abstractVM1.gethostPM());
		Assert.assertEquals("The 2. VM is not matching with the abstract version of it", ffc.getBins().get(0),
				abstractVM2.gethostPM());
		Assert.assertEquals("The 3. VM is not matching with the abstract version of it", ffc.getBins().get(1),
				abstractVM3.gethostPM());
		Assert.assertEquals("The 4. VM is not matching with the abstract version of it", ffc.getBins().get(2),
				abstractVM4.gethostPM());
	}
	
	// This test verifies that all resources of each VM are correct


	@Test(timeout = 100)
	public void checkVMresources() throws Exception {
		
		Timed.simulateUntilLastEvent();
		
		createAbstractModel();
		
		Item_VirtualMachine abstractVM1 = ffc.getBins().get(0).getVM(0);
		Item_VirtualMachine abstractVM2 = ffc.getBins().get(0).getVM(1);
		Item_VirtualMachine abstractVM3 = ffc.getBins().get(1).getVM(0);
		Item_VirtualMachine abstractVM4 = ffc.getBins().get(2).getVM(0);
		
		
		Assert.assertEquals("The cores of the first abstract VM does not match with the real version of it", abstractVM1.getRequiredCPUs(),
				VM1.getResourceAllocation().allocated.getRequiredCPUs(), 0);
		Assert.assertEquals("The perCoreProcessingPower of the first abstract VM does not match with the real version of it",
				abstractVM1.getRequiredProcessingPower(), VM1.getResourceAllocation().allocated.getRequiredProcessingPower(), 0);
		Assert.assertEquals("The memory of the first abstract VM does not match with the real version of it", abstractVM1.getRequiredMemory(),
				VM1.getResourceAllocation().allocated.getRequiredMemory());
		
		Assert.assertEquals("The cores of the second abstract VM does not match with the real version of it", abstractVM2.getRequiredCPUs(),
				VM2.getResourceAllocation().allocated.getRequiredCPUs(), 0);
		Assert.assertEquals("The perCoreProcessingPower of the second abstract VM does not match with the real version of it", 
				abstractVM2.getRequiredProcessingPower(), VM2.getResourceAllocation().allocated.getRequiredProcessingPower(), 0);
		Assert.assertEquals("The memory of the second abstract VM does not match with the real version of it", abstractVM2.getRequiredMemory(),
				VM2.getResourceAllocation().allocated.getRequiredMemory());
		
		Assert.assertEquals("The cores of the third abstract VM does not match with the real version of it", abstractVM3.getRequiredCPUs(),
				VM3.getResourceAllocation().allocated.getRequiredCPUs(), 0);
		Assert.assertEquals("The perCoreProcessingPower of the third abstract VM does not match with the real version of it", 
				abstractVM3.getRequiredProcessingPower(), VM3.getResourceAllocation().allocated.getRequiredProcessingPower(), 0);
		Assert.assertEquals("The memory of the third abstract VM does not match with the real version of it", abstractVM3.getRequiredMemory(),
				VM3.getResourceAllocation().allocated.getRequiredMemory());
		
		Assert.assertEquals("The cores of the fourth abstract VM does not match with the real version of it", abstractVM4.getRequiredCPUs(),
				VM4.getResourceAllocation().allocated.getRequiredCPUs(), 0);
		Assert.assertEquals("The perCoreProcessingPower of the fourth abstract VM does not match with the real version of it", 
				abstractVM4.getRequiredProcessingPower(), VM4.getResourceAllocation().allocated.getRequiredProcessingPower(), 0);
		Assert.assertEquals("The memory of the fourth abstract VM does not match with the real version of it", abstractVM4.getRequiredMemory(),
				VM4.getResourceAllocation().allocated.getRequiredMemory());
	}
	
	/** 				
	 * algorithm:			Do the load of each abstract PM fit with the consumed resources?  
	 *       				Do the right MigPMs get chosen?
	 *       				Is the algorithm for migration correct and the right one used (load-dependant)?
	 *       				Is the shut down method working?
	 *      				Can a not running PM get started if necessary?
	 */
	
	
	// first PM: overloaded second PM: underloaded third PM: normal
	
	// This test verifies the correct setting of the load of each PM
	
	@Test(timeout = 100)
	public void checkAbstractPMLoad() throws Exception {
		
		Timed.simulateUntilLastEvent();
		
		createAbstractModel();
		
		Bin_PhysicalMachine first = ffc.getBins().get(0);
		Bin_PhysicalMachine second = ffc.getBins().get(1);
		Bin_PhysicalMachine third= ffc.getBins().get(2);
		
		Timed.simulateUntilLastEvent();
		
		Assert.assertEquals("The first PM has not the right State", Bin_PhysicalMachine.State.OVERLOADED_RUNNING, first.getState());
		Assert.assertEquals("The second PM has not the right State", Bin_PhysicalMachine.State.UNDERLOADED_RUNNING, second.getState());
		Assert.assertEquals("The third PM has not the right State", Bin_PhysicalMachine.State.NORMAL_RUNNING, third.getState());
		
	}
	
	// This test verifies the functionality of the getMigPM()-method
	
	@Test(timeout = 100)
	public void verifyMigPM() throws Exception {
		
		Timed.simulateUntilLastEvent();
		
		createAbstractModel();
		
		Bin_PhysicalMachine first = ffc.getBins().get(0);
		Bin_PhysicalMachine second = ffc.getBins().get(1);
		
		Item_VirtualMachine firstVM = first.getVM(0);
		
		ffc.migrateOverloadedPM(first);
		
		Assert.assertEquals("The first PM has not the right State after migration", 
				Bin_PhysicalMachine.State.NORMAL_RUNNING, first.getState());
		Assert.assertEquals("The first VM has not the right host", 
				second ,firstVM.gethostPM());
	}
	
	// This test verifies the functionality of the optimize()-method
	
	@Test(timeout = 100)
	public void verifyFFAlgorithmEasy() throws Exception {
		
		Timed.simulateUntilLastEvent();
		
		createAbstractModel();
		
		Bin_PhysicalMachine first = ffc.getBins().get(0);
		Bin_PhysicalMachine second = ffc.getBins().get(1);
		Bin_PhysicalMachine third= ffc.getBins().get(2);
		
		Item_VirtualMachine firstVM = first.getVM(0);
		Item_VirtualMachine secondVM = first.getVM(1);
		Item_VirtualMachine thirdVM = second.getVM(0);
		Item_VirtualMachine fourthVM = third.getVM(0);
		
		ffc.optimize();
		
		Assert.assertEquals("The first PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.NORMAL_RUNNING, first.getState());
		Assert.assertEquals("The second PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.NORMAL_RUNNING, second.getState());
		Assert.assertEquals("The third PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.NORMAL_RUNNING, third.getState());
		
		Assert.assertEquals("The first VM has not the right host PM after optimization", 
				second, firstVM.gethostPM());
		Assert.assertEquals("The second VM has not the right host PM after optimization", 
				first, secondVM.gethostPM());
		Assert.assertEquals("The third VM has not the right host PM after optimization", 
				second, thirdVM.gethostPM());
		Assert.assertEquals("The fourth VM has not the right host PM after optimization", 
				third, fourthVM.gethostPM());
		
	}
	

	//Method to create the abstract working model
	
	public void createComplexAbstractModel() throws Exception {
		

		PhysicalMachine testOverPM4 = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk,
				reqond, reqoffd, defaultTransitions);
		
		PhysicalMachine testUnderPM5 = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk,
				reqond, reqoffd, defaultTransitions);
		
		PhysicalMachine testNormalPM6 = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk,
				reqond, reqoffd, defaultTransitions);
		
		PhysicalMachine testUnderPM7 = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk,
				reqond, reqoffd, defaultTransitions);
		
		PhysicalMachine testUnderPM8 = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk,
				reqond, reqoffd, defaultTransitions);
		
		//save the PMs inside the register
		
		basic.registerHost(testOverPM4);
		basic.registerHost(testUnderPM5);
		basic.registerHost(testNormalPM6);
		basic.registerHost(testUnderPM7);
		basic.registerHost(testUnderPM8);
		
		 // The four VMs set the Load of PM1 to overloaded, PM2 to underloaded and PM3 to normal.
		
		
		VirtualAppliance VA5 = new VirtualAppliance("VA5", 1000, 0, false, testOverPM4.localDisk.getMaxStorageCapacity() / 20);
		VirtualAppliance VA6 = new VirtualAppliance("VA6", 1000, 0, false, testOverPM4.localDisk.getMaxStorageCapacity() / 20);
		VirtualAppliance VA7 = new VirtualAppliance("VA7", 1000, 0, false, testUnderPM5.localDisk.getMaxStorageCapacity() / 20);
		VirtualAppliance VA8 = new VirtualAppliance("VA8", 1000, 0, false, testNormalPM6.localDisk.getMaxStorageCapacity() / 20);
		VirtualAppliance VA9 = new VirtualAppliance("VA9", 1000, 0, false, testUnderPM7.localDisk.getMaxStorageCapacity() / 20);
		VirtualAppliance VA10 = new VirtualAppliance("VA10", 1000, 0, false, testUnderPM8.localDisk.getMaxStorageCapacity() / 20);
		
		VirtualMachine VM5 = new VirtualMachine(VA5);
		VirtualMachine VM6 = new VirtualMachine(VA6);
		VirtualMachine VM7 = new VirtualMachine(VA7);
		VirtualMachine VM8 = new VirtualMachine(VA8);
		VirtualMachine VM9 = new VirtualMachine(VA9);
		VirtualMachine VM10 = new VirtualMachine(VA10);
		
		//save the VAs inside the register and the PMs
		
		reqDisk.registerObject(VA5);
		reqDisk.registerObject(VA6);
		reqDisk.registerObject(VA7);
		reqDisk.registerObject(VA8);
		reqDisk.registerObject(VA9);
		reqDisk.registerObject(VA10);
		
		testOverPM4.localDisk.registerObject(VA5);
		testOverPM4.localDisk.registerObject(VA6);
		testUnderPM5.localDisk.registerObject(VA7);
		testNormalPM6.localDisk.registerObject(VA8);
		testUnderPM7.localDisk.registerObject(VA9);
		testUnderPM8.localDisk.registerObject(VA10);
		
		Timed.simulateUntilLastEvent();
		
		switchOnVM(VM1, this.bigConstraints, testOverPM1, true);
		switchOnVM(VM2, this.mediumConstraints, testOverPM1, true);
		switchOnVM(VM3, this.smallConstraints, testUnderPM2, true);
		switchOnVM(VM4, this.bigConstraints, testNormalPM3, true);
		switchOnVM(VM5, this.bigConstraints, testOverPM4, true);
		switchOnVM(VM6, this.mediumConstraints, testOverPM4, true);
		switchOnVM(VM7, this.smallConstraints, testUnderPM5, true);
		switchOnVM(VM8, this.bigConstraints, testNormalPM6, true);
		switchOnVM(VM9, this.smallConstraints, testUnderPM7, true);
		switchOnVM(VM10, this.smallConstraints, testUnderPM8, true);
		
		Timed.simulateUntilLastEvent();
		
		ffc = new FirstFitConsolidation(basic);
		
		Timed.simulateUntilLastEvent();
	}
	
	// This test ensures the functionality of the FF algorithm on a few more PMs running simultaneously
	
	//undone
	
	@Test(timeout = 100)
	public void verifyFFAlgorithm() throws Exception {
		
		Timed.simulateUntilLastEvent();
		
		this.createComplexAbstractModel();
		
		Bin_PhysicalMachine firstOverloaded = ffc.getBins().get(0);
		Bin_PhysicalMachine secondUnderloaded = ffc.getBins().get(1);
		Bin_PhysicalMachine thirdNormal = ffc.getBins().get(2);
		Bin_PhysicalMachine fourthOverloaded = ffc.getBins().get(3);
		Bin_PhysicalMachine fifthUnderloaded = ffc.getBins().get(4);
		Bin_PhysicalMachine sixthNormal = ffc.getBins().get(5);
		Bin_PhysicalMachine seventhUnderloaded = ffc.getBins().get(6);
		Bin_PhysicalMachine eighthUnderloaded = ffc.getBins().get(7);
				
		Item_VirtualMachine firstVM = firstOverloaded.getVM(0);
		Item_VirtualMachine secondVM = firstOverloaded.getVM(1);
		Item_VirtualMachine thirdVM = secondUnderloaded.getVM(0);
		Item_VirtualMachine fourthVM = thirdNormal.getVM(0);
		Item_VirtualMachine fifthVM = fourthOverloaded.getVM(0);
		Item_VirtualMachine sixthVM = fourthOverloaded.getVM(1);
		Item_VirtualMachine seventhVM = fifthUnderloaded.getVM(0);
		Item_VirtualMachine eighthVM = sixthNormal.getVM(0);
		Item_VirtualMachine ninthVM = seventhUnderloaded.getVM(0);
		Item_VirtualMachine tenthVM = eighthUnderloaded.getVM(0);
		
		
		ffc.optimize();
		
		Assert.assertNotEquals("The first PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.OVERLOADED_RUNNING, firstOverloaded.getState());
		Assert.assertNotEquals("The first PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.UNDERLOADED_RUNNING, firstOverloaded.getState());
		
		Assert.assertNotEquals("The second PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.OVERLOADED_RUNNING, secondUnderloaded.getState());
		Assert.assertNotEquals("The second PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.UNDERLOADED_RUNNING, secondUnderloaded.getState());
		
		Assert.assertNotEquals("The third PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.OVERLOADED_RUNNING, thirdNormal.getState());
		Assert.assertNotEquals("The third PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.UNDERLOADED_RUNNING, thirdNormal.getState());
		
		Assert.assertNotEquals("The fourth PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.OVERLOADED_RUNNING, fourthOverloaded.getState());
		Assert.assertNotEquals("The fourth PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.UNDERLOADED_RUNNING, fourthOverloaded.getState());
		
		Assert.assertNotEquals("The fifth PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.OVERLOADED_RUNNING, fifthUnderloaded.getState());
		Assert.assertNotEquals("The fifth PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.UNDERLOADED_RUNNING, fifthUnderloaded.getState());
		
		Assert.assertNotEquals("The sixth PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.OVERLOADED_RUNNING, sixthNormal.getState());
		Assert.assertNotEquals("The sixth PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.UNDERLOADED_RUNNING, sixthNormal.getState());
		
		Assert.assertNotEquals("The seventh PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.OVERLOADED_RUNNING, seventhUnderloaded.getState());
		Assert.assertNotEquals("The seventh PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.UNDERLOADED_RUNNING, seventhUnderloaded.getState());
		
		Assert.assertNotEquals("The eighth PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.OVERLOADED_RUNNING, eighthUnderloaded.getState());
		Assert.assertNotEquals("The eighth PM has not the right State after optimization", 
				Bin_PhysicalMachine.State.UNDERLOADED_RUNNING, eighthUnderloaded.getState());
		
		Assert.assertEquals("The first VM has not the right host PM after optimization", 
				secondUnderloaded, firstVM.gethostPM());
		Assert.assertEquals("The second VM has not the right host PM after optimization", 
				firstOverloaded, secondVM.gethostPM());
		Assert.assertEquals("The third VM has not the right host PM after optimization", 
				secondUnderloaded, thirdVM.gethostPM());
		Assert.assertEquals("The fourth VM has not the right host PM after optimization", 
				thirdNormal, fourthVM.gethostPM());
		Assert.assertEquals("The fifth VM has not the right host PM after optimization", 
				fifthUnderloaded, fifthVM.gethostPM());
		Assert.assertEquals("The sixth VM has not the right host PM after optimization", 
				fourthOverloaded, sixthVM.gethostPM());
		Assert.assertEquals("The seventh VM has not the right host PM after optimization", 
				fifthUnderloaded, seventhVM.gethostPM());
		Assert.assertEquals("The eighth VM has not the right host PM after optimization", 
				sixthNormal, eighthVM.gethostPM());
		Assert.assertEquals("The ninth VM has not the right host PM after optimization", 
				eighthUnderloaded, ninthVM.gethostPM());
		Assert.assertEquals("The tenth VM has not the right host PM after optimization", 
				eighthUnderloaded, tenthVM.gethostPM());
				
		
	}
	
	
	// This test verifies the shut down of empty PMs
	@Test(timeout = 100)
	public void checkShutdowns() throws Exception {
		
		
		PhysicalMachine emptyPM = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk,
				reqond, reqoffd, defaultTransitions);
		basic.registerHost(emptyPM);
		Timed.simulateUntilLastEvent();
		
		createAbstractModel();
		
		Bin_PhysicalMachine empty= ffc.getBins().get(3);
		
		ffc.shutEmptyPMsDown();
		
		Assert.assertEquals("The empty PM is not shut down", State.EMPTY_OFF, empty.getState());
	}
	
	// This test verifies the starting of a shut down PM if a migration is not possible on the running PMs
	
	//undone and not possible
	@Test(timeout = 100)
	public void checkStartingShutDownPM() throws Exception {
		
		PhysicalMachine emptyPM = new PhysicalMachine(reqcores, reqProcessing, reqmem, reqDisk,
				reqond, reqoffd, defaultTransitions);
		
		basic.registerHost(emptyPM);
		
		Timed.simulateUntilLastEvent();
		emptyPM.switchoff(testOverPM1);	//not possible on alwayson machines
		//Assert.assertEquals("The empty PM is not shut down correctly", PhysicalMachine.State.OFF, emptyPM.getState());
		Timed.simulateUntilLastEvent();
		
		createAbstractModel();
		
		Bin_PhysicalMachine first = ffc.getBins().get(0);
		Bin_PhysicalMachine second = ffc.getBins().get(1);
		Bin_PhysicalMachine third= ffc.getBins().get(2);
		Bin_PhysicalMachine empty= ffc.getBins().get(3);
		
	}
	 
	 
	/**  				
	 * graph: 				Do actions exist?
	 * 		  				Are the actions bound to their order?
	 * 		  				Is the selection out of the algorithm correkt?
	 * 		  				Are the changes done inside the simulator?
	 */
	
	// This test verifies, that every node can be constructed
	
	@Test(timeout = 100)
	public void checkNodes() {
		
	}
	
	// This test verifies the observance of a given order 
	
	@Test(timeout = 100)
	public void verfiyOrder() {
		
	}
	
	// This test verifies the correct creation of the graph out of the FirstFitConsolidation
	
	@Test(timeout = 100)
	public void verifyGraphCreation() {
		
	}
	
	// This test verfies the functionality of the graph-functions
	
	@Test(timeout = 100)
	public void checkGraphFunctions() {
		
	}
	
	// This test verifies everything together, which means, that out of a given situation every previous step is taken and
	// at the end the necassary changes are made inside the simulator
	
	@Test(timeout = 100)
	public void verifyFullFunctionality() {
		
	}
}
