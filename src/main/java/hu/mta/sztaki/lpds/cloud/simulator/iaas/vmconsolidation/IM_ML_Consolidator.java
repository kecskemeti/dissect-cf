package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

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
	protected InfrastructureModel modelFactory(final InfrastructureModel input, final boolean original,
			final boolean localsearch) {
		// TODO Auto-generated method stub
		return new InfrastructureModel(input, original, localsearch);
	}
	
	@Override
	protected void createPopArray(final int len) {
		population=new InfrastructureModel[len];
	}
}
