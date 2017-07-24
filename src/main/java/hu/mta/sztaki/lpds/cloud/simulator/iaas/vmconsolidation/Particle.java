package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

<<<<<<< HEAD
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
	
	/**
	 * Used to get the actual fitnessValue of this particle
	 * @param location The actual result of this particle.
	 * @return new fitnessValue
	 */
	public double evaluateFitnessFunction(List<ModelPM> location) {
		double result = 0;
		
		// TODO problem, need to find a fitness function
				
		return result;
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
		fitnessValue = evaluateFitnessFunction(location);
=======
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
>>>>>>> refs/remotes/origin/master
		return fitnessValue;
	}
}