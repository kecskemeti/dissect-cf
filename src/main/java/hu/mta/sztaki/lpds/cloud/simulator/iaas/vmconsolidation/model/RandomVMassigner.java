package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.MachineLearningConsolidator;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */
public class RandomVMassigner implements GenHelper {
	public static final RandomVMassigner globalRandomAssigner = new RandomVMassigner();

	public boolean shouldUseDifferent() {
		return true;
	}

	@Override
	public int whatShouldWeUse(final InfrastructureModel im, final int vm) {
		return MachineLearningConsolidator.random.nextInt(im.bins.length);
	}
}