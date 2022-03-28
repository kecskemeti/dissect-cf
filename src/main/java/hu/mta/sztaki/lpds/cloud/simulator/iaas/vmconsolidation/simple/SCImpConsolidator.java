package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.simple;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.ModelBasedConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.PreserveAllocations;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.improver.SimpleConsImprover;

public class SCImpConsolidator extends ModelBasedConsolidator {

	public SCImpConsolidator(IaaSService toConsolidate, long consFreq) {
		super(toConsolidate, consFreq);
	}

	@Override
	protected void processProps() {
	}

	@Override
	protected InfrastructureModel optimize(InfrastructureModel initial) {
		return new InfrastructureModel(initial, PreserveAllocations.singleton, SimpleConsImprover.singleton);
	}

}
