package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

/**
 * @author Rene Ponto
 *
 * This class manages VM consolidation with a particle swarm optimization algorithm. For that, particles
 * are used to find the best solution (minimized fitness function) for consolidation. At the moment the 
 * amount of currently active PMs, the number of migrations and the amount of overAllocated PMs are used 
 * for the fitness.
 */

public class PsoConsolidator extends MachineLearningConsolidator<Particle> {

	/** the problem dimension, gets defined according to the amounts of VMs */
	private int dimension;	
	
	/** learning factor one */
	private int c1;		
	
	/** learning factor two */
	private int c2;				
	
	/** used to get a new velocity for each particle */
	private final double w = 0.6;
	
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
		setOmitAllocationCheck(true);
	}
	
	/**
	 * Reads the properties file and sets the constant values for consolidation.
	 */
	@Override
	protected void processProps() {
		super.processProps();
		this.c1 = Integer.parseInt(props.getProperty("psoC1"));
		this.c2 = Integer.parseInt(props.getProperty("psoC2"));
	}
	
	/**
	 * The toString-method, used for debugging.
	 */
	public String toString() {
		String erg = "Amount of VMs: " + dimension + ", GlobalBest: " + this.globalBest + ", GlobalBestLocation: " + this.globalBestLocation;		
		return erg;
	}
	
	@Override
	protected Particle modelFactory(final Particle input, final boolean original, final boolean localsearch) {
		final Particle p=new Particle(input, getPopFillIndex(), original, localsearch);
		p.updateLocation();
		p.initVelocity();
		return p;
	}
	
	@Override
	protected void createPopArray(final int len) {
		population=new Particle[len];
	}

	/**
	 * Perform the particle swarm optimization algorithm to optimize the mapping of VMs to PMs.
	 */
	@Override
	protected InfrastructureModel optimize(InfrastructureModel input) {
		// get the dimension by getting the amount of VMs on the actual PMs
		this.dimension = input.items.length;
		initializePopulation(new Particle(input,-1,true,false));
		
		int iterations = 0;
		
		// it is assured that the mappings and the location of each Particle are in sync at the start of the loop
		while(iterations < this.nrIterations) {
			// step 1 - update pBest			
			for(Particle p : population) {
				
				//Logger.getGlobal().info("Before setting best values, Particle " + p.getNumber() + ", " + p.toString());
				
				//p.evaluateFitnessFunction();	
				
				if(iterations == 0) {
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
			if(iterations == 0 || population[bestParticleIndex].evaluateFitnessFunction().isBetterThan(globalBest)) {
				globalBest = population[bestParticleIndex].evaluateFitnessFunction();
				globalBestLocation = population[bestParticleIndex].getLocation();		
			}
			
			//Logger.getGlobal().info("GlobalBest: " + globalBest + ", GlobalBestLocation: " + globalBestLocation);
			
			for(int i = 0; i < population.length; i++) {
				Particle p = population[i];
				
				if(iterations == 0) {
					ArithmeticVector newLoc = p.getLocation().addUp(p.getVelocity());	// adds up the velocity to create the updated location
					p.setLocation(newLoc);		
					p.updateMappings();   	// adjusts the mappings with the new location
					
					// we do not have to update the velocity, because it is updated before updating the location in the loop
				}
				else {
					double r1 = random.nextDouble();
					double r2 = random.nextDouble();
					
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
					p.useLocalSearch();
					p.updateLocation();
					
					//Logger.getGlobal().info("Iteration " + t + ", Updated Particle " + p.getNumber() + System.getProperty("line.separator") + p.toString());
				}
				
			}			
			//Logger.getGlobal().info("In iteration " + t + ", GlobalBest: " + globalBest + ", GlobalBestLocation: " + globalBestLocation);
			iterations++;
		}				
		implementSolution(input);
		return input;
	}	
	
	/**
	 * In this method the hostPM of each VM is compared to the solution of the algorithm, the globalBestLocation.
	 * If the hosts are the same, no migration is needed, if not, we migrate the VM to the new host.
	 */
	private void implementSolution(InfrastructureModel best) {
		//Implement solution in the model
		for(int i = 0; i < dimension; i++) {					
			ModelPM oldPm = best.items[i].gethostPM();
			ModelPM newPm = best.bins[globalBestLocation.get(i).intValue() - 1];		// has to be done because the ids start at one
			if(newPm != oldPm)
				oldPm.migrateVM(best.items[i], newPm);
		}
	}
	
	/**
	 * Method to find the position of the particle with the smallest fitness value of all Particles.
	 * 
	 * @return The position where the value is in the vector.
	 */
	private int getMinPos() {		
		Fitness minValue = population[0].evaluateFitnessFunction();
		int pos = 0;
		
		for(int i = 0; i < population.length; i++) {
			if(population[i].evaluateFitnessFunction().isBetterThan(minValue)) {
				minValue = population[i].evaluateFitnessFunction();
				pos = i;
			}
		}		
		return pos;
	}
	
}