package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */
public class PreserveAllocations implements GenHelper {
	public static final PreserveAllocations singleton = new PreserveAllocations();

	private PreserveAllocations() {
	}

	@Override
	public boolean shouldUseDifferent() {
		return false;
	}

	/**
	 * This is never to be called!
	 */

	@Override
	public int whatShouldWeUse(final InfrastructureModel im, final int vm) {
		return -1;
	}

}
