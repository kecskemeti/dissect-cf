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
	
	int count = 1;	// Counter for the graph actions
	
	/**
	 * The constructor for the First-Fit-Consolidator. It uses the PM-ArrayList out of the
	 * superclass Consolidator.
	 * @param parent
	 * 			The IaaSService of the superclass Consolidator.
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
	
	/** 
	 * @return bins
	 */
	public ArrayList <Bin_PhysicalMachine> getBins() {
		return bins;
	}
	
	/** 
	 * @return actions
	 */
	public ArrayList <Action> getActions() {
		return actions;
	}	
	
	/** 
	 * Functionality of this optimization:
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
		
		while(isOverloaded() || isUnderloaded()) {
			for(Bin_PhysicalMachine pm : this.getBins()) {
				State state = pm.getState();
				
				if(state == State.NORMAL_RUNNING || state == State.UNCHANGEABLE_OVERLOADED || state == State.UNCHANGEABLE_UNDERLOADED) {
					
				}
				else {
					
					if(state == State.UNDERLOADED_RUNNING || state == State.STILL_UNDERLOADED) {
						
						if(pm.isHostingVMs() != true) {
							pm.changeState(State.EMPTY_RUNNING);
						}
						else {
							migrateUnderloadedPM(pm);
						}
					}
					else {
						if(state ==  State.OVERLOADED_RUNNING || state == State.STILL_OVERLOADED) {
							migrateOverloadedPM(pm);
						}
					}
				}
			}
		}
		
		shutEmptyPMsDown();		//at the end all empty PMs have to be shut down
		
		super.createGraph(getActions());		//creates the graph with all previously done actions
		super.performActions();				//do the changes inside the simulator
	}
	
	/**
	 * Identifies PMs of the bins-ArrayList with the State OVERLOADED_RUNNING and STILL_OVERLOADED.
	 * @return true, if there is a overloaded PM.
	 */
	private boolean isOverloaded() {
		
		boolean x = false;
		for(int i = 0; i < getBins().size(); i++) {
			if(getBins().get(i).getState().equals(State.OVERLOADED_RUNNING) || getBins().get(i).getState().equals(State.STILL_OVERLOADED) ) {
				x = true;
				return true;
			}
		}
		return x;
	}
	
	/**
	 * Identifies PMs of the bins-ArrayList with the State UNDERLOADED_RUNNING and STILL_UNDERLOADED.
	 * @return true, if there is a underloaded PM.
	 */
	private boolean isUnderloaded() {
		
		boolean x = false;
		for(int i = 0; i < getBins().size(); i++) {
			if(getBins().get(i).getState().equals(State.UNDERLOADED_RUNNING) || getBins().get(i).getState().equals(State.STILL_UNDERLOADED) ) {
				x = true;
				return true;
			}
		}
		return x;
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
	private Bin_PhysicalMachine getMigPm(Item_VirtualMachine vm) {
		
		Item_VirtualMachine toMig = vm;
		
		//These are the constraints of the VM
		ResourceVector vmRes = toMig.getResources();
		
		//now we have to search for a fitting pm
		for(int i = 0; i < bins.size(); i++) {
			
			if(bins.get(i) == vm.gethostPM()) {
				
			}
			else {
				Bin_PhysicalMachine actualPM = this.bins.get(i);
				
				//These are the constraints of the actual PM
				ResourceVector pmRes = actualPM.getAvailableResources();
				
				if(pmRes.isGreater(vmRes)) {
					
					actualPM.consumeResources(toMig);
					actualPM.checkLoad();
					if(actualPM.getState().equals(State.OVERLOADED_RUNNING)) {
						actualPM.deconsumeResources(toMig);
					}
					else {
						//the PM which first fits to the criteria
						return actualPM;
					}
				}
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
				actions.add(new StartAction(count++, bins.get(i)));	//give the information to the graph
			}
		}
		return start;	 //no PM can be started
	}
	
	
	/**
	 * This method is created to get the first VM on a PM. 
	 * @param x
	 * 			The Variable which shows, which PM has to be used.
	 * @return first VM on a PM
	 */
	private Item_VirtualMachine getFirstVM(Bin_PhysicalMachine x) {
		return x.getVMs().get(0);
	}
	
	/**
	 * This method has to be called after all migrations were done. It is checked which
	 * PMs do not have any VMs hosted and then this method shut them down. A node is created
	 * to add this information to the graph.
	 */
	public void shutEmptyPMsDown() {
		
		for(int i = 0; i < bins.size(); i++) {
			if(!bins.get(i).isHostingVMs() && bins.get(i).getState() != State.EMPTY_OFF) {
				bins.get(i).switchOff();
				actions.add(new ShutDownAction(count++, bins.get(i)));	//give the information to the graph
			}
		}
	}
	
	/** 
	 * Method for migrating overloaded PMs.
	 */
	public void migrateOverloadedPM(Bin_PhysicalMachine source) {
		
		State state = source.getState();
		
		while(state.equals(State.OVERLOADED_RUNNING) || state.equals(State.STILL_OVERLOADED)) {
			if(source.getVMs().isEmpty()) {
				source.checkLoad();
				return;
			}
						
			Item_VirtualMachine actual = getFirstVM(source);	//now taking the first VM on this PM and try to migrate it to a target
			Bin_PhysicalMachine pm = getMigPm(actual);
			
			if(pm == null) {
				if(state.equals(State.OVERLOADED_RUNNING)) {					
					source.changeState(State.STILL_OVERLOADED);
					return; // no migration possible anymore
				}
				else {					
					source.changeState(State.UNCHANGEABLE_OVERLOADED);
					return; // no migration possible anymore, second try
				}
			}			
			else {
				actual.gethostPM().migrateVM(actual, pm);
				actions.add(new MigrationAction(count++, source, pm, actual)); 	//give the information to the graph
			}
			
			source.checkLoad();		//check if the state has changed
			state = source.getState();		//set the actual State
		}
	}
	
	/** 
	 * Method for migrating underloaded PMs.
	 */
	
	public void migrateUnderloadedPM(Bin_PhysicalMachine source) {
		
		int i = 1;
		State state = source.getState();
		
		while(state.equals(State.UNDERLOADED_RUNNING) || state.equals(State.STILL_UNDERLOADED)) {
			if(source.getVMs().isEmpty()) {
				source.checkLoad();
				return;
			}
			ArrayList <Item_VirtualMachine> migVMs = new ArrayList <Item_VirtualMachine>();
			Item_VirtualMachine actual = getFirstVM(source);	//now taking the first VM on this PM and try to migrate it to a target
			Bin_PhysicalMachine pm = getMigPm(actual); 
			
			if(pm == null) {
				
				if(migVMs.isEmpty()) {
					if(state.equals(State.UNDERLOADED_RUNNING)) {
						source.changeState(State.STILL_UNDERLOADED);
						return; // no migration possible and no has been done previously
					}
					else if(state.equals(State.STILL_UNDERLOADED)) {
						source.changeState(State.UNCHANGEABLE_UNDERLOADED);
						return; // no migration possible and no has been done previously, second try
					}
				}
				else {
					for(int x = i ; x > 0; x--) {
						
						Item_VirtualMachine demig = migVMs.get(x);
						demig.gethostPM().migrateVM(demig, source);
						
						//ToDo : knoten vom migrieren entfernen
						
						if(state.equals(State.UNDERLOADED_RUNNING)) {
							source.changeState(State.STILL_UNDERLOADED);
						}
						else {
							source.changeState(State.UNCHANGEABLE_UNDERLOADED);
						}
					}
				} 
			}
			else {
				migVMs.add(actual);
				i++;
				actual.gethostPM().migrateVM(actual, pm);
				actions.add(new MigrationAction(count++, source, pm, actual)); 	//give the information to the graph
			}
			
			source.checkLoad();		//check if the state has changed
			state = source.getState();		//set the actual State
		}
	}
}