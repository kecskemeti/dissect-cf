package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.SimpleConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

/**
 * Represents a possible solution of the VM consolidation problem, i.e., a
 * mapping of VMs to PMs. Can be used as an individual in the population.
 */
public class InfrastructureModel {
	private final ArrayList<ModelVM> tempvmlist = new ArrayList<>();
	/** List of all available bins */
	protected ModelPM[] bins;
	/** List of all available items */
	protected ModelVM[] items;

	// Fitness of the solution...
	/**
	 * Total amount of PM overloads, aggregated over all PMs and all resource types
	 */
	protected double totalOverAllocated;
	/** Number of PMs that are on */
	protected int nrActivePms;
	/** Number of migrations necessary from original placement of the VMs */
	protected int nrMigrations;
	// Fitness ends

	final private static Comparator<ModelVM> mvmComp = new Comparator<ModelVM>() {
		@Override
		public int compare(final ModelVM vm1, final ModelVM vm2) {
			return Double.compare(vm2.getResources().getTotalProcessingPower(),
					vm1.getResources().getTotalProcessingPower());
		}
	};

	final private static Comparator<ModelVM> mvmIdCmp = new Comparator<ModelVM>() {
		@Override
		public int compare(final ModelVM vm1, final ModelVM vm2) {
			return Integer.compare(vm1.basedetails.id, vm2.basedetails.id);
		}
	};

	final private static Comparator<ModelPM> mpmComp = new Comparator<ModelPM>() {
		@Override
		public int compare(final ModelPM pm1, final ModelPM pm2) {
			return Double.compare(pm2.consumed.getTotalProcessingPower(), pm1.consumed.getTotalProcessingPower());
		}
	};

	final private static Comparator<ModelPM> mpmFreeComp = new Comparator<ModelPM>() {
		@Override
		public int compare(final ModelPM pm1, final ModelPM pm2) {
			return -pm1.free.compareTo(pm2.free);
		}
	};

	/**
	 * Creates a solution with an empty mapping that will need to be filled somehow,
	 * e.g., using #fillRandomly().
	 */
	public InfrastructureModel(final InfrastructureModel base, final boolean original, final boolean applylocalsearch) {
		this(base);
		if (!original) {
			for (final ModelVM vm : items) {
				updateMapping(vm, bins[MachineLearningConsolidator.random.nextInt(bins.length)]);
			}
		}
		if (applylocalsearch) {
			useLocalSearch();
		}
		calculateFitness();
	}

	private InfrastructureModel(final InfrastructureModel toCopy) {
		bins = new ModelPM[toCopy.bins.length];
		final List<ModelVM> mvms = new ArrayList<>();
		for (int i = 0; i < bins.length; i++) {
			bins[i] = new ModelPM(toCopy.bins[i]);
			mvms.addAll(bins[i].getVMs());
		}
		convItemsArr(mvms);
	}

	private void convItemsArr(final List<ModelVM> mvms) {
		items = mvms.toArray(ModelVM.mvmArrSample);
		Arrays.sort(items, mvmIdCmp);
	}

	/**
	 * In this part all PMs and VMs will be put inside this abstract model. For that
	 * the bins-list contains all PMs as ModelPMs and all VMs as ModelVMs
	 * afterwards.
	 * 
	 * @param pmList All PMs which are currently registered in the IaaS service.
	 */
	public InfrastructureModel(final PhysicalMachine[] pmList, final boolean onlyNonEmpty, final double upperThreshold,
			final double lowerThreshold) {
		final List<ModelPM> pminit = new ArrayList<>(pmList.length);
		final List<ModelVM> vminit = new ArrayList<>();
		int binIndex = 0;
		for (int i = 0; i < pmList.length; i++) {
			// now every PM will be put inside the model with its hosted VMs
			final PhysicalMachine pm = pmList[i];
			// If using a non-externally-controlled PM scheduler, consider only non-empty
			// PMs for consolidation
			if (!(pm.isHostingVMs()) && onlyNonEmpty)
				continue;
			final ModelPM bin = new ModelPM(pm, binIndex++, upperThreshold, lowerThreshold);
			for (final VirtualMachine vm : pm.publicVms) {
				final ModelVM item = new ModelVM(vm, bin, vminit.size());
				bin.addVM(item);
				vminit.add(item);
			}
			pminit.add(bin);
		}

		bins = pminit.toArray(ModelPM.mpmArrSample);
		convItemsArr(vminit);
		calculateFitness();
	}

	/**
	 * Auxiliary method. PRE: the maps #loads and #used are already filled POST:
	 * fitness.nrActivePms and fitness.totalOverAllocated are correct
	 */
	protected void calculateFitness() {
		nrActivePms = 0;
		totalOverAllocated = 0;
		nrMigrations = 0;
		for (final ModelPM pm : bins) {
			if (pm.isHostingVMs()) {
				nrActivePms++;
				final ResourceConstraints ut = pm.getUpperThreshold();
				if (pm.consumed.getTotalProcessingPower() > ut.getRequiredMemory())
					totalOverAllocated += pm.consumed.getTotalProcessingPower() / ut.getTotalProcessingPower();
				if (pm.consumed.getRequiredMemory() > ut.getRequiredMemory())
					totalOverAllocated += pm.consumed.getRequiredMemory() / ut.getRequiredMemory();
			}
		}
		for(final ModelVM vm:items) {
			if(vm.basedetails.initialHost.hashCode()!=vm.gethostPM().hashCode()) {
				nrMigrations++;
			}
		}
	}

	private void updateMapping(final ModelVM v, final ModelPM p) {
		v.gethostPM().migrateVM(v, p);
	}

	protected void useLocalSearch() {
		if (MachineLearningConsolidator.doLocalSearch1) {
			improve();
		} else if (MachineLearningConsolidator.doLocalSearch2) {
			simpleConsolidatorImprove();
		}
	}

	/**
	 * Improving a solution by relieving overloaded PMs, emptying underloaded PMs,
	 * and finding new hosts for the thus removed VMs using BFD.
	 */
	private void improve() {
		tempvmlist.clear();
		// relieve overloaded PMs + empty underloaded PMs
		for (final ModelPM pm : bins) {
			final List<ModelVM> vmsOfPm = pm.getVMs();
			int l = vmsOfPm.size() - 1;
			while (l >= 0 && (pm.isOverAllocated() || pm.isUnderAllocated())) {
				final ModelVM vm = vmsOfPm.get(l--);
				tempvmlist.add(vm);
				pm.removeVM(vm);
			}
		}
		// find new host for the VMs to migrate using BFD
		Collections.sort(tempvmlist, mvmComp);
		final ModelPM[] binsToTry = bins.clone();
		Arrays.sort(binsToTry, mpmComp);
		final int tvmls = tempvmlist.size();
		for (int i = 0; i < tvmls; i++) {
			final ModelVM vm = tempvmlist.get(i);
			ModelPM targetPm = null;
			for (int j = 0; j < binsToTry.length; j++) {
				final ModelPM pm = binsToTry[j];
				if (pm.isMigrationPossible(vm)) {
					targetPm = pm;
					break;
				}
			}
			if (targetPm == null)
				targetPm = vm.prevPM;
			targetPm.addVM(vm);
		}
	}

	/**
	 * The algorithm out of the simple consolidator, adjusted to work with the
	 * abstract model.
	 */
	private void simpleConsolidatorImprove() {
//		Logger.getGlobal().info("starting to improve with second local search");

		// create an array out of the bins
		ModelPM[] pmList = new ModelPM[bins.length];
		int runningLen = 0;
		for (int i = 0; i < pmList.length; i++) {
			final ModelPM curr = bins[i];
			if (curr.isHostingVMs() && curr.free.getTotalProcessingPower() > SimpleConsolidator.pmFullLimit) {
				pmList[runningLen++] = curr;
			}
		}

//		Logger.getGlobal().info("size of the pmList: " + pmList.length);

		boolean didMove;
		runningLen--;
		int beginIndex = 0;
		final HashSet<ModelVM> alreadyMoved = new HashSet<>();
		do {
			didMove = false;

			// sort the array from highest to lowest free capacity with an adjusted version
			// of the fitting pm comparator
			Arrays.sort(pmList, beginIndex, runningLen + 1, mpmFreeComp);

//			Logger.getGlobal().info("filtered array: " + Arrays.toString(pmList));

			for (int i = beginIndex; i < runningLen; i++) {
				final ModelPM source = pmList[i];
				final ModelVM[] vmList = source.getVMs().toArray(ModelVM.mvmArrSample);
				int vmc = 0;
				for (int vmidx = 0; vmidx < vmList.length; vmidx++) {
					final ModelVM vm = vmList[vmidx];
					if (alreadyMoved.contains(vm))
						continue;
					// ModelVMs can only run, so we need not to check the state (there is none
					// either)
					for (int j = runningLen; j > i; j--) {
						final ModelPM target = pmList[j];

						if (target.isMigrationPossible(vm)) {
							source.migrateVM(vm, target);
							alreadyMoved.add(vm);

							if (target.free.getTotalProcessingPower() < SimpleConsolidator.pmFullLimit) {
								// Ensures that those PMs that barely have resources will not be
								// considered in future runs of this loop
								if (j != runningLen) {
									if (j == runningLen - 1) {
										pmList[j] = pmList[runningLen];
									} else {
										System.arraycopy(pmList, j + 1, pmList, j, runningLen - j);
									}
								}
								runningLen--;
							}
							vmc++;
							didMove = true;
							break;
						}
					}
				}
				if (vmc == vmList.length) {
					pmList[i] = pmList[beginIndex++];
				}
			}
		} while (didMove);
	}

	interface GenHelper {
		boolean shouldUseDifferent();

		ModelPM whatShouldWeUse(int vm);
	}

	private InfrastructureModel genNew(final GenHelper helper) {
		final InfrastructureModel result = new InfrastructureModel(this);
		for (int i = 0; i < items.length; i++) {
			if (helper.shouldUseDifferent()) {
				result.updateMapping(items[i], helper.whatShouldWeUse(i));
			}
		}
		result.useLocalSearch();
		result.calculateFitness();
		return result;
	}

	/**
	 * Create a new solution by mutating the current one. Each gene (i.e., the
	 * mapping of each VM) is replaced by a random one with probability mutationProb
	 * and simply copied otherwise. Note that the current solution (this) is not
	 * changed.
	 */
	InfrastructureModel mutate(final double mutationProb) {
		return genNew(new GenHelper() {

			@Override
			public ModelPM whatShouldWeUse(final int vm) {
				return bins[MachineLearningConsolidator.random.nextInt(bins.length)];
			}

			@Override
			public boolean shouldUseDifferent() {
				return MachineLearningConsolidator.random.nextDoubleFast() < mutationProb;
			}
		});
	}

	/**
	 * Create a new solution by recombinating this solution with another. Each gene
	 * (i.e., the mapping of each VM) is taken randomly either from this or the
	 * other parent. Note that the two parents are not changed.
	 * 
	 * @param other The other parent for the recombination
	 * @return A new solution resulting from the recombination
	 */
	InfrastructureModel recombinate(final InfrastructureModel other) {
		return genNew(new GenHelper() {
			@Override
			public boolean shouldUseDifferent() {
				return MachineLearningConsolidator.random.nextBoolean();
			}

			@Override
			public ModelPM whatShouldWeUse(final int vm) {
				return other.items[vm].gethostPM();
			}
		});
	}

	/**
	 * Decides if this fitness value is better than the other. Note that this
	 * relation is not a total order: it is possible that from two fitness values,
	 * no one is better than the other.
	 * 
	 * @param other Another fitness value
	 * @return true if this is better than other
	 */
	boolean isBetterThan(final InfrastructureModel other) {
		return betterThan(this.totalOverAllocated, this.nrActivePms, this.nrMigrations, other.totalOverAllocated, other.nrActivePms, other.nrMigrations);
	}

	protected static final boolean betterThan(final double oA1, final int nAPM1, final int nMg1, final double oA2, final int nAPM2, final int nMg2) {
		// The primary objective is the total overload. If there is a clear
		// difference (>1%) in that, this decides which is better.
		// If there is no significant difference in the total overload, then
		// the number of active PMs decides.
		// If there is no significant difference in the total overload, nor
		// in the number of active PMs, then the number of migrations decides.
		return oA1 < oA2 * 0.99 || oA2 >= oA1 * 0.99 && (nAPM1 < nAPM2 || nAPM2 == nAPM1 && nMg1 < nMg2);
	}

	/**
	 * String representation of both the mapping and the fitness of the given
	 * solution.
	 */
	@Override
	public String toString() {
		final StringBuilder result = new StringBuilder("[m=(");
		boolean first = true;
		for (final ModelVM vm : items) {
			if (!first)
				result.append(',');
			result.append(vm.hashCode()).append("->").append(vm.gethostPM().hashCode());
			first = false;
		}
		result.append("),f=(").append(totalOverAllocated).append(',').append(nrActivePms).append(',')
				.append(nrMigrations).append(")]");
		return result.toString();
	}
}
