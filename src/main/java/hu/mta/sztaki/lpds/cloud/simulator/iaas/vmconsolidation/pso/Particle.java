package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.pso;

import java.util.Arrays;
import java.util.Comparator;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.CachingPRNG;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.GenHelper;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelPM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelVM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.improver.NonImprover;

/**
 * @author Rene Ponto
 *
 *         The idea for particles is to have a list of double values where all
 *         hostPMs in order of their hosted VMs are. Therefore the id of the
 *         specific PM is used. On the given list mathematical operations can be
 *         done, like addition and substraction. For that list an
 *         ArithmeticVector is used.
 * 
 *         The ArithmeticVector location contains only the IDs of the PMs while
 *         the mapping contains the specific mapping of PMs to VMs inside
 *         Solution.
 */

public class Particle extends InfrastructureModel {

	/**
	 * Total amount of PM overloads, aggregated over all PMs and all resource types
	 */
	protected double bestTotalOverAllocated = Double.MAX_VALUE;
	/** Number of PMs that are on */
	protected int bestNrActivePms = Integer.MAX_VALUE;
	/** Number of migrations necessary from original placement of the VMs */
	protected int bestNrMigrations = Integer.MAX_VALUE;
	private final ArithmeticVector currentLocation;
	private final ArithmeticVector currentVelocity;
	private final int[] vmFromHashToLocalIdx;
	private final int[] vmFromLocalIdxToHash;
	private final int[] pmFromHashToLocalIdx;
	private final int[] pmFromLocalIdxToHash;
	private ArithmeticVector personalBest;

	/** used to get a new velocity for each particle */
	private static final double w = 0.6;

	/**
	 * Creates a new Particle and sets the bins list with the values out of the
	 * model. For that the constructor of the superclass "Solution" is used.
	 * 
	 * @param bins   The currently existing pms.
	 * @param number The id of this particle.
	 */
	public Particle(final InfrastructureModel base, final GenHelper vmAssignment,
			final InfrastructureModel.Improver localsearch) {
		super(base, vmAssignment, NonImprover.singleton);
		final ModelPM[] srtPMs = bins.clone();
		Arrays.sort(srtPMs, new Comparator<ModelPM>() {
			@Override
			public int compare(final ModelPM arg0, final ModelPM arg1) {
				return Double.compare(arg0.basedetails.pm.freeCapacities.getTotalProcessingPower(),
						arg1.basedetails.pm.freeCapacities.getTotalProcessingPower());
			}
		});
		pmFromHashToLocalIdx = new int[srtPMs.length];
		for (int i = 0; i < srtPMs.length; i++) {
			pmFromHashToLocalIdx[srtPMs[i].hashCode()] = i;
		}
		pmFromLocalIdxToHash = new int[srtPMs.length];
		for (int i = 0; i < srtPMs.length; i++) {
			pmFromLocalIdxToHash[i] = srtPMs[i].hashCode();
		}

		final ModelVM[] sorted = items.clone();
		Arrays.sort(sorted, new Comparator<ModelVM>() {
			@Override
			public int compare(final ModelVM arg0, final ModelVM arg1) {
				return -Double.compare(arg0.getResources().getTotalProcessingPower(),
						arg1.getResources().getTotalProcessingPower());
			}
		});
		vmFromHashToLocalIdx = new int[sorted.length];
		for (int i = 0; i < sorted.length; i++) {
			vmFromHashToLocalIdx[sorted[i].basedetails.id] = i;
		}
		vmFromLocalIdxToHash = new int[sorted.length];
		for (int i = 0; i < sorted.length; i++) {
			vmFromLocalIdxToHash[i] = sorted[i].basedetails.id;
		}
		double[] velBase = new double[base.items.length];
		double[] locBase = new double[base.items.length];
		/*
		 * Creates the initial location and velocity of a particle. The velocity points
		 * randomly in a positive or negative direction.
		 */
		for (int i = 0; i < base.items.length; i++) {
			locBase[i] = pmFromHashToLocalIdx[sorted[i].getHostID()];
			// here we make a random chance of getting a lower id or a higher id
			velBase[i] = CachingPRNG.genBoolean() ? 1.0 : -1.0; // add the random velocity
		}
		currentVelocity = new ArithmeticVector(velBase);
		currentLocation = new ArithmeticVector(locBase);
		// adds up the velocity to create the initial location
		currentLocation.addUp(currentVelocity);
		// adjusts the mappings with the new location
		updateMappings(localsearch);
	}

	/**
	 * Allows the creation of a particle as an average of the swarm specified in the
	 * parameter
	 * 
	 * @param baseSwarm
	 */
	public Particle(final Particle[] baseSwarm) {
		super(baseSwarm[0], new GenHelper() {
			double[] locBase = genLocBase();

			private double[] genLocBase() {
				double[] returner = new double[baseSwarm[0].items.length];
				Arrays.fill(returner, 0);
				for (int i = 0; i < returner.length; i++) {
					for (int j = 0; j < baseSwarm.length; j++) {
						returner[i] += baseSwarm[j].currentLocation.data[i];
					}
					returner[i] /= baseSwarm.length;
				}
				return returner;
			}

			@Override
			public int whatShouldWeUse(final InfrastructureModel im, final int vm) {
				return (int) locBase[vm];
			}

			@Override
			public boolean shouldUseDifferent() {
				return true;
			}
		}, NonImprover.singleton);
		vmFromHashToLocalIdx = baseSwarm[0].vmFromHashToLocalIdx.clone();
		vmFromLocalIdxToHash = baseSwarm[0].vmFromLocalIdxToHash.clone();
		pmFromHashToLocalIdx = baseSwarm[0].pmFromHashToLocalIdx.clone();
		pmFromLocalIdxToHash = baseSwarm[0].pmFromLocalIdxToHash.clone();
		double[] velBase = new double[baseSwarm[0].items.length];
		Arrays.fill(velBase, 0);
		for (int i = 0; i < velBase.length; i++) {
			for (int j = 0; j < baseSwarm.length; j++) {
				velBase[i] += baseSwarm[j].currentVelocity.data[i];
			}
			velBase[i] /= baseSwarm.length;
		}
		currentLocation = new ArithmeticVector(new double[baseSwarm[0].items.length]);
		currentVelocity = new ArithmeticVector(new double[baseSwarm[0].items.length]);
		updateLocationFromMapping();
		System.arraycopy(velBase, 0, currentVelocity.data, 0, currentVelocity.data.length);
		savePBest();
	}

	/**
	 * Has to be called when using the arithmetics or after the mapping has changed.
	 * Note that there is a difference in saving the pms inside the mappings and
	 * inside the location.
	 */
	private boolean updateLocationFromMapping() {
		// Saves the previous location so we can determine where we came from to the
		// current location represented in mapping
//		System.arraycopy(currentLocation.data, 0, currentVelocity.data, 0, currentLocation.data.length);
		for (int i = 0; i < items.length; i++) {
			// ModelPMs are stored in the bins array indexed with their hashcode
			currentLocation.data[i] = pmFromHashToLocalIdx[items[vmFromLocalIdxToHash[i]].getHostID()];
		}
		// Determines what velocity vector was actually used to reach this location
//		currentVelocity.scale(-1);
//		currentVelocity.addUp(currentLocation);
		if (improvedOnPersonal()) {
			// the aim is to minimize the function
			savePBest();
			return true;
		}
		return false;
	}

	/**
	 * We use the actual location do update the current mapping. For that we have to
	 * check each of the mappings (loads, mapping, used) and update them depending
	 * on the changeds inside the location. Note that there is a difference in
	 * saving the pms inside the mappings and inside the location.
	 */
	private boolean updateMappings(final InfrastructureModel.Improver localSearch) {
		// check if the mappings and the location are different, then adjust the
		// mappings
		for (int i = 0; i < items.length; i++) {
			final int vmMapping = (int) currentLocation.data[i];

			// the host of this vm, has to be done because the ids start at one
			final ModelPM locPm = bins[pmFromLocalIdxToHash[vmMapping < 0 ? 0
					: vmMapping >= bins.length ? bins.length - 1 : vmMapping]];
			final int orI = vmFromLocalIdxToHash[i];
			final ModelPM mappedPm = items[orI].gethostPM();

			// now we have to check if both hosts are similar, then we can move on with the
			// next. Otherwise we have to adjust the mappings
			if (locPm.hashCode() != mappedPm.hashCode()) {
				// pms are not the same
				mappedPm.migrateVM(items[orI], locPm);
			}
		}

		localSearch.improve(this);

		// determine the fitness
		calculateFitness();

		return updateLocationFromMapping();
	}

	private boolean improvedOnPersonal() {
		return betterThan(totalOverAllocated, nrActivePms, nrMigrations, bestTotalOverAllocated, bestNrActivePms,
				bestNrMigrations);
	}

	private void savePBest() {
		this.bestNrActivePms = this.nrActivePms;
		this.bestNrMigrations = this.nrMigrations;
		this.bestTotalOverAllocated = this.totalOverAllocated;
		personalBest = new ArithmeticVector(currentLocation);
	}

	private void createAndAddVelocityComponent(final ArithmeticVector direction, final double rnd) {
		final ArithmeticVector comp = new ArithmeticVector(direction);
		comp.subtract(currentLocation);
		comp.scale(rnd);
		currentVelocity.addUp(comp);
	}

	public boolean isBetterBest(final Particle o) {
		return betterThan(bestTotalOverAllocated, bestNrActivePms, bestNrMigrations, o.bestTotalOverAllocated,
				o.bestNrActivePms, o.bestNrMigrations);
	}

	/**
	 * Comment on the function to update the velocity:
	 * 
	 * At first the actual Velocity of the Particle has to be multiplied with the
	 * user-supplied coefficient w, which is called the inertiaComponent. After this
	 * the cognitiveComponent has to be added to the inertiaComponent, which
	 * inherits the multiplication of the coefficients c1 and r1, multiplied with
	 * the result of the personalBestLocation of the Particle minus the actual
	 * location of it. Afterwards the socialComponent also has to be added to the
	 * parts before. It inherits the multiplication of the coefficients c2 and r2,
	 * multiplied with the result of the subtraction of the globalBestLocation with
	 * the actual location of the Particle.
	 */
	public void updateVelocity(final Particle bestSoFar, final double rnd1, final double rnd2) {
		currentVelocity.scale(w);
		// The cognitive component:
		createAndAddVelocityComponent(personalBest, rnd1);
		// The social component:
		createAndAddVelocityComponent(bestSoFar.personalBest, rnd2);
	}

	/**
	 * now the mapping has to be converted to an ArithmeticVector adds up the
	 * velocity to create the updated location then adjusts the mappings with the
	 * new location
	 * 
	 * @param localSearch
	 */
	public boolean updateLocation(final InfrastructureModel.Improver localSearch) {
		currentLocation.addUp(currentVelocity);
		return updateMappings(localSearch);
	}

	public void replaceMappingWithPersonalBest() {
		System.arraycopy(personalBest.data, 0, currentLocation.data, 0, personalBest.data.length);
		updateMappings(NonImprover.singleton);
	}

	@Override
	public String toString() {
		return "B(" + bestTotalOverAllocated + "," + bestNrActivePms + "," + bestNrMigrations + ")" + super.toString();
	}
}