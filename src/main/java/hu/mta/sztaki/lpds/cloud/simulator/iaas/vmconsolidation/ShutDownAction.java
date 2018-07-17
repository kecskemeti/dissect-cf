package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.IControllablePmScheduler;

/**
 * This class stores actions, which need to shut down a PM in the simulator.
 */
public class ShutDownAction extends Action {

	// Reference to the model of the PM, which needs to shut down
	public final ModelPM pmToShutDown;

	/** PM scheduler */
	public final IControllablePmScheduler pmScheduler;

	/**
	 * Constructor for an action to shut a PM down.
	 * 
	 * @param id           The ID of this action.
	 * @param pmToShutDown The reference to the PM inside the simulator to get shut
	 *                     down.
	 * @param pmScheduler  Reference to the PM scheduler of the IaaS service
	 */
	public ShutDownAction(final int id, final ModelPM pmToShutDown, final IControllablePmScheduler pmScheduler) {
		super(id, Type.SHUTDOWN);
		this.pmToShutDown = pmToShutDown;
		this.pmScheduler = pmScheduler;
		// Logger.getGlobal().info("ShutDownAction created");
	}

	/**
	 * This method determines the predecessors of this action. A predecessor of a
	 * shut-down action is a migration from this PM.
	 */
	@Override
	public void determinePredecessors(List<Action> actions) {
		// looking for migrations with this PM as source
		for (final Action action : actions) {
			if (action.type.equals(Type.MIGRATION)) {
				if ((((MigrationAction) action).mvm.basedetails.initialHost.hashCode()==pmToShutDown.hashCode())) {
					this.addPredecessor(action);
				}
			}
		}
	}

	@Override
	public String toString() {
		return super.toString() + pmToShutDown.toShortString();
	}

	/**
	 * This method shuts the PM inside the simulator down.
	 */
	@Override
	public void execute() {
		final PhysicalMachine pm = this.pmToShutDown.getPM();
		if (!pm.isHostingVMs())
			pmScheduler.switchOff(pm);
	}

}