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
 * the amount of currenty active PMs is used for the fitness.
 */

public class PsoConsolidator extends ModelBasedConsolidator {

	// constants for doing consolidation
	private final int swarmSize = 20;		// defines the amount of particles
	private final int maxIterations = 50;	// defines the maximum of iterations
	private int dimension;					// the problem dimension, gets defined according to the amounts of VMs	
	private final int c1 = 2;				// learning factor one
	private final int c2 = 2;				// learning factor two

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
	 * @param upperThreshold
	 * 			The double value representing the upper Threshold.
	 * @param lowerThreshold
	 * 			The double value representing the lower Threshold.
	 * @param consFreq
	 * 			This value determines, how often the consolidation should run.
	 */
	public PsoConsolidator(IaaSService toConsolidate, final double upperThreshold, final double lowerThreshold,long consFreq) {
		super(toConsolidate, upperThreshold, lowerThreshold, consFreq);
	}
	
	public String toString() {
		String erg = "Amount of VMs: " + dimension + ", GlobalBest: " + this.globalBest + ", GlobalBestLocation: " + this.globalBestLocation;		
		return erg;
	}

	/**
	 * Method to create a swarm and the defined amount of particles. Each Particle gets a random
	 * location and velocity.
	 */
	private void initializeSwarm() {
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
	 * Initializes variables, sets the problem dimension and creates the swarm.
	 */
	private void initializePSO() {
		// get the dimension by getting the amount of VMs on the actual PMs
		this.dimension = items.size();
		
		this.globalBest = new Fitness();
		
		// set all values of the personalBest to -1 to show that there is no personalBest at the beginning
		globalBest.nrActivePms = -1;
		globalBest.nrMigrations = -1;
		globalBest.totalOverload = -1;	
		
		this.globalBestLocation = new ArithmeticVector();
		
		//Logger.getGlobal().info("Initialized PSO, " + this.toString());
		
		//has to be done before starting the actual algorithm
		initializeSwarm();
	}

	@Override
	protected void optimize() {
		
		initializePSO();
		
		
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
		
		while(t < this.maxIterations) {
			// step 1 - update pBest
			for(int i = 0; i < swarmSize; i++) {
				
				Particle p = swarm.get(i);
				
				Logger.getGlobal().info("Iteration " + t + ", Particle " + i + ", " + p.toString());
				
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
			
			if(t == 0 || swarm.get(bestParticleIndex).getFitnessValue().isBetterThan(globalBest)) {
				globalBest = swarm.get(bestParticleIndex).getFitnessValue();
				globalBestLocation = swarm.get(bestParticleIndex).getLocation();		// gets size of one, error 
			}
			
			//Logger.getGlobal().info("GlobalBest: " + globalBest + ", GlobalBestLocation: " + globalBestLocation);
			
			for(int i = 0; i < swarmSize; i++) {
				
				if(t == 0) {
					Particle p = swarm.get(i);
					
					ArithmeticVector newLoc = p.getLocation().addUp(p.getVelocity());				
					p.setLocation(newLoc);
					
				}
				else {
					double r1 = generator.nextDouble();
					double r2 = generator.nextDouble();
					
					Particle p = swarm.get(i);
					
					// step 3 - update velocity
					
					double w = 1 - (((double) t) / maxIterations) * (1 - 0);
					
					ArithmeticVector first = p.getVelocity().multiply(w);
					ArithmeticVector second = p.getPBestLocation().subtract(p.getLocation().multiply(r1 * c1));
					ArithmeticVector third = globalBestLocation.subtract(p.getLocation().multiply(r2 * c2));		
					
					//Logger.getGlobal().info("Particle: " + i + ", " + first.size() + ", " + second.size() + ", " + third.size());
					
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
			
			// last step
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
	 * Method to find the smallest fitness value of all Particles.
	 * @param swarm The vector with all Particles.
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
}