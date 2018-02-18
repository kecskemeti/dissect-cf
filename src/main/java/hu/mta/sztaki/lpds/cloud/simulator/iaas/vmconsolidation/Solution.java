package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;

/**
 * Represents a possible solution of the VM consolidation problem, i.e., a
 * mapping of VMs to PMs. Can be used as an individual in the population.
 */
public class Solution {
	/** List of all available bins */
	protected List<ModelPM> bins;
	/** Mapping of VMs to PMs */
	protected Map<ModelVM, ModelPM> mapping;
	/** Current resource use of the PMs */
	protected Map<ModelPM, ResourceVector> loads;
	/** Flags for each PM whether it is in use */
	protected Map<ModelPM, Boolean> used;
	/** For generating random numbers */
	protected Random random;
	/** Each gene is replaced by a random value with this probability during mutation */
	private double mutationProb;
	/** Fitness of the solution */
	protected Fitness fitness;
	/** Controls whether new solutions (created by mutation or recombination) should be improved with a local search */
	protected boolean doLocalSearch=false;

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
			doLocalSearch=Boolean.parseBoolean(props.getProperty("doLocalSearch"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	public Solution clone() {
		Solution newSol=new Solution(bins, mutationProb);
		newSol.mapping.putAll(this.mapping);
		newSol.loads.putAll(this.loads);
		newSol.used.putAll(this.used);
		newSol.fitness.nrActivePms=this.fitness.nrActivePms;
		newSol.fitness.nrMigrations=this.fitness.nrMigrations;
		newSol.fitness.totalOverAllocated=this.fitness.totalOverAllocated;
		return newSol;
	}

	/**
	 * Auxiliary method.
	 * PRE: the maps #loads and #used are already filled
	 * POST: fitness.nrActivePms and fitness.totalOverAllocated are correct
	 */
	protected void countActivePmsAndOverloads() {
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
		if(doLocalSearch)
			improve();
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
	 * Improving a solution by relieving overloaded PMs, emptying underloaded
	 * PMs, and finding new hosts for the thus removed VMs using BFD.
	 */
	protected void improve() {
		List<ModelVM> vmsToMigrate=new ArrayList<>();
		//create inverse mapping
		Map<ModelPM,Vector<ModelVM>> vmsOfPms=new HashMap<>();
		for(ModelPM pm : bins) {
			vmsOfPms.put(pm, new Vector<>());
		}
		for(ModelVM vm : mapping.keySet()) {
			vmsOfPms.get(mapping.get(vm)).add(vm);
		}
		
		//relieve overloaded PMs + empty underloaded PMs
		for(ModelPM pm : bins) {
			ConstantConstraints cap=pm.getTotalResources();
//			Logger.getGlobal().info("ConstantConstraints: " + cap.getTotalProcessingPower() + ", " + cap.getRequiredMemory() + 
//					", loads: " + loads.get(pm).getTotalProcessingPower() + ", " + loads.get(pm).getRequiredMemory());
			while(loads.get(pm).getTotalProcessingPower()>cap.getTotalProcessingPower()*pm.getUpperThreshold()
					|| loads.get(pm).getRequiredMemory()>cap.getRequiredMemory()*pm.getUpperThreshold()) {
				//PM is overloaded
				Vector<ModelVM> vmsOfPm=vmsOfPms.get(pm);
				
				//Logger.getGlobal().info("overloaded, vmsOfPm, size: " + vmsOfPm.size() + ", " + vmsOfPm.toString());
				
				ModelVM vm=vmsOfPm.remove(vmsOfPm.size()-1);
				vmsToMigrate.add(vm);
				loads.get(pm).subtract(vm.getResources());
				if(vmsOfPm.isEmpty())
					used.put(pm, false);
			}
			if(loads.get(pm).getTotalProcessingPower()<=cap.getTotalProcessingPower()*pm.getLowerThreshold()
					&& loads.get(pm).getRequiredMemory()<=cap.getRequiredMemory()*pm.getLowerThreshold()) {
				//PM is underloaded
				Vector<ModelVM> vmsOfPm=vmsOfPms.get(pm);
				
				//Logger.getGlobal().info("underloaded, vmsOfPm, size: " + vmsOfPm.size());
				
				for(ModelVM vm : vmsOfPm) {
					vmsToMigrate.add(vm);
					loads.get(pm).subtract(vm.getResources());
				}
				vmsOfPm.removeAllElements();
				used.put(pm, false);
			}
		}
		//find new host for the VMs to migrate using BFD
		Collections.sort(vmsToMigrate, new Comparator<ModelVM>() {
			@Override
			public int compare(ModelVM vm1, ModelVM vm2) {
				return Double.compare(vm2.getResources().getTotalProcessingPower(), vm1.getResources().getTotalProcessingPower());
			}
		});
		List<ModelPM> binsToTry=new ArrayList<>(bins);
		Collections.sort(binsToTry, new Comparator<ModelPM>() {
			@Override
			public int compare(ModelPM pm1, ModelPM pm2) {
				return Double.compare(loads.get(pm2).getTotalProcessingPower(), loads.get(pm1).getTotalProcessingPower());
			}
		});
		for(ModelVM vm : vmsToMigrate) {
			ModelPM targetPm=null;
			for(ModelPM pm : binsToTry) {
				ResourceVector newLoad=loads.get(pm);
				newLoad.singleAdd(vm.getResources());
				ConstantConstraints cap=pm.getTotalResources();
				if(newLoad.getTotalProcessingPower()<=cap.getTotalProcessingPower()*pm.getUpperThreshold()
						&& newLoad.getRequiredMemory()<=cap.getRequiredMemory()*pm.getUpperThreshold()) {
					targetPm=pm;
					break;
				}
			}
			if(targetPm==null)
				targetPm=vm.getInitialPm();
			mapping.put(vm, targetPm);
			loads.get(targetPm).singleAdd(vm.getResources());
			used.put(targetPm, true);
			if(targetPm!=vm.getInitialPm())
				fitness.nrMigrations++;
		}
		countActivePmsAndOverloads();
	}

	/**
	 * Creates a mapping based on FirstFit. 
	 */
	void createFirstFitSolution() {
		createUnchangedSolution();
		improve();
		// System.err.println("createFirstFitSolution() -> mapping: "+mappingToString());
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
		if(doLocalSearch)
			result.improve();
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
			result.loads.get(pm).singleAdd(vm.getResources());
			result.used.put(pm,true);
			if(pm!=vm.getInitialPm())
				result.fitness.nrMigrations++;
		}
		result.countActivePmsAndOverloads();
		if(doLocalSearch)
			result.improve();
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
			result = result + vm.getId() + "->" + mapping.get(vm).getNumber();
			first = false;
		}
		result = result + "),f=" + fitness.toString() + "]";
		return result;
	}

	/**
	 * String representation of the mapping of the given solution.
	 */
	public String mappingToString() {
		String result = "[mapping=(";
		boolean first = true;
		for (ModelVM vm : mapping.keySet()) {
			if (!first)
				result = result + ",";
			result = result + vm.getId() + "->" + mapping.get(vm).getNumber();
			first = false;
		}
		result = result + ")]";
		return result;
	}
	
	/**
	 * String representation of the loads of each pm of the current mapping.
	 */
	public String loadsToString() {
		String result = "[loads=(";
		boolean first = true;
		for (ModelPM pm : loads.keySet()) {
			
			
			if(!loads.get(pm).isEmpty()) {
				if (!first)
					result = result + ",";
				
				result = result + pm.getNumber() + "::" + loads.get(pm).toString();
				first = false;
			}
		}
		result = result + ")]";
		return result;
	}
	
	/**
	 * String representation of the used-mapping of the given solution.
	 */
	public String usedToString() {
		String result = "[used=(";
		boolean first = true;
		for (ModelPM pm : used.keySet()) {
			
			if(!used.get(pm))
				continue;
			else {
				if (!first)
					result = result + ", ";
				
				result = result + pm.getNumber();
				first = false;
			}			
			
		}
		result = result + ")]";
		return result;
	}
}
