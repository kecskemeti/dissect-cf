package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.pso;

import java.util.Arrays;

/**
 * @author Rene Ponto
 *
 *         This class is used to create an Object for the location and the
 *         velocity of particles of the pso-consolidator. Despite of that there
 *         are operations like add an ArithmeticVector, subtract an
 *         ArithmeticVector and multiply it with a constant.
 */
public final class ArithmeticVector {

	final double[] data;

	/**
	 * 
	 * @param baseData is not copied, but referenced from the class. It is assumed
	 *                 that the passed array is not modified outside after receiving
	 *                 it via the constructor! For making an actual copy of the data
	 *                 passed, use the copy constructor.
	 */
	public ArithmeticVector(final double[] baseData) {
		data = baseData;
	}

	public ArithmeticVector(final ArithmeticVector toCopy) {
		data = Arrays.copyOf(toCopy.data, toCopy.data.length);
	}

	/**
	 * Method to add another ArithmeticVector to this one. There is a defined border
	 * so there can no PM be used which is not in the IaaS.
	 * 
	 * @param addMe The second ArithmeticVector, the velocity.
	 */
	public void addUp(final ArithmeticVector addMe) {
		for (int i = 0; i < addMe.data.length; i++) {
			data[i] += addMe.data[i];
		}
	}

	/**
	 * Method to subtract another ArithmeticVector of this one. There is a defined
	 * border for not using a PM with an ID lower than 1, because such a PM does not
	 * exist.
	 * 
	 * @param subtractMe The second ArithmeticVector, the velocity.
	 */
	public void subtract(final ArithmeticVector subtractMe) {
		for (int i = 0; i < subtractMe.data.length; i++) {
			data[i] -= subtractMe.data[i];
		}
	}

	/**
	 * Method to multiply every value of this class with a constant.
	 * 
	 * @param scale The double Value to multiply with.
	 * @return The solution of this operation as a new ArithmeticVector.
	 */
	public void scale(final double scale) {
		for (int i = 0; i < data.length; i++) {
			data[i] *= scale;
		}
	}
}