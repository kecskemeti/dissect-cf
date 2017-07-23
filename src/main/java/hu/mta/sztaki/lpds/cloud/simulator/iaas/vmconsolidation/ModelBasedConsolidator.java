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
 * @author Julian Bellendorf, Ren� Ponto
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
public abstract class ModelBasedConsolidator extends Consolidator {

	protected List<ModelPM> bins;

	/**
	 * The constructor for VM consolidation. It expects an IaaSService and a variable
	 * which says how often the consolidation shall occur.
	 * 
	 * @param toConsolidate
	 * 			The used IaaSService.
	 * @param consFreq
	 * 			This value determines, how often the consolidation should run.
	 */
	public ModelBasedConsolidator(IaaSService toConsolidate, long consFreq) {
		super(toConsolidate, consFreq);
		bins = new ArrayList<>();
	}

	protected void doConsolidation(PhysicalMachine[] pmList) {
		instantiate(pmList);
		optimize();
		List<Action> actions=modelDiff();
		//Logger.getGlobal().info("Number of actions: "+actions.size());
		createGraph(actions);
		//printGraph(actions);
		performActions(actions);
	}

	/**
	 * In this part all PMs and VMs will be put inside this abstract model.
	 */
	private void instantiate(PhysicalMachine[] pmList) {
		bins.clear();
		for (int i = 0; i < pmList.length; i++) {
			// now every PM will be put inside the model with its hosted VMs
			PhysicalMachine pm = pmList[i];
			ModelPM bin = new ModelPM(pm, pm.getCapacities().getRequiredCPUs(), 
					pm.getCapacities().getRequiredProcessingPower(),pm.getCapacities().getRequiredMemory(), i + 1);
			for(VirtualMachine vm : pm.publicVms) {
				ModelVM item=new ModelVM(vm, bin, 
						vm.getResourceAllocation().allocated.getRequiredCPUs(), 
						vm.getResourceAllocation().allocated.getRequiredProcessingPower(), 
						vm.getResourceAllocation().allocated.getRequiredMemory(), vm.getVa().id);
				bin.addVM(item);
			}
			bins.add(bin);
		}
		Logger.getGlobal().info("Instantiated model: "+toString());
	}

	protected abstract void optimize();

	private List<Action> modelDiff() {
		List<Action> actions=new ArrayList<>();
		int i=0;
		for(ModelPM bin : bins) {
			if(bin.getState()!=State.EMPTY_OFF && !bin.getPM().isRunning())
				actions.add(new StartAction(i++, bin));
			if(bin.getState()==State.EMPTY_OFF && bin.getPM().isRunning())
				actions.add(new ShutDownAction(i++, bin));
			for(ModelVM item : bin.getVMs()) {
				if(item.gethostPM()!=item.getInitialPm())
					actions.add(new MigrationAction(i++, item.getInitialPm(), item.gethostPM(), item));
			}
		}
		return actions;
	}

	/**
	 * Determine the dependencies between the actions.
	 */
	private void createGraph(List<Action> actions) {
		for(Action action : actions) {			
			action.determinePredecessors(actions);
		}
	}

	//here the necessary actions are performed
	//do the migration, shut a PM down, start a PM
	private void performActions(List<Action> actions) {
		for(Action action : actions) {			
			if(action.getPredecessors().isEmpty()) {
				action.execute();
			}
		}
	}

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
