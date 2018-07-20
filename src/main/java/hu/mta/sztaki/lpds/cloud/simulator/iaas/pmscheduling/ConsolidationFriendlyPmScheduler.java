package hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling;

import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.CapacityChangeEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler.QueueingEvent;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

/**
 * A PM scheduler that (i) can be controlled by a VM consolidator for switching
 * on/off PMs and (ii) reacts to requests from a VM scheduler for switching on
 * PMs.
 * 
 * @author Zoltan Mann
 */
public class ConsolidationFriendlyPmScheduler extends PhysicalMachineController implements IControllablePmScheduler {
	final HashSet<PhysicalMachine> startedPMSbecauseofQueue = new HashSet<>();

	public ConsolidationFriendlyPmScheduler(final IaaSService parent) {
		super(parent);
	}

	/**
	 * Remote control for switching on a PM.
	 */
	@Override
	public void switchOn(final PhysicalMachine pm) {
		if (!startedPMSbecauseofQueue.contains(pm)) {
			pm.turnon();
		}
	}

	/**
	 * Remote control for switching off a PM.
	 */
	@Override
	public void switchOff(final PhysicalMachine pm) {
		try {
			if (!startedPMSbecauseofQueue.contains(pm)) {
				pm.switchoff(null);
			}
		} catch (VMManagementException | NetworkException e) {
			System.err.println("Exception while trying to switch off a PM, as instructed by the consolidator");
			e.printStackTrace();
		}
	}

	/**
	 * A PM was registered to / de-registered from the IaaS service -> there is
	 * nothing to do for us.
	 */
	@Override
	protected CapacityChangeEvent<PhysicalMachine> getHostRegEvent() {
		return new CapacityChangeEvent<PhysicalMachine>() {
			@Override
			public void capacityChanged(final ResourceConstraints newCapacity, final List<PhysicalMachine> alteredPMs) {
			}
		};
	}

	/**
	 * The VM scheduler alarms us that there are not enough running PMs -> we should
	 * turn on one or more PMs, if we can and unless sufficient PMs are being turned
	 * on already.
	 */
	@Override
	protected QueueingEvent getQueueingEvent() {
		return new Scheduler.QueueingEvent() {
			@Override
			public void queueingStarted() {
				// First we determine the set of PMs that are off
				// and the total capacity of the PMs that are currently being turned on
				// We should turn on PMs as long as there are PMs that are off
				// and the capacity of the PMs being turned on is not sufficient for the
				// requests in the queue
				AlterableResourceConstraints capacityTurningOn;
				PhysicalMachine toTurnOn;
				do {
					toTurnOn = null;
					capacityTurningOn = AlterableResourceConstraints.getNoResources();
					for (final PhysicalMachine pm : parent.machines) {
						if (toTurnOn == null && PhysicalMachine.ToOfforOff.contains(pm.getState())) {
							toTurnOn = pm;
						}
						if (pm.getState().equals(PhysicalMachine.State.SWITCHINGON)) {
							capacityTurningOn.singleAdd(pm.getCapacities());
						}
					}
					if (capacityTurningOn.compareTo(parent.sched.getTotalQueued()) >= 0) {
						return;
					}
					if (toTurnOn != null) {
						capacityTurningOn.singleAdd(toTurnOn.getCapacities());
						toTurnOn.subscribeStateChangeEvents(new PhysicalMachine.StateChangeListener() {

							@Override
							public void stateChanged(final PhysicalMachine pm, final State oldState,
									final State newState) {
								if (newState.equals(PhysicalMachine.State.RUNNING)) {
									startedPMSbecauseofQueue.remove(pm);
									pm.unsubscribeStateChangeEvents(this);
								}

							}
						});
						startedPMSbecauseofQueue.add(toTurnOn);
						toTurnOn.turnon();
					}
				} while (toTurnOn != null && capacityTurningOn.compareTo(parent.sched.getTotalQueued()) < 0);
			}
		};
	}

}
