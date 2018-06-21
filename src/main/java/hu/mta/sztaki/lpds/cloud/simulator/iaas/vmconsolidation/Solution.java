package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

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
	protected Map<ModelPM, AlterableResourceConstraints> loads;
	/** Flags for each PM whether it is in use */
	protected Map<ModelPM, Boolean> used;
	/** For generating random numbers */
	protected Random random;
	/** Each gene is replaced by a random value with this probability during mutation */
	private double mutationProb;
	/** Fitness of the solution */
	protected Fitness fitness;
	/** Controls whether new solutions (created by mutation or recombination) should be improved with a local search */
	protected boolean doLocalSearch1=false;
	/** simple consolidator local search */
	protected boolean doLocalSearch2=false;

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
			loads.put(pm, new AlterableResourceConstraints(ConstantConstraints.noResources));
			used.put(pm, false);
		}
		
		props = new Properties();		
		try {
			File file = new File("consolidationProperties.xml");
			FileInputStream fileInput = new FileInputStream(file);
			props.loadFromXML(fileInput);
			fileInput.close();
			random = new Random(Long.parseLong(props.getProperty("seed")));
			doLocalSearch1=Boolean.parseBoolean(props.getProperty("doLocalSearch1"));
			doLocalSearch2=Boolean.parseBoolean(props.getProperty("doLocalSearch2"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	/**
	 * Creates a clone of this solution with the same mappings.
	 * 
	 * @return A new object containing the same mappings as this solution.
	 */
	public Solution clone() {
		Solution newSol=new Solution(bins, mutationProb);
		
		//TODO maybe we have to make a deep copy for the mappings
		newSol.mapping.putAll(this.mapping);
		
		// clone all ResourceVectors for each pm and put it inside the used-mapping
		for(ModelPM pm : loads.keySet()) {
			newSol.loads.put(pm, new AlterableResourceConstraints(loads.get(pm)));
			newSol.used.put(pm,true);
		}
		
		newSol.fitness.nrMigrations=this.fitness.nrMigrations;
		
		// after cloning everything else we can determine the missing fitness values
		newSol.countActivePmsAndOverloads();
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
			AlterableResourceConstraints allocation = loads.get(pm);
			ResourceConstraints cap = pm.getTotalResources();
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
		if(doLocalSearch1) {
			improve();
		} else if(doLocalSearch2){
			simpleConsolidatorImprove();
		}
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
			ResourceConstraints cap=pm.getTotalResources();
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
				AlterableResourceConstraints newLoad=new AlterableResourceConstraints(loads.get(pm));
				newLoad.singleAdd(vm.getResources());
				ResourceConstraints cap=pm.getTotalResources();
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
	 * The algorithm out of the simple consolidator, adjusted to work with the abstract model.
	 */
	protected void simpleConsolidatorImprove() {
//		Logger.getGlobal().info("starting to improve with second local search");
		
		// create an array out of the bins
		ModelPM[] arr = new ModelPM[bins.size()];
		for(int i = 0; i < bins.size(); i++) {			
			arr[i] = bins.get(i);
		}
		
		// now filter out the not running machines
		int runningLen = 0;
		for (int i = 0; i < arr.length; i++) {
			if (arr[i].isRunning()) {
				arr[runningLen++] =arr[i];
			}
		}
		ModelPM[] pmList = new ModelPM[runningLen];
		System.arraycopy(arr, 0, pmList, 0, runningLen);		
		
//		Logger.getGlobal().info("size of the pmList: " + pmList.length);

		boolean didMove;
		do {
			didMove = false;
			
			// sort the array from highest to lowest free capacity with an adjusted version of the fitting pm comparator
			Arrays.sort(pmList, new Comparator<ModelPM>() {
				@Override
				public int compare(ModelPM pm1, ModelPM pm2) {
					return -pm1.getFreeResources().compareTo(pm2.getFreeResources());
				}
			});
			
			int lastItem = runningLen - 1;
			
//			Logger.getGlobal().info("filtered array: " + Arrays.toString(pmList));
			
			for(int i = 0; i < pmList.length; i++) {
				ModelPM source = pmList[i];
				if( source.isHostingVMs() ) {
					ModelVM[] vmList = source.getVMs().toArray(new ModelVM[source.getVMs().size()]);
					
					for (int vmidx = 0; vmidx < vmList.length; vmidx++) {
						ModelVM vm = vmList[vmidx];
						// ModelVMs can only run, so we need not to check the state (there is none either)
						for (int j = lastItem; j > i; j--) {
							ModelPM target = pmList[j];
							if (target.getFreeResources().getTotalProcessingPower() < 0.00000001) {
								// Ensures that those PMs that barely have resources will not be 
								// considered in future runs of this loop
								
								lastItem = j;
								continue;
							}
							
							if(target.isMigrationPossible(vm)) {
								mapping.put(vm, target);
								loads.get(target).singleAdd(vm.getResources());
								used.put(target, true);
								
								if(target!=vm.getInitialPm())
									fitness.nrMigrations++;
								
								didMove = true;
								break;
							}
						}
					}
				}
			}
		} while (didMove);
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
		if(doLocalSearch1) {
			result.improve();
		} else if(doLocalSearch2){
			result.simpleConsolidatorImprove();
		}
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
		if(doLocalSearch1) {
			result.improve();
		} else if(doLocalSearch2){
			result.simpleConsolidatorImprove();
		}
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
			result = result + vm.hashCode() + "->" + mapping.get(vm).hashCode();
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
			result = result + vm.hashCode() + "->" + mapping.get(vm).hashCode();
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
			
			
			if(loads.get(pm).compareTo(ConstantConstraints.noResources)!=0) {
				if (!first)
					result = result + ",";
				
				result = result + pm.hashCode() + "::" + loads.get(pm).toString();
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
				
				result = result + pm.hashCode();
				first = false;
			}			
			
		}
		result = result + ")]";
		return result;
	}
}
