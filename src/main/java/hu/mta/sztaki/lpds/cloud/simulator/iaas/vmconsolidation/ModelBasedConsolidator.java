package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

	/**
	 * @author Julian Bellendorf, René Ponto
	 * 
	 * This class gives the necessary variables and methods for VM consolidation.
	 * The main idea is to make an abstract model out of the given PMs and its VMs with the original
	 * properties and let an algorithm (optimize) do the new placement of the VMs in order
	 * to save power by shutting down unused PMs. Therefore a threshold is made to set
	 * the states where migrations are needed of the PMs.
	 * 
	 * After this process one graph shall be created with the following information:
	 * 		1. Which PMs shall start?
	 * 		2. Which VMs on which PMs shall be migrated to target PMs?
	 * 		3. Which PMs shall be shut down?
	 * 
	 * At last the created graph out of the changes needs to get worked with. This graph 
	 * has all changes saved in nodes (actions) which are going to be done inside the simulator
	 * when there is nothing to be done before doing the action on the actual node.
	 */

public abstract class ModelBasedConsolidator /*extends Consolidator*/ implements VirtualMachine.StateChange, PhysicalMachine.StateChangeListener {
	
	IaaSService toConsolidate;
	ArrayList <ModelPM> bins = new ArrayList <ModelPM>();
	//ArrayList for saving the actions which have to be performed inside the simulator
	ArrayList <Action> actions = new ArrayList<Action>();
	
	/**
	 * The abstract constructor for VM consolidation. It expects an IaaSService and a variable
	 * which says how often the consolidation shall occur.
	 * 
	 * @param toConsolidate
	 * 			The used IaaSService.
	 * @param consFreq
	 * 			This value determines, how often the consolidation should run.
	 */
	public ModelBasedConsolidator(IaaSService toConsolidate, long consFreq) {
		
		//super(toConsolidate, consFreq);
		
		this.toConsolidate = toConsolidate;
		Handler logFileHandler;
		try {
			logFileHandler = new FileHandler("log.txt");
			logFileHandler.setFormatter(new SimpleFormatter());
			Logger.getGlobal().addHandler(logFileHandler);
		} catch (Exception e) {
			System.out.println("Could not open log file for output"+e);
			System.exit(-1);
		}
		
		this.instantiate();		//create the abstract model
	}
	
	/** 
	 * @return bins
	 */
	public ArrayList <ModelPM> getBins() {
		return bins;
	}
	
	/** 
	 * @return actions
	 */
	public ArrayList <Action> getActions() {
		return actions;
	}		
	
	/**
	 * In this part all PMs and VMs will be put inside this abstract model.
	*/
	public void instantiate() {
		for (int i = 0; i < toConsolidate.machines.size(); i++) {
			
			// now every PM will be put inside the model with its hosted VMs
			PhysicalMachine pm = toConsolidate.machines.get(i);
			ArrayList <ModelVM> items = new ArrayList <ModelVM>();
			ArrayList <VirtualMachine> vmList = new ArrayList <VirtualMachine>();
			vmList.addAll(pm.listVMs());
			
			ModelPM act = new ModelPM(pm, items, pm.getCapacities().getRequiredCPUs(), 
					pm.getCapacities().getRequiredProcessingPower(),pm.getCapacities().getRequiredMemory(), i + 1);
			
			for(int j = 0; j < pm.listVMs().size(); j ++) {
				items.add(new ModelVM(vmList.get(j), act, 
						vmList.get(j).getResourceAllocation().allocated.getRequiredCPUs(), 
						vmList.get(j).getResourceAllocation().allocated.getRequiredProcessingPower(), 
						vmList.get(j).getResourceAllocation().allocated.getRequiredMemory(), vmList.get(j).getVa().id));
			}
			
			act.initializePM(items);
			bins.add(act);
		}
		Logger.getGlobal().info("Instantiated model: "+toString());
	}
	
	
	/**
	 * The method for doing the consolidation, which means start PMs, stop PMs, migrate VMs, ...
	 */
	public abstract void optimize(); 
	
	/**
	 * The graph which does the changes.
	 */
	public void createGraph(ArrayList<Action> actions) {
		
		for(int i = 0; i < actions.size(); i++) {
			Logger.getGlobal().info(actions.get(i).toString());
			//actual action is a migration
			actions.get(i).determinePredecessors(actions);
			if(actions.get(i).getType().equals(Action.Type.MIGRATION)){
				((MigrationAction)actions.get(i)).getItemVM().getVM().subscribeStateChange(this);		
			}
			//actual action shall shut down a PM
			if(actions.get(i).getType().equals(Action.Type.SHUTDOWN)){
				((ShutDownAction)actions.get(i)).getShutDownPM().getPM().subscribeStateChangeEvents(this);		
			}
			//actual action is starting a PM
			//this can be done instantly, there can be no previous actions
			if(actions.get(i).equals(Action.Type.START)){
				((StartAction)actions.get(i)).getStartPM().getPM().subscribeStateChangeEvents(this);
			}
		}
		for(int j = 0; j < actions.size(); j++) {			
			actions.get(j).determineSuccessors(actions);
		}
	}

	//here the necessary actions are performed
	//do the migration, shut a PM down, start a PM
	public void performActions() throws VMManagementException, NetworkException{
		for(int i = 0; i < actions.size(); i++) {
			if(actions.get(i).getPrevious().isEmpty()) {
				actions.get(i).execute();
			}
		}
	}
	
	//if a migration is successful, this action gets removed of every other node which
	//has this one as the previous element
	@Override
	public void stateChanged(VirtualMachine vm, hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State oldState, 
			hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State newState) {
		if(newState.equals(VirtualMachine.State.RUNNING)){
			for(int i = 0; i < actions.size(); i++){
					if(actions.get(i).getType().equals(Action.Type.MIGRATION) && ((MigrationAction) actions.get(i)).getItemVM().getVM().equals(vm)){
						Action act = actions.get(i);
						for(int j = 0; j < act.getSuccessors().size(); j++){
							act.getSuccessors().get(j).removePrevious(act);
						}
						for(int j = 0; j < act.getSuccessors().size(); j++){
							act.removeSuccessor(act.getSuccessors().get(j));
						}
						actions.remove(actions.get(i));
					}
			}
			try {
				performActions();
			} catch (VMManagementException e) {
				e.printStackTrace();
			} catch (NetworkException e) {
				e.printStackTrace();
			}
		}
	}
		
	//if starting a PM is succesful, this action gets removed of every other node which
	//has this one as the previous element
	@Override
	public void stateChanged(PhysicalMachine pm, hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State oldState,
			hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State newState) {
		if(newState.equals(PhysicalMachine.State.RUNNING)){
			for(int i = 0; i < actions.size(); i++){
				if(actions.get(i).getType().equals(Action.Type.START) && ((StartAction) actions.get(i)).getStartPM().getPM().equals(pm)){
					Action act = actions.get(i);
					for(int j = 0; j < act.getSuccessors().size(); j++){
						act.getSuccessors().get(j).removePrevious(act);
					}
					for(int j = 0; j < act.getSuccessors().size(); j++){
						act.removeSuccessor(act.getSuccessors().get(j));
					}
					actions.remove(act);
				}
			}
			
			try {
				performActions();
			} catch (VMManagementException e) {
				e.printStackTrace();
			} catch (NetworkException e) {
				e.printStackTrace();
			}
		}	
		if(newState.equals(PhysicalMachine.State.OFF)){
			for(int i = 0; i < actions.size(); i++){
				if(actions.get(i).getType().equals(Action.Type.SHUTDOWN) && ((ShutDownAction) actions.get(i)).getShutDownPM().getPM().equals(pm)){
					Action act = actions.get(i);
					actions.remove(act);
				}
			}
		}
	}

	/**
	 * The toString()-method.
	 */
	public String toString() {
		String result="";
		boolean first=true;
		for(ModelPM bin : bins) {
			if(!first)
				result=result+"\n";
			result=result+bin.toString();
			first=false;
		}
		return result;
	}
}