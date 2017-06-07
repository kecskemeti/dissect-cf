package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;

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

public class VMConsolidation_base extends Scheduler{
	
	//Kopie des übergebenen Service
	IaaSService basicModel;
	ArrayList <PhysicalMachine> bins = new ArrayList <PhysicalMachine>();
	//ArrayList zum Speichern der Aktionen im Modell, die auf den übergebenen Service angewandt werden sollen
	ArrayList <Node> actions = new ArrayList<Node>();
	// Zähler für die Aktionen
	int count = 1;
	
	public VMConsolidation_base(IaaSService parent) throws Exception {
		super(parent);
		basicModel = (IaaSService) deepCopy(parent);
		bins = getPMs();
	}


	
//	ArrayList <VirtualMachine> items = new ArrayList <VirtualMachine>();
//	Map <PhysicalMachine, ArrayList <VirtualMachine>> binsitems = new HashMap <PhysicalMachine, ArrayList <VirtualMachine>>();
	
	
	/*
	 * erstellt eine tiefe Kopie von dem übergebenen IaaSService, auf der die Änderungen durchgeführt werden können
	 */
	public static Object deepCopy( Object o ) throws Exception{
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	new ObjectOutputStream( baos ).writeObject( o );

	ByteArrayInputStream bais = new ByteArrayInputStream( baos.toByteArray() );

	return new ObjectInputStream(bais).readObject();
	}
	
	/**
	 * In this part all PMs and VMs will be put inside this abstract model.
	 
	
	final static int reqcores = 2, reqProcessing = 3, reqmem = 4,
			reqond = 2 * (int) 1, reqoffd = (int) 1;
	
	Map <PhysicalMachine, ArrayList <VirtualMachine>> getPM() {
		for (PhysicalMachine pm : parent.machines) {
			ArrayList <VirtualMachine> items = new ArrayList <VirtualMachine>();
			items.addAll(pm.listVMs());
			ArrayList <VirtualMachine> itemsM = new ArrayList <VirtualMachine>();
			for(int i = 0; i < items.size(); i++){
				VirtualAppliance vaM = new VirtualAppliance(items.get(i).getVa().id, items.get(i).getVa().getStartupProcessing(), items.get(i).getVa().getBgNetworkLoad(), false, items.get(i).getVa().size);
				VirtualMachine vmM = new VirtualMachine(vaM);
				itemsM.add(vmM);
			}
			PhysicalMachine pmM = new PhysicalMachine(pm.getCapacities().getRequiredCPUs(), pm.getCapacities().getRequiredProcessingPower(), pm.getCapacities().getRequiredMemory(), pm.localDisk,
					reqond, reqoffd, null);
			binsitems.put(pmM, items);
			bins.add(pmM);
		}
		return binsitems;
	}*/
	
	public ArrayList<PhysicalMachine> getPMs() {
		for (PhysicalMachine pm : basicModel.machines) {
			bins.add(pm);
		}
	return bins;
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
	void createGraph(ArrayList <PhysicalMachine> list1, ArrayList <VirtualMachine> migrations, 
			ArrayList <PhysicalMachine> targets, ArrayList <PhysicalMachine> list3) {
	}
	@Override
	protected ConstantConstraints scheduleQueued() {
		// TODO Auto-generated method stub
		return null;
	}

}