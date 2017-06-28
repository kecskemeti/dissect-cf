package hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling;

import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.CapacityChangeEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler.QueueingEvent;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class EmptyScheduler extends PhysicalMachineController {

	public EmptyScheduler(IaaSService parent) {
		super(parent);	
	}
	
	/**
	 * Defines to do the following when a new host is (de)registered to the
	 * parent IaaSService:
	 * 
	 * For registration the host will turn on.
	 * For deregistration the host will switch off.
	 */
	@Override
	protected VMManager.CapacityChangeEvent<PhysicalMachine> getHostRegEvent() {
		return new CapacityChangeEvent<PhysicalMachine>() {
			@Override
			public void capacityChanged(ResourceConstraints newCapacity, List<PhysicalMachine> alteredPMs) {
				final boolean newRegistration = parent.isRegisteredHost(alteredPMs.get(0));
				final int size = alteredPMs.size();
				if (newRegistration) {
					for (int i = 0; i < size; i++) {
						PhysicalMachine pm = alteredPMs.get(i);
						if (PhysicalMachine.ToOfforOff.contains(pm.getState())) {
							pm.turnon();
						}
					}
				} else {
					for (int i = 0; i < size; i++) {
						PhysicalMachine pm = alteredPMs.get(i);
						if (PhysicalMachine.ToOnorRunning.contains(pm.getState())) {
							try {
								pm.switchoff(null);
							} catch (VMManagementException e) {
								e.printStackTrace();
							} catch (NetworkException e) {
								e.printStackTrace();
							}
						}
					}
				}
			}
		};
	}

	/**
	 * Describes an event handler that does nothing upon the start of VM
	 * queueing. This PM controller has not anything to do anyway, becouse
	 * it is normaly used with a consolidator, which manages everything else.
	 */
	@Override
	protected QueueingEvent getQueueingEvent() {
		return new QueueingEvent() {
			@Override
			public void queueingStarted() {
				// do nothing, we already have all the machines running
			}
		};
	}
}
