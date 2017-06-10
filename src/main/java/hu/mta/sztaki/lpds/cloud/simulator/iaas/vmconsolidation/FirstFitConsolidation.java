package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.Bin_PhysicalMachine.State;

	/**
	 * @author Rene
	 *
	 * This class is used to do the consolidation with first fit. It can be easy used in the consolidator by simply creating
	 * an instance of it. The migration, PM-selection for migration and every other method according to this 
	 * is implemented as first fit.
	 */


public class FirstFitConsolidation {
	
	ArrayList <Bin_PhysicalMachine> bins = new ArrayList <Bin_PhysicalMachine>();
	ArrayList <Node> actions;
	// Zähler für die Aktionen
	int count = 1;
	
	/**
	 * The constructor for the First-Fit-Consolidator.
	 * 
	 * @param bins
	 * 			The ArrayList which contains the PMs.
	 * @param actions
	 * 			The ArrayList for the graph.
	 */
	
	public FirstFitConsolidation(ArrayList <Bin_PhysicalMachine> bins, ArrayList <Node> actions) {
		
		this.bins = bins;
		this.actions = actions;
	}	
	
	/**
	 * Getter for bins-list.
	 * @return bins
	 */
	
	public ArrayList <Bin_PhysicalMachine> getBins() {
		return bins;
	}
	
	/**
	 * Getter for the action-list.
	 * @return actions
	 */
	
	public ArrayList <Node> getActions() {
		return actions;
	}	
	
	/**
	 * This is the algorithm for migration, which uses the First-Fit-Algorithm to place VMs on other PMs
	 * if the status is an other than 'normal'. Another method is called if no PM on the actually 
	 * running PMs fulfills the constraints. 
	 * 
	 * Braucht ein rework, nicht aktuell
	 */
	
	void migrationAlgorithm() {
		
		for(int i = 0; i <bins.size(); i++) {
			if(bins.get(i).getState() == State.NORMAL_RUNNING) {
				
			}
			else {
				
				while(bins.get(i).getState() == State.UNDERLOADED_RUNNING || bins.get(i).getState() ==  State.OVERLOADED_RUNNING) {
					
					if(bins.get(i).isHostingVMs() != true) {
						bins.get(i).changeState(State.EMPTY_RUNNING);
					}
					
					else {
						Bin_PhysicalMachine actual = bins.get(i);
						Item_VirtualMachine x = this.getFirstVM(actual);
						Bin_PhysicalMachine y = this.getMigPm(x);
							actual.migrateVM(x, y);
							actions.add(new MigrateVMNode(count++, actual, y, x)); 
					}
				}
			}
		}
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
		return startPMs(toMig.getRequiredCPUs(), toMig.getRequiredProcessingPower(), toMig.getRequiredMemory());	//get new PM
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
	
	private Bin_PhysicalMachine startPMs(double cores, double power, long mem){
		Bin_PhysicalMachine start = null;
		for(int i = 0; i < bins.size(); i++){
			
			if(start != null)
				return start;
			
			if(bins.get(i).getState().equals(State.EMPTY_OFF) && bins.get(i).getRequiredCPUs()
					>= cores && bins.get(i).getRequiredProcessingPower() >= power && bins.get(i).getRequiredMemory() >= mem){
				bins.get(i).switchOn();
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
				actions.add(new ShutDownNode(count++, bins.get(i)));
			}
		}
	}
	
	/**
	 * In this method the status of each PM in the simulation is considered.
	 * To do so, the methods 'underloaded' and 'overloaded' are used, which 
	 * check if the used resources are more or less than the defined threshold for
	 * overloaded / underloaded. 
	 * At the end every PM has a status which fits to the load.
	 */
	
	protected void checkLoad() {
		for(int i = 0; i < bins.size(); i++) {
			
			if(bins.get(i).isHostingVMs() == false) {
				bins.get(i).changeState(State.EMPTY_RUNNING);
			}
			else {
			
				if(underloaded(bins.get(i)))  {
					bins.get(i).changeState(State.UNDERLOADED_RUNNING);
				}
				else {
					if(overloaded(bins.get(i))) {
						bins.get(i).changeState(State.OVERLOADED_RUNNING);
					}
					else
						bins.get(i).changeState(State.NORMAL_RUNNING);
				}	
			}
		}
	}
	
	/**
	 * Method for checking if the actual PM is overloaded.
	 * @param pm
	 * 			The PhysicalMachine which shall be checked.
	 * @return true if overloaded, false otherwise
	 */
	
	private boolean overloaded(Bin_PhysicalMachine pm) {
		
		if(pm.getRequiredCPUs()-pm.getAvailableCPUs() >= 0.75 || pm.getRequiredMemory()-pm.getAvailableMemory() >= 0.75 
				|| pm.getRequiredProcessingPower()-pm.getAvailableProcessingPower() >= 0.75) {
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
	
	private boolean underloaded(Bin_PhysicalMachine pm) {
		
		if(pm.getRequiredCPUs()-pm.getAvailableCPUs() <= 0.25 || pm.getRequiredMemory()-pm.getAvailableMemory() <= 0.25 
				|| pm.getRequiredProcessingPower()-pm.getAvailableProcessingPower() <= 0.25) {
			return true;
		}
		else
			return false;
	}
	
	
	/**
	 * Method for migrating underloaded PMs.
	 * Instead of doing every migration if there are no more target PMs, the migrations will not be done and the PM
	 * will be left in underloaded.
	 * 
	 * @return
	 * 		The ArrayList with all necassary migrations.
	 */
	
	public ArrayList<Item_VirtualMachine> migratePM(Bin_PhysicalMachine source) {
		
		ArrayList <Item_VirtualMachine> migrations = new ArrayList <Item_VirtualMachine>();
		
		double avCores = source.getAvailableCPUs();
		double avPCP = source.getAvailableProcessingPower();
		long avMem = source.getAvailableMemory();
		
		while(avCores < source.getRequiredCPUs() * 0.75 || avPCP < source.getRequiredProcessingPower() * 0.75 
				|| avMem < source.getRequiredMemory() * 0.75) {
			
			//jetzt immer eine vm mit ff raus nehmen und auf die ff pm migrieren.
			Item_VirtualMachine actual = getFirstVM(source);
			migrations.add(actual);
			
			avCores = avCores - actual.getRequiredCPUs();
			avPCP = avPCP - actual.getRequiredProcessingPower();
			avMem = avMem - actual.getRequiredMemory();
			
			//dabei immer prüfen, ob der status normal erreicht worden ist
			
			if(source.getState().equals(State.NORMAL_RUNNING)) {
				return migrations;
			}
		}
		return migrations;
	}
	
	/**
	 * The idea here is to check if the whole list can be migrated anywhere. If that is the case,
	 * the migrations will be done. If not, it will be checked if the source PM was/is overloaded
	 * or underloaded.
	 * 
	 * In the first Case, all migrations which can be done will be done.
	 * In the second Case, no migration occurs and nothing will be changed.
	 * 
	 * @param vms
	 * 			The ArrayList out of migratePM().
	 */

	public void migrateMoreVMs(ArrayList <Item_VirtualMachine> vms) {
		
		Bin_PhysicalMachine host = vms.get(0).gethostPM();
		
		
	}
	
	
	
	/**
	 * Method for migrating overloaded PMs.
	 * A list gets created for doing the migrations. If the PM is still overloaded after there can be
	 * done no more migrations, every element out of the list will be migrated nontheless. 
	 * 
	 * noch nicht fertig
	 * 
	 * @return
	 */
	
	/*public ArrayList<Item_VirtualMachine> migrateOverloadedPM(Bin_PhysicalMachine source) {
		
		ArrayList <Item_VirtualMachine> migrations = new ArrayList <Item_VirtualMachine>();
		
		double avCores = source.getAvailableCPUs();
		double avPCP = source.getAvailableProcessingPower();
		long avMem = source.getAvailableMemory();
		
		while(avCores < source.getRequiredCPUs() * 0.25 || avPCP < source.getRequiredProcessingPower() * 0.25 
				|| avMem < source.getRequiredMemory() * 0.25) {
			
			//jetzt immer eine vm mit ff raus nehmen und auf die ff pm migrieren.
			
			Item_VirtualMachine actual = getFirstVM(source);
			migrations.add(actual);
			
			avCores = avCores - actual.getRequiredCPUs();
			avPCP = avPCP - actual.getRequiredProcessingPower();
			avMem = avMem - actual.getRequiredMemory();
			
			//dabei immer prüfen, ob der status normal erreicht worden ist
			
			if(source.getState().equals(State.NORMAL_RUNNING)) {
				return migrations;
			}
		}
		
		return migrations;
	}
	*/
	
}
