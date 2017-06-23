package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

	/**
	 * @author Julian, René
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



public class Consolidator implements VirtualMachine.StateChange, PhysicalMachine.StateChangeListener{
	
	IaaSService basic;
	ArrayList <Bin_PhysicalMachine> bins = new ArrayList <Bin_PhysicalMachine>();
	//ArrayList for saving the actions which have to be performed inside the simulator
	ArrayList <Action> actions = new ArrayList<Action>();
	
	public Consolidator(IaaSService parent) throws Exception {
		this.basic = parent;
		bins = instantiate();
	}
	
	
	/**
	 * In this part all PMs and VMs will be put inside this abstract model.
	*/

	ArrayList <Bin_PhysicalMachine> instantiate() {
		ArrayList <Bin_PhysicalMachine> pmList = new ArrayList<Bin_PhysicalMachine>();
		for (int i = 0; i < basic.machines.size(); i++) {
			
			PhysicalMachine pm = basic.machines.get(i);
			ArrayList <Item_VirtualMachine> vmList = new ArrayList <Item_VirtualMachine>();
			ArrayList <VirtualMachine> items = new ArrayList <VirtualMachine>();
			items.addAll(pm.listVMs());
			
			Bin_PhysicalMachine act = new Bin_PhysicalMachine(pm, vmList, pm.getCapacities().getRequiredCPUs(), 
					pm.getCapacities().getRequiredProcessingPower(),pm.getCapacities().getRequiredMemory(), i);
			
			for(int j = 0; j < pm.listVMs().size(); j ++) {
				vmList.add(new Item_VirtualMachine(items.get(j), act, 
						items.get(j).getResourceAllocation().allocated.getRequiredCPUs(), 
						items.get(j).getResourceAllocation().allocated.getRequiredProcessingPower(), 
						items.get(j).getResourceAllocation().allocated.getRequiredMemory(), items.get(j).getVa().id));
			}
			
			act.setVMs(vmList);
			pmList.add(act);
		}
		return pmList;
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
		
	}
	
	
	/**
	 * The graph which does the changes.
	 */
	public void createGraph(ArrayList<Action> actions) {
		
		for(int i = 0; i < actions.size(); i++) {
			//actual action is a migration
			if(actions.get(i).getTarget() != null){
				Action act = actions.get(i);
				act.getItemVM().getVM().subscribeStateChange(this);
				//looking for actions where a PM gets started, that is the target of this migration
				for(int j = 0; j < actions.size(); i++) {
					if(actions.get(j).getstartpm().equals(act.getTarget())){
						act.addPrevious(actions.get(j));
					}
				}			
			}
			//actual action shall shut down a PM
			if(actions.get(i).getshutdownpm() != null){
				Action act = actions.get(i);
				act.getshutdownpm().getPM().subscribeStateChangeEvents(this);
				//looking for migrations with this PM as source, which needs to get shut down
				for(int j = 0; j < actions.size(); i++) {
					if(actions.get(j).getSource().equals(act.getshutdownpm())){
						act.addPrevious(actions.get(j));
					}
				}
				
			}
			//actual action is starting a PM
			//this can be done instantly, there can be no previous actions
			if(actions.get(i).getstartpm() != null){
				Action act = actions.get(i);
				act.getstartpm().getPM().subscribeStateChangeEvents(this);
			}
		}
	}
	
	//here the necessary actions are performed
	//do the migration, shut a PM down, start a PM
	public void performActions() throws VMManagementException, NetworkException{
		for(int i = 0; i < actions.size(); i++) {
			if(actions.get(i).getTarget() != null && actions.get(i).getPrevious().isEmpty()){
				PhysicalMachine source = actions.get(i).getSource().getPM();
				PhysicalMachine target = actions.get(i).getTarget().getPM();
				VirtualMachine vm = actions.get(i).getItemVM().getVM();
				source.migrateVM(vm, target);
			}
			if(actions.get(i).getshutdownpm() != null && actions.get(i).getPrevious().isEmpty()){
				PhysicalMachine pm = actions.get(i).getshutdownpm().getPM();
				pm.switchoff(null);
			}
			
			if(actions.get(i).getstartpm() != null && actions.get(i).getPrevious().isEmpty()){
				PhysicalMachine pm = actions.get(i).getstartpm().getPM();
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
							if(actions.get(i).getPrevious().get(j).getItemVM().getVM().equals(vm)){
								actions.get(i).getPrevious().remove(actions.get(i).getPrevious().get(j));
							}
						}
					}
					if(actions.get(i).getItemVM().getVM().equals(vm)){
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
							if(actions.get(i).getPrevious().get(j).getstartpm().getPM().equals(pm)){
								actions.get(i).getPrevious().remove(actions.get(i).getPrevious().get(j));
							}
						}
					}
					if(actions.get(i).getstartpm().getPM().equals(pm)){
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

