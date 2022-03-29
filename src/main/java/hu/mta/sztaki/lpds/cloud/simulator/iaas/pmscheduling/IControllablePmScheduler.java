package hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;

/**
 * A PM scheduler that can be controlled externally.
 * 
 * @author Zoltan Mann
 */
public interface IControllablePmScheduler {
	void switchOn(PhysicalMachine pm);
	void switchOff(PhysicalMachine pm);
}
