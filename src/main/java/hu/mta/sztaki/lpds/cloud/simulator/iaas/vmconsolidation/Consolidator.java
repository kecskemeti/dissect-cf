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
		
	FirstFitConsolidation ffc = new FirstFitConsolidation(bins, actions);
	
	
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
		createGraph(ffc.actions);
		
		//erst muss die pm klasse fertig werden
		//ffc.shutEmptyPMsDown(super.checkLoad());
	}
	
	
	/**
	 * The graph which does the changes.
	 */

	@SuppressWarnings("unchecked")
	public void createGraph(ArrayList<Node> actions) {
		ArrayList<Node> actionsneu = new ArrayList<>();
		for(int i = 0; i < actions.size(); i++) {
			Node akt = null;
			if(actions.get(i) instanceof StartNode){
				akt = actions.get(i);
				actionsneu.add(akt);
				actions.remove(akt);
				for(int k = 0; k < actions.size(); k++) {
					if(actions.get(k) instanceof MigrateVMNode && (((MigrateVMNode) actions.get(k)).getTarget().equals(((StartNode) akt).getPM())
							||(((MigrateVMNode) actions.get(k)).getTarget().equals(((MigrateVMNode) akt).getTarget())))){
						actions.get(k).addVorgaenger(akt);
						actionsneu.add(actions.get(k));
						akt = actions.get(k);
						actions.remove(k);
					}
				}
			}
		}
		for(int i = 0; i < actions.size(); i++) {
			Node akt = null;
			if(actions.get(i) instanceof MigrateVMNode){
				akt = actions.get(i);
				actionsneu.add(akt);
				actions.remove(akt);
				for(int k = 0; k < actions.size(); k++) {
					if(actions.get(k) instanceof MigrateVMNode && ((MigrateVMNode) actions.get(k)).getTarget().equals(((MigrateVMNode)akt).getTarget())){
						actions.get(k).addVorgaenger(akt);
						actionsneu.add(actions.get(k));
						akt = actions.get(k);
						actions.remove(k);
					}
				}
			}
		}
		
		for(int i = 0; i < actions.size(); i++) {
			Node akt = null;
			if(actions.get(i) instanceof ShutDownNode){
				akt = actions.get(i);
				actionsneu.add(akt);
				actions.remove(akt);
				for(int k = 0; k < actions.size(); k++) {
					if(actions.get(k) instanceof MigrateVMNode && ((MigrateVMNode) actions.get(k)).getSource().equals(((ShutDownNode) akt).getPM())){
						actions.get(k).addVorgaenger(akt);
						actionsneu.add(actions.get(k));
						akt = actions.get(k);
						actions.remove(k);
					}
				}
			}
		}
		actions.addAll(actionsneu);
	}
}

