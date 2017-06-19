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
	 * to save power by shutting down unused PMs.
	 * 
	 * After this process one (or more) lists shall be created with the following information:
	 * 		1. Which PMs shall start?
	 * 		2. Which VMs on which PMs shall be migrated to target PMs?
	 * 		3. Which PMs shall be shut down?
	 * The order plays an important role, because it could be necessary to do Steps 1 and 2
	 * Simultaneously. 
	 * 
	 * At last the given list(s) have to be given to another method, which implements a graph out of
	 * the items in the list. This graph can be implemented as an EventListener with constraints to
	 * do the changes in the real simulation.
	 */



public class Consolidator implements VirtualMachine.StateChange, PhysicalMachine.StateChangeListener{
	
	IaaSService basic;
	ArrayList <Bin_PhysicalMachine> bins = new ArrayList <Bin_PhysicalMachine>();
	//ArrayList zum Speichern der Aktionen im Modell, die auf den übergebenen Service angewandt werden sollen
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
		for (PhysicalMachine pm : basic.machines) {
			ArrayList <Item_VirtualMachine> vmList = new ArrayList <Item_VirtualMachine>();
			ArrayList <VirtualMachine> items = new ArrayList <VirtualMachine>();
			items.addAll(pm.listVMs());
			Bin_PhysicalMachine act = new Bin_PhysicalMachine(pm, vmList, pm.getCapacities().getRequiredCPUs(), pm.getCapacities().getRequiredProcessingPower(),pm.getCapacities().getRequiredMemory());
			//if(!pm.isRunning()){
			//	act.changeState(act.state.off);
			//}	
			for(int i = 0; i < items.size(); i++){
				vmList.add(new Item_VirtualMachine(items.get(i), act, items.get(i).getResourceAllocation().allocated.getRequiredCPUs(), items.get(i).getResourceAllocation().allocated.getRequiredProcessingPower(), items.get(i).getResourceAllocation().allocated.getRequiredMemory()));
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
			//aktuelle Aktion stellt eine Migration dar
			if(actions.get(i).getTarget() != null){
				Action act = actions.get(i);
				act.getItemVM().getVM().subscribeStateChange(this);
				//Suche nach Aktionen, bei denen eine PM gestartet wird, die die aktuelle Migration zum Ziel hat
				for(int j = 0; j < actions.size(); i++) {
					if(actions.get(j).getstartpm().equals(act.getTarget())){
						act.addVorgaenger(actions.get(j));
					}
				}			
			}
			//aktuelle Aktion soll eine PM herunterfahren
			if(actions.get(i).getshutdownpm() != null){
				Action act = actions.get(i);
				act.getshutdownpm().getPM().subscribeStateChangeEvents(this);
				//Suche nach Migrationen, die von der PM ausgehen, die heruntergefahren werden soll
				for(int j = 0; j < actions.size(); i++) {
					if(actions.get(j).getSource().equals(act.getshutdownpm())){
						act.addVorgaenger(actions.get(j));
					}
				}
				
			}
			//aktuelle Aktion soll eine PM starten, 
			//eine solche Aktion kann sofort  ausgefürt werden, hat keine Vorgänger
			if(actions.get(i).getstartpm() != null){
				Action act = actions.get(i);
				act.getstartpm().getPM().subscribeStateChangeEvents(this);
			}
		}
	}
	
	public void performActions() throws VMManagementException, NetworkException{
		for(int i = 0; i < actions.size(); i++) {
			if(actions.get(i).getTarget() != null && actions.get(i).getVorgaenger().isEmpty()){
				PhysicalMachine source = actions.get(i).getSource().getPM();
				PhysicalMachine target = actions.get(i).getTarget().getPM();
				VirtualMachine vm = actions.get(i).getItemVM().getVM();
				source.migrateVM(vm, target);
			}
			if(actions.get(i).getshutdownpm() != null && actions.get(i).getVorgaenger().isEmpty()){
				PhysicalMachine pm = actions.get(i).getshutdownpm().getPM();
				pm.switchoff(null);
			}
			
			if(actions.get(i).getstartpm() != null && actions.get(i).getVorgaenger().isEmpty()){
				PhysicalMachine pm = actions.get(i).getstartpm().getPM();
				pm.turnon();
			}
		}
	}
	
	//wenn eine Migration abgeschlossen ist, wird diese Aktion von den Vorgängerlisten der übrigen Knoten entfernt
		@Override
		public void stateChanged(VirtualMachine vm, hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State oldState, 
				hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State newState) {
			if(oldState.equals(VirtualMachine.State.MIGRATING) && newState.equals(VirtualMachine.State.RUNNING)){
				for(int i = 0; i < actions.size(); i++){
					if(!actions.get(i).getVorgaenger().isEmpty()){
						for(int j = 0; j < actions.get(i).getVorgaenger().size(); j++){
							if(actions.get(i).getVorgaenger().get(j).getItemVM().getVM().equals(vm)){
								actions.get(i).getVorgaenger().remove(actions.get(i).getVorgaenger().get(j));
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
		
		//wenn eine PM gestartet ist, wird diese Aktion von den Vorgängerlisten der übrigen Knoten entfernt
		@Override
		public void stateChanged(PhysicalMachine pm, hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State oldState,
				hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State newState) {
			if(oldState.equals(PhysicalMachine.State.SWITCHINGON) && newState.equals(PhysicalMachine.State.RUNNING)){
				for(int i = 0; i < actions.size(); i++){
					if(!actions.get(i).getVorgaenger().isEmpty()){
						for(int j = 0; j < actions.get(i).getVorgaenger().size(); j++){
							if(actions.get(i).getVorgaenger().get(j).getstartpm().getPM().equals(pm)){
								actions.get(i).getVorgaenger().remove(actions.get(i).getVorgaenger().get(j));
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

