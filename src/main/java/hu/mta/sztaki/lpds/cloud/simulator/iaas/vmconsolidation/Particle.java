package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

public class Particle {
	
	private double fitnessValue;	// used to evaluate the quality of the solution
	private double velocity;		// the actual velocity
	private double location;		// the actual location
	
	private double localBest;
	
	
	public Particle() {
		
	}

	public Particle(double fitnessValue, double velocity, double location) {
		this.fitnessValue = fitnessValue;
		this.velocity = velocity;
		this.location = location;
	}
	
	public double getVelocity() {
		return velocity;
	}

	public void setVelocity(double velocity) {
		this.velocity = velocity;
	}

	public double getLocation() {
		return location;
	}

	public void setLocation(double location) {
		this.location = location;
	}

	public double getFitnessValue() {
		//todo
		return fitnessValue;
	}
}