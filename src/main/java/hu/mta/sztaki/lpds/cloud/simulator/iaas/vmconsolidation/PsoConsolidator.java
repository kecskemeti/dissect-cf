package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.Random;
import java.util.Vector;
import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

/**
 * @author Rene Ponto
 *
 * This class manages VM consolidation with a particle swarm optimization algorithm. For that, particles
 * are used to find the best solution (minimized fitness function) for consolidation. At the moment only
 * the amount of currently active PMs is used for the fitness.
 */

public class PsoConsolidator extends ModelBasedConsolidator {

	// constants for doing consolidation
	private int swarmSize;		// defines the amount of particles
	private int nrIterations;	// defines the amount of iterations
	private int dimension;					// the problem dimension, gets defined according to the amounts of VMs	
	private int c1;				// learning factor one
	private int c2;				// learning factor two

	public int count = 1;	// counter for the graph actions

	// create and initialize all necessary components
	private Vector<Particle> swarm = new Vector<Particle>();

	private Fitness globalBest;
	private ArithmeticVector globalBestLocation;

	Random generator = new Random();


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
	}
	
	/**
	 * The toString-method, used for debugging.
	 */
	public String toString() {
		String erg = "Amount of VMs: " + dimension + ", GlobalBest: " + this.globalBest + ", GlobalBestLocation: " + this.globalBestLocation;		
		return erg;
	}

	/**
	 * Method to create a swarm and the defined amount of particles. Each Particle gets a random
	 * location and velocity.
	 */
	private void initializeSwarm() {
		swarm.clear();
		Particle p;
		for(int i = 0; i < swarmSize; i++) {
			p = new Particle(items, bins);
			
			
			// randomize location (deployment of the VMs to PMs) inside a space defined in Problem Set
			ArithmeticVector loc = new ArithmeticVector();
			for(int j = 0; j < this.dimension; j++) {
				
				// create a new double to add random PMs to the list of the actual particle
				double a = generator.nextInt(bins.size());
				
				loc.add(a); 	// add the random PM
			}
			
			// randomize velocity in the range defined in Problem Set
			ArithmeticVector vel = new ArithmeticVector();
			for(int j = 0; j < this.dimension; j++) {
				
				// create a new double to add random PMs to the list of the actual particle
				double a;
				
				// here we make a random chance of getting a PM with a lower id or a higher id
				if(generator.nextBoolean())
					a = + 1;
				else
					a = - 1;
				
				vel.add(a); 	// add the random PM
			}
			
			p.setLocation(loc);
			p.setVelocity(vel);
			
			//give every particle a negative pBestLocation to start
			ArithmeticVector bestLoc = new ArithmeticVector();
			for(int j = 0; j < this.dimension; j++) {
				bestLoc.add(-1.0);
			}
			p.setPBestLocation(bestLoc);
			
			swarm.add(p);
			
			// Logger.getGlobal().info("Added Particle: " + i + ", " + p.toString());
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
		
		for(int i = 0; i < swarmSize; i++) {
			Particle p = swarm.get(i);
			
			//Logger.getGlobal().info("Before setting best values, Particle " + i + " " + p.toString());			
			
			// fitnessFunction has to be evaluated before setting best values
			p.evaluateFitnessFunction();
			
			p.setPBest(p.getFitnessValue());
			p.setPBestLocation(p.getLocation());		
			
			//Logger.getGlobal().info("After setting best values, but before starting iterations, Particle " + i + ", PBest: " + p.getPBest() 
			//+ ", PBestLocation: " + p.getPBestLocation());			
		}
		int t = 0;		// counter for the iterations
		
		while(t < this.nrIterations) {
			// step 1 - update pBest
			for(int i = 0; i < swarmSize; i++) {				
				Particle p = swarm.get(i);				
				//Logger.getGlobal().info("Iteration " + t + ", Particle " + i + ", " + p.toString());	
				
				p.evaluateFitnessFunction();	
				
				//the aim is to minimize the function
				if(p.getFitnessValue().isBetterThan(p.getPBest())) {
					p.setPBest(p.getFitnessValue());
					p.setPBestLocation(p.getLocation());
				}
			}
			
			// step 2 - update gBest
			int bestParticleIndex = getMinPos();	// get the position of the minimum fitness value			
			Logger.getGlobal().info("bestParticleIndex: " + bestParticleIndex + " in Iteration " + t);
			
			// set the new global best fitness / location
			if(t == 0 || swarm.get(bestParticleIndex).getFitnessValue().isBetterThan(globalBest)) {
				globalBest = swarm.get(bestParticleIndex).getFitnessValue();
				globalBestLocation = swarm.get(bestParticleIndex).getLocation();		
			}
			
			//Logger.getGlobal().info("GlobalBest: " + globalBest + ", GlobalBestLocation: " + globalBestLocation);
			
			for(int i = 0; i < swarmSize; i++) {
				Particle p = swarm.get(i);
				
				if(t == 0) {										
					ArithmeticVector newLoc = p.getLocation().addUp(p.getVelocity());				
					p.setLocation(newLoc);					
				}
				else {
					double r1 = generator.nextDouble();
					double r2 = generator.nextDouble();
					
					// step 3 - update velocity
					
					double w = 1 - (((double) t) / nrIterations) * (1 - 0);
					
					ArithmeticVector first = p.getVelocity().multiply(w);
					ArithmeticVector second = p.getPBestLocation().subtract(p.getLocation().multiply(r1 * c1));
					ArithmeticVector third = globalBestLocation.subtract(p.getLocation().multiply(r2 * c2));		
					
					ArithmeticVector newVel = first.addUp(second).addUp(third);				
					p.setVelocity(newVel);
					
					//Logger.getGlobal().info("Particle: " + i + ", new Velocity: " + newVel);
					
					// step 4 - update location

					ArithmeticVector newLoc = p.getLocation().addUp(p.getVelocity());				
					p.setLocation(newLoc);
					
					//Logger.getGlobal().info("Particle: " + i + ", new Location: " + newLoc);					
					//Logger.getGlobal().info("Iteration " + t + ", Updated Particle " + i + System.getProperty("line.separator") + p.toString());
				}
				
			}
			
			Logger.getGlobal().info("In iteration " + t + ", GlobalBest: " + globalBest + ", GlobalBestLocation: " + globalBestLocation);
			
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
			ModelPM newPm = bins.get(globalBestLocation.get(i).intValue());		
			if(newPm != oldPm)
				oldPm.migrateVM(items.get(i), newPm);
		}
		adaptPmStates();
	}
	
	/**
	 * Method to find the position of the particle with the smallest fitness value of all Particles.
	 * @return The position where the value is in the vector.
	 */
	private int getMinPos() {		
		Fitness minValue = swarm.get(0).getFitnessValue();
		int pos = 0;
		
		for(int i = 0; i < swarm.size(); i++) {
			if(swarm.get(i).getFitnessValue().isBetterThan(minValue)) {
				minValue = swarm.get(i).getFitnessValue();
				pos = i;
			}
		}		
		return pos;
	}
	
	public Fitness getGlobalBest() {
		return globalBest;
	}
	
	public ArithmeticVector getGlobalBestLocation() {
		return globalBestLocation;
	}
}