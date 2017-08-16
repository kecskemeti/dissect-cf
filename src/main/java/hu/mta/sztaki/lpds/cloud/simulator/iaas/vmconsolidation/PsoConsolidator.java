package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.Random;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

/**
 * @author René Ponto
 *
 * This class manages VM consolidation with a particle swarm optimization algorithm. For that, particles
 * are used to find the best solution (minimized fitness function) for consolidation.
 * 
 * TODO At the moment no migrations are done correctly.
 */

public class PsoConsolidator extends ModelBasedConsolidator {

	// constants for doing consolidation
	private final int swarmSize = 20;		// defines the amount of particles
	private final int maxIterations = 100;	// defines the maximum of iterations
	private int dimension;					// the problem dimension, gets defined according to the amounts of VMs	

	public int count = 1;	// counter for the graph actions

	// create and initialize all necessary components
	private Vector<Particle> swarm = new Vector<Particle>();

	private double globalBest;
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

	/**
	 * Method to create a swarm and a few particles. Should be finished.
	 */
	private void initializeSwarm() {
		Particle p;
		for(int i = 0; i < swarmSize; i++) {
			p = new Particle();
			
			
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
				
				// here we make a 50/50 chance of getting a PM with a lower id or a higher id
				int zeroOrOne = (int) Math.round(Math.random());								
				if(zeroOrOne < 1)
					a = loc.get(j) + generator.nextInt(bins.size());
				else
					a = loc.get(j) - generator.nextInt(bins.size());
				
				vel.add(a); 	// add the random PM
			}
			
			p.setLocation(loc);
			p.setVelocity(vel);
			swarm.add(p);
		}
	}
	
	/**
	 * Initializes variables, sets the problem dimension and creates the swarm.
	 */
	private void initializePSO() {
		// get the dimension by getting the amount of VMs on the actual PMs
		this.dimension = items.size();
		
		this.globalBest = -1;	// we have to take a negative value becouse of the minimizing 
		this.globalBestLocation = new ArithmeticVector();
		
		//has to be done before starting the actual algorithm
		initializeSwarm();
	}

	@Override
	protected void optimize() {
		
		initializePSO();
		
		for(int i = 0; i < swarmSize; i++) {
			Particle p = swarm.get(i);
			p.setPBest(p.getFitnessValue());
			p.setPBestLocation(swarm.get(i).getLocation());
		}
		
		int t = 0;		// counter for the iterations
		
		while(t < this.maxIterations) {
			// step 1 - update pBest
			for(int i = 0; i < swarmSize; i++) {
				
				Particle p = swarm.get(i);
				
				//the aim is to minimize the function
				if(p.getFitnessValue() < p.getPBest()) {
					p.setPBest(p.getFitnessValue());
					p.setPBestLocation(p.getLocation());
				}
			}
			
			// step 2 - update gBest
			int bestParticleIndex = getMinPos(swarm);	// get the position of the minimum fitness value
			
			if(t == 0 || swarm.get(bestParticleIndex).getFitnessValue() < globalBest) {
				globalBest = swarm.get(bestParticleIndex).getFitnessValue();
				globalBestLocation = swarm.get(bestParticleIndex).getLocation();
			}
			
			for(int i = 0; i < swarmSize; i++) {
				
				double r1 = generator.nextDouble();
				double r2 = generator.nextDouble();
				
				Particle p = swarm.get(i);
				
				// step 3 - update velocity
				
				double w = 1 - (((double) t) / maxIterations) * (1 - 0);
				
				ArithmeticVector first = p.getVelocity().multiply(w);
				ArithmeticVector second = (p.getPBestLocation().subtract(p.getLocation())).multiply((r1 * 2));
				ArithmeticVector third = (globalBestLocation.subtract(p.getLocation())).multiply((r2 * 2));				
				ArithmeticVector newVel = first.add(second).add(third);				
				p.setVelocity(newVel);
				
				// step 4 - update location

				ArithmeticVector newLoc = p.getLocation().add(newVel);				
				p.setLocation(newLoc);
			}
			
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
	private int getMinPos(Vector<Particle> swarm) {
		
		double minValue = swarm.get(0).getFitnessValue();
		int pos = 0;
		
		for(int i = 0; i < swarm.size(); i++) {
			if(swarm.get(i).getFitnessValue() < minValue) {
				minValue = swarm.get(i).getFitnessValue();
				pos = i;
			}
		}		
		return pos;
	}
}