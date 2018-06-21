package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.Consolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.SimpleConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.IControllablePmScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.ModelPM.State;

/**
 * @author Julian Bellendorf, Rene Ponto, Zoltan Mann
 * 
 *         This class gives the necessary variables and methods for VM
 *         consolidation. The main idea is to make an abstract model out of the
 *         given PMs and its VMs with the original properties and let an
 *         algorithm (optimize) do the new placement of the VMs in order to save
 *         power by shutting down unused PMs. 
 *         
 *         After the optimization is done the differences of the ModelPMs / ModelVMs
 *         are calculated and the necessary respective changes are saved inside Action-
 *         Classes. Afterwards a graph is created with an ordered list of all actions.
 *         At last the graph is being executed and the changes are made to the non-
 *         abstract IaaS-System.
 */
public abstract class ModelBasedConsolidator extends Consolidator {

	protected List<ModelPM> bins;
	protected List<ModelVM> items;
	protected double lowerThreshold, upperThreshold;

	protected Properties props;
	
	public static boolean doingConsolidation = false;

	/**
	 * The constructor for VM consolidation. It expects an IaaSService, a value for
	 * the upper threshold, a value for the lower threshold and a variable which
	 * says how often the consolidation shall occur.
	 * 
	 * @param toConsolidate
	 *            The used IaaSService.
	 * @param consFreq
	 *            This value determines, how often the consolidation should run.
	 */
	public ModelBasedConsolidator(IaaSService toConsolidate, long consFreq) {
		super(toConsolidate, consFreq);

		bins = new ArrayList<>();
		items = new ArrayList<>();
		try {
			loadProps();
		} catch (InvalidPropertiesFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This is the method where the order for consolidation is defined. Before the 
	 * consolidation starts, we create a model of the current situation of the IaaS. 
	 * Then the consolidation-algorithm is invoked. After the optimization is done 
	 * the differences of the ModelPMs / ModelVMs are calculated and the necessary 
	 * respective changes are saved inside Action-Classes. Afterwards a graph is 
	 * created with an ordered list of all actions. At last the graph is being 
	 * executed and the changes are made to the non-abstract IaaS-System.
	 * 
	 * @param pmList
	 *            All PMs which are currently registered in the IaaS service.
	 */
	protected void doConsolidation(PhysicalMachine[] pmList) {
		doingConsolidation = true;
		instantiate(pmList);
		optimize();
		Logger.getGlobal().info("Optimized model: " + toString());
		List<Action> actions = modelDiff();
		Logger.getGlobal().info(actionsToString(actions));
		// Logger.getGlobal().info("Number of actions: "+actions.size());
		createGraph(actions);
		// printGraph(actions);
		performActions(actions);
		doingConsolidation = false;
	}

	public static void clearStatics() {
		SimpleConsolidator.migrationCount = 0;
	}

	private void loadProps() throws InvalidPropertiesFormatException, IOException {
		props = new Properties();
		File file = new File("consolidationProperties.xml");
		FileInputStream fileInput = new FileInputStream(file);
		props.loadFromXML(fileInput);
		fileInput.close();
		lowerThreshold = Double.parseDouble(props.getProperty("lowerThreshold"));
		upperThreshold = Double.parseDouble(props.getProperty("upperThreshold"));
		processProps();
	}

	/**
	 * To be implemented by all model based consolidator derivatives so they can
	 * load the relevant parameters from the file
	 */
	protected abstract void processProps();

	/**
	 * In this part all PMs and VMs will be put inside this abstract model. For that
	 * the bins-list contains all PMs as ModelPMs and all VMs as ModelVMs
	 * afterwards.
	 * 
	 * @param pmList
	 *            All PMs which are currently registered in the IaaS service.
	 */
	private void instantiate(PhysicalMachine[] pmList) {
		bins.clear();
		items.clear();
		int vmIndex = 0;
		for (int i = 0; i < pmList.length; i++) {
			// now every PM will be put inside the model with its hosted VMs
			PhysicalMachine pm = pmList[i];
			//If using a non-externally-controlled PM scheduler, consider only non-empty PMs for consolidation
			if(!(pm.isHostingVMs()) && !(toConsolidate.pmcontroller instanceof IControllablePmScheduler))
				continue;
			ModelPM bin = new ModelPM(pm, i + 1, upperThreshold, lowerThreshold);
			for (VirtualMachine vm : pm.publicVms) {
				vmIndex++;
				ModelVM item = new ModelVM(vm, bin, vmIndex);
				bin.addVM(item);
				items.add(item);
			}
			bins.add(bin);
		}
		
		Logger.getGlobal().info("Instantiated model at "+Timed.getFireCount()+": " + toString());
	}

	/**
	 * The method to do the consolidation. It is individualized by each specific
	 * consolidator.
	 */
	protected abstract void optimize();

	/**
	 * Creates the actions-list with migration-/start- and shutdown-actions.
	 * 
	 * @return The list with all the actions.
	 */
	private List<Action> modelDiff() {
		//If we have an externally controllable PM scheduler, then we also create start-up and shut-down actions, otherwise only migration actions
		IControllablePmScheduler controllablePmScheduler=null;
		if(toConsolidate.pmcontroller instanceof IControllablePmScheduler) {
			controllablePmScheduler=(IControllablePmScheduler) toConsolidate.pmcontroller;
		}
		List<Action> actions = new ArrayList<>();
		int i = 0;
		for (ModelPM bin : bins) {
			if(controllablePmScheduler!=null) {
				if (bin.getState() != State.EMPTY_OFF && !bin.getPM().isRunning())
					actions.add(new StartAction(i++, bin, controllablePmScheduler));
				if (bin.getState() == State.EMPTY_OFF && bin.getPM().isRunning())
					actions.add(new ShutDownAction(i++, bin, controllablePmScheduler));
			}
			for (ModelVM item : bin.getVMs()) {
				if (item.gethostPM() != item.getInitialPm()) {
					actions.add(new MigrationAction(i++, item.getInitialPm(), item.gethostPM(), item));
					SimpleConsolidator.migrationCount++;
				}
			}
		}
		return actions;
	}

	/**
	 * Determines the dependencies between the actions.
	 * 
	 * @param actions
	 *            The action-list with all changes that have to be done inside the
	 *            simulator.
	 */
	private void createGraph(List<Action> actions) {
		for (Action action : actions) {
			action.determinePredecessors(actions);
		}
	}

	/**
	 * Checks if there are any predecessors of the actual action. If not, its
	 * execute()-method is called.
	 * 
	 * @param actions
	 *            The action-list with all changes that have to be done inside the
	 *            simulator.
	 */
	private void performActions(List<Action> actions) {
		for (Action action : actions) {
			if (action.getPredecessors().isEmpty()) {
				action.execute();
			}
		}
	}

	/**
	 * Creates a graph with the toString()-method of each action.
	 * 
	 * @param actions
	 *            The action-list with all changes that have to be done inside the
	 *            simulator.
	 */
	public void printGraph(List<Action> actions) {
		String s = "";
		for (Action action : actions) {
			s = s + action.toString() + "\n";
			for (Action pred : action.getPredecessors())
				s = s + "    pred: " + pred.toString() + "\n";
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
		for (ModelPM bin : bins) {
			if(!bin.isHostingVMs())
				continue;
			if (!first)
				result = result + "\n";
			result = result + bin.toString();
			first = false;
		}
		return result;
	}

	/**
	 * The toString()-method for the list of actions, used for debugging.
	 */
	public String actionsToString(List<Action> actions) {
		String result = "";
		boolean first = true;
		for (Action a : actions) {
			if (!first)
				result = result + "\n";
			result = result + a.toString();
			first = false;
		}
		return result;
	}

	/**
	 * This method can be called after all migrations were done. It is checked which
	 * PMs do not have any VMs hosted and then this method shut them down. A node is
	 * created to add this information to the graph.
	 */
	protected void shutDownEmptyPMs() {
		for (ModelPM pm : getBins()) {
			if (!pm.isHostingVMs() && pm.getState() != State.EMPTY_OFF) {
				pm.switchOff(); // shut down this PM
			}
		}
	}

	/**
	 * PMs that should host at least one VM are switched on.
	 */
	protected void switchOnNonEmptyPMs() {
		for (ModelPM pm : getBins()) {
			if (pm.isHostingVMs() && pm.getState() == State.EMPTY_OFF) {
				pm.switchOn();
			}
		}
	}

	/**
	 * PMs that should host at least one VM are switched on; PMs that should host no
	 * VM are switched off.
	 */
	protected void adaptPmStates() {
		shutDownEmptyPMs();
		switchOnNonEmptyPMs();
	}
	
	/**
	 * Getter for the upper Threshold of each PM.
	 * 
	 * @return The upperThreshold.
	 */
	public double getUpperThreshold() {
		return upperThreshold;
	}

	/**
	 * Getter for the lower Threshold of each PM.
	 * 
	 * @return The lowerThreshold.
	 */
	public double getLowerThreshold() {
		return lowerThreshold;
	}

}
