package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

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
	 * The constructor for the First-Fit-Consolidator. This class uses the methods out of the superclass, for example
	 * instantiate() is used to create the ModelPMs and ModelVMs, createGraph() is used to get a graph with nodes which has got
	 * the information, what shall be done in which order inside the simulator for migration and starting / shut down PMs, and
	 * performActions() does the changes based on the graph.
	 * 
	 * @param parent
	 * 			The IaaSService of the superclass Consolidator.
	 * @param upperThreshold
	 * 			The double value representing the upper Threshold.
	 * @param lowerThreshold
	 * 			The double value representing the lower Threshold.
	 * @param consFreq
	 * 			This value determines, how often the consolidation should run.
	 */
	public FirstFitConsolidation(IaaSService parent, final double upperThreshold, final double lowerThreshold, long consFreq) {
		super(parent, consFreq);
		for(int i = 0; i < bins.size(); i++) {
			bins.get(i).setLowerThreshold(lowerThreshold);
			bins.get(i).setUpperThreshold(upperThreshold);
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
	 * The method for doing the consolidation, which means start PMs, stop PMs, migrate VMs, ...
	 * To do that, every action is saved as an Action-Node inside a graph, which does the changes
	 * inside the simulator.
	 */
	public void optimize() {
		
		while(isOverAllocated() || isUnderAllocated()) {
			for(ModelPM pm : this.getBins()) {				
				if(pm.isNothingToChange()) {
					
				}
				else {					
					if(pm.isUnderAllocatedChangeable()) {						
						if(pm.isHostingVMs() != true) {
							pm.changeState(State.EMPTY_RUNNING);
						}
						else {
							migrateUnderAllocatedPM(pm);
						}
					}
					else {
						if(pm.isOverAllocatedChangeable()) {
							migrateOverAllocatedPM(pm);
						}
					}
				}
			}
		}
		
		//cleares the VMlist of each PM, so no VM is in the list more than once
		for(ModelPM pm : this.getBins()) {
			ArrayList<ModelVM> allVMsOnPM = pm.getVMs();			
			Set<ModelVM> setItems = new LinkedHashSet<ModelVM>(allVMsOnPM);
			allVMsOnPM.clear();
			allVMsOnPM.addAll(setItems);
		}
		
		shutEmptyPMsDown();		//at the end all empty PMs have to be shut down		
		createGraph(getActions());		//creates the graph with all previously done actions
		Logger.getGlobal().info("At end of optimization: "+toString());
		try {
			performActions();				//do the changes inside the simulator
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
	 * 		   starts a new PM if there is no running VM with the needed resources.
	 */
	public ModelPM getMigPm(ModelVM vm) {
		Logger.getGlobal().info("vm="+vm.toString());
		ModelVM toMig = vm;
		
		//now we have to search for a fitting pm
		for(int i = 0; i < bins.size(); i++) {		
			ModelPM actualPM = getBins().get(i);
			Logger.getGlobal().info("evaluating pm "+actualPM.toString());
			if(actualPM == toMig.gethostPM() 
					|| actualPM.getState().equals(State.EMPTY_RUNNING) 
					|| actualPM.getState().equals(State.EMPTY_OFF) || actualPM.getState().equals(State.OVERALLOCATED_RUNNING) 
					|| actualPM.getState().equals(State.STILL_OVERALLOCATED) || actualPM.getState().equals(State.UNCHANGEABLE_OVERALLOCATED)) {
				
			}
			else {
				
				if(actualPM.isMigrationPossible(toMig)) {					
					return actualPM;
				}
			}
		}		
		//now we have to take an empty PM if possible, because no running PM is possible to take the load of the VM		
		for(int j = 0; j < bins.size(); j++) {
			ModelPM actualPM = getBins().get(j);
			Logger.getGlobal().info("evaluating pm "+actualPM.toString());
			if(actualPM != vm.gethostPM() || actualPM.getState().equals(State.EMPTY_RUNNING) 
					|| actualPM.getState().equals(State.EMPTY_OFF) ) {
				
				if(actualPM.isMigrationPossible(toMig)) {					
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
	private void shutEmptyPMsDown() {
		
		for(int i = 0; i < bins.size(); i++) {
			if(!bins.get(i).isHostingVMs() && bins.get(i).getState() != State.EMPTY_OFF) {
				bins.get(i).switchOff();
				actions.add(new ShutDownAction(count++, bins.get(i)));	//give the information to the graph
			}
		}
	}
	
	/** 
	 * This method handles the migration of all VMs of an OverAllocated PM, til the state changes to 
	 * NORMAL_RUNNING. To do that, a targetPM will be find for every VM on this PM and then the migrations 
	 * will be performed. If not enough VMs can be migrated, the state of this PM will be changed to
	 * STILL_OVERALLOCATED, so there will be another try to migrate the surplus VMs.
	 * 
	 * @param source
	 * 			The source PM which host the VMs to migrate.
	 */
	private void migrateOverAllocatedPM(ModelPM source) {
		Logger.getGlobal().info("source="+source.toString());
		State state = source.getState();
		
		while(source.isOverAllocatedChangeable()) {
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
	 * This method handles the migration of all VMs of an underAllocated PM. To do that, a targetPM will be
	 * find for every VM on this PM and then the migrations will be performed, but if not all of the hosted VMs
	 * can be migrated (after this process the PM would still host running VMs), nothing will be changed.
	 * 
	 * @param source
	 * 			The source PM which host the VMs to migrate.
	 */	
	private void migrateUnderAllocatedPM(ModelPM source) {
						
		int x = 0;		//variable for getting VM		
		State state = source.getState();
		
		ArrayList <Action> migrationActions = new ArrayList <Action>();		//save all actions in this list before 
		ArrayList <ModelPM> migPMs = new ArrayList <ModelPM>();
		
		for(int j = 0; j < source.getVMs().size(); j++){
						
			ModelVM actual = source.getVM(x);	//now taking the next VM on this PM and try to migrate it to a target			
			ModelPM pm = getMigPm(actual); 
			
			if(pm != null) {
				migPMs.add(pm);
				migrationActions.add(new MigrationAction(count++, source, pm, actual));
				x++;
			}
			else {				
				if(migrationActions.isEmpty()) {
					if(state.equals(State.UNDERALLOCATED_RUNNING)) {
						source.changeState(State.STILL_UNDERALLOCATED);
						return; // no migration possible and no one has been done previously
					}
					else if(state.equals(State.STILL_UNDERALLOCATED)) {
						source.changeState(State.UNCHANGEABLE_UNDERALLOCATED);
						return; // no migration possible and no one has been done previously, second try
					}
				}	
			}				
			state = source.getState();		//set the actual State
		}
		
		for(int i = 0; i < migrationActions.size(); i++) {
			source.migrateVM(source.getVM(i), migPMs.get(i));
			actions.add(migrationActions.get(i)); 	//give the information to the graph
		}
		
	}
	
}