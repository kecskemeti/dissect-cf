package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.MachineLearningConsolidator;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */

public class MutatedInfrastructureModel extends InfrastructureModel {
	private static GenHelper mutator;

	public static void prepareMutator(final double mutationProb) {
		mutator = new RandomVMassigner() {
			@Override
			public boolean shouldUseDifferent() {
				return MachineLearningConsolidator.random.nextDoubleFast() < mutationProb;
			}
		};
	}

	/**
	 * Create a new solution by mutating the current one. Each gene (i.e., the
	 * mapping of each VM) is replaced by a random one with probability mutationProb
	 * and simply copied otherwise. Note that the current solution (this) is not
	 * changed.
	 */

	public MutatedInfrastructureModel(final InfrastructureModel m, final InfrastructureModel.Improver imp) {
		super(m, mutator, imp);
	}
}
