package hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling;

import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.CapacityChangeEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.ModelBasedConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler.QueueingEvent;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class OnOffScheduler extends PhysicalMachineController implements IControllablePmScheduler {
	
	public OnOffScheduler(IaaSService parent) {
		super(parent);	
	}
	
	/**
	 * Defines to do the following when a new host is (de)registered to the
	 * parent IaaSService:
	 * TODO
	 * For registration the pm will be started.
	 * For deregistration the host will get switched off.
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
					for (int i = 0; i < size; i++) {
						PhysicalMachine pm = alteredPMs.get(i);
						if (PhysicalMachine.ToOnorRunning.contains(pm.getState())) {
							try {
								pm.switchoff(null);
							} catch (VMManagementException | NetworkException e) {
								e.printStackTrace();
							}
						}
					}
				}
			}
		};
	}
	
	/**
	 * TODO
	 * Method for starting a PM.
	 * @param toStart
	 * 			The PhysicalMachine which needs to start.
	 */
	public void startOnePhysicalMachine() {
		final int pmsize = parent.machines.size();
		if (parent.runningMachines.size() != pmsize) {
			for (int i = 0; i < pmsize; i++) {
				final PhysicalMachine n = parent.machines.get(i);
				if (PhysicalMachine.ToOfforOff.contains(n.getState())) {
					n.turnon();
					break;
				}
			}
		}
	}
	
	/**
	 * Describes an event handler that waits until a consolidation run is done. After that
	 * it does the migrations etc. which are given by the consolidator and works itself through
	 * the queue to handle the QueueingEvents.
	 */
	@Override
	protected QueueingEvent getQueueingEvent() {
		return new QueueingEvent() {
			@Override
			public void queueingStarted() {
				
				//TODO after the scheduler receives the starting events from the consolidator, it shall start those PMs first.
				
				if(ModelBasedConsolidator.doingConsolidation == false)
					startOnePhysicalMachine();
			}
		};
	}

	@Override
	public void switchOn(PhysicalMachine pm) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchOff(PhysicalMachine pm) {
		// TODO Auto-generated method stub
		
	}
}
