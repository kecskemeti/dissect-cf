package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.GenHelper;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.RandomVMassigner;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */

public abstract class IM_PB_Consolidator extends PopulationBasedConsolidator<InfrastructureModel> {
	protected GenHelper mutator;

	public IM_PB_Consolidator(final IaaSService toConsolidate, final long consFreq) {
		super(toConsolidate, consFreq);
	}

	@Override
	protected InfrastructureModel modelFactory(final InfrastructureModel input, final GenHelper vmAssignment,
			final InfrastructureModel.Improver localsearch) {
		return new InfrastructureModel(input, vmAssignment, localsearch);
	}

	@Override
	protected InfrastructureModel transformInput(final InfrastructureModel input) {
		return input;
	}

	@Override
	protected void createPopArray(final int len) {
		population = new InfrastructureModel[len];
	}

	@Override
	protected void processProps() {
		super.processProps();
		prepareMutator(Double.parseDouble(props.getProperty("mutationProb")));
	}

	/**
	 * Create a new solution by mutating the current one. Each gene (i.e., the
	 * mapping of each VM) is replaced by a random one with probability mutationProb
	 * and simply copied otherwise. Note that the current solution (this) is not
	 * changed.
	 */
	private void prepareMutator(final double mutationProb) {
		mutator = new RandomVMassigner() {
			@Override
			public boolean shouldUseDifferent() {
				return PopulationBasedConsolidator.random.nextDoubleFast() < mutationProb;
			}
		};
	}

}
