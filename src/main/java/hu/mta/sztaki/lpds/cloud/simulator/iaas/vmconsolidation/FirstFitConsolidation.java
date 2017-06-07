package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.Collection;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.Bin_PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

	/**
	 * @author Rene
	 *
	 * This class is used to do the consolidation with first fit. It can be easy used in the consolidator by simply creating
	 * an instance of it.
	 */


public class FirstFitConsolidation {
	
	
	public FirstFitConsolidation(ArrayList <PhysicalMachine> bins, ArrayList <Node> actions, int i) {
		
		this.bins = bins;
		this.actions = actions;
		count = i;
	}

	ArrayList <PhysicalMachine> bins = new ArrayList <PhysicalMachine>();
	ArrayList <Node> actions;
	int count;
	
	
	/**
	 * Getter for every important and needed variable and list.
	 * @return
	 * 			bins, actions and count
	 */
	
	ArrayList <PhysicalMachine> getBins() {
		
		return bins;
	}
	
	ArrayList <Node> getActions() {
		
		return actions;
	}
	
	int getCount() {
		
		return count;
	}
	
	
	
	/**
	 * This is the algorithm for migration, which uses the First-Fit-Algorithm to place VMs on other PMs
	 * if the status is an other than 'normal'.
	 * @param arr
	 * 			The loadbalance array to check the status of the PMs.
	 * ruft Methode zum Start einer weiteren PM auf, wenn auf aktuell laufenden nicht genügend Kapazitäten frei sind
	 */
	
	void migrationAlgorithm() {
		
		State [] arr = checkLoad();
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
					arr = checkLoad();
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
	
	public void shutEmptyPMsDown(State [] arr) {
		
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
	}
	
	/**
	 * In this method the status of each PM in the simulation is considered.
	 * To do so, the methods 'underloaded' and 'overloaded' are used, which 
	 * check if the used resources are more or less than the defined threshold for
	 * overloaded / underloaded. 
	 * At the end an State array is returned which has the status 'overloaded', 
	 * 'underloaded' and 'normal' at the position of the PMs in the bins-array, so
	 * the two arrays are matching.
	 *  @return a list with the status for every PM
	 */
	
	protected State[] checkLoad() {
		State checklist [] = new State[bins.size()];
		for(int i = 0; i < bins.size(); i++) {
			if(this.underloaded(bins.get(i)))  {
				checklist[i] = State.underloaded;
			}
			else {
				if(this.overloaded(bins.get(i))) {
					checklist[i] = State.overloaded;
				}
				else
					checklist[i] = State.normal;
			}
		}
		return checklist;
	}
	
	/**
	 * Method for checking if the actual PM is overloaded.
	 * @param pm
	 * 			The PhysicalMachine which shall be checked.
	 * @return true if overloaded, false otherwise
	 */
	
	private boolean overloaded(PhysicalMachine pm) {
		ResourceConstraints all = pm.getCapacities();
		ResourceConstraints available = pm.availableCapacities;
		if(all.getTotalProcessingPower() - available.getTotalProcessingPower() >= all.getTotalProcessingPower() * 0.75) {
			return true;
		}
		else
			return false;
	}
	
	/**
	 * Method for checking if the actual PM is underloaded.
	 * @param pm
	 * 			The PhysicalMachine which shall be checked.
	 * @return true if underloaded, false otherwise	  
	 */
	
	private boolean underloaded(PhysicalMachine pm) {
		ResourceConstraints all = pm.getCapacities();
		ResourceConstraints available = pm.availableCapacities;
		if(all.getTotalProcessingPower() - available.getTotalProcessingPower() <= all.getTotalProcessingPower() * 0.25) {
			return true;
		}
		else
			return false;
	}
	
	
}
