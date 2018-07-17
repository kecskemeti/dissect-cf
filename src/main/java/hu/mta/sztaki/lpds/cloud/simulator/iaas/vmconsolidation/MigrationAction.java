package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

/**
 * This class stores actions, that need to commit a migration in the simulator.
 */
public class MigrationAction extends Action implements VirtualMachine.StateChange {

	// Reference to the model of the VM, which needs be migrated
	public final ModelVM mvm;

	/**
	 * Constructor for an action which shall migrate a VM inside the simulator.
	 * 
	 * @param id     The ID of this action.
	 * @param source The PM which is currently hosting the VM.
	 * @param target The PM which shall host this VM after migration.
	 * @param vm     The reference to the VM which shall be migrated.
	 */
	public MigrationAction(final int id, final ModelVM vm) {
		super(id, Type.MIGRATION);
		this.mvm = vm;
	}

	/**
	 * This method determines the predecessors of this action. A predecessor of a
	 * migration action is a starting action, which starts the target PM of this
	 * action. Furthermore, migrations from our target PM are also considered
	 * predecessors, in order to prohibit temporary overloads of the PM. TODO: this
	 * needs improvement, as it can currently lead to deadlocks.
	 */
	@Override
	public void determinePredecessors(final List<Action> actions) {
		// looking for actions where a PM gets started, that is the target of this
		// migration
		for (final Action action : actions) {
			if (action.type.equals(Type.START)) {
				if ((((StartAction) action).pmToStart.hashCode() == mvm.gethostPM().hashCode())) {
					this.addPredecessor(action);
				}
			}
			// If two PMs would like to migrate one VM to each other,
			// there could be a loop. Not solved yet.
			if (action.type.equals(Type.MIGRATION)) {
				if (((MigrationAction) action).mvm.basedetails.initialHost.hashCode() == mvm.gethostPM().hashCode()) {
					this.addPredecessor(action);
				}
			}
		}
	}

	@Override
	public String toString() {
		return super.toString() + " Source:  " + mvm.basedetails.initialHost.toShortString() + " Target: "
				+ mvm.gethostPM().toShortString() + " VM: " + mvm.toShortString();
	}

	/**
	 * Method for doing the migration inside the simulator.
	 */
	@Override
	public void execute() {
		final PhysicalMachine simSourcePM = mvm.basedetails.initialHost.getPM();
		final VirtualMachine simVM = mvm.basedetails.vm;
		if (simSourcePM.publicVms.contains(simVM)) {
			final PhysicalMachine simTargetPM = mvm.gethostPM().getPM();
			if (simVM.getMemSize() > simTargetPM.freeCapacities.getRequiredMemory()
					|| simVM.getPerTickProcessingPower() > simTargetPM.freeCapacities.getTotalProcessingPower()) {
				finished();
			} else if (simVM.getState() != VirtualMachine.State.RUNNING
					&& simVM.getState() != VirtualMachine.State.SUSPENDED) {
				finished();
			} else if (simTargetPM.isRunning()) {
				simVM.subscribeStateChange(this); // observe the VM which shall be migrated
				try {
					simSourcePM.migrateVM(simVM, simTargetPM);
				} catch (VMManagementException e) {
					e.printStackTrace();
				} catch (NetworkException e) {
					e.printStackTrace();
				}
			} else {
				finished();
			}
		} else {
			finished();
		}

	}

	/**
	 * The stateChanged-logic, if the VM changes its state to RUNNING after
	 * migrating, then it do not has to be observed any longer.
	 */
	@Override
	public void stateChanged(final VirtualMachine vm, final State oldState, final State newState) {
		if (newState.equals(VirtualMachine.State.RUNNING)) {
			vm.unsubscribeStateChange(this);
			// Logger.getGlobal().info("Migration action finished");
			// Logger.getGlobal().info("Finished at "+Timed.getFireCount()+":
			// "+toString()+", hash="+Integer.toHexString(System.identityHashCode(this)));
			finished();
		}
	}

}
