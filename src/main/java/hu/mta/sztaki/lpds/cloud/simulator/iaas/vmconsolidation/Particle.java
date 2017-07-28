package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

	
	/**
	 * @author René Ponto
	 *
	 * The idea for particles is to have a list of double values where all hostPMs in Order of their hosted VMs are.
	 * Therefore the id of the specific PM is used.
	 */

public class Particle {
	
	private double fitnessValue;	// used to evaluate the quality of the solution
	private ArithmeticVector velocity;		// the actual velocity : in which direction shall the solution go?
	private ArithmeticVector location;		// the actual location : possible solution
		
	public Particle() {
		
	}

	public Particle(double fitnessValue, ArithmeticVector velocity, ArithmeticVector location) {
		this.fitnessValue = fitnessValue;
		this.velocity = velocity;
		this.location = location;
	}
	
	/**
	 * Used to get the actual fitnessValue of this particle
	 * @param location The actual result of this particle.
	 * @return new fitnessValue
	 */
	public double evaluateFitnessFunction(ArithmeticVector location) {
		double result = 0;
		
		// TODO problem, need to find a fitness function
				
		return result;
	}
	
	public ArithmeticVector getVelocity() {
		return velocity;
	}

	public void setVelocity(ArithmeticVector velocity) {
		this.velocity = velocity;
	}

	public ArithmeticVector getLocation() {
		return location;
	}

	public void setLocation(ArithmeticVector location) {
		this.location = location;
	}

	public double getFitnessValue() {
		fitnessValue = evaluateFitnessFunction(location);
		return fitnessValue;
	}
}