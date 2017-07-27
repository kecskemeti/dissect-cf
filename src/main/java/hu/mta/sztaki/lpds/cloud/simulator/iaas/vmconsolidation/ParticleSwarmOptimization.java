package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

/**
 * @author René Ponto
 *
 * This class manages VM consolidation with a particle swarm optimization algorithm. For that, particles
 * are used to find the best solution for consolidation.
 */

public class ParticleSwarmOptimization extends ModelBasedConsolidator {

	// constants for doing consolidation
	private final int swarmSize = 20;		// defines the amount of particles
	private final int maxIterations = 100;	// defines the maximum of iterations
	private int dimension;					// the problem dimension, gets defined according to the amounts of VMs	
	private double C1 = 2.0;				// constant 1
	private double C2 = 2.0;				// constant 2
	private double wUpperBound = 1.0;		// the upper bound for variable w (used in the optimize()-method)
	private double wLowerBound = 0.0;		// the lower bound for variable w
	
	// TODO
	private final double errorTolerance = 0; 	// defines the tolerance value

	// used for setting the velocity for each particle
	private static final double velocityLow = -1;
	private static final double velocityHigh = 1;
	

	public int count = 1;	// Counter for the graph actions

	// create and initialize all necessary components
	private Vector<Particle> swarm = new Vector<Particle>();
	private double[] pBest = new double[swarmSize];
	private Vector<List<ModelPM>> pBestLocation = new Vector<List<ModelPM>>();
	private double[] fitnessValueList = new double[swarmSize];

	private double globalBest;
	private List<ModelPM> globalBestLocation;

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
	public ParticleSwarmOptimization(IaaSService toConsolidate, final double upperThreshold, final double lowerThreshold,long consFreq) {
		super(toConsolidate, upperThreshold, lowerThreshold, consFreq);
	}

	/**
	 * Method to create a swarm and a few particles. Should be finished.
	 */
	private void initializeSwarm() {
		Particle p;
		for(int i = 0; i < swarmSize; i++) {
			p = new Particle();
			
			List<ModelPM> loc = new ArrayList<ModelPM>();
			
			// randomize location (deployment of the VMs to PMs) inside a space defined in Problem Set
			for(int j = 0; j < this.dimension; j++) {
				// create a new int to add random PMs to the list of the actual particle
				int a = generator.nextInt(bins.size()) + 1;
				
				loc.add(bins.get(a)); 	// add the random PM
			}
			
			// randomize velocity in the range defined in Problem Set
			double vel = 0;
			vel = velocityLow + generator.nextDouble() * (velocityHigh - velocityLow);
			
			p.setLocation(loc);
			p.setVelocity(vel);			
			swarm.add(p);
		}
	}
	
	/**
	 * Initializes variables, sets the problem dimension and creates the swarm.
	 */
	private void initializePSO() {
		int dim = 0;
		for(ModelPM p : bins) {
			dim = dim + p.getVMs().size();
		}
		this.dimension = dim;
		
		this.globalBest = 0;
		this.globalBestLocation = new ArrayList <ModelPM>();
		
		//has to be done before starting the actual algorithm
		initializeSwarm();
		updateFitnessList();
	}

	@Override
	protected void optimize() {
		
		initializePSO();
		
		for(int i = 0; i < swarmSize; i++) {
			pBest[i] = fitnessValueList[i];
			pBestLocation.add(swarm.get(i).getLocation());
		}
		
		int t = 0;		// counter for the iterations
		
		double w;
		double err = 9999;
		
		while(t < this.maxIterations && err > this.errorTolerance) {
			// step 1 - update pBest
			for(int i = 0; i < swarmSize; i++) {				
				//unclear
				if(fitnessValueList[i] < pBest[i]) {
					pBest[i] = fitnessValueList[i];
					pBestLocation.set(i, swarm.get(i).getLocation());
				}
			}
			
			// step 2 - update gBest
			int bestParticleIndex = getMinPos(fitnessValueList);	// get the position of the minimum fitness value
			
			if(t == 0 || fitnessValueList[bestParticleIndex] < globalBest) {
				globalBest = fitnessValueList[bestParticleIndex];
				globalBestLocation = swarm.get(bestParticleIndex).getLocation();
			}
			
			w = wUpperBound - (((double) t) / maxIterations) * (wUpperBound - wLowerBound);
			
			for(int i = 0; i < swarmSize; i++) {
				
				double r1 = generator.nextDouble();
				double r2 = generator.nextDouble();
				
				Particle p = swarm.get(i);
				
				// step 3 - update velocity
				
				// TODO: create a subtraction / comparison
				/*
				double newVel = (w * p.getVelocity()) + 
				(r1 * C1) * (pBestLocation.get(i) - p.getLocation()) +
				(r2 * C2) * (globalBestLocation - p.getLocation());
				*/
				
				//p.setVelocity(newVel);
				
				// step 4 - update location
				List<ModelPM> newLoc = new ArrayList<ModelPM>();
				
				//TODO update location
				
				p.setLocation(newLoc);
			}
			
			// last step
			t++;
			updateFitnessList();
		}
	}	
	
	/**
	 * Method to find the position where the smallest fitness value is.
	 * @param list The list with all the fitnessValues of the particles.
	 * @return The position where the value is in the list.
	 */
	private int getMinPos(double[] list) {
		int pos = 0;
		double minValue = list[0];
		
		for(int i = 0; i < list.length; i++) {
			if(list[i] < minValue) {
				minValue = list[i];
				pos = i;
			}
		}
		
		return pos;
	}
	
	private void updateFitnessList() {
		for(int i = 0; i < swarmSize; i++) {
			fitnessValueList[i] = swarm.get(i).getFitnessValue();
		}
	}
}