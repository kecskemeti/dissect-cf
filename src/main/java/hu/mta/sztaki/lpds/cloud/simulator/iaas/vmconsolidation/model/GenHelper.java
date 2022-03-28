package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model;

public interface GenHelper {
	boolean shouldUseDifferent();

	/**
	 * 
	 * @param im
	 * @param vm
	 * @return the PM's id which should be used to host the Vm with the id passed in the parameter
	 */
	int whatShouldWeUse(InfrastructureModel im, int vm);
}