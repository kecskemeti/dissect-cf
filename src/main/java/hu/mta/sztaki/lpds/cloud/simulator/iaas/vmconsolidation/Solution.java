package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	private Map<ModelVM,ModelPM> mapping;
	/** For generating random numbers */
	private Random random;
	/** Each gene is replaced by a random value with this probability during mutation */ 
	private static final double mutationProb=0.2;

	/** 
	 * Creates a solution with an empty mapping that will need to be filled
	 * somehow, e.g., using #fillRandomly().
	 */
	public Solution(List<ModelPM> bins) {
		this.bins=bins;
		mapping=new HashMap<>();
		random=new Random();
	}

	/**
	 * Creates a random mapping: for each VM, one PM is chosen uniformly
	 * randomly.
	 */
	void fillRandomly() {
		for(ModelPM pm : bins) {
			for(ModelVM vm : pm.getVMs()) {
				ModelPM randPm=bins.get(random.nextInt(bins.size()));
				mapping.put(vm,randPm);
			}
		}
	}

	/**
	 * Computes the total of PM overloads, aggregated over all PMs and all
	 * resource types. This can be used as a component of the fitness.
	 */
	double getTotalOverload() {
		double result=0;
		//First determine the load of each PM under our mapping.
		Map<Integer,ResourceVector> loads=new HashMap<>();
		for(ModelPM pm : bins) {
			loads.put(pm.getNumber(),new ResourceVector(0,0,0));
		}
		for(ModelVM vm : mapping.keySet()) {
			ModelPM pm=mapping.get(vm);
			loads.get(pm.getNumber()).add(vm.getResources());
		}
		//For each PM, see if it is overloaded; if yes, increase the result accordingly.
		for(ModelPM pm : bins) {
			ResourceVector load=loads.get(pm.getNumber());
			ConstantConstraints cap=pm.getTotalResources();
			if(load.getTotalProcessingPower()>cap.getTotalProcessingPower()*pm.getUpperThreshold())
				result+=load.getTotalProcessingPower()/(cap.getTotalProcessingPower()*pm.getUpperThreshold());
			if(load.getRequiredMemory()>cap.getRequiredMemory()*pm.getUpperThreshold())
				result+=load.getRequiredMemory()/(cap.getRequiredMemory()*pm.getUpperThreshold());
		}
		return result;
	}

	/**
	 * Compute the number of PMs that should be on, given our mapping. This
	 * can be used as a component of the fitness.
	 */
	int getNrActivePms() {
		int result=0;
		//Decide for each PM whether it is used by at least one VM.
		Map<ModelPM,Boolean> used=new HashMap<>();
		for(ModelPM pm : bins) {
			used.put(pm,false);
		}
		for(ModelVM vm : mapping.keySet()) {
			ModelPM pm=mapping.get(vm);
			used.put(pm,true);
		}
		//Count the number of PMs that are in use.
		for(ModelPM pm : bins) {
			if(used.get(pm))
				result++;
		}
		return result;
	}

	/**
	 * Compute the number of migrations needed, given our mapping. This
	 * can be used as a component of the fitness.
	 */
	int getNrMigrations() {
		int result=0;
		//See for each VM whether it must be migrated.
		for(ModelVM vm : mapping.keySet()) {
			if(mapping.get(vm)!=vm.gethostPM())
				result++;
		}
		return result;
	}

	/**
	 * Compute the fitness value belonging to this solution. Note that this
	 * is a rather computation-intensive operation.
	 */
	public Fitness evaluate() {
		Fitness result=new Fitness();
		result.totalOverload=getTotalOverload();
		result.nrActivePms=getNrActivePms();
		result.nrMigrations=getNrMigrations();
		return result;
	}

	/**
	 * Create a new solution by mutating the current one. Each gene (i.e.,
	 * the mapping of each VM) is replaced by a random one with probability
	 * mutationProb and simply copied otherwise. Note that the current 
	 * solution (this) is not changed.
	 */
	Solution mutate() {
		Solution result=new Solution(bins);
		for(ModelVM vm : mapping.keySet()) {
			ModelPM pm;
			if(random.nextDouble()<mutationProb)
				pm=bins.get(random.nextInt(bins.size()));
			else
				pm=mapping.get(vm);
			result.mapping.put(vm,pm);
		}
		return result;
	}

	/**
	 * Create a new solution by recombinating this solution with another.
	 * Each gene (i.e., the mapping of each VM) is taken randomly either
	 * from this or the other parent. Note that the two parents are not
	 * changed.
	 * @param other The other parent for the recombination
	 * @return A new solution resulting from the recombination
	 */
	Solution recombinate(Solution other) {
		Solution result=new Solution(bins);
		for(ModelVM vm : mapping.keySet()) {
			ModelPM pm;
			if(random.nextBoolean())
				pm=other.mapping.get(vm);
			else
				pm=this.mapping.get(vm);
			result.mapping.put(vm,pm);
		}
		return result;
	}

	/**
	 * Implement solution in the model by performing the necessary migrations.
	 */
	public void implement() {
		for(ModelVM vm : mapping.keySet()) {
			ModelPM oldPm=vm.gethostPM();
			ModelPM newPm=mapping.get(vm);
			if(newPm!=oldPm)
				oldPm.migrateVM(vm,newPm);
		}
	}

	/**
	 * String representation of both the mapping and the fitness of the
	 * given solution.
	 */
	public String toString() {
		String result="[m=(";
		boolean first=true;
		for(ModelVM vm : mapping.keySet()) {
			if(!first)
				result=result+",";
			result=result+vm.id+"->"+mapping.get(vm).getNumber();
			first=false;
		}
		result=result+"),f="+evaluate().toString()+"]";
		return result;
	}
}
