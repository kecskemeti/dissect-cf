package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model;

public interface GenHelper {
	boolean shouldUseDifferent();

	ModelPM whatShouldWeUse(InfrastructureModel im, int vm);
}