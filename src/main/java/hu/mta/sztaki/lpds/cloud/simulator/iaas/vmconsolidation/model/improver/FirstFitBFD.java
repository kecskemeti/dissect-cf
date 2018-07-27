package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.improver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelPM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelVM;

/**
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2018"
 */
public class FirstFitBFD extends InfrastructureModel implements InfrastructureModel.Improver {
	public static final FirstFitBFD singleton = new FirstFitBFD();
	final private static Comparator<ModelVM> mvmComp = new Comparator<ModelVM>() {
		@Override
		public int compare(final ModelVM vm1, final ModelVM vm2) {
			return Double.compare(vm2.getResources().getTotalProcessingPower(),
					vm1.getResources().getTotalProcessingPower());
		}
	};

	final private static Comparator<ModelPM> mpmComp = new Comparator<ModelPM>() {
		@Override
		public int compare(final ModelPM pm1, final ModelPM pm2) {
			return Double.compare(pm2.consumed.getTotalProcessingPower(), pm1.consumed.getTotalProcessingPower());
		}
	};

	private FirstFitBFD() {
		super(new PhysicalMachine[0], 0, false, 0);
	}

	/**
	 * Improving a solution by relieving overloaded PMs, emptying underloaded PMs,
	 * and finding new hosts for the thus removed VMs using BFD.
	 */
	@Override
	public void improve(final InfrastructureModel toImprove) {
		final ArrayList<ModelVM> tempvmlist = new ArrayList<>();
		// relieve overloaded PMs + empty underloaded PMs
		for (final ModelPM pm : toImprove.bins) {
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
		final ModelPM[] binsToTry = toImprove.bins.clone();
		Arrays.sort(binsToTry, mpmComp);
		final int tvmls = tempvmlist.size();
		for (int i = 0; i < tvmls; i++) {
			final ModelVM vm = tempvmlist.get(i);
			ModelPM targetPm = null;
			for (final ModelPM pm : binsToTry) {
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
}
