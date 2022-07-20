/*
 *  ========================================================================
 *  DIScrete event baSed Energy Consumption simulaTor
 *    					             for Clouds and Federations (DISSECT-CF)
 *  ========================================================================
 *
 *  This file is part of DISSECT-CF.
 *
 *  DISSECT-CF is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or (at
 *  your option) any later version.
 *
 *  DISSECT-CF is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with DISSECT-CF.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  (C) Copyright 2019-20, Gabor Kecskemeti, Rene Ponto, Zoltan Mann
 */
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.simple;

import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.ModelBasedConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelPM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelVM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.PreserveAllocations;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.improver.NonImprover;

public class ExhaustiveConsolidator extends ModelBasedConsolidator {

	private JFrame optiProgressFrame;

	public ExhaustiveConsolidator(final IaaSService toConsolidate, final long consFreq) {
		super(toConsolidate, consFreq);
	}

	int depth = -1;

	private InfrastructureModel doInfra(ArrayList<ModelVM> toPlace, InfrastructureModel base, int visPrg) {
		depth++;
		visPrg--;
		Container cp = null;
		JProgressBar jpb = null;
		JPanel jp = null;
		if (visPrg > 0) {
			cp = optiProgressFrame.getContentPane();
			jp = new JPanel();
			JLabel jl = new JLabel("L" + depth);
			jl.setPreferredSize(new Dimension(30, 30));
			jp.add(jl);
			jpb = new JProgressBar();
			jpb.setPreferredSize(new Dimension(300, 30));
			jp.add(jpb);
			cp.add(jp);
			optiProgressFrame.setSize(340, cp.getComponents().length * 35);
		}
		final int itemToRemove = toPlace.size() - 1;
		final ModelVM curr = toPlace.remove(itemToRemove);
		final boolean lastItem = itemToRemove == 0;
		InfrastructureModel best = lastItem
				? new InfrastructureModel(base, PreserveAllocations.singleton, NonImprover.singleton)
				: base;
		boolean didNotSeeHost = true;
		final double[] alreadyCheckedCompletelyFreeCapacityPMs = new double[base.bins.length];
		int alreadyCheckedLen = 0;
		Arrays.fill(alreadyCheckedCompletelyFreeCapacityPMs, -1.0);
		int pmidx = 0;
		nextPM: for (final ModelPM aPM : base.bins) {
			if (visPrg > 0) {
				jpb.setValue(100 * pmidx++ / base.bins.length);
				optiProgressFrame.repaint();
			}
			// We don't need to test with the current host
			if (didNotSeeHost && aPM == curr.gethostPM()) {
				didNotSeeHost = false;
				continue;
			}
			// We don't need to test on a host with no processing power.
			if (aPM.free.getTotalProcessingPower() < 0.0000001) {
				continue;
			}
			if (!aPM.isHostingVMs()) {
				final double totcaps = aPM.getTotalResources().getTotalProcessingPower();
				// There is no point of checking completely empty machines that have the same
				// spec we have checked before as this is just a useless permutation. With the
				// typical completely homogeneous infras, we will only have a single item here.
				for (int i = 0; i < alreadyCheckedLen; i++) {
					// Not so efficient, but likely not contain too many elements anyways
					if (totcaps == alreadyCheckedCompletelyFreeCapacityPMs[i]) {
						continue nextPM;
					}
				}
				alreadyCheckedCompletelyFreeCapacityPMs[alreadyCheckedLen++] = totcaps;
			}
			if (aPM.isMigrationPossible(curr)) {
				final ModelPM currHost = curr.gethostPM();
				currHost.migrateVM(curr, aPM);
				InfrastructureModel alt;
				if (lastItem) {
					base.calculateFitness();
					alt = base;
				} else {
					alt = doInfra(toPlace, base, visPrg);
				}
				if (alt.isBetterThan(best)) {
					if (lastItem) {
						alt = new InfrastructureModel(base, PreserveAllocations.singleton, NonImprover.singleton);
					}
					best = alt;
				}
				// We keep the infra as we have received it
				aPM.migrateVM(curr, currHost);
			}
		}
		if (visPrg > 0) {
			cp.remove(jp);
		}
		toPlace.add(curr);
		depth--;
		return best == base ? new InfrastructureModel(base, PreserveAllocations.singleton, NonImprover.singleton)
				: best;
	}

	@Override
	protected InfrastructureModel optimize(final InfrastructureModel initial) {
		if (initial.items.length < 2) {
			// There is no way we can improve on the current placement as wherever this VM
			// is we are best just keeping it on the host. Note that on more heterogeneous
			// infrastructures, this would still be a good idea to try.
			return initial;
		}

		double maxCores = -1;
		int pmsThatHostATM = 0;
		for (final ModelPM pm : initial.bins) {
			maxCores = Math.max(pm.getTotalResources().getRequiredCPUs(), maxCores);
			pmsThatHostATM += pm.isHostingVMs() ? 1 : 0;
		}
		if (pmsThatHostATM == 1) {
			// There is no chance to reduce the amount of PMs. We are better off just
			// keeping the current PM hosting all VMs. Note that his might not be always a
			// good idea on heterogeneous infrastructures.
			return initial;
		}
		maxCores /= 2;
		final ArrayList<ModelVM> toMove = new ArrayList<>();
		int nonMovableVMs = 0;
		for (final ModelVM vm : initial.items) {
			if (vm.getResources().getRequiredCPUs() < maxCores) {
				toMove.add(vm);
			} else {
				nonMovableVMs++;
			}
		}
		if (toMove.isEmpty()) {
			// There are no VMs that we can move around, let's keep the infra as is.
			return initial;
		}
		if (nonMovableVMs == pmsThatHostATM) {
			// No hosts are on which host non-movable VMs.
			return initial;
		}

		// We really need the exhaustive search now.
		optiProgressFrame = new JFrame("Consolidating at " + Timed.getFireCount());
		optiProgressFrame.setLocation(Integer.parseInt(System.getProperty("EC.LocX")),
				Integer.parseInt(System.getProperty("EC.LocY")));
		Container cont = optiProgressFrame.getContentPane();
		cont.setLayout(new BoxLayout(cont, BoxLayout.PAGE_AXIS));

		optiProgressFrame.setVisible(true);
		InfrastructureModel ret = doInfra(toMove, initial, Math.min(5, toMove.size()));
		optiProgressFrame.dispose();
		System.out.println(".");
		return ret;
	}

	@Override
	protected void processProps() {
		// TODO Auto-generated method stub

	}

}
