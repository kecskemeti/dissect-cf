package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

/**
 * @author Rene Ponto
 *
 * The idea for particles is to have a list of double values where all hostPMs in order of their hosted VMs are.
 * Therefore the id of the specific PM is used. On the given list mathematical operations can be done, like addition
 * and substraction. For that list an ArithmeticVector is used.
 * 
 * The ArithmeticVector location contains only the IDs of the PMs while the mapping contains the specific mapping of
 * PMs to VMs inside Solution.
 */

public class Particle extends InfrastructureModel {

	private int number;

	private ArithmeticVector velocity;		// the actual velocity : in which direction shall the solution go?
	private ArithmeticVector location;		// the actual location : possible solution

	private Fitness personalBest;	// the personal best Fitness so far
	private ArithmeticVector personalBestLocation;	// the personal best location so far
	
	/**
	 * Creates a new Particle and sets the bins list with the values out of the model. For that the constructor
	 * of the superclass "Solution" is used.
	 * 
 	 * @param bins The currently existing pms.
 	 * @param number The id of this particle.
 	 */
	public Particle(InfrastructureModel base, int number, boolean orig, boolean localsearch) {
		super(base,orig,localsearch);
		this.number = number;
		this.location = new ArithmeticVector(bins.length);
	}
	
	/**
	 * Has to be called when using the arithmetics or after the mapping has changed. Note that there is a
	 * difference in saving the pms inside the mappings and inside the location.
	 */
	public void updateLocation() {

		//Logger.getGlobal().info("Before updateLocation(), new location: " + location + ", mapping: " + mappingToString());
		
		location.clear();

		for(ModelVM v : items) {
			ModelPM p=v.gethostPM();
			for(int i=0;i<bins.length;i++) {
				if(bins[i]==p) {
					location.add((double)i+1);
					break;
				}
			}
		}
		
		//Logger.getGlobal().info("After updateLocation(), new location: " + location + ", mapping: " + mappingToString());
	}
	
	/**
	 * We use the actual location do update the current mapping. For that we have to check each of
	 * the mappings (loads, mapping, used) and update them depending on the changeds inside the 
	 * location. Note that there is a difference in saving the pms inside the mappings and inside 
	 * the location.
	 */
	public void updateMappings() {
		
		roundValues();			
		
		//Logger.getGlobal().info("Before updateMappings(), location: " + location + ", mapping: " + mappingToString());	
		
		// check if the mappings and the location are different, then adjust the mappings
		for (int i = 0; i < location.size(); i++) {
			
			//System.out.println("bins.size="+bins.size()+", location[i]="+location.get(i));
			ModelPM locPm = bins[location.get(i).intValue() - 1 ];	// the host of this vm, has to be done 
																			// because the ids start at one
			
			ModelPM mappedPm = items[i].gethostPM();
			
			// now we have to check if both hosts are similar, then we can move on with the next.
			// Otherwise we have to adjust the mappings
			if(locPm.hashCode() == mappedPm.hashCode()) {
				continue;	// pms are the same
			}
			else {
				// pms are not the same
				fitness.nrMigrations++;
				mappedPm.migrateVM(items[i], locPm);
			}
		}
		
		// determine the fitness
		this.countActivePmsAndOverloads();
		
		//Logger.getGlobal().info("After updateMappings(), location: " + location + ", mapping: " + mappingToString());
		
	}
	
	/**
	 * Creates the initial velocity of a particle. The velocity points randomly in a 
	 * positive or negative direction.
	 */
	public void initVelocity() {
		ArithmeticVector vel = new ArithmeticVector(bins.length);
		
		for(int j = 0; j < items.length; j++) {			
			double a;			
			// here we make a random chance of getting a lower id or a higher id
			if(PsoConsolidator.random.nextBoolean()) {
				a = + 1;
			}				
			else {
				a = - 1;
			}
			vel.add(a); 	// add the random velocity				
		}
		
		this.setVelocity(vel);
	}

	/**
	 * Get the current fitness values.
	 * 
	 * @return The Fitness-object belonging to this particle.
	 */
	public Fitness evaluateFitnessFunction() {
		return super.evaluate();
	}

	/**
	 * Getter for the number of this particle.
	 * 
	 * @return The number of this particle.
	 */
	public int getNumber() {
		return number;
	}

	/**
	 * Getter for the fitness of this particle.
	 * 
	 * @return The fitness of this particle.
	 */
	public Fitness getPBest() {
		return this.personalBest;
	}

	/**
	 * Sets the personal best fitness with the parametrized Fitness.
	 * 
	 * @param fitness The new Fitness.
	 */
	public void setPBest(Fitness fitness) {
		this.personalBest = fitness;
	}

	/**
	 * Getter for the best location achieved so far.
	 * 
	 * @return An ArithmeticVector with the personal best location.
	 */
	public ArithmeticVector getPBestLocation() {
		return this.personalBestLocation;
	}

	/**
	 * Sets the personal best location with the parametrized ArithmeticVector.
	 * 
	 * @param loc The new best location.
	 */
	public void setPBestLocation(ArithmeticVector loc) {		
		this.personalBestLocation = loc;
	}

	/**
	 * Getter for the current velocity.
	 * 
	 * @return The current velocity of this particle.
	 */
	public ArithmeticVector getVelocity() {
		return velocity;
	}

	/**
	 * Sets the velocity of this particle with the parametrized ArithmeticVector.
	 * 
	 * @param velocity The new velocity.
	 */
	public void setVelocity(ArithmeticVector velocity) {
		this.velocity = velocity;
	}

	/**
	 * Getter for the current location.
	 * 
	 * @return The current location of this particle.
	 */
	public ArithmeticVector getLocation() {
		return location;
	}

	/**
	 * Sets the location of this particle with the parametrized ArithmeticVector.
	 * 
	 * @param location The new location.
	 */
	public void setLocation(ArithmeticVector location) {
		this.location = location;
	}

	/**
	 * Replaces the decimals with one zero to round the values. Can be used after the arithmetics inside the
	 * particle swarm optimization to work with round values.
	 */
	private void roundValues() {
		for(int i = 0; i < location.size(); i++) {
		
			double value = location.get(i).intValue();
			location.set(i, value);
		}
	}

	/**
	 * The toString-method, used for debugging.
	 * 
	 * @return A String-object containing the current location, velocity, Fitness, personal best Fitness and 
	 * 		   the personal best location.
	 */
	public String toString() {
		String erg = "Location: " + this.getLocation() + System.getProperty("line.separator") 
			+ "Velocity: " + this.getVelocity() + System.getProperty("line.separator") 
			+ "FitnessValue: " + this.evaluateFitnessFunction() + ", PersonalBestFitness: " 
			+ this.getPBest() + System.getProperty("line.separator")
			+ "PersonalBestLocation: " + this.getPBestLocation() + System.getProperty("line.separator");
	
		return erg;
	}
}