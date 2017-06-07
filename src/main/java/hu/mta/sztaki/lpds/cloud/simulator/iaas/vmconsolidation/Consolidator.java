package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

	/**
	 * @author Julian, René
	 * 
	 * This class manages the Consolidation of VMs. At the moment a simple first-fit algorithm for migration will be
	 * implemented, but after this we will change this class to use other more complex algorithms for consolidation.
	 */

public class Consolidator extends VMConsolidation_base {
	
	public Consolidator(IaaSService parent) throws Exception {
		super(parent);
	}
		
	
	//das splitten muss noch geklärt werden
	FirstFitConsolidation ffc = new FirstFitConsolidation(bins, actions, count);
	
	
	/**
	 * Functionality of this optimization:
	 * 
	 * Step 1: Check the threshold of all PMs. A PM is underloaded, if its used resources are lower than 25 %
	 * 		   of the totalProcessingPower and it is overloaded, if its used resources are higher than 75 %
	 * 		   of the totalProcessingPower.
	 * Step 2: Call the migration algorithm and do the migrations. Concurrently the graph shall be created (Step 3), which contain 
	 * 		   the changes made during this step in order to do the migration inside the real simulation, too.
	 * Step 3: Create a graph and fill it with the changes that had been done.
	 */

	public void optimize() {
		ffc.migrationAlgorithm();
		createGraph(actions);
		
		//erst muss die pm klasse fertig werden
		//ffc.shutEmptyPMsDown(super.checkLoad());
	}
	
	
	/**
	 * The graph which does the changes.
	 */

	public void createGraph(ArrayList<Node> actions) {
		for(int i = 0; i < actions.size(); i++) {
			
		}
	}
}

