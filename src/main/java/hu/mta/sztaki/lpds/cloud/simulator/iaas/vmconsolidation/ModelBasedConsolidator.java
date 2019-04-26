package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Properties;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.Consolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.SimpleConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.IControllablePmScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.actions.Action;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.actions.MigrationAction;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.actions.ShutDownAction;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.actions.StartAction;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelPM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelVM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.PreserveAllocations;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.improver.NonImprover;

/**
 * @author Julian Bellendorf, Rene Ponto, Zoltan Mann
 * 
 *         This class gives the necessary variables and methods for VM
 *         consolidation. The main idea is to make an abstract model out of the
 *         given PMs and its VMs with the original properties and let an
 *         algorithm (optimize) do the new placement of the VMs in order to save
 *         power by shutting down unused PMs.
 * 
 *         After the optimization is done the differences of the ModelPMs /
 *         ModelVMs are calculated and the necessary respective changes are
 *         saved inside Action- Classes. Afterwards a graph is created with an
 *         ordered list of all actions. At last the graph is being executed and
 *         the changes are made to the non- abstract IaaS-System.
 */
public abstract class ModelBasedConsolidator extends Consolidator {

	protected double lowerThreshold, upperThreshold;

	protected Properties props;

	private Action[] previousActions = Action.actArrSample;

	/**
	 * The constructor for VM consolidation. It expects an IaaSService, a value for
	 * the upper threshold, a value for the lower threshold and a variable which
	 * says how often the consolidation shall occur.
	 * 
	 * @param toConsolidate The used IaaSService.
	 * @param consFreq      This value determines, how often the consolidation
	 *                      should run.
	 */
	public ModelBasedConsolidator(final IaaSService toConsolidate, final long consFreq) {
		super(toConsolidate, consFreq);

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
	 * @param pmList All PMs which are currently registered in the IaaS service.
	 */
	protected void doConsolidation(final PhysicalMachine[] pmList) {
		// Cancel this round if there are unfinished actions.
		for (final Action a : previousActions) {
			if (!a.isFinished()) {
				return;
			}
		}
		final InfrastructureModel input = new InfrastructureModel(pmList, lowerThreshold,
				!(toConsolidate.pmcontroller instanceof IControllablePmScheduler), upperThreshold);
		// the input is duplicated before sending it for optimisation to allow
		// consolidators directly change the input model
		final InfrastructureModel solution = optimize(
				new InfrastructureModel(input, PreserveAllocations.singleton, NonImprover.singleton));
		if (solution.isBetterThan(input)) {
			previousActions = modelDiff(solution);
			// Logger.getGlobal().info("Number of actions: "+actions.size());
			createGraph();
			// printGraph(actions);
			performActions();
		}
	}

	public static void clearStatics() {
		SimpleConsolidator.migrationCount = 0;
	}

	private void loadProps() throws InvalidPropertiesFormatException, IOException {
		props = new Properties();
		final File file = new File("consolidationProperties.xml");
		final FileInputStream fileInput = new FileInputStream(file);
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
	 * The method to do the consolidation. It is individualized by each specific
	 * consolidator.
	 * 
	 */
	protected abstract InfrastructureModel optimize(InfrastructureModel initial);

	/**
	 * Creates the actions-list with migration-/start- and shutdown-actions.
	 * 
	 * @return The list with all the actions.
	 */
	private Action[] modelDiff(InfrastructureModel solution) {
		// If we have an externally controllable PM scheduler, then we also create
		// start-up and shut-down actions, otherwise only migration actions
		IControllablePmScheduler controllablePmScheduler = null;
		if (toConsolidate.pmcontroller instanceof IControllablePmScheduler) {
			controllablePmScheduler = (IControllablePmScheduler) toConsolidate.pmcontroller;
		}
		final List<Action> actions = new ArrayList<>();
		if (controllablePmScheduler != null) {
			for (final ModelPM bin : solution.bins) {
				final boolean pmWillBeRunning = PhysicalMachine.ToOnorRunning.contains(bin.getPM().getState());
				if (bin.isHostingVMs()) {
					if (!pmWillBeRunning)
						actions.add(new StartAction(bin, controllablePmScheduler));
				} else {
					if (pmWillBeRunning)
						actions.add(new ShutDownAction(bin, controllablePmScheduler));
				}
			}
		}
		for (final ModelVM item : solution.items) {
			if (item.getHostID() != item.basedetails.initialHost.hashCode()) {
				actions.add(new MigrationAction(item));
			}
		}
		return actions.toArray(Action.actArrSample);
	}

	/**
	 * Determines the dependencies between the actions.
	 * 
	 * @param actions The action-list with all changes that have to be done inside
	 *                the simulator.
	 */
	private void createGraph() {
		for (final Action action : previousActions) {
			action.determinePredecessors(previousActions);
		}
	}

	/**
	 * Checks if there are any predecessors of the actual action. If not, its
	 * execute()-method is called.
	 * 
	 * @param actions The action-list with all changes that have to be done inside
	 *                the simulator.
	 */
	private void performActions() {
		for (final Action action : previousActions) {
			if (action.isReady()) {
				action.execute();
			}
		}
	}

	/**
	 * The toString()-method for the list of actions, used for debugging.
	 */
	public String actionsToString(final List<Action> actions) {
		String result = "";
		boolean first = true;
		for (final Action a : actions) {
			if (!first)
				result = result + "\n";
			result = result + a.toString();
			first = false;
		}
		return result;
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
