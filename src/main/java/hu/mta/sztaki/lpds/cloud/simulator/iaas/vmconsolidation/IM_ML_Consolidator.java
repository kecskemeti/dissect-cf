package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.GenHelper;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */

public abstract class IM_ML_Consolidator extends MachineLearningConsolidator<InfrastructureModel> {
	public IM_ML_Consolidator(final IaaSService toConsolidate, final long consFreq) {
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
}
