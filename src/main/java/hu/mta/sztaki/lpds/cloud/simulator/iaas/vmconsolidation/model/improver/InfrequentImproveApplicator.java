package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.improver;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.PopulationBasedConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */
public class InfrequentImproveApplicator extends InfrastructureModel implements InfrastructureModel.Improver {
	private final InfrastructureModel.Improver base;

	public InfrequentImproveApplicator(final InfrastructureModel.Improver baseImprover) {
		super(new PhysicalMachine[0], 0, false, 0);
		base = baseImprover;
	}

	@Override
	public void improve(final InfrastructureModel im) {
		if (PopulationBasedConsolidator.random.nextDoubleFast()<.4) {
			base.improve(im);
		}
	}

}
