package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
	private Map<ModelVM,ModelPM> mapping;
	/** For generating random numbers */
	private Random random;
	/** Each gene is replaced by a random value with this probability during mutation */ 
	private double mutationProb;
	
	Properties props;

	/** 
	 * Creates a solution with an empty mapping that will need to be filled
	 * somehow, e.g., using #fillRandomly().
	 */
	public Solution(List<ModelPM> bins) {
		this.bins=bins;
		setMutationProb();
		mapping=new HashMap<>();
		random=new Random();
	}
	
	/**
	 * Reads the properties file and sets the mutationProb.
	 */
	private void setMutationProb(){
		
		props = new Properties();
		File file = new File("consolidationProperties.xml");
		FileInputStream fileInput = null;
		try {
			fileInput = new FileInputStream(file);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		try {
			props.loadFromXML(fileInput);
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			fileInput.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		this.mutationProb = Double.parseDouble(props.getProperty("mutationProb"));
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
		//System.err.println("fillRandomly() -> mapping: "+mappingToString());
	}

	/**
	 * Computes the total of PM which are overallocated, aggregated over all PMs and all
	 * resource types. This can be used as a component of the fitness.
	 */
	double getTotalOverAllocated() {
		//System.err.println("getTotalOverAllocated() -> mapping: "+mappingToString());
		double result=0;
		//First determine the allocation of each PM under our mapping.
		Map<Integer,ResourceVector> allocations=new HashMap<>();
		for(ModelPM pm : bins) {
			allocations.put(pm.getNumber(),new ResourceVector(0,0,0));
		}
		for(ModelVM vm : mapping.keySet()) {
			ModelPM pm=mapping.get(vm);
			ResourceVector pmLoad=allocations.get(pm.getNumber());
			ResourceVector vmLoad=vm.getResources();
			pmLoad.add(vmLoad);
		}
		//For each PM, see if it is overallocated; if yes, increase the result accordingly.
		for(ModelPM pm : bins) {
			ResourceVector allocation=allocations.get(pm.getNumber());
			ConstantConstraints cap=pm.getTotalResources();
			if(allocation.getTotalProcessingPower()>cap.getTotalProcessingPower()*pm.getUpperThreshold())
				result+=allocation.getTotalProcessingPower()/(cap.getTotalProcessingPower()*pm.getUpperThreshold());
			if(allocation.getRequiredMemory()>cap.getRequiredMemory()*pm.getUpperThreshold())
				result+=allocation.getRequiredMemory()/(cap.getRequiredMemory()*pm.getUpperThreshold());
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
		result.totalOverAllocated=getTotalOverAllocated();
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

	/**
	 * String representation of the mapping of the
	 * given solution.
	 */
	public String mappingToString() {
		String result="[m=(";
		boolean first=true;
		for(ModelVM vm : mapping.keySet()) {
			if(!first)
				result=result+",";
			result=result+vm.id+"->"+mapping.get(vm).getNumber();
			first=false;
		}
		result=result+")]";
		return result;
	}
}
