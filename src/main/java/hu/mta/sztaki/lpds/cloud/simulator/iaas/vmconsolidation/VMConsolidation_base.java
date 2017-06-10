package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;


import java.util.ArrayList;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;

	/**
	 * @author Julian, René
	 * 
	 * This Interface gives the necessary variables and methods for VM consolidation.
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
	 * At last the given list(s) have to be given to another class, which implements a graph out of
	 * the items in the list. This graph can be implemented as an EventListener with constraints to
	 * do the changes in the real simulation.
	 */

public class VMConsolidation_base {
	
	IaaSService basic;
	ArrayList <Bin_PhysicalMachine> bins = new ArrayList <Bin_PhysicalMachine>();
	//ArrayList zum Speichern der Aktionen im Modell, die auf den übergebenen Service angewandt werden sollen
	ArrayList <Node> actions = new ArrayList<Node>();
	
	public VMConsolidation_base(IaaSService parent) throws Exception {
		this.basic = parent;
		bins = getPMs();
	}	
	
	/*
	 * erstellt eine tiefe Kopie von dem übergebenen IaaSService, auf der die Änderungen durchgeführt werden können
	 
	public static Object deepCopy( Object o ) throws Exception{
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	new ObjectOutputStream( baos ).writeObject( o );

	ByteArrayInputStream bais = new ByteArrayInputStream( baos.toByteArray() );

	return new ObjectInputStream(bais).readObject();
	}*/
	
	/**
	 * In this part all PMs and VMs will be put inside this abstract model.
	 
	*/

	ArrayList <Bin_PhysicalMachine> getPMs() {
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
	 * The optimization algorithm in the middle part of the first Comment.
	 * This is used to create different migration algorithms and integrate them.
	 */
	void optimize() {
	}
	
	/**
	 * The method to create the graph out of the created lists in 'optimize()'.
	 */
	void createGraph(ArrayList <Bin_PhysicalMachine> list1, ArrayList <Item_VirtualMachine> migrations, 
			ArrayList <Bin_PhysicalMachine> targets, ArrayList <Bin_PhysicalMachine> list3) {
	}
}