package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;

/**
	 * @author Rene Ponto
	 *
	 * The idea for particles is to have a list of double values where all hostPMs in Order of their hosted VMs are.
	 * Therefore the id of the specific PM is used.
	 */

public class Particle {
	
	private Fitness fitnessValue;			// used to evaluate the quality of the solution
	private ArithmeticVector velocity;		// the actual velocity : in which direction shall the solution go?
	private ArithmeticVector location;		// the actual location : possible solution
	
	List<ModelVM> items;
	List<ModelPM> bins;
	
	private Fitness personalBest;
	private ArithmeticVector personalBestLocation;
		
	/**
	 * Empty constructor becouse every value of a Particle is set using the setter-methods.
	 */
	public Particle(List<ModelVM> items, List<ModelPM> bins) {
		this.items = items;
		this.bins = bins;
		
		personalBestLocation = new ArithmeticVector();
		personalBest = new Fitness();
		
		// set all values of the personalBest to -1 to show that there is no personalBest at the beginning
		personalBest.nrActivePms = -1;
		personalBest.nrMigrations = -1;
		personalBest.totalOverload = -1;		
	}
	
	/**
	 * Computes the total of PM overloads, aggregated over all PMs and all
	 * resource types. This can be used as a component of the fitness.
	 */
	double getTotalOverload() {
		//roundValues();
		//Logger.getGlobal().info(this.toString());
		
		double result = 0;
		//Now we determine the allocation of every PM
		Map<ModelPM,ResourceVector> allocations = new HashMap<>();
		for(ModelPM pm : bins) {
			allocations.put(pm, new ResourceVector(0,0,0));
		}
		for(int i = 0; i < items.size(); i++) {
			ModelPM pm = bins.get(location.get(i).intValue());
			allocations.get(pm).add(items.get(i).getResources());
		}
		//For each PM, see if it is overloaded; if yes, increase the result accordingly.
		for(ModelPM pm : bins) {
			ResourceVector allocation = allocations.get(pm);
			ConstantConstraints cap = pm.getTotalResources();
			if(allocation.getTotalProcessingPower() > cap.getTotalProcessingPower()*pm.getUpperThreshold())
				result += allocation.getTotalProcessingPower() / (cap.getTotalProcessingPower()*pm.getUpperThreshold());
			if(allocation.getRequiredMemory() > cap.getRequiredMemory()*pm.getUpperThreshold())
				result += allocation.getRequiredMemory() / (cap.getRequiredMemory()*pm.getUpperThreshold());
		}
		return result;
	}

	/**
	 * Compute the number of PMs that should be on, given our mapping. This
	 * is a component of the fitness.
	 */
	private int getNrActivePms() {	
		//clears the list so there is no pm more than once there
		Set<Double> setItems = new LinkedHashSet<Double>(location);
        return setItems.size();
	}
	
	/**
	 * Compute the number of migrations needed, given our mapping. This
	 * can be used as a component of the fitness.
	 */
	int getNrMigrations() {
		//Logger.getGlobal().info(this.toString());
		int result = 0;
		//See for each VM whether it must be migrated.
		for(int i = 0; i < items.size(); i++) {
			ModelPM oldPm = items.get(i).gethostPM();
			ModelPM newPm = bins.get(location.get(i).intValue());		
			if(newPm != oldPm)
				result++;
		}
		return result;
	}
	
	/**
	 * Used to get the actual fitnessValue of this particle
	 * @param location The actual result of this particle.
	 * @return new fitnessValue
	 */
	public void evaluateFitnessFunction() {
		roundValues();
		Fitness result = new Fitness();
		
		result.totalOverload = getTotalOverload();			
		result.nrActivePms = getNrActivePms();
		result.nrMigrations = getNrMigrations();
		
		fitnessValue = result;
	}
	
	public Fitness getPBest() {
		return this.personalBest;
	}
	
	public void setPBest(Fitness fitness) {
		this.personalBest = fitness;
	}
	
	public ArithmeticVector getPBestLocation() {
		return this.personalBestLocation;
	}
	
	public void setPBestLocation(ArithmeticVector loc) {		
		this.personalBestLocation = loc;
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

	public Fitness getFitnessValue() {
		//fitnessValue = evaluateFitnessFunction();
		return fitnessValue;
	}
	
	private void roundValues() {
		for(int i = 0; i < location.size(); i++) {
			
			double value = location.get(i).intValue();
			location.set(i, value);
		}
	}
	
	public String toString() {
		String erg = "Location: " + this.getLocation() + System.getProperty("line.separator") 
			+ "Velocity: " + this.getVelocity() + System.getProperty("line.separator") 
			+ "FitnessValue: " + this.getFitnessValue() + ", PersonalBestFitness: " 
			+ this.getPBest() + System.getProperty("line.separator")
			+ "PersonalBestLocation: " + this.getPBestLocation() + System.getProperty("line.separator");
		
		return erg;
	}
}