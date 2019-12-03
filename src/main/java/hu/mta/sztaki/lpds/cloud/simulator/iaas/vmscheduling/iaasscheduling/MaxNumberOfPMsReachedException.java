package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.iaasscheduling;

/**
 *
 * @author Simon Csaba
 */
public class MaxNumberOfPMsReachedException extends RuntimeException {

	/**
	 * Creates a new instance of <code>NumbreOfPMsReachedException</code>
	 * without detail message.
	 */
	public MaxNumberOfPMsReachedException() {
	}

	/**
	 * Constructs an instance of <code>NumbreOfPMsReachedException</code> with
	 * the specified detail message.
	 *
	 * @param msg the detail message.
	 */
	public MaxNumberOfPMsReachedException(String msg) {
		super(msg);
	}
}
