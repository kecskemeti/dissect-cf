package hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling;

import java.util.List;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.CapacityChangeEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler.QueueingEvent;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

/**
 * A PM scheduler that (i) can be controlled by a VM consolidator for
 * switching on/off PMs and (ii) reacts to requests from a VM scheduler for
 * switching on PMs.
 * 
 * @author Zoltan Mann
 */
public class ConsolidationFriendlyPmScheduler extends PhysicalMachineController implements IControllablePmScheduler {

	public ConsolidationFriendlyPmScheduler(IaaSService parent) {
		super(parent);
	}

	/**
	 * Remote control for switching on a PM.
	 */
	@Override
	public void switchOn(PhysicalMachine pm) {
		pm.turnon();
	}

	/**
	 * Remote control for switching off a PM.
	 */
	@Override
	public void switchOff(PhysicalMachine pm) {
		try {
			pm.switchoff(null);
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
			public void capacityChanged(ResourceConstraints newCapacity, List<PhysicalMachine> alteredPMs) {
			}
		};
	}

	/**
	 * The VM scheduler alarms us that there are not enough running PMs -> we
	 * should turn on one or more PMs, if we can and unless sufficient PMs are 
	 * being turned on already.
	 */
	@Override
	protected QueueingEvent getQueueingEvent() {
		return new Scheduler.QueueingEvent() {
			@Override
			public void queueingStarted() {
				//First we determine the set of PMs that are off 
				//and the total capacity of the PMs that are currently being turned on
				Vector<PhysicalMachine> offPms=new Vector<>();
				AlterableResourceConstraints capacityTurningOn=AlterableResourceConstraints.getNoResources();
				for(PhysicalMachine pm : parent.machines) {
					if(PhysicalMachine.ToOfforOff.contains(pm.getState())) {
						offPms.add(pm);
					}
					if(pm.getState().equals(PhysicalMachine.State.SWITCHINGON)) {
						capacityTurningOn.singleAdd(pm.getCapacities());
					}
				}
				//We should turn on PMs as long as there are PMs that are off 
				//and the capacity of the PMs being turned on is not sufficient for the requests in the queue
				while(offPms.size()>0 && capacityTurningOn.compareTo(parent.sched.getTotalQueued())<0) {
					PhysicalMachine pm=offPms.remove(offPms.size()-1);
					capacityTurningOn.singleAdd(pm.getCapacities());
					pm.turnon();
				}
			}
		};
	}

}
