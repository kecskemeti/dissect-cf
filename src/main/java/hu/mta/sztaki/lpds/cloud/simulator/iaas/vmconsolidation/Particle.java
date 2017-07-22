package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.List;
	
	/**
	 * @author René Ponto
	 *
	 * The idea for particles is to have a list of PMs where all hostPMs in Order of their hosted VMs are.
	 */

public class Particle {
	
	private double fitnessValue;	// used to evaluate the quality of the solution
	private double velocity;		// the actual velocity : in which direction shall the solution go?
	private List <ModelPM> location;		// the actual location : possible solution
		
	public Particle() {
		
	}

	public Particle(double fitnessValue, double velocity, List <ModelPM> location) {
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

	public List <ModelPM> getLocation() {
		return location;
	}

	public void setLocation(List <ModelPM> location) {
		this.location = location;
	}

	public double getFitnessValue() {
		//todo
		return fitnessValue;
	}
}