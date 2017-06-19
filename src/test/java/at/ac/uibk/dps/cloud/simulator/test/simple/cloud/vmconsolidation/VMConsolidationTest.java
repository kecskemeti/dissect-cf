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
	
	final static int reqcores = 8, reqProcessing = 3, reqmem = 16,
			reqond = 2 * (int) aSecond, reqoffd = (int) aSecond;
	
	
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
	
	//The different ResourceConstraints to get an other load for each PM
	
	final ResourceConstraints smallConstraints = new ConstantConstraints(
			reqcores / 8, reqProcessing / 8, reqmem / 8);
	
	final ResourceConstraints mediumConstraints = new ConstantConstraints(reqcores / 3, reqProcessing / 3, reqmem / 3);
	
	final ResourceConstraints bigConstraints = new ConstantConstraints(reqcores / 2, reqProcessing / 2, reqmem / 2);
	
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
		
		basic.registerHost(testOverPM1);
		basic.registerHost(testUnderPM2);
		basic.registerHost(testNormalPM3);
		basic.registerRepository(reqDisk);
		
		
		 // The four VMs set the Load of PM1 to overloaded, PM2 to underloaded and PM3 to normal.
		
		
		VA1 = new VirtualAppliance("VA1", 1000, 0, false, testOverPM1.localDisk.getMaxStorageCapacity() / 10);
		VA2 = new VirtualAppliance("VA2", 1000, 0, false, testOverPM1.localDisk.getMaxStorageCapacity() / 10);
		VA3 = new VirtualAppliance("VA3", 1000, 0, false, testUnderPM2.localDisk.getMaxStorageCapacity() / 10);
		VA4 = new VirtualAppliance("VA4", 1000, 0, false, testNormalPM3.localDisk.getMaxStorageCapacity() / 10);
		
		
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
	
	//Edit: Jetzt starten alle, aber sind noch im start up und nicht im running
	
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
		Assert.assertEquals("The 3. VM is not matching with the abstract version of it", ffc.getBins().get(0),
				abstractVM3.gethostPM());
		Assert.assertEquals("The 4. VM is not matching with the abstract version of it", ffc.getBins().get(1),
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
		
		
		Assert.assertSame("The cores of the first abstract VM does not match with the real version of it", abstractVM1.getRequiredCPUs(),
				VM1.getResourceAllocation().allocated.getRequiredCPUs());
		Assert.assertSame("The perCoreProcessingPower of the first abstract VM does not match with the real version of it",
				abstractVM1.getRequiredProcessingPower(), VM1.getResourceAllocation().allocated.getRequiredProcessingPower());
		Assert.assertEquals("The memory of the first abstract VM does not match with the real version of it", abstractVM1.getRequiredMemory(),
				VM1.getResourceAllocation().allocated.getRequiredMemory());
		
		Assert.assertSame("The cores of the second abstract VM does not match with the real version of it", abstractVM2.getRequiredCPUs(),
				VM2.getResourceAllocation().allocated.getRequiredCPUs());
		Assert.assertSame("The perCoreProcessingPower of the second abstract VM does not match with the real version of it", 
				abstractVM2.getRequiredProcessingPower(), VM2.getResourceAllocation().allocated.getRequiredProcessingPower());
		Assert.assertEquals("The memory of the second abstract VM does not match with the real version of it", abstractVM2.getRequiredMemory(),
				VM2.getResourceAllocation().allocated.getRequiredMemory());
		
		Assert.assertSame("The cores of the third abstract VM does not match with the real version of it", abstractVM3.getRequiredCPUs(),
				VM3.getResourceAllocation().allocated.getRequiredCPUs());
		Assert.assertSame("The perCoreProcessingPower of the third abstract VM does not match with the real version of it", 
				abstractVM3.getRequiredProcessingPower(), VM3.getResourceAllocation().allocated.getRequiredProcessingPower());
		Assert.assertEquals("The memory of the third abstract VM does not match with the real version of it", abstractVM3.getRequiredMemory(),
				VM3.getResourceAllocation().allocated.getRequiredMemory());
		
		Assert.assertSame("The cores of the fourth abstract VM does not match with the real version of it", abstractVM4.getRequiredCPUs(),
				VM4.getResourceAllocation().allocated.getRequiredCPUs());
		Assert.assertSame("The perCoreProcessingPower of the fourth abstract VM does not match with the real version of it", 
				abstractVM4.getRequiredProcessingPower(), VM4.getResourceAllocation().allocated.getRequiredProcessingPower());
		Assert.assertEquals("The memory of the fourth abstract VM does not match with the real version of it", abstractVM4.getRequiredMemory(),
				VM4.getResourceAllocation().allocated.getRequiredMemory());
	}
	
	/** 				
	 * algorithm:			Do the load of each abstract PM fit with the consumed resources?  
	 *       				Do the right MigPMs get chosen?
	 *       				Is the algorithm for migration correct and the right one used (load-dependant)?
	 *       				Is the shut down method working?
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
		
		Assert.assertEquals("The first PM has not the right State", Bin_PhysicalMachine.State.OVERLOADED_RUNNING, first.getState());
		Assert.assertEquals("The second PM has not the right State", Bin_PhysicalMachine.State.UNDERLOADED_RUNNING, second.getState());
		Assert.assertEquals("The third PM has not the right State", Bin_PhysicalMachine.State.NORMAL_RUNNING, third.getState());
		
	}
	
	// This test verifies the functionality of the getMigPM()-method
	
	@Test(timeout = 100)
	public void verifyMigPM() {
		
	}
	
	// This test verifies the functionality of the optimize()-method
	
	@Test(timeout = 100)
	public void verifyFFAlgorithm() {
		
	}
	
	// This test verifies the shut down of empty PMs
	
	@Test(timeout = 100)
	public void checkShutdowns() {
		
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
