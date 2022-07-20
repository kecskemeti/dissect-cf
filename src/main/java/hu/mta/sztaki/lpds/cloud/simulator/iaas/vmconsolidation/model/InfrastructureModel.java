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
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model;

import java.util.ArrayList;
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

/**
 * Represents a possible solution of the VM consolidation problem, i.e., a
 * mapping of VMs to PMs. Can be used as an individual in the population.
 */
public class InfrastructureModel {

	public interface Improver {
		void improve(InfrastructureModel im);
	}

	/**
	 * List of all available bins, order is important, all models have the same bin
	 * order at a particular consolidation run
	 */
	public final ModelPM[] bins;
	/**
	 * List of all available items, order is important, all models have the same
	 * items order at a particular consolidation run
	 */
	public final ModelVM[] items;

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

	public InfrastructureModel(final InfrastructureModel toCopy, final GenHelper helper, final Improver localSearch) {
		bins = new ModelPM[toCopy.bins.length];
		items = new ModelVM[toCopy.items.length];

		// Base copy without mapping
		for (int i = 0; i < bins.length; i++) {
			bins[i] = new ModelPM(toCopy.bins[i]);
		}

		// Mapping as desired by helper
		for (int i = 0; i < items.length; i++) {
			final ModelVM oldVM = toCopy.items[i];
			items[i] = new ModelVM(oldVM);
			bins[helper.shouldUseDifferent() ? helper.whatShouldWeUse(this, i) : oldVM.getHostID()].addVM(items[i]);
		}
		localSearch.improve(this);
		calculateFitness();
	}

	/**
	 * In this part all PMs and VMs will be put inside this abstract model. For that
	 * the bins-list contains all PMs as ModelPMs and all VMs as ModelVMs
	 * afterwards.
	 * 
	 * @param pmList All PMs which are currently registered in the IaaS service.
	 */
	public InfrastructureModel(final PhysicalMachine[] pmList, final double lowerThreshold, final boolean onlyNonEmpty,
			final double upperThreshold) {
		final List<ModelPM> pminit = new ArrayList<>(pmList.length);
		final List<ModelVM> vminit = new ArrayList<>(pmList.length);
		int nonHostingRunningPMs = 0;
		for (final PhysicalMachine pm : pmList) {
			// now every PM will be put inside the model with its hosted VMs
			// If using a non-externally-controlled PM scheduler, consider only non-empty
			// PMs for consolidation
			if (!pm.isHostingVMs()) {
				if (onlyNonEmpty)
					continue;
				else if (pm.isRunning())
					nonHostingRunningPMs++;
			}
			final ModelPM bin = new ModelPM(pm, lowerThreshold, pminit.size(), upperThreshold);
			for (final VirtualMachine vm : pm.publicVms) {
				final ModelVM item = new ModelVM(vm, bin, vminit.size());
				bin.addVM(item);
				vminit.add(item);
			}
			pminit.add(bin);
		}

		bins = pminit.toArray(ModelPM.mpmArrSample);
		items = vminit.toArray(ModelVM.mvmArrSample);
		calculateFitness();
		nrActivePms += nonHostingRunningPMs;
	}

	/**
	 * Auxiliary method. PRE: the maps #loads and #used are already filled POST:
	 * fitness.nrActivePms and fitness.totalOverAllocated are correct
	 */
	public void calculateFitness() {
		nrActivePms = 0;
		totalOverAllocated = 0;
		nrMigrations = 0;
		for (final ModelPM pm : bins) {
			if (pm.isHostingVMs()) {
				nrActivePms++;
				final ResourceConstraints ut = pm.getUpperThreshold();
				if (pm.consumed.getTotalProcessingPower() > ut.getTotalProcessingPower())
					totalOverAllocated += pm.consumed.getTotalProcessingPower() / ut.getTotalProcessingPower();
				if (pm.consumed.getRequiredMemory() > ut.getRequiredMemory())
					totalOverAllocated += (double)pm.consumed.getRequiredMemory() / ut.getRequiredMemory();
			}
		}
		for (final ModelVM vm : items) {
			if (vm.basedetails.initialHost.hashCode() != vm.getHostID()) {
				nrMigrations++;
			}
		}
	}

	/**
	 * Decides if this fitness value is better than the other. Note that this
	 * relation is not a total order: it is possible that from two fitness values,
	 * no one is better than the other.
	 * 
	 * @param other Another fitness value
	 * @return true if this is better than other
	 */
	public boolean isBetterThan(final InfrastructureModel other) {
		return betterThan(this.totalOverAllocated, this.nrActivePms, this.nrMigrations, other.totalOverAllocated,
				other.nrActivePms, other.nrMigrations);
	}

	protected static boolean betterThan(final double oA1, final int nAPM1, final int nMg1, final double oA2,
			final int nAPM2, final int nMg2) {
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
		final StringBuilder result = new StringBuilder("[f=(");
		result.append(totalOverAllocated).append(',').append(nrActivePms).append(',').append(nrMigrations)
				.append("),m=(");
		boolean first = true;
		for (final ModelVM vm : items) {
			if (!first)
				result.append(',');
			result.append(vm.hashCode()).append("->").append(vm.getHostID());
			first = false;
		}
		result.append(")]");
		return result.toString();
	}

	public int getNrActivePms() {
		return nrActivePms;
	}
}
