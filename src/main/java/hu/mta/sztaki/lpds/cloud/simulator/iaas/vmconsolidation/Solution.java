package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;

/**
 * Represents a possible solution of the VM consolidation problem, i.e., a
 * mapping of VMs to PMs. Can be used as an individual in the population.
 */
public class Solution {
	/** List of all available bins */
	private List<ModelPM> bins;
	/** Mapping of VMs to PMs */
	private Map<ModelVM, ModelPM> mapping;
	/** Current resource use of the PMs */
	private Map<ModelPM, ResourceVector> loads;
	/** Flags for each PM whether it is in use */
	private Map<ModelPM, Boolean> used;
	/** For generating random numbers */
	private Random random;
	/** Each gene is replaced by a random value with this probability during mutation */
	private double mutationProb;
	/** Fitness of the solution */
	private Fitness fitness;

	Properties props;

	/**
	 * Creates a solution with an empty mapping that will need to be filled somehow,
	 * e.g., using #fillRandomly().
	 */
	public Solution(List<ModelPM> bins, double mp) {
		this.bins = bins;
		mutationProb = mp;
		mapping = new HashMap<>();
		loads=new HashMap<>();
		used=new HashMap<>();
		fitness=new Fitness();

		for (ModelPM pm : bins) {
			loads.put(pm, new ResourceVector(0, 0, 0));
			used.put(pm, false);
		}
		
		props = new Properties();		
		try {
			File file = new File("consolidationProperties.xml");
			FileInputStream fileInput = new FileInputStream(file);
			props.loadFromXML(fileInput);
			fileInput.close();
			random = new Random(Long.parseLong(props.getProperty("seed")));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	/**
	 * Auxiliary method.
	 * PRE: the maps #loads and #used are already filled
	 * POST: fitness.nrActivePms and fitness.totalOverAllocated are correct
	 */
	private void countActivePmsAndOverloads() {
		fitness.nrActivePms=0;
		fitness.totalOverAllocated=0;
		for (ModelPM pm : bins) {
			if(used.get(pm))
				fitness.nrActivePms++;
			ResourceVector allocation = loads.get(pm);
			ConstantConstraints cap = pm.getTotalResources();
			if (allocation.getTotalProcessingPower() > cap.getTotalProcessingPower() * pm.getUpperThreshold())
				fitness.totalOverAllocated += allocation.getTotalProcessingPower()
						/ (cap.getTotalProcessingPower() * pm.getUpperThreshold());
			if (allocation.getRequiredMemory() > cap.getRequiredMemory() * pm.getUpperThreshold())
				fitness.totalOverAllocated += allocation.getRequiredMemory() / (cap.getRequiredMemory() * pm.getUpperThreshold());
		}
	}

	/**
	 * Creates a random mapping: for each VM, one PM is chosen uniformly randomly.
	 */
	void fillRandomly() {
		fitness.nrMigrations=0;
		for (ModelPM pm : bins) {
			for (ModelVM vm : pm.getVMs()) {
				ModelPM randPm = bins.get(random.nextInt(bins.size()));
				mapping.put(vm, randPm);
				loads.get(randPm).singleAdd(vm.getResources());
				used.put(randPm,true);
				if(pm!=randPm)
					fitness.nrMigrations++;
			}
		}
		countActivePmsAndOverloads();
		// System.err.println("fillRandomly() -> mapping: "+mappingToString());
	}

	/**
	 * Creates the same mapping as existing before consolidation has started.
	 */
	void createUnchangedSolution() {
		fitness.nrMigrations=0;
		for(ModelPM pm : bins) {
			for(ModelVM vm : pm.getVMs()) {
				mapping.put(vm, pm);
				loads.get(pm).singleAdd(vm.getResources());
				used.put(pm,true);
			}
		}
		countActivePmsAndOverloads();
		// System.err.println("createUnchangedSolution() -> mapping: "+mappingToString());
	}

	/**
	 * Creates a mapping based on FirstFit. 
	 */
	void createFirstFitSolution() {		
		fitness.nrMigrations=0;
		for(int a = 0; a < bins.size(); a++) {
			ModelPM pm = bins.get(a);
			if(!pm.isHostingVMs())
				continue;
			
			List<ModelVM> vmsOnPm = pm.getVMs();
			for(int b = 0; b < vmsOnPm.size(); b++) {
				ModelVM vm = vmsOnPm.get(b);
				for(int i = 0; i < bins.size(); i++) {
					ModelPM targetPm=bins.get(i);
					if(targetPm.isMigrationPossible(vm)) {
						if(vm == null) {
							System.err.println("VM is null at firstfit creation.");
							continue;
						}
						mapping.put(vm, targetPm);
						loads.get(targetPm).singleAdd(vm.getResources());
						used.put(targetPm,true);
					}
				}
			}
		}
		countActivePmsAndOverloads();
		// System.err.println("createFirstFitSolution() -> mapping: "+mappingToString());
	}

	/**
	 * Compute the number of PMs that should be on, given our mapping. This can be
	 * used as a component of the fitness.
	 */
	int getNrActivePms() {
		int result = 0;
		// Decide for each PM whether it is used by at least one VM.
		Map<ModelPM, Boolean> used = new HashMap<>();
		for (ModelPM pm : bins) {
			used.put(pm, false);
		}
		for (ModelVM vm : mapping.keySet()) {
			ModelPM pm = mapping.get(vm);
			used.put(pm, true);
		}
		// Count the number of PMs that are in use.
		for (ModelPM pm : bins) {
			if (used.get(pm))
				result++;
		}
		return result;
	}

	/**
	 * Get the fitness value belonging to this solution.
	 */
	public Fitness evaluate() {
		return fitness;
	}

	/**
	 * Create a new solution by mutating the current one. Each gene (i.e., the
	 * mapping of each VM) is replaced by a random one with probability mutationProb
	 * and simply copied otherwise. Note that the current solution (this) is not
	 * changed.
	 */
	Solution mutate() {
		Solution result = new Solution(bins, mutationProb);
		result.fitness.nrMigrations=0;
		for (ModelVM vm : mapping.keySet()) {
			ModelPM pm;
			if (random.nextDouble() < mutationProb)
				pm = bins.get(random.nextInt(bins.size()));
			else
				pm = mapping.get(vm);
			result.mapping.put(vm, pm);
			result.loads.get(pm).singleAdd(vm.getResources());
			result.used.put(pm,true);
			if(pm!=vm.getInitialPm())
				result.fitness.nrMigrations++;
		}
		result.countActivePmsAndOverloads();
		return result;
	}

	/**
	 * Create a new solution by recombinating this solution with another. Each gene
	 * (i.e., the mapping of each VM) is taken randomly either from this or the
	 * other parent. Note that the two parents are not changed.
	 * 
	 * @param other
	 *            The other parent for the recombination
	 * @return A new solution resulting from the recombination
	 */
	Solution recombinate(Solution other) {
		Solution result = new Solution(bins, mutationProb);
		result.fitness.nrMigrations=0;
		for (ModelVM vm : mapping.keySet()) {
			ModelPM pm;
			if (random.nextBoolean())
				pm = other.mapping.get(vm);
			else
				pm = this.mapping.get(vm);
			result.mapping.put(vm, pm);
			result.loads.get(pm).singleAdd(vm.getResources());	//FIXME bug, sometimes vm is null at this point with first fit
			result.used.put(pm,true);
			if(pm!=vm.getInitialPm())
				result.fitness.nrMigrations++;
		}
		result.countActivePmsAndOverloads();
		return result;
	}

	/**
	 * Implement solution in the model by performing the necessary migrations.
	 */
	public void implement() {
		for (ModelVM vm : mapping.keySet()) {
			ModelPM oldPm = vm.gethostPM();
			ModelPM newPm = mapping.get(vm);
			if (newPm != oldPm)
				oldPm.migrateVM(vm, newPm);
		}
	}

	/**
	 * String representation of both the mapping and the fitness of the given
	 * solution.
	 */
	public String toString() {
		String result = "[m=(";
		boolean first = true;
		for (ModelVM vm : mapping.keySet()) {
			if (!first)
				result = result + ",";
			result = result + vm.id + "->" + mapping.get(vm).getNumber();
			first = false;
		}
		result = result + "),f=" + fitness.toString() + "]";
		return result;
	}

	/**
	 * String representation of the mapping of the given solution.
	 */
	public String mappingToString() {
		String result = "[m=(";
		boolean first = true;
		for (ModelVM vm : mapping.keySet()) {
			if (!first)
				result = result + ",";
			result = result + vm.id + "->" + mapping.get(vm).getNumber();
			first = false;
		}
		result = result + ")]";
		return result;
	}
}
