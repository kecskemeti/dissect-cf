package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.Collection;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

	/**
	 * @author Julian, René
	 * 
	 * This class manages the Consolidation of VMs. At the moment a simple first-fit algorithm for migration will be
	 * implemented, but after this we will change this class to use other more complex algorithms for consolidation.
	 */

public class Optimizer extends VMConsolidation_base {
	
	public Optimizer(IaaSService parent) throws Exception {
		super(parent);
	}
		
	
	//das splitten muss noch geklärt werden
	FirstFitConsolidation ffc = new FirstFitConsolidation(bins);
	
	
	/**
	 * Functionality of this optimization:
	 * 
	 * Step 1: Check the threshold of all PMs. A PM is underloaded, if its used resources are lower than 25 %
	 * 		   of the totalProcessingPower and it is overloaded, if its used resources are higher than 75 %
	 * 		   of the totalProcessingPower.
	 * Step 2: Call the migration algorithm and do the migrations. Concurrently the graph shall be created (Step 3), which contain 
	 * 		   the changes made during this step in order to do the migration inside the real simulation, too.
	 * Step 3: Create a graph and fill it with the changes that had been done.
	 */

	public void optimize() {
		migrationAlgorithm();
		createGraph(actions);
		
		//erst muss die pm klasse fertig werden
		//ffc.shutEmptyPMsDown(super.checkLoad());
	}
	
	/**
	 * This is the algorithm for migration, which uses the First-Fit-Algorithm to place VMs on other PMs
	 * if the status is an other than 'normal'.
	 * @param arr
	 * 			The loadbalance array to check the status of the PMs.
	 * ruft Methode zum Start einer weiteren PM auf, wenn auf aktuell laufenden nicht genügend Kapazitäten frei sind
	 */
	
	private void migrationAlgorithm() {
		
		State [] arr = super.checkLoad();
		for(int i = 0; i < arr.length; i++) {
			if(arr[i] == State.normal) {
				
			}
			else {
				while(arr[i] == State.underloaded || arr[i] ==  State.overloaded) {
					
					if(bins.get(i).isHostingVMs() != true) {
						arr[i] = State.empty;
					}
					else {
						PhysicalMachine actual = bins.get(i);
						VirtualMachine x = this.getbiggestVM(i);
						PhysicalMachine y = this.getMigPm(x, arr);
						if(y == null){
							y = startPMs(x.getPerTickProcessingPower());						 
						}
						
						try {
							actual.migrateVM(x, y);
							actions.add(new MigrateVMNode(count++, actual, y, x));
						} catch (VMManagementException e) {
							e.printStackTrace();
						} catch (NetworkException e) {
							e.printStackTrace();
						}
					}
					arr = super.checkLoad();
				}
			}
		}
	}

	/**
	 * This method is written to get a PM where a given VM can be migrated without changing the
	 * status of the PM to 'overloaded'. This is done by first fit.
	 * 
	 * Method not finished! HashMap has to be used.
	 * @param VM
	 * 			The VM which shall be migrated.
	 * @param arr
	 * 			The loadbalance array to check the status of the PMs.
	 * @return A PM where the given VM can be migrated
	 * gibt null zurück, wenn keine aktive PM mit ausreichend Kapazitäten bereit steht
	 */
	
	private PhysicalMachine getMigPm(VirtualMachine VM, State[] arr) {
		
		VirtualMachine toMig = VM;
		
		//These are the constraints of the VM
		ResourceConstraints toMigCons = toMig.getResourceAllocation().allocated;
		double toMigCons_reqCPU = toMigCons.getRequiredCPUs();
		double toMigCons_reqPP = toMigCons.getRequiredProcessingPower();
		double toMigCons_reqMem = toMigCons.getRequiredMemory();
		
		//now we have to search for a fitting pm
		for(int i = 0; i < bins.size(); i++) {
			
			PhysicalMachine actualPM = this.bins.get(i);
			
			//These are the constraints of the actual PM
			ResourceConstraints actualPMcons = actualPM.getCapacities();
			double PMcons_reqCPU = actualPMcons.getRequiredCPUs();
			double PMcons_reqPP = actualPMcons.getRequiredProcessingPower();
			double PMcons_reqMem = actualPMcons.getRequiredMemory();
			
			if(PMcons_reqCPU > toMigCons_reqCPU && PMcons_reqPP > toMigCons_reqPP && PMcons_reqMem > toMigCons_reqMem) {
				return actualPM;
			}
		}	
		return startPMs(toMigCons.getTotalProcessingPower());	//get new PM
	}
	
	
	
	/*
	 * startet eine zuvor ausgeschaltet PM neu. Dabei wird anhand des übergebenen Parameters geprüft, ob die
	 * zu startende PM ausreichend Kapazitäten bietet
	 */
	private PhysicalMachine startPMs(double d){
		PhysicalMachine start = null;
		for(int i = 0; i < bins.size(); i++){
			if(!bins.get(i).isRunning() && bins.get(i).getCapacities().getTotalProcessingPower() < d){
				bins.get(i).turnon();
				start = bins.get(i);
				actions.add(new StartNode(count++, bins.get(i)));
			}
		}
		return start;	 
	}
	
	
	/**
	 * This method is created to find the VM with the biggest consumption on a PM. 
	 * @param x
	 * 			The Variable which shows, which PM has to be used.
	 * @return VM with the biggest consumption
	 */
	
	private VirtualMachine getbiggestVM(int x) {
		PhysicalMachine pm = bins.get(x);
		Collection <VirtualMachine> vms = pm.listVMs();		
		ArrayList <VirtualMachine> list = new ArrayList<VirtualMachine>(vms);
		VirtualMachine biggest = list.get(0);
		
		for(int i = 0; i < vms.size(); i++) {
			
			VirtualMachine bigVM = list.get(i);
			if(bigVM.getPerTickProcessingPower() > biggest.getPerTickProcessingPower()) {
				biggest = bigVM;
			}
		}
		return biggest;
	}
	
	/**
	 * This method has to be called after all migrations were done. The given array
	 * contains all information about which PMs have to be shut down and here
	 * this will be done.
	 * @param arr
	 * 			the balance array, gives necessary information which PMS have to get shut down
	 */
	
/*	private void shutEmptyPMsDown(State [] arr) {
		
		for(int i = 0; i < arr.length; i++) {
			if(arr[i] == State.empty) {
				PhysicalMachine pm = bins.get(i);
				try {
					pm.switchoff(bins.get(0));
				} catch (VMManagementException e) {
					e.printStackTrace();
				} catch (NetworkException e) {
					e.printStackTrace();
				}
			}
		}
	} */
	
	
	
	
	
	
	
	/**
	 * The graph which does the changes.
	 */

	public void createGraph(ArrayList<Node> actions) {
		for(int i = 0; i < actions.size(); i++) {
			
		}
	}
}

