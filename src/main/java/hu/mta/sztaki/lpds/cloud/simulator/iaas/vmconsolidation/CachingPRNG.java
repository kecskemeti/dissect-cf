package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */
public class CachingPRNG {
	private static long cache;
	private static int rem = 0;

	public static boolean genBoolean() {
		if (rem == 0) {
			cache = MachineLearningConsolidator.random.nextLong();
			rem = 64;
		}
		rem--;
		return ((cache = cache >> 1) & 1) == 1;
	}
}
