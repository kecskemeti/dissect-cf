package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.Bin_PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

	/**
	 * @author Rene
	 *
	 * This class is used to do the consolidation with first fit. It can be easy used in the consolidator by simply creating
	 * an instance of it. The migration, PM-selection for migration and every other method according to this 
	 * is implemented as first fit.
	 */


public class FirstFitConsolidation extends Consolidator {
	
	ArrayList <Bin_PhysicalMachine> bins = new ArrayList <Bin_PhysicalMachine>();
	ArrayList <Action> actions;	// The ArrayList for doing the changes inside the simulator
	int count = 1;	// Counter for the graph actions
	
	/**
	 * The constructor for the First-Fit-Consolidator.
	 * @param parent
	 * 			The IaaSService of the superclass Consolidator
	 */
	
	public FirstFitConsolidation(IaaSService parent) throws Exception {
		super(parent);
	}	
	
	public void stateChanged(VirtualMachine vm, hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State oldState, 
			hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State newState) {
		super.stateChanged(vm, oldState, newState);
	}
	
	public void stateChanged(PhysicalMachine pm, hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State oldState,
			hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State newState) {
		super.stateChanged(pm, oldState, newState);
	}
	
	/** Getter for bins-list.
	 * @return bins
	 */
	
	public ArrayList <Bin_PhysicalMachine> getBins() {
		return bins;
	}
	
	/** Getter for the action-list.
	 * @return actions
	 */
	
	public ArrayList <Action> getActions() {
		return actions;
	}	
	
	/** Functionality of this optimization:
	 * 
	 * Step 1: Check the threshold of all PMs. A PM is underloaded, if its used resources are lower than 25 %
	 * 		   of the totalProcessingPower and it is overloaded, if its used resources are higher than 75 %
	 * 		   of the totalProcessingPower.
	 * Step 2: Call the migration algorithm and do the migrations. Concurrently the graph shall be created (Step 3), which contain 
	 * 		   the changes made during this step in order to do the migration inside the real simulation, too.
	 * Step 3: Create a graph and fill it with the changes that had been done.
	 * @throws NetworkException 
	 * @throws VMManagementException 
	 */
	
	public void optimize() throws VMManagementException, NetworkException {
		
		bins = super.instantiate();
		
		for(int i = 0; i < bins.size(); i++) {
			
			if(bins.get(i).getState() == State.NORMAL_RUNNING) {
				
			}
			else {
				
				while(bins.get(i).getState() == State.UNDERLOADED_RUNNING) {
					
					if(bins.get(i).isHostingVMs() != true) {
						bins.get(i).changeState(State.EMPTY_RUNNING);
					}
					else {
						migrateUnderloadedPM(bins.get(i));
					}
				}
				
				while(bins.get(i).getState() ==  State.OVERLOADED_RUNNING) {
					migrateOverloadedPM(bins.get(i));
				}
			}
		}
		
		super.createGraph(getActions());
		super.performActions();
	}

	/**
	 * This method is written to get a PM where a given VM can be migrated without changing the
	 * status of the PM to 'overloaded'. This is done by first fit.
	 * 
	 * @param VM
	 * 			The VM which shall be migrated.
	 * @return A PM where the given VM can be migrated
	 * 		   starts a new PM if there is no one with needed resources.
	 */
	
	private Bin_PhysicalMachine getMigPm(Item_VirtualMachine VM) {
		
		Item_VirtualMachine toMig = VM;
		
		//These are the constraints of the VM
		double toMigCons_reqCPU = toMig.getRequiredCPUs();
		double toMigCons_reqPP = toMig.getRequiredProcessingPower();
		double toMigCons_reqMem = toMig.getRequiredMemory();
		
		//now we have to search for a fitting pm
		for(int i = 0; i < bins.size(); i++) {
			
			Bin_PhysicalMachine actualPM = this.bins.get(i);
			
			//These are the constraints of the actual PM
			double PMcons_reqCPU = actualPM.getRequiredCPUs();
			double PMcons_reqPP = actualPM.getRequiredProcessingPower();
			double PMcons_reqMem = actualPM.getRequiredMemory();
			
			if(PMcons_reqCPU > toMigCons_reqCPU && PMcons_reqPP > toMigCons_reqPP && PMcons_reqMem > toMigCons_reqMem) {
				//the PM which first fits to the criteria
				return actualPM;
			}
		}	
		return startPMs(toMig.getResources());	//get new PM
	}
	
	
	
	/**
	 * Starts a PM which contains the necassary resources for hosting the previous VM.
	 * This is done by first-fit.
	 * 
	 * @param cores
	 * 			The needed cores.
	 * @param power
	 * 			The needed processing power.
	 * @param mem
	 * 			The needed memory.
	 * 
	 * @return A PM with the needed resources.
	 */
	
	private Bin_PhysicalMachine startPMs(ResourceVector second){
		Bin_PhysicalMachine start = null;
		for(int i = 0; i < bins.size(); i++){
			
			if(start != null)
				return start;
			
			if(bins.get(i).getState().equals(State.EMPTY_OFF) && bins.get(i).getTotalResources().isGreater(second)){
				bins.get(i).switchOn();
				start = bins.get(i);
				actions.add(new Action(count++, bins.get(i), null, null, null, null));
			}
		}
		return start;	 //keine PM kann gestartet werden
	}
	
	
	/**
	 * This method is created to find the VM with the biggest consumption on a PM. 
	 * @param x
	 * 			The Variable which shows, which PM has to be used.
	 * @return VM with the biggest consumption
	 */
	
	private Item_VirtualMachine getFirstVM(Bin_PhysicalMachine x) {
		return x.getVMs().get(0);
	}
	
	/**
	 * This method has to be called after all migrations were done. The given array
	 * contains all information about which PMs have to be shut down and here
	 * this will be done.
	 * 
	 * @param arr
	 * 			the balance array, gives necessary information which PMS have to get shut down
	 */
	
	public void shutEmptyPMsDown() {
		
		for(int i = 0; i < bins.size(); i++) {
			if(bins.get(i).equals(State.EMPTY_RUNNING) ) {
				bins.get(i).switchOff();
				actions.add(new Action(count++, null, null, null, null, bins.get(i)));
			}
		}
	}
	
	/** Method for migrating overloaded PMs.
	 * @return The ArrayList with all necassary migrations.
	 */
	
	public void migrateOverloadedPM(Bin_PhysicalMachine source) {
		
		while(source.getState().equals(State.OVERLOADED_RUNNING)) {
			
			//jetzt immer eine vm mit ff raus nehmen und auf die ff pm migrieren.

			Item_VirtualMachine actual = getFirstVM(source);
			Bin_PhysicalMachine pm = getMigPm(actual);
			
			
			if(pm == null) {
				return; // keine Migration mehr m�glich
			}
			else {
				actual.gethostPM().migrateVM(actual, pm);
				actions.add(new Action(count++, null, source, pm, actual, null)); 
			}
			
			//dabei immer pr�fen, ob der status normal erreicht worden ist
			
			source.checkLoad();
			
		}
	}
	
	/** Method for migrating underloaded PMs.
	 * @return The ArrayList with all necassary migrations.
	 */
	
	public void migrateUnderloadedPM(Bin_PhysicalMachine source) {
		
		int i = 1;
		
		while(source.getState().equals(State.UNDERLOADED_RUNNING)) {
			ArrayList <Item_VirtualMachine> migVMs = new ArrayList <Item_VirtualMachine>();
			
			//jetzt immer eine vm mit ff raus nehmen und auf die ff pm migrieren.

			Item_VirtualMachine actual = getFirstVM(source);
			Bin_PhysicalMachine pm = getMigPm(actual); 
			if(pm == null) {
				
				if(migVMs.isEmpty()) {
					return; // keine Migration m�glich, es wurde au�erdem auch noch keine durchgef�hrt
				}
				else {
					for(int x = i ; x > 0; x--) {
						
						Item_VirtualMachine demig = migVMs.get(x);
						demig.gethostPM().migrateVM(demig, source);
						//ToDo : knoten vom migrieren entfernen
					}
				} 
			}
			else {
				migVMs.add(actual);
				i++;
				actual.gethostPM().migrateVM(actual, pm);
				actions.add(new Action(count++, null, source, pm, actual, null)); 
			}
			
			//dabei immer pr�fen, ob der status normal erreicht worden ist
			
			source.checkLoad();
		}
	}
}