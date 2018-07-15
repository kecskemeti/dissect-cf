package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.HashSet;
import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

/**
 * @author Rene Ponto
 *
 *         This class is used to do the consolidation with first fit, i.e. the
 *         target PM for a VM is selected using first fit, the VMs on a PM are
 *         selected using first fit etc.
 */
public class FirstFitConsolidator extends ModelBasedConsolidator {

	HashSet<ModelPM> unchangeableBins;

	/**
	 * The constructor for the First-Fit-Consolidator. Only the consolidation has to
	 * be done. All things like creating the model, doing the changes in the
	 * simulator etc are done by the ModelBasedConsolidator.
	 * 
	 * @param toConsolidate The IaaSService of the superclass Consolidator.
	 * @param consFreq      This value determines, how often the consolidation
	 *                      should run.
	 */
	public FirstFitConsolidator(IaaSService toConsolidate, long consFreq) {
		super(toConsolidate, consFreq);
	}

	@Override
	protected void processProps() {
		// since the properties are not needed, nothing has to be done here.
	}

	/**
	 * The method for doing the consolidation, which means start PMs, stop PMs,
	 * migrate VMs, ... To do that, every action is saved as an Action-Node inside a
	 * graph, which does the changes inside the simulator.
	 */
	@Override
	public InfrastructureModel optimize(InfrastructureModel sol) {
		unchangeableBins = new HashSet<>(sol.bins.length);
		if (isOverAllocated(sol) || isUnderAllocated(sol)) {
			for (int i = 0; i < sol.bins.length; i++) {
				ModelPM pm = sol.bins[i];
				if (isNothingToChange(pm))
					continue;
				if (pm.isUnderAllocated()) {
					migrateUnderAllocatedPM(pm, sol);
				}
				if (pm.isOverAllocated()) {
					migrateOverAllocatedPM(pm, sol);
				}
			}
		}

		/*
		 * //clears the VMlist of each PM, so no VM is in the list more than once
		 * for(ModelPM pm : getBins()) { List<ModelVM> allVMsOnPM = pm.getVMs();
		 * Set<ModelVM> setItems = new LinkedHashSet<ModelVM>(allVMsOnPM);
		 * allVMsOnPM.clear(); allVMsOnPM.addAll(setItems); }
		 */

		return sol;
	}

	interface PMCheck {
		boolean check(ModelPM pm);
	}

	private static final PMCheck oaChecker = new PMCheck() {
		public boolean check(final ModelPM pm) {
			return pm.isOverAllocated();
		};
	};

	private static final PMCheck uaChecker = new PMCheck() {
		public boolean check(final ModelPM pm) {
			return pm.isOverAllocated();
		};
	};

	private boolean checkifStateExists(final PMCheck chk, final InfrastructureModel sol) {
		for (final ModelPM pm : sol.bins) {
			if (chk.check(pm)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Identifies PMs of the bins-ArrayList with the State OVERALLOCATED_RUNNING.
	 * 
	 * @return true, if there is an overAllocated PM.
	 */
	private boolean isOverAllocated(InfrastructureModel sol) {
		return checkifStateExists(oaChecker, sol);
	}

	/**
	 * Identifies PMs of the bins-ArrayList with the State UNDERALLOCATED_RUNNING.
	 * 
	 * @return true, if there is an underAllocated PM.
	 */
	private boolean isUnderAllocated(InfrastructureModel sol) {
		return checkifStateExists(uaChecker, sol);
	}

	/**
	 * This method is written to get a PM where a given VM can be migrated without
	 * changing the status of the PM to 'overAllocated'. This is done by first fit.
	 * We focus first on reusing an existing PM rather than starting a new one.
	 * 
	 * @param toMig The VM which shall be migrated.
	 * @return A PM where the given VM can be migrated; starts a new PM if there is
	 *         no running VM with the needed resources; null is returned if no
	 *         appropriate PM was found.
	 */
	private ModelPM getMigPm(ModelVM toMig, InfrastructureModel sol) {
		// Logger.getGlobal().info("vm="+toMig.toString());
		// now we have to search for a fitting pm
		for (int i = 0; i < sol.bins.length; i++) {
			ModelPM actualPM = sol.bins[i];
			// Logger.getGlobal().info("evaluating pm "+actualPM.toString());
			if (actualPM != toMig.gethostPM() && actualPM.isOn() && actualPM.isHostingVMs()
					&& !actualPM.isOverAllocated() && !unchangeableBins.contains(actualPM)
					&& actualPM.isMigrationPossible(toMig)) {
				return actualPM;
			}
		}

		// now we have to take an empty PM if possible, because no running PM is
		// possible to take the load of the VM
		for (int j = 0; j < sol.bins.length; j++) {
			ModelPM actualPM = sol.bins[j];
			// Logger.getGlobal().info("evaluating pm "+actualPM.toString());
			if (actualPM != toMig.gethostPM() || actualPM.isHostingVMs()) {

				if (actualPM.isMigrationPossible(toMig)) {
					return actualPM;
				}
			} else {
				if (!actualPM.isOn()) {
					return startPM(toMig.getResources(), sol); // start an empty_off PM
				}
			}
		}
		Logger.getGlobal().warning("No appropriate PM found as migPM");
		return null;
	}

	/**
	 * Starts a PM which contains the necessary resources for hosting the previous
	 * VM. This is done by first-fit. If no PM can be started, a warning is thrown
	 * to show that.
	 * 
	 * @param VMConstraints The ResourceConstraints of the VM, which shall be hosted
	 *                      on a not running PM
	 * @return A PM with the needed resources or null if no appropriate PM was
	 *         found.
	 */
	private ModelPM startPM(ResourceConstraints VMConstraints, InfrastructureModel sol) {
		for (ModelPM pm : sol.bins) {
			if (!pm.isOn() && VMConstraints.compareTo(pm.getTotalResources()) <= 0) {
				pm.switchOn(); // start this PM
				return pm;
			}
		}
		Logger.getGlobal().warning("No appropriate PM found");
		return null;
	}

	private boolean isNothingToChange(ModelPM pm) {
		return unchangeableBins.contains(pm);
	}

	/**
	 * This method handles the migration of all VMs of an OverAllocated PM, till the
	 * state changes to NORMAL_RUNNING. To do that, a targetPM will be found for
	 * every VM on this PM and then the migrations will be performed. If not enough
	 * VMs can be migrated, the state of this PM will be changed to
	 * UNCHANGEABLE_OVERALLOCATED.
	 * 
	 * @param source The source PM which host the VMs to migrate.
	 */
	private void migrateOverAllocatedPM(ModelPM source, InfrastructureModel sol) {
		// Logger.getGlobal().info("source="+source.toString());
		while (!isNothingToChange(source) && source.isOverAllocated()) {
			ModelVM actual = source.getVMs().get(0); // now taking the first VM on this PM and try to migrate it to a target
			ModelPM pm = getMigPm(actual, sol);
			// if there is no PM to host the actual VM of the source PM, change the state
			// depending on its acutal state
			if (pm == null) {
				unchangeableBins.add(source);
			} else {
				actual.gethostPM().migrateVM(actual, pm); // do the migration
			}
		}
	}

	/**
	 * This method handles the migration of all VMs of an underAllocated PM. To do
	 * that, a targetPM will be find for every VM on this PM and then the migrations
	 * will be performed, but if not all of the hosted VMs can be migrated (after
	 * this process the PM would still host running VMs), nothing will be changed.
	 * 
	 * @param source The source PM which host the VMs to migrate.
	 */
	private void migrateUnderAllocatedPM(ModelPM source, InfrastructureModel sol) {

		if (!source.isHostingVMs()) {
			source.switchOff();
			return; // if there are no VMs, we cannot migrate anything
		}

		ModelVM[] mvms=source.getVMs().toArray(ModelVM.mvmArrSample);
		for (int i=1;i<=mvms.length;i++) {
			ModelVM v=mvms[mvms.length-i];
			ModelPM t=getMigPm(v,sol);
			if(t!=null) {
				source.migrateVM(v, t);
			} else {
				i--;
				if(i==0) {
					unchangeableBins.add(source);
				}
				// cancel out the previous migrations
				for(;i>=1;i--) {
					v=mvms[mvms.length-i];
					v.gethostPM().migrateVM(v, source);
				}
				return;
			}
		}
	}
}