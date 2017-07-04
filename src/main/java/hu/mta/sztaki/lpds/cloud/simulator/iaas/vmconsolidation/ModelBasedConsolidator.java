package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

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
	 * The order plays an important role, because it could be necessary to do Steps 1 and 2
	 * Simultaneously. 
	 * 
	 * At last the created graph out of the changes needs to get worked with. This graph 
	 * has all changes saved in nodes (actions) which are going to be done inside the simulator
	 * when there is nothing to be done before doing the action on the actual node.
	 */



public class ModelBasedConsolidator implements VirtualMachine.StateChange, PhysicalMachine.StateChangeListener{
	
	IaaSService toConsolidate;
	ArrayList <ModelPM> bins = new ArrayList <ModelPM>();
	//ArrayList for saving the actions which have to be performed inside the simulator
	ArrayList <Action> actions = new ArrayList<Action>();
	
	//variables for the threshold
	double up;
	double low;
	
	/**
	 * The abstract constructorfor VM-consolidation. It expects an IaaSService and the two thresholds for
	 * defining the borders for consolidation which both are betwenn 0 and 1 (of course the upperThreshold has
	 * to be greater than the lowerThreshold).
	 * 
	 * @param parent
	 * 			The used IaaSService
	 * @param upperThreshold
	 * 			The desired value for the upper threshold
	 * @param lowerThreshold
	 * 			The desired value for the lower threshold
	 * @throws Exception
	 */
	public ModelBasedConsolidator(IaaSService parent, double upperThreshold, double lowerThreshold) throws Exception {
		this.toConsolidate = parent;
		this.instantiate();
		up = upperThreshold;
		low = lowerThreshold;
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
			
			PhysicalMachine pm = toConsolidate.machines.get(i);
			ArrayList <ModelVM> items = new ArrayList <ModelVM>();
			ArrayList <VirtualMachine> vmList = new ArrayList <VirtualMachine>();
			vmList.addAll(pm.listVMs());
			
			ModelPM act = new ModelPM(pm, items, pm.getCapacities().getRequiredCPUs(), 
					pm.getCapacities().getRequiredProcessingPower(),pm.getCapacities().getRequiredMemory(), i +1, up, low);
			
			for(int j = 0; j < pm.listVMs().size(); j ++) {
				items.add(new ModelVM(vmList.get(j), act, 
						vmList.get(j).getResourceAllocation().allocated.getRequiredCPUs(), 
						vmList.get(j).getResourceAllocation().allocated.getRequiredProcessingPower(), 
						vmList.get(j).getResourceAllocation().allocated.getRequiredMemory(), vmList.get(j).getVa().id));
			}
			
			act.initializePM(items);
			bins.add(act);
		}
	}
	
	
	/**
	 * The method for doing the consolidation, which means start PMs, stop PMs, migrate VMs, ...
	 * 
	 * @throws NetworkException 
	 * @throws VMManagementException 
	 */
	public void optimize() throws VMManagementException, NetworkException {
		
	}
	
	
	/**
	 * The graph which does the changes.
	 */
	public void createGraph(ArrayList<Action> actions) {
		
		for(int i = 0; i < actions.size(); i++) {
			//actual action is a migration
			actions.get(i).createGraph(actions);
			if(actions.get(i).getType().equals(Action.Type.MIGRATION)){
				((MigrationAction)actions.get(i)).getItemVM().getVM().subscribeStateChange(this);		
			}
			//actual action shall shut down a PM
			if(actions.get(i).getType().equals(Action.Type.SHUTDOWN)){
				((ShutDownAction)actions.get(i)).getshutdownpm().getPM().subscribeStateChangeEvents(this);		
			}
			//actual action is starting a PM
			//this can be done instantly, there can be no previous actions
			if(actions.get(i).equals(Action.Type.START)){
				((StartAction)actions.get(i)).getstartpm().getPM().subscribeStateChangeEvents(this);
			}
		}
	}
	
	//here the necessary actions are performed
	//do the migration, shut a PM down, start a PM
	public void performActions() throws VMManagementException, NetworkException{
		for(int i = 0; i < actions.size(); i++) {
			if(actions.get(i).getType().equals(Action.Type.MIGRATION) && actions.get(i).getPrevious().isEmpty()){
				PhysicalMachine source = ((MigrationAction)actions.get(i)).getSource().getPM();
				PhysicalMachine target = ((MigrationAction)actions.get(i)).getTarget().getPM();
				VirtualMachine vm = ((MigrationAction)actions.get(i)).getItemVM().getVM();
				source.migrateVM(vm, target);
			}
			if(actions.get(i).equals(Action.Type.SHUTDOWN) && actions.get(i).getPrevious().isEmpty()){
				PhysicalMachine pm = ((ShutDownAction)actions.get(i)).getshutdownpm().getPM();
				pm.switchoff(null);
			}
			
			if(actions.get(i).equals(Action.Type.START) && actions.get(i).getPrevious().isEmpty()){
				PhysicalMachine pm = ((StartAction)actions.get(i)).getstartpm().getPM();
				pm.turnon();
			}
		}
	}
	
	//if a migration is succesful, this action gets removed of every other node which
	//has this one as the previous element
	@Override
	public void stateChanged(VirtualMachine vm, hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State oldState, 
			hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State newState) {
		if(oldState.equals(VirtualMachine.State.MIGRATING) && newState.equals(VirtualMachine.State.RUNNING)){
			for(int i = 0; i < actions.size(); i++){
				if(!actions.get(i).getPrevious().isEmpty()){
					for(int j = 0; j < actions.get(i).getPrevious().size(); j++){
						if(((MigrationAction) actions.get(i).getPrevious().get(j)).getItemVM().getVM().equals(vm)){
							actions.get(i).getPrevious().remove(actions.get(i).getPrevious().get(j));
						}
					}
				}
				if(((MigrationAction)actions.get(i)).getItemVM().getVM().equals(vm)){
					actions.remove(actions.get(i));
				}
			}
			try {
				performActions();
			} catch (VMManagementException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NetworkException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
		
	//if starting a PM is succesful, this action gets removed of every other node which
	//has this one as the previous element
	@Override
	public void stateChanged(PhysicalMachine pm, hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State oldState,
			hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State newState) {
		if(oldState.equals(PhysicalMachine.State.SWITCHINGON) && newState.equals(PhysicalMachine.State.RUNNING)){
			for(int i = 0; i < actions.size(); i++){
				if(!actions.get(i).getPrevious().isEmpty()){
					for(int j = 0; j < actions.get(i).getPrevious().size(); j++){
						if(((StartAction) actions.get(i).getPrevious().get(j)).getstartpm().getPM().equals(pm)){
							actions.get(i).getPrevious().remove(actions.get(i).getPrevious().get(j));
						}
					}
				}
				if(((StartAction)actions.get(i)).getstartpm().getPM().equals(pm)){
					actions.remove(actions.get(i));
				}
			}
			
			try {
				performActions();
			} catch (VMManagementException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NetworkException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}		
	}
}

