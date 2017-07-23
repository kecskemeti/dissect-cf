package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

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
	int SWARM_SIZE = 30;
	int MAX_ITERATION = 100;
	int PROBLEM_DIMENSION = bins.size();

	int count = 1;	// Counter for the graph actions

	private Vector<Particle> swarm = new Vector<Particle>();
	private double[] pBest = new double[SWARM_SIZE];
	private double[] fitnessValueList = new double[SWARM_SIZE];

	private double globalBest;
	private List<ModelPM> gBestLocation;

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
	public ParticleSwarmOptimization(IaaSService toConsolidate, long consFreq) {
		super(toConsolidate, consFreq);
		initializeSwarm();
	}

	/**
	 * Method to create a swarm and a few particles.
	 */
	private void initializeSwarm() {
	}

	@Override
	protected void optimize() {
		// TODO look at psoprocess
	}	

	private void updateGlobalBestLocation(List <ModelPM> global) {
		this.gBestLocation = global;
	}

	private void updateGlobalBest(double value) {
		this.globalBest = value;
	}

	private void fitnessFunction() {
		//TODO
	}
}