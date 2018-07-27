package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.improver;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */
public class NonImprover implements InfrastructureModel.Improver {
	public static final NonImprover singleton = new NonImprover();

	private NonImprover() {

	}

	@Override
	public void improve(final InfrastructureModel im) {
		// Does nothing
	}
}
