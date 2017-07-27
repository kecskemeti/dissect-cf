package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.Consolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.ModelPM.State;

/**
 * @author Julian Bellendorf, René Ponto
 * 
 * This class gives the necessary variables and methods for VM consolidation.
 * The main idea is to make an abstract model out of the given PMs and its VMs with the original
 * properties and let an algorithm (optimize) do the new placement of the VMs in order
 * to save power by shutting down unused PMs. Therefore a threshold is made to set
 * the states where migrations are needed of the PMs.
 * 
 * During this process one graph gets created inside the superclass ModelBasedConsolidator with 
 * the following information:
 * 		1. Which PMs shall start?
 * 		2. Which VMs on which PMs shall be migrated to target PMs?
 * 		3. Which PMs shall be shut down?
 * 
 * At last the changes have to get delivered to the simulator. The created graph 
 * has all changes saved in nodes (actions) which are going to be done inside the simulator
 * when there is nothing to be done before doing the action on the actual node.
 */
public abstract class ModelBasedConsolidator extends Consolidator {

	protected List<ModelPM> bins;
	
	private double upperThreshold;
	private double lowerThreshold;

	/**
	 * The constructor for VM consolidation. It expects an IaaSService, a value for the upper threshold,
	 * a value for the lower threshold and a variable which says how often the consolidation shall occur.
	 * 
	 * @param toConsolidate
	 * 			The used IaaSService.
	 * @param upperThreshold
	 * 			The double value representing the upper Threshold.
	 * @param lowerThreshold
	 * 			The double value representing the lower Threshold.
	 * @param consFreq
	 * 			This value determines, how often the consolidation should run.
	 */
	public ModelBasedConsolidator(IaaSService toConsolidate, final double upperThreshold, final double lowerThreshold, long consFreq) {
		super(toConsolidate, consFreq);
		
		// save the thresholds
		this.upperThreshold = upperThreshold;		
		this.lowerThreshold = lowerThreshold;
		
		bins = new ArrayList<>();
	}

	/**
	 * This is the method where the order for consolidation is defined. At first the abstract
	 * model gets created, then the thresholds are set for each PM, afterwards the optimize()-method
	 * is called (which is individualized by each specific consolidator) and the graph gets created 
	 * out of the action-list. At last the changes get done inside the simulator.
	 * 
	 * @param pmList
	 * 				All PMs which are currently registered in the IaaS service.
	 */
	protected void doConsolidation(PhysicalMachine[] pmList) {
		instantiate(pmList);
		for(int i = 0; i < bins.size(); i++) {
			bins.get(i).setLowerThreshold(lowerThreshold);
			bins.get(i).setUpperThreshold(upperThreshold);
		}
		optimize();
		List<Action> actions = modelDiff();
		//Logger.getGlobal().info("Number of actions: "+actions.size());
		createGraph(actions);
		//printGraph(actions);
		performActions(actions);
	}

	/**
	 * In this part all PMs and VMs will be put inside this abstract model. For that
	 * the bins-list contains all PMs as ModelPMs and all VMs as ModelVMs afterwards.
	 * 
	 * @param pmList
	 * 				All PMs which are currently registered in the IaaS service.
	 */
	private void instantiate(PhysicalMachine[] pmList) {
		bins.clear();
		for (int i = 0; i < pmList.length; i++) {
			// now every PM will be put inside the model with its hosted VMs
			PhysicalMachine pm = pmList[i];
			ModelPM bin = new ModelPM(pm, pm.getCapacities().getRequiredCPUs(), 
					pm.getCapacities().getRequiredProcessingPower(),pm.getCapacities().getRequiredMemory(), i + 1);
			for(VirtualMachine vm : pm.publicVms) {
				ModelVM item = new ModelVM(vm, bin, 
						vm.getResourceAllocation().allocated.getRequiredCPUs(), 
						vm.getResourceAllocation().allocated.getRequiredProcessingPower(), 
						vm.getResourceAllocation().allocated.getRequiredMemory(), vm.getVa().id);
				bin.addVM(item);
			}
			bins.add(bin);
		}
		Logger.getGlobal().info("Instantiated model: "+toString());
	}

	/**
	 * The method to do the consolidation. It is individualized by each specific consolidator.
	 */
	protected abstract void optimize();

	/**
	 * Creates the actions-list with migration-/start- and shutdown-actions.
	 * @return The list with all the actions.
	 */
	private List<Action> modelDiff() {
		List<Action> actions=new ArrayList<>();
		int i = 0;
		for(ModelPM bin : bins) {
			if(bin.getState() != State.EMPTY_OFF && !bin.getPM().isRunning())
				actions.add(new StartAction(i++, bin));
			if(bin.getState() == State.EMPTY_OFF && bin.getPM().isRunning())
				actions.add(new ShutDownAction(i++, bin));
			for(ModelVM item : bin.getVMs()) {
				if(item.gethostPM() != item.getInitialPm())
					actions.add(new MigrationAction(i++, item.getInitialPm(), item.gethostPM(), item));
			}
		}
		return actions;
	}

	/**
	 * Determines the dependencies between the actions.
	 * 
	 * @param actions
	 * 				The action-list with all changes that have to be done inside the simulator.
	 */
	private void createGraph(List<Action> actions) {
		for(Action action : actions) {			
			action.determinePredecessors(actions);
		}
	}

	/**
	 * Checks if there are any predecessors of the actual action. If not, its 
	 * execute()-method is called.
	 * @param actions 
	 * 				The action-list with all changes that have to be done inside the simulator.
	 */
	private void performActions(List<Action> actions) {
		for(Action action : actions) {			
			if(action.getPredecessors().isEmpty()) {
				action.execute();
			}
		}
	}

	/**
	 * Creates a graph with the toString()-method of each action.
	 * @param actions 
	 * 				The action-list with all changes that have to be done inside the simulator.
	 */
	public void printGraph(List<Action> actions) {
		String s="";
		for(Action action : actions) {
			s=s+action.toString()+"\n";
			for(Action pred : action.getPredecessors())
				s=s+"    pred: "+pred.toString()+"\n";
		}
		Logger.getGlobal().info(s);
	}

	public List<ModelPM> getBins() {
		return bins;
	}

	/**
	 * The toString()-method, used for debugging.
	 */
	public String toString() {
		String result = "";
		boolean first = true;
		for(ModelPM bin : bins) {
			if(!first)
				result = result+"\n";
			result = result+bin.toString();
			first = false;
		}
		return result;
	}
}
