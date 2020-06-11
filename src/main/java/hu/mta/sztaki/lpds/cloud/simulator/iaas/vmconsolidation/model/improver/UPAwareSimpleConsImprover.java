package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.improver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.SimpleConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelPM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelVM;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */
public class UPAwareSimpleConsImprover extends SimpleConsImprover {
	public static final UPAwareSimpleConsImprover singleton = new UPAwareSimpleConsImprover();

	final private static Comparator<ModelPM> mpmFreeComp = new Comparator<ModelPM>() {
		@Override
		public int compare(final ModelPM pm1, final ModelPM pm2) {
			return -pm1.free.compareTo(pm2.free);
		}
	};

	public UPAwareSimpleConsImprover() {
		super();
	}

	/**
	 * The algorithm out of the simple consolidator, adjusted to work with the
	 * abstract model.
	 */
	@Override
	public void improve(final InfrastructureModel toImprove) {

		// Cleaning up over-used PMs
		final ArrayList<ModelPM> underProvisioned = new ArrayList<>(toImprove.bins.length);
		final ArrayList<ModelPM> otherPMs = new ArrayList<>(toImprove.bins.length);
		for (final ModelPM curr : toImprove.bins) {
			if (curr.isHostingVMs() && curr.free.getTotalProcessingPower() < 0) {
				underProvisioned.add(curr);
			} else if (curr.free.getTotalProcessingPower() > SimpleConsolidator.pmFullLimit) {
				otherPMs.add(curr);
			}
		}


		boolean didMove;
		final HashSet<ModelVM> alreadyMoved = new HashSet<>();
		do {
			didMove = false;

			// Tries to fill in the heaviest loaded PMs first.
			otherPMs.sort(mpmFreeComp);

			undLoop: for (int i = underProvisioned.size()-1; i >= 0; i--) {
				final ModelPM source = underProvisioned.get(i);
				final ModelVM[] vmList = source.getVMs().toArray(ModelVM.mvmArrSample);
				for (int vmidx = 0; vmidx < vmList.length; vmidx++) {
					final ModelVM vm = vmList[vmidx];
					if (alreadyMoved.contains(vm))
						continue;
					// ModelVMs can only run, so we need not to check the state (there is none
					// either)
					for (int j = otherPMs.size()-1; j >= 0; j--) {
						final ModelPM target = otherPMs.get(j);

						if (target.isMigrationPossible(vm)) {
							source.migrateVM(vm, target);
							alreadyMoved.add(vm);
							didMove = true;
							if (target.free.getTotalProcessingPower() < SimpleConsolidator.pmFullLimit) {
								otherPMs.remove(j);
							}
							if (source.free.getTotalProcessingPower() > 0) {
								// The source PM is now capable to serve its hosted VMs.
								underProvisioned.remove(i);
								continue undLoop;
							}
							break;
						}
					}
				}
			}
		} while (didMove);
		
		// There are no underprovisioned VMs now. We can head to the simple consolidator's core algorithm.
		super.improve(toImprove);
	}
}
