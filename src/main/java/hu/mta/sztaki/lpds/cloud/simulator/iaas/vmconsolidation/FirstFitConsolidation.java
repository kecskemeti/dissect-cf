package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.ModelPM.State;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

	/**
	 * @author René Ponto
	 *
	 * This class is used to do the consolidation with first fit. It can be easy used as a consolidator by simply creating
	 * an instance of it. The migration, PM-selection for migration and every other method according to this 
	 * is implemented as first fit.
	 */


public class FirstFitConsolidation extends ModelBasedConsolidator {
	
	int count = 1;	// Counter for the graph actions
	
	/**
	 * The constructor for the First-Fit-Consolidator. It uses the PM-ArrayList out of the
	 * superclass Consolidator.
	 * @param parent
	 * 			The IaaSService of the superclass Consolidator.
	 */
	
	public FirstFitConsolidation(IaaSService parent, double upperThreshold, double lowerThreshold, long consFreq) throws Exception {
		super(parent, consFreq);
		for(int i = 0; i < bins.size(); i++) {
			bins.get(i).setThreshold(upperThreshold, lowerThreshold);
		}
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
	 * Functionality of this optimization:
	 * 
	 * Step 1: Check the threshold of all PMs. A PM is underAllocated, if its used resources are lower than 25 %
	 * 		   of the cores, perCoreProcessingPower and the memory and it is overAllocated, if its used resources are higher than 75 %
	 * 		   of the cores, perCoreProcessingPower or the memory.
	 * Step 2: Call the migration algorithm and do the migrations. Concurrently the graph shall be created (Step 3), which contain 
	 * 		   the changes made during this step in order to do the migration inside the real simulation, too.
	 * Step 3: Create a graph and fill it with the changes that had been done.
	 */
	public void optimize() {
		
		while(isOverAllocated() || isUnderAllocated()) {
			for(ModelPM pm : this.getBins()) {
				State state = pm.getState();
				
				if(state == State.NORMAL_RUNNING || state == State.UNCHANGEABLE_OVERALLOCATED || state == State.UNCHANGEABLE_UNDERALLOCATED) {
					
				}
				else {
					
					if(state == State.UNDERALLOCATED_RUNNING || state == State.STILL_UNDERALLOCATED) {
						
						if(pm.isHostingVMs() != true) {
							pm.changeState(State.EMPTY_RUNNING);
						}
						else {
							migrateUnderAllocatedPM(pm);
						}
					}
					else {
						if(state ==  State.OVERALLOCATED_RUNNING || state == State.STILL_OVERALLOCATED) {
							migrateOverAllocatedPM(pm);
						}
					}
				}
			}
		}
		
		for(ModelPM pm : this.getBins()) {
			ArrayList<ModelVM> allVMsOnPM = pm.getVMs();
			
			Set<ModelVM> setItems = new LinkedHashSet<ModelVM>(allVMsOnPM);
			allVMsOnPM.clear();
			allVMsOnPM.addAll(setItems);
		}
		
		shutEmptyPMsDown();		//at the end all empty PMs have to be shut down
		
		super.createGraph(getActions());		//creates the graph with all previously done actions
		try {
			super.performActions();				//do the changes inside the simulator
		} catch (VMManagementException e) {
			e.printStackTrace();
		} catch (NetworkException e) {
			e.printStackTrace();
		}				
	}
	
	/**
	 * Identifies PMs of the bins-ArrayList with the State OVERALLOCATED_RUNNING and STILL_OVERALLOCATED.
	 * @return true, if there is a overAllocated PM.
	 */
	private boolean isOverAllocated() {
		
		boolean x = false;
		for(int i = 0; i < getBins().size(); i++) {
			if(getBins().get(i).getState().equals(State.OVERALLOCATED_RUNNING) || getBins().get(i).getState().equals(State.STILL_OVERALLOCATED) ) {
				x = true;
				return true;
			}
		}
		return x;
	}
	
	/**
	 * Identifies PMs of the bins-ArrayList with the State UNDERALLOCATED_RUNNING and STILL_UNDERALLOCATED.
	 * @return true, if there is a underAllocated PM.
	 */
	private boolean isUnderAllocated() {
		
		boolean x = false;
		for(int i = 0; i < getBins().size(); i++) {
			if(getBins().get(i).getState().equals(State.UNDERALLOCATED_RUNNING) || getBins().get(i).getState().equals(State.STILL_UNDERALLOCATED) ) {
				x = true;
				return true;
			}
		}
		return x;
	}

	/**
	 * This method is written to get a PM where a given VM can be migrated without changing the
	 * status of the PM to 'overAllocated'. This is done by first fit.
	 * 
	 * @param VM
	 * 			The VM which shall be migrated.
	 * @return A PM where the given VM can be migrated
	 * 		   starts a new PM if there is no one with needed resources.
	 */
	public ModelPM getMigPm(ModelVM vm) {
		
		ModelVM toMig = vm;
		
		//now we have to search for a fitting pm
		for(int i = 0; i < bins.size(); i++) {		
			ModelPM actualPM = getBins().get(i);
			if(actualPM == toMig.gethostPM() 
					|| actualPM.getState().equals(State.EMPTY_RUNNING) 
					|| actualPM.getState().equals(State.EMPTY_OFF) || actualPM.getState().equals(State.OVERALLOCATED_RUNNING) 
					|| actualPM.getState().equals(State.STILL_OVERALLOCATED) || actualPM.getState().equals(State.UNCHANGEABLE_OVERALLOCATED)) {
				
			}
			else {
				
				if(actualPM.doOrCancelMigration(toMig)) {
					
					return actualPM;
				}
			}
		}
		
		//now we have to take an empty PM if possible, because no running PM is possible to take the load of the VM
		
		for(int j = 0; j < bins.size(); j++) {
			ModelPM actualPM = getBins().get(j);
			if(actualPM != vm.gethostPM() || actualPM.getState().equals(State.EMPTY_RUNNING) 
					|| actualPM.getState().equals(State.EMPTY_OFF) ) {
				
				if(actualPM.doOrCancelMigration(toMig)) {
					
					return actualPM;
				}
			}
			else {
				
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
	private ModelPM startPMs(ResourceConstraints VMConstraints){
		ModelPM start = null;
		for(int i = 0; i < bins.size(); i++){
			
			if(start != null)
				return start;
			
			if(bins.get(i).getState().equals(State.EMPTY_OFF) && VMConstraints.compareTo(bins.get(i).getTotalResources()) <= 0){
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
	private ModelVM getFirstVM(ModelPM x) {
		return x.getVM(0);
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
	 * Method for migrating overAllocated PMs.
	 */
	public void migrateOverAllocatedPM(ModelPM source) {
		
		State state = source.getState();
		
		while(state.equals(State.OVERALLOCATED_RUNNING) || state.equals(State.STILL_OVERALLOCATED)) {
			if(source.getVMs().isEmpty()) {
				source.checkAllocation();
				return;
			}
						
			ModelVM actual = getFirstVM(source);	//now taking the first VM on this PM and try to migrate it to a target			
			ModelPM pm = getMigPm(actual);
			
			if(pm == null) {
				if(state.equals(State.OVERALLOCATED_RUNNING)) {					
					source.changeState(State.STILL_OVERALLOCATED);
					return; // no migration possible anymore
				}
				else {					
					source.changeState(State.UNCHANGEABLE_OVERALLOCATED);
					return; // no migration possible anymore, second try
				}
			}			
			else {
				actual.gethostPM().migrateVM(actual, pm);
				actions.add(new MigrationAction(count++, source, pm, actual)); 	//give the information to the graph
			}
			
			source.checkAllocation();		//check if the state has changed
			state = source.getState();		//set the actual State
		}
	}
	
	/** 
	 * Method for migrating underAllocated PMs.
	 */	
	public void migrateUnderAllocatedPM(ModelPM source) {
		
		int i = 0;	
		State state = source.getState();
		
		while(state.equals(State.UNDERALLOCATED_RUNNING) || state.equals(State.STILL_UNDERALLOCATED)) {
			if(source.getVMs().isEmpty()) {
				source.checkAllocation();
				return;
			}
			ArrayList <ModelVM> migVMs = new ArrayList <ModelVM>();
			ModelVM actual = getFirstVM(source);	//now taking the first VM on this PM and try to migrate it to a target			
			ModelPM pm = getMigPm(actual); 
			
			if(pm == null) {
				
				if(migVMs.isEmpty()) {
					if(state.equals(State.UNDERALLOCATED_RUNNING)) {
						source.changeState(State.STILL_UNDERALLOCATED);
						return; // no migration possible and no one has been done previously
					}
					else if(state.equals(State.STILL_UNDERALLOCATED)) {
						source.changeState(State.UNCHANGEABLE_UNDERALLOCATED);
						return; // no migration possible and no one has been done previously, second try
					}
				}
				else {
					for(int x = i ; x > 0; x--) {
						
						ModelVM demig = migVMs.get(x);
						demig.gethostPM().migrateVM(demig, source);
						
						//ToDo : knoten vom migrieren entfernen
						
						if(state.equals(State.UNDERALLOCATED_RUNNING)) {
							source.changeState(State.STILL_UNDERALLOCATED);
						}
						else {
							source.changeState(State.UNCHANGEABLE_UNDERALLOCATED);
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
			state = source.getState();		//set the actual State
		}
	}
	/*
	@Override
	public void tick(long fires) {
		// TODO Auto-generated method stub
		
	}*/
}