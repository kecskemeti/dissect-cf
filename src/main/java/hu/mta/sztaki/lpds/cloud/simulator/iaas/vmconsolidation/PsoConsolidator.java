package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

/**
 * @author Rene Ponto
 *
 * This class manages VM consolidation with a particle swarm optimization algorithm. For that, particles
 * are used to find the best solution (minimized fitness function) for consolidation. At the moment the 
 * amount of currently active PMs, the number of migrations and the amount of overAllocated PMs are used 
 * for the fitness.
 */

public class PsoConsolidator extends SolutionBasedConsolidator {

	/** defines the amount of particles */
	private int swarmSize;		
	
	/** defines the amount of iterations */
	private int nrIterations;	
	
	/** the problem dimension, gets defined according to the amounts of VMs */
	private int dimension;	
	
	/** learning factor one */
	private int c1;		
	
	/** learning factor two */
	private int c2;				
	
	/** used to get a new velocity for each particle */
	private final double w = 0.6;
	
	/** For generating random numbers */
	private Random generator;

	/** counter for the graph actions */
	public int count = 1;	
	
	/** counter for creating particles */
	private int particleCounter = 1; 

	/** the swarm with all created particles */
	private Vector<Particle> swarm = new Vector<Particle>();

	/** the best fitness values so far */
	private Fitness globalBest;
	
	/** the best fitness values so far */
	private ArithmeticVector globalBestLocation;

	
	/**
	 * The constructor uses its superclass-constructor to create an abstract model and work on the 
	 * modelled PMs and VMs. After finding the solution everything will be done inside the simulator.
	 * 
	 * @param toConsolidate
	 * 			The iaas service with the machines to consolidate.	
	 * @param consFreq
	 * 			This value determines, how often the consolidation should run.
	 */
	public PsoConsolidator(IaaSService toConsolidate, long consFreq) {
		super(toConsolidate, consFreq);		
	}
	
	/**
	 * Reads the properties file and sets the constant values for consolidation.
	 */
	@Override
	protected void processProps() {
	
		this.swarmSize = Integer.parseInt(props.getProperty("psoSwarmSize"));
		this.nrIterations = Integer.parseInt(props.getProperty("psoNrIterations"));
		this.c1 = Integer.parseInt(props.getProperty("psoC1"));
		this.c2 = Integer.parseInt(props.getProperty("psoC2"));
		this.generator = new Random(Long.parseLong(props.getProperty("seed")));
		
		determineCreations(swarmSize);
	}
	
	/**
	 * The toString-method, used for debugging.
	 */
	public String toString() {
		String erg = "Amount of VMs: " + dimension + ", GlobalBest: " + this.globalBest + ", GlobalBestLocation: " + this.globalBestLocation;		
		return erg;
	}

	/**
	 * Method to create a swarm and the defined amount of particles. Each Particle gets a 
	 * random location and velocity except one, which represents the situation before 
	 * starting consolidating.
	 */
	private void initializeSwarm() {
		swarm.clear();
		for(int i = 0; i < swarmSize; i++) {
			swarm.add(new Particle(bins, particleCounter));
			++particleCounter;
		}
		
		//create random solutions
		for(int i = 0; i < randomCreations; i++) {
			Particle p = swarm.get(i);
			p.fillRandomly();
			p.initVelocity(false);
		}
		//create firstfit solutions
		for(int i = randomCreations; i < firstFitCreations + randomCreations; i++) {
			Particle p = swarm.get(i);
			p.createFirstFitSolution();
			p.initVelocity(false);
		}
		//create unchanged solutions
		for(int i = firstFitCreations + randomCreations; i < firstFitCreations + randomCreations + unchangedCreations; i++) {
			Particle p = swarm.get(i);
			p.createUnchangedSolution();
			p.initVelocity(true);
		}
		
	}
	
	/**
	 * Perform the particle swarm optimization algorithm to optimize the mapping of VMs to PMs.
	 */
	@Override
	protected void optimize() {
		// get the dimension by getting the amount of VMs on the actual PMs
		this.dimension = items.size();
		initializeSwarm();
		
		int t = 0;		// counter for the iterations
		
		while(t < this.nrIterations) {
			// step 1 - update pBest
			for(Particle p : swarm) {
				
				//Logger.getGlobal().info("Before setting best values, Particle " + p.getNumber() + ", " + p.toString());
				
				//p.evaluateFitnessFunction();	
				
				if(t == 0) {
					p.setPBest(p.evaluateFitnessFunction());
					p.setPBestLocation(p.getLocation());
				}				
				
				//the aim is to minimize the function
				if(p.evaluateFitnessFunction().isBetterThan(p.getPBest())) {
					p.setPBest(p.evaluateFitnessFunction());
					p.setPBestLocation(p.getLocation());
				}
				//Logger.getGlobal().info("Iteration " + t + ", Particle " + p.getNumber() + ", " + p.toString());
			}
			
			// step 2 - update gBest
			int bestParticleIndex = getMinPos();	// get the position of the minimum fitness value			
			//Logger.getGlobal().info("bestParticleIndex: " + bestParticleIndex + " in Iteration " + t);
			
			// set the new global best fitness / location
			if(t == 0 || swarm.get(bestParticleIndex).evaluateFitnessFunction().isBetterThan(globalBest)) {
				globalBest = swarm.get(bestParticleIndex).evaluateFitnessFunction();
				globalBestLocation = swarm.get(bestParticleIndex).getLocation();		
			}
			
			//Logger.getGlobal().info("GlobalBest: " + globalBest + ", GlobalBestLocation: " + globalBestLocation);
			
			for(int i = 0; i < swarmSize; i++) {
				Particle p = swarm.get(i);
				
				if(t == 0) {
					p.updateLocation();		// creates an ArithmeticVector out of the initial mapping
					ArithmeticVector newLoc = p.getLocation().addUp(p.getVelocity());	// adds up the velocity to create the updated location
					p.setLocation(newLoc);		
					p.updateMappings();   	// adjusts the mappings with the new location
				}
				else {
					double r1 = generator.nextDouble();
					double r2 = generator.nextDouble();
					
					// step 3 - update velocity
					
					/**
					 * Comment on the function to update the velocity:
					 * 
					 * At first the actual Velocity of the Particle has to be multiplied with the user-supplied coefficient w,
					 * which is called the inertiaComponent. After this the cognitiveComponent has to be added to the 
					 * inertiaComponent, which inherits the multiplication of the coefficients c1 and r1, multiplied with the 
					 * result of the personalBestLocation of the Particle minus the actual location of it. Afterwards the 
					 * socialComponent also has to be added to the parts before. It inherits the multiplication of the
					 * coefficients c2 and r2, multiplied with the result of the subtraction of the globalBestLocation with the
					 * actual location of the Particle.
					 */
					
					ArithmeticVector inertiaComponent = p.getVelocity().multiply(w);
					ArithmeticVector cognitiveComponent = (p.getPBestLocation().subtract(p.getLocation()).multiply(c1 * r1));
					ArithmeticVector socialComponent = (globalBestLocation.subtract(p.getLocation()).multiply(c2 * r2));
					ArithmeticVector newVel = inertiaComponent.addUp(cognitiveComponent).addUp(socialComponent);
					
					p.setVelocity(newVel);
					
					//Logger.getGlobal().info("Particle: " + p.getNumber() + ", new Velocity: " + newVel);
					
					// step 4 - update location

					// now the mapping has to be converted to an ArithmeticVector
					p.updateLocation();	
					ArithmeticVector newLoc = p.getLocation().addUp(p.getVelocity());	// adds up the velocity to create the updated location
					p.setLocation(newLoc);		
					p.updateMappings();   	// adjusts the mappings with the new location
										
					if(p.doLocalSearch1) {
						//Logger.getGlobal().info("For particle " + p.getNumber() + ", starting local search");
						p.improve();
						p.updateLocation();	// we have to update the location afterwards
					}
					else if(p.doLocalSearch2){
						p.simpleConsolidatorImprove();
					}
					
					//Logger.getGlobal().info("Iteration " + t + ", Updated Particle " + p.getNumber() + System.getProperty("line.separator") + p.toString());
				}
				
			}
			
			//Logger.getGlobal().info("In iteration " + t + ", GlobalBest: " + globalBest + ", GlobalBestLocation: " + globalBestLocation);
			
			// increase counter for the iterations
			t++;
		}				
		implementSolution();
	}	
	
	/**
	 * In this method the hostPM of each VM is compared to the solution of the algorithm, the globalBestLocation.
	 * If the hosts are the same, no migration is needed, if not, we migrate the VM to the new host.
	 */
	private void implementSolution() {
		//Implement solution in the model
		for(int i = 0; i < dimension; i++) {					
			ModelPM oldPm = items.get(i).gethostPM();
			ModelPM newPm = bins.get(globalBestLocation.get(i).intValue() - 1);		// has to be done because the ids start at one
			if(newPm != oldPm)
				oldPm.migrateVM(items.get(i), newPm);
		}
		adaptPmStates();
}
	
	/**
	 * Method to find the position of the particle with the smallest fitness value of all Particles.
	 * 
	 * @return The position where the value is in the vector.
	 */
	private int getMinPos() {		
		Fitness minValue = swarm.get(0).evaluateFitnessFunction();
		int pos = 0;
		
		for(int i = 0; i < swarm.size(); i++) {
			if(swarm.get(i).evaluateFitnessFunction().isBetterThan(minValue)) {
				minValue = swarm.get(i).evaluateFitnessFunction();
				pos = i;
			}
		}		
		return pos;
	}
	
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

	public class Particle extends Solution {
	
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
		public Particle(List<ModelPM> bins, int number) {
			super(bins, 0);
			this.number = number;
			this.location = new ArithmeticVector();
		}
		
		/**
		 * Has to be called when using the arithmetics or after the mapping has changed. Note that there is a
		 * difference in saving the pms inside the mappings and inside the location.
		 */
		public void updateLocation() {

			//Logger.getGlobal().info("Before updateLocation(), new location: " + location + ", mapping: " + mappingToString());
			
			// clear the location
			location.clear();
			
			// we have to save the mapping in the order of the vms, which means that we have to add at first the
			// host pm for vm1, then the host pm for vm2 and so on
			
			// TODO use comparator
			// sort the map
			int x = 0;
			Map<ModelVM, ModelPM> newMapping = new HashMap<>();
			
			while(x < items.size()) {
				for (ModelVM vm : mapping.keySet()) {
					if(vm.hashCode() == items.get(x).hashCode()) {
						newMapping.put(vm, mapping.get(vm));
						++x;
					}
				}
			}			
			
			// now fill the location
			for (ModelVM vm : newMapping.keySet()) {
				location.add( (double) mapping.get(vm).hashCode() );	// save the id of the pm
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
				
				ModelVM currentVm = items.get(i);	// the first vm of the location
				ModelPM currentPm = bins.get( location.get(i).intValue() - 1 );	// the host of this vm, has to be done 
																				// because the ids start at one
				
				ModelPM mappedPm = mapping.get(currentVm);
				
				// now we have to check if both hosts are similar, then we can move on with the next.
				// Otherwise we have to adjust the mappings
				if(currentPm == mappedPm) {
					continue;	// pms are the same
				}
				else {
					fitness.nrMigrations++;
					mapping.put(currentVm, currentPm);	//put the new pm
					
					loads.get(mappedPm).subtract(currentVm.getResources());
					loads.get(currentPm).singleAdd(currentVm.getResources());
					
					// check if the pm is still used
					if(used.containsKey(mappedPm)) {
						used.put(mappedPm, true);
					}
					else {
						used.put(mappedPm, false);
					}
				}
			}
			
			// determine the fitness
			this.countActivePmsAndOverloads();
			
			// the actual mappings
			//Logger.getGlobal().info("mapping: " + mappingToString());
			//Logger.getGlobal().info("loads: " + loadsToString());
			//Logger.getGlobal().info("used: " + usedToString());
			
			//Logger.getGlobal().info("After updateMappings(), location: " + location + ", mapping: " + mappingToString());
			
		}
		
		/**
		 * Creates the initial velocity of a particle. If the solution is unchanged, the velocity is 
		 * initializes with '0', otherwise it points randomly in a positive or negative direction.
		 * 
		 * @param unchanged Determines if this is an unchanged solution.
		 */
		public void initVelocity(boolean unchanged) {
			ArithmeticVector vel = new ArithmeticVector();
			
			if(unchanged) {
				for(int j = 0; j < mapping.keySet().size(); j++) {
					vel.add(0.0);
				}				
			}
			else {
				for(int j = 0; j < mapping.keySet().size(); j++) {			
					double a;			
					// here we make a random chance of getting a lower id or a higher id
					if(generator.nextBoolean()) {
						a = + 1;
					}				
					else {
						a = - 1;
					}
					vel.add(a); 	// add the random velocity
				}
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
	
}