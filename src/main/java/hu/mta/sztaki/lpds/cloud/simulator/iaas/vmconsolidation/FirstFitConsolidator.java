package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.ModelPM.State;

/**
 * @author René Ponto
 *
 * This class is used to do the consolidation with first fit, i.e. the target
 * PM for a VM is selected using first fit, the VMs on a PM are selected using first fit etc.
 */
public class FirstFitConsolidator extends ModelBasedConsolidator {

	int count = 1;	// Counter for the graph actions

	/**
	 * The constructor for the First-Fit-Consolidator. This class uses the methods out of the superclass, for example
	 * instantiate() is used to create the ModelPMs and ModelVMs, createGraph() is used to get a graph with nodes which has got
	 * the information, what shall be done in which order inside the simulator for migration and starting / shut down PMs. The 
	 * methods which do the changes inside the simulator also are in the super class ModelBasedConsolidation.
	 * 
	 * So the superclass is implemented in that way, that here only the consolidation has to be done. All things like
	 * creating the model, doing the changes in the simulator etc are done by ModelBasedConsolidator.
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
	public FirstFitConsolidator(IaaSService toConsolidate, final double upperThreshold, final double lowerThreshold, long consFreq) {
		super(toConsolidate, upperThreshold, lowerThreshold, consFreq);
	}

	/**
	 * The method for doing the consolidation, which means start PMs, stop PMs, migrate VMs, ...
	 * To do that, every action is saved as an Action-Node inside a graph, which does the changes
	 * inside the simulator.
	 */
	@Override
	public void optimize() {
		while(isOverAllocated() || isUnderAllocated()) {
			for(ModelPM pm : this.getBins()) {				
				if(pm.isNothingToChange()) {
					// do nothing
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

		//clears the VMlist of each PM, so no VM is in the list more than once
		for(ModelPM pm : this.getBins()) {
			List<ModelVM> allVMsOnPM = pm.getVMs();			
			Set<ModelVM> setItems = new LinkedHashSet<ModelVM>(allVMsOnPM);
			allVMsOnPM.clear();
			allVMsOnPM.addAll(setItems);
		}

		shutEmptyPMsDown();		//at the end all empty PMs have to be shut down		
		Logger.getGlobal().info("At end of optimization: "+toString());
	}

	/**
	 * Identifies PMs of the bins-ArrayList with the State OVERALLOCATED_RUNNING and STILL_OVERALLOCATED.
	 * @return true, if there is an overAllocated PM.
	 */
	private boolean isOverAllocated() {
		for(ModelPM pm : getBins()) {
			if(pm.getState().equals(State.OVERALLOCATED_RUNNING) || pm.getState().equals(State.STILL_OVERALLOCATED) ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Identifies PMs of the bins-ArrayList with the State UNDERALLOCATED_RUNNING and STILL_UNDERALLOCATED.
	 * @return true, if there is an underAllocated PM.
	 */
	private boolean isUnderAllocated() {
		for(ModelPM pm : getBins()) {
			if(pm.getState().equals(State.UNDERALLOCATED_RUNNING) || pm.getState().equals(State.STILL_UNDERALLOCATED) ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * This method is written to get a PM where a given VM can be migrated without changing the
	 * status of the PM to 'overAllocated'. This is done by first fit.
	 * 
	 * @param VM
	 * 			The VM which shall be migrated.
	 * @return A PM where the given VM can be migrated;
	 * 		   starts a new PM if there is no running VM with the needed resources;
	 * 		   null is returned if no appropriate PM was found.
	 */
	public ModelPM getMigPm(ModelVM toMig) {
		//Logger.getGlobal().info("vm="+toMig.toString());
		//now we have to search for a fitting pm
		for(int i = 0; i < bins.size(); i++) {		
			ModelPM actualPM = getBins().get(i);
			//Logger.getGlobal().info("evaluating pm "+actualPM.toString());
			if(actualPM == toMig.gethostPM() 
					|| actualPM.getState().equals(State.EMPTY_RUNNING) 
					|| actualPM.getState().equals(State.EMPTY_OFF) || actualPM.getState().equals(State.OVERALLOCATED_RUNNING) 
					|| actualPM.getState().equals(State.STILL_OVERALLOCATED) || actualPM.getState().equals(State.UNCHANGEABLE_OVERALLOCATED)) {
				// do nothing, this PM shall not host this VM
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
			//Logger.getGlobal().info("evaluating pm "+actualPM.toString());
			if(actualPM != toMig.gethostPM() || actualPM.getState().equals(State.EMPTY_RUNNING)) {
				
				if(actualPM.isMigrationPossible(toMig)) {					
					return actualPM;
				}
			}
			else {
				// do nothing, get the next one
			}	
		}
		return startPM(toMig.getResources());	//start an empty_off PM
	}
	
	/**
	 * Starts a PM which contains the necessary resources for hosting the previous VM.
	 * This is done by first-fit. If no PM can be started, a warning is thrown to show
	 * that.
	 * 
	 * @param VMConstraints
	 * 			The ResourceConstraints of the VM, which shall be hosted on a not running PM 
	 * @return A PM with the needed resources or null if no appropriate PM was found.
	 */
	private ModelPM startPM(ResourceConstraints VMConstraints) {
		for(ModelPM pm : getBins()){
			if(pm.getState().equals(State.EMPTY_OFF) && VMConstraints.compareTo(pm.getTotalResources()) <= 0){
				pm.switchOn();		//start this PM
				return pm;
			}
		}
		Logger.getGlobal().warning("No appropriate PM found");
		return null;
	}

	/**
	 * This method is created to get the first VM on a PM. Caution: the PM must
	 * contain at least one VM.
	 * @param x
	 * 			The PM that has to be used.
	 * @return first VM on this PM.
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
		for(ModelPM pm : getBins()){
			if(!pm.isHostingVMs() && pm.getState() != State.EMPTY_OFF) {
				pm.switchOff();	//shut down this PM
			}
		}
	}

	/** 
	 * This method handles the migration of all VMs of an OverAllocated PM, till the state changes to 
	 * NORMAL_RUNNING. To do that, a targetPM will be found for every VM on this PM and then the migrations 
	 * will be performed. If not enough VMs can be migrated, the state of this PM will be changed to
	 * STILL_OVERALLOCATED, so there will be another try to migrate the surplus VMs.
	 * 
	 * @param source
	 * 			The source PM which host the VMs to migrate.
	 */
	private void migrateOverAllocatedPM(ModelPM source) {
		//Logger.getGlobal().info("source="+source.toString());
		State state = source.getState();
		while(source.isOverAllocatedChangeable()) {
			if(source.getVMs().isEmpty()) {
				// check the allocation again and do nothing else
				source.checkAllocation();
				return;
			}
			ModelVM actual = getFirstVM(source);	//now taking the first VM on this PM and try to migrate it to a target			
			ModelPM pm = getMigPm(actual);
			// if there is no PM to host the actual VM of the source PM, change the state depending on its acutal state
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
				actual.gethostPM().migrateVM(actual, pm);	//do the migration
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
		ArrayList <ModelPM> migPMs = new ArrayList <ModelPM>();				//save all PMs for hosting VMs depending on their order

		for(int j = 0; j < source.getVMs().size(); j++){
			ModelVM actual = source.getVM(x);	//now taking the next VM on this PM and try to migrate it to a target			
			ModelPM pm = getMigPm(actual); 
			// if there is a PM which could host the actual VM, save it
			if(pm != null) {
				pm.reserveResources(actual);		//reserve the resource for the possible migration
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
				else {
					for(int a = 0; a < migPMs.size(); a++) {
						migPMs.get(a).setResourcesFree();
					}
				}
			}				
			state = source.getState();		//set the actual State
		}

		for(int i = 0; i < migrationActions.size(); i++) {
			source.migrateVM(source.getVM(i), migPMs.get(i));
		}
	}
}