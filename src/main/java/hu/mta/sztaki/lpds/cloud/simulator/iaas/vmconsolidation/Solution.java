package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.SimpleConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;

/**
 * Represents a possible solution of the VM consolidation problem, i.e., a
 * mapping of VMs to PMs. Can be used as an individual in the population.
 */
public class Solution {
	/** List of all available bins */
	protected ModelPM[] bins;
	/** Mapping of VMs to PMs */
	protected Map<ModelVM, ModelPM> mapping;
	/** Current resource use of the PMs */
	protected Map<ModelPM, AlterableResourceConstraints> loads;
	/** Flags for each PM whether it is in use */
	protected Map<ModelPM, Boolean> used;
	/** Each gene is replaced by a random value with this probability during mutation */
	final double mutationProb;
	/** Fitness of the solution */
	protected Fitness fitness;

	final private static Comparator<ModelVM> mvmComp=new Comparator<ModelVM>() {
		@Override
		public int compare(final ModelVM vm1, final ModelVM vm2) {
			return Double.compare(vm2.getResources().getTotalProcessingPower(), vm1.getResources().getTotalProcessingPower());
		}
	};
	
	final private Comparator<ModelPM> mpmComp=new Comparator<ModelPM>() {
		@Override
		public int compare(final ModelPM pm1, final ModelPM pm2) {
			return Double.compare(loads.get(pm2).getTotalProcessingPower(), loads.get(pm1).getTotalProcessingPower());
		}
	};
	
	final private Comparator<ModelPM> mpmFreeComp=new Comparator<ModelPM>() {
		@Override
		public int compare(final ModelPM pm1, final ModelPM pm2) {
			return -pm1.getFreeResources().compareTo(pm2.getFreeResources());
		}
	};
	
	
	/**
	 * Creates a solution with an empty mapping that will need to be filled somehow,
	 * e.g., using #fillRandomly().
	 */
	public Solution(final ModelPM[] bins, final double mp) {
		this.bins = bins;
		mutationProb = mp;
		mapping = new HashMap<>();
		loads=new HashMap<>();
		used=new HashMap<>();
		fitness=new Fitness();

		for (final ModelPM pm : bins) {
			loads.put(pm, new AlterableResourceConstraints(ConstantConstraints.noResources));
			used.put(pm, false);
		}
		
		
	}

	/**
	 * Creates a clone of this solution with the same mappings.
	 * 
	 * @return A new object containing the same mappings as this solution.
	 */
	public Solution clone() {
		final Solution newSol=new Solution(bins, mutationProb);
		
		//TODO maybe we have to make a deep copy for the mappings
		newSol.mapping.putAll(this.mapping);
		
		// clone all ResourceVectors for each pm and put it inside the used-mapping
		for(final ModelPM pm : loads.keySet()) {
			newSol.loads.get(pm).singleAdd(loads.get(pm));
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
		for (final ModelPM pm : bins) {
			if(used.get(pm))
				fitness.nrActivePms++;
			final AlterableResourceConstraints allocation = loads.get(pm);
			if (allocation.getTotalProcessingPower() > pm.getUpperThreshold().getTotalProcessingPower() )
				fitness.totalOverAllocated += allocation.getTotalProcessingPower()
						/ (pm.getUpperThreshold().getTotalProcessingPower());
			if (allocation.getRequiredMemory() > pm.getUpperThreshold().getRequiredMemory())
				fitness.totalOverAllocated += allocation.getRequiredMemory() / pm.getUpperThreshold().getRequiredMemory();
		}
	}
	
	void updateMapping(ModelVM v, ModelPM p) {
		mapping.put(v, p);
		loads.get(p).singleAdd(v.getResources());
		used.put(p,true);
		if(p!=v.initialHost) {
			fitness.nrMigrations++;
		}
	}
	/**
	 * Creates a random mapping: for each VM, one PM is chosen uniformly randomly.
	 */
	void fillRandomly() {
		fitness.nrMigrations=0;
		for (final ModelPM pm : bins) {
			for (final ModelVM vm : pm.getVMs()) {
				updateMapping(vm,bins[SolutionBasedConsolidator.random.nextInt(bins.length)]);
			}
		}
		useLocalSearch();
		// System.err.println("fillRandomly() -> mapping: "+mappingToString());
	}

	private void useLocalSearch() {
		if(SolutionBasedConsolidator.doLocalSearch1) {
			improve();
		} else if(SolutionBasedConsolidator.doLocalSearch2){
			simpleConsolidatorImprove();
		}
		countActivePmsAndOverloads();
	}
	
	/**
	 * Creates the same mapping as existing before consolidation has started.
	 */
	void createUnchangedSolution() {
		fitness.nrMigrations=0;
		for(final ModelPM pm : bins) {
			for(final ModelVM vm : pm.getVMs()) {
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
		final List<ModelVM> vmsToMigrate=new ArrayList<>();
		//create inverse mapping
		final Map<ModelPM,ArrayList<ModelVM>> vmsOfPms=new HashMap<>();
		final ModelVM[] vms=mapping.keySet().toArray(new ModelVM[mapping.size()]);
		for(final ModelVM vm : vms) {
			final ModelPM pm=mapping.get(vm);
			ArrayList<ModelVM> vmsOfPm=vmsOfPms.get(pm);
			if(vmsOfPm==null) {
				vmsOfPm=new ArrayList<>();
				vmsOfPms.put(pm, vmsOfPm);
			}
			vmsOfPm.add(vm);
		}
		
		//relieve overloaded PMs + empty underloaded PMs
		for(final ModelPM pm : bins) {
//			Logger.getGlobal().info("ConstantConstraints: " + cap.getTotalProcessingPower() + ", " + cap.getRequiredMemory() + 
//					", loads: " + loads.get(pm).getTotalProcessingPower() + ", " + loads.get(pm).getRequiredMemory());
			final AlterableResourceConstraints currLoad=loads.get(pm);
			while(currLoad.getTotalProcessingPower()>pm.getUpperThreshold().getTotalProcessingPower()
					|| currLoad.getRequiredMemory()>pm.getUpperThreshold().getRequiredMemory()) {
				//PM is overloaded
				final ArrayList<ModelVM> vmsOfPm=vmsOfPms.get(pm);
				
				//Logger.getGlobal().info("overloaded, vmsOfPm, size: " + vmsOfPm.size() + ", " + vmsOfPm.toString());
				
				final ModelVM vm=vmsOfPm.remove(vmsOfPm.size()-1);
				vmsToMigrate.add(vm);
				currLoad.subtract(vm.getResources());
			}
			if(currLoad.getTotalProcessingPower()<=pm.getLowerThreshold().getTotalProcessingPower()&&currLoad.getRequiredMemory()<=pm.getLowerThreshold().getRequiredMemory()) {
				//PM is underloaded
				final ArrayList<ModelVM> vmsOfPm=vmsOfPms.get(pm);
				if(vmsOfPm!=null) {
				//Logger.getGlobal().info("underloaded, vmsOfPm, size: " + vmsOfPm.size());
				
					for(final ModelVM vm : vmsOfPm) {
						vmsToMigrate.add(vm);
						currLoad.subtract(vm.getResources());
					}
					vmsOfPm.clear();
					used.put(pm, false);
				}
			}
		}
		//find new host for the VMs to migrate using BFD
		Collections.sort(vmsToMigrate, mvmComp);
		final ModelPM[] binsToTry=Arrays.copyOf(bins, bins.length);
		Arrays.sort(binsToTry, mpmComp);
		for(int i=0;i<vmsToMigrate.size();i++) {
			final ModelVM vm=vmsToMigrate.get(i);
			ModelPM targetPm=null;
			for(int j=0;j<binsToTry.length;j++) {
				final ModelPM pm=binsToTry[j];
				final AlterableResourceConstraints newLoad=new AlterableResourceConstraints(loads.get(pm));
				newLoad.singleAdd(vm.getResources());
				if(newLoad.getTotalProcessingPower()<=pm.getUpperThreshold().getTotalProcessingPower()&&newLoad.getRequiredMemory()<=pm.getUpperThreshold().getRequiredMemory()) {
					targetPm=pm;
					break;
				}
			}
			if(targetPm==null)
				targetPm=vm.initialHost;
			mapping.put(vm, targetPm);
			loads.get(targetPm).singleAdd(vm.getResources());
			used.put(targetPm, true);
			if(targetPm!=vm.initialHost)
				fitness.nrMigrations++;
		}
	}
	
	/**
	 * The algorithm out of the simple consolidator, adjusted to work with the abstract model.
	 */
	protected void simpleConsolidatorImprove() {
//		Logger.getGlobal().info("starting to improve with second local search");
		
		// create an array out of the bins
		ModelPM[] pmList = new ModelPM[bins.length];
		int runningLen = 0;
		for(int i = 0; i < pmList.length; i++) {
			final ModelPM curr=bins[i];
			if (curr.isHostingVMs() && curr.getFreeResources().getTotalProcessingPower()>SimpleConsolidator.pmFullLimit) {
				pmList[runningLen++]=curr;
			}
		}
		
//		Logger.getGlobal().info("size of the pmList: " + pmList.length);

		boolean didMove;
		runningLen--;
		int beginIndex=0;
		final HashSet<ModelVM> alreadyMoved=new HashSet<>();
		do {
			didMove = false;
			
			// sort the array from highest to lowest free capacity with an adjusted version of the fitting pm comparator
			Arrays.sort(pmList, beginIndex, runningLen+1, mpmFreeComp);
			
//			Logger.getGlobal().info("filtered array: " + Arrays.toString(pmList));
			
			for(int i = beginIndex; i < runningLen; i++) {
				final ModelPM source = pmList[i];
				final ModelVM[] vmList = source.getVMs().toArray(new ModelVM[source.getVMs().size()]);
				int vmc=0;
				for (int vmidx = 0; vmidx < vmList.length; vmidx++) {
					final ModelVM vm = vmList[vmidx];
					if(alreadyMoved.contains(vm)) continue;
					// ModelVMs can only run, so we need not to check the state (there is none either)
					for (int j = runningLen; j > i; j--) {
						final ModelPM target = pmList[j];
							
						if(target.isMigrationPossible(vm)) {
							mapping.put(vm, target);
							loads.get(target).singleAdd(vm.getResources());
							used.put(target, true);
							alreadyMoved.add(vm);
							
							if(target!=vm.initialHost)
								fitness.nrMigrations++;
							if (target.getFreeResources().getTotalProcessingPower() < SimpleConsolidator.pmFullLimit) {
								// Ensures that those PMs that barely have resources will not be 
								// considered in future runs of this loop
								if(j!=runningLen) {
									if(j==runningLen-1) {
										pmList[j]=pmList[runningLen];
									} else {
										System.arraycopy(pmList, j+1, pmList, j, runningLen -j);
									}
								}
								runningLen --;
							}
							vmc++;
							didMove = true;
							break;
						}
					}
				}
				if(vmc==vmList.length) {
					pmList[i]=pmList[beginIndex++];
				}
			}
		} while (didMove);
	}

	/**
	 * Creates a mapping based on FirstFit. 
	 */
	void createFirstFitSolution() {
		createUnchangedSolution();
		useLocalSearch();
		// System.err.println("createFirstFitSolution() -> mapping: "+mappingToString());
	}

	/**
	 * Get the fitness value belonging to this solution.
	 */
	public Fitness evaluate() {
		return fitness;
	}

	interface GenHelper {
		boolean shouldUseDifferent();
		ModelPM whatShouldWeUse(ModelVM vm);
	}
	
	GenHelper mutator=new GenHelper() {

		@Override
		public ModelPM whatShouldWeUse(final ModelVM vm) {
			return bins[SolutionBasedConsolidator.random.nextInt(bins.length)];
		}
		
		@Override
		public boolean shouldUseDifferent() {
			return SolutionBasedConsolidator.random.nextDouble() < mutationProb;
		}
	};
	
	private Solution genNew(final GenHelper helper) {
		Solution result = new Solution(bins, mutationProb);
		result.fitness.nrMigrations=0;
		final ModelVM[] vms=mapping.keySet().toArray(new ModelVM[mapping.size()]);
		for (final ModelVM vm : vms) {
			updateMapping(vm,helper.shouldUseDifferent()?helper.whatShouldWeUse(vm):mapping.get(vm));
		}
		result.useLocalSearch();
		return result;
	}
	
	/**
	 * Create a new solution by mutating the current one. Each gene (i.e., the
	 * mapping of each VM) is replaced by a random one with probability mutationProb
	 * and simply copied otherwise. Note that the current solution (this) is not
	 * changed.
	 */
	Solution mutate() {
		return genNew(mutator);
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
	Solution recombinate(final Solution other) {
		return genNew(new GenHelper() {
		@Override
			public boolean shouldUseDifferent() {
				return SolutionBasedConsolidator.random.nextBoolean();
			}
		@Override
			public ModelPM whatShouldWeUse(final ModelVM vm) {
				return other.mapping.get(vm);
			}
		});
	}

	/**
	 * Implement solution in the model by performing the necessary migrations.
	 */
	public void implement() {
		for (final ModelVM vm : mapping.keySet()) {
			final ModelPM oldPm = vm.gethostPM();
			final ModelPM newPm = mapping.get(vm);
			if (newPm != oldPm)
				oldPm.migrateVM(vm, newPm);
		}
	}

	/**
	 * String representation of both the mapping and the fitness of the given
	 * solution.
	 */
	@Override
	public String toString() {
		final StringBuilder result = new StringBuilder("[m=(");
		boolean first = true;
		for (final ModelVM vm : mapping.keySet()) {
			if (!first)
				result.append(',');
			result.append(vm.hashCode()).append("->").append(mapping.get(vm).hashCode());
			first = false;
		}
		result.append("),f=").append(fitness.toString()).append(']');
		return result.toString();
	}
}
