/*
 *  ========================================================================
 *  DIScrete event baSed Energy Consumption simulaTor
 *    					             for Clouds and Federations (DISSECT-CF)
 *  ========================================================================
 *
 *  This file is part of DISSECT-CF.
 *
 *  DISSECT-CF is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or (at
 *  your option) any later version.
 *
 *  DISSECT-CF is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with DISSECT-CF.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  (C) Copyright 2019-20, Gabor Kecskemeti, Rene Ponto, Zoltan Mann
 */
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
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

	private boolean alwaysEnact;

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
		var startTime = Instant.now();
		// Cancel this round if there are unfinished actions.
		if (Arrays.stream(previousActions).anyMatch(Predicate.not(Action::isFinished))) {
			return;
		}
		var input = new InfrastructureModel(pmList, lowerThreshold,
				!(toConsolidate.pmcontroller instanceof IControllablePmScheduler), upperThreshold);
		// the input is duplicated before sending it for optimisation to allow
		// consolidators directly change the input model
		var solution = optimize(
				new InfrastructureModel(input, PreserveAllocations.singleton, NonImprover.singleton));
		if (solution.isBetterThan(input) || alwaysEnact) {
			previousActions = modelDiff(solution);
			// Logger.getGlobal().info("Number of actions: "+actions.size());
			createGraph();
			// printGraph(actions);
			var spentDuration = Duration.between(Instant.now(), startTime);
			//FIXME: assumes that a single tick is a millisecond. Should use a more comprehensive time & unit conversion
			var millisSpent = spentDuration.get(ChronoUnit.MILLIS);
			new DeferredEvent(millisSpent) {
				@Override
				protected void eventAction() {
					performActions();
				}
			};
		}
	}

	public static void clearStatics() {
		SimpleConsolidator.migrationCount = 0;
	}

	private void loadProps() throws IOException {
		props = new Properties();
		final File file = new File("consolidationProperties.xml");
		final FileInputStream fileInput = new FileInputStream(file);
		props.loadFromXML(fileInput);
		fileInput.close();
		lowerThreshold = Double.parseDouble(props.getProperty("lowerThreshold"));
		upperThreshold = Double.parseDouble(props.getProperty("upperThreshold"));
		final String alwaysEnactStr = props.getProperty("alwaysEnact");
		alwaysEnact = Boolean.parseBoolean(alwaysEnactStr);
		if (alwaysEnact) {
			System.err.println(
					"WARNING: Consolidation results are always enacted even if they lead to the same ore worse infrastructure state");
		}
		processProps();
	}

	/**
	 * To be implemented by all model based consolidator derivatives, so they can
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
			if (item.getHostID() != item.basedetails.initialHost().hashCode()) {
				actions.add(new MigrationAction(item));
			}
		}
		return actions.toArray(Action.actArrSample);
	}

	/**
	 * Determines the dependencies between the actions.
	 * 
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
		StringBuilder result = new StringBuilder();
		boolean first = true;
		for (final Action a : actions) {
			if (!first)
				result.append("\n");
			result.append(a.toString());
			first = false;
		}
		return result.toString();
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
