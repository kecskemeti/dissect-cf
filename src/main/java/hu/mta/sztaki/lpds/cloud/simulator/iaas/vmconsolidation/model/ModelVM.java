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

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

/**
 * @author Julian Bellendorf, Rene Ponto
 * 
 *         This class represents a VM in this abstract model. It has only the
 *         necessary things for consolidation which means the hosting PM, its
 *         needed resources and the id of the original VM. The resource
 *         consumption happens inside the ModelPM class.
 */
public class ModelVM {
	public static final ModelVM[] mvmArrSample = new ModelVM[0];

	private ModelPM hostPM;
	public ModelPM prevPM;
	public final ImmutableVMComponents basedetails;

	/**
	 * This represents a VirtualMachine of the simulator. For that this class
	 * contains the real VM itself, the actual abstract host, the resources (cores,
	 * perCoreProcessing, memory) and the id for debugging. With the resources a
	 * ResourceVector is created for this VM.
	 * 
	 * @param vm    The real Virtual Machine in the Simulator.
	 * @param pm    The hosting PM.
	 * @param id    The ID of the original VM.
	 */
	public ModelVM(final VirtualMachine vm, final ModelPM pm, final int id) {
		basedetails = new ImmutableVMComponents(vm, pm, id);
		hostPM = pm; // save the host PM
	}

	/**
	 * Host is not copied as it is assumed to be changed after copy
	 * @param toCopy
	 */
	public ModelVM(final ModelVM toCopy) {
		this.basedetails = toCopy.basedetails;
		// host is not copied
	}

	/**
	 * toString() is just for debugging and returns the ID, cores,
	 * perCoreProcessingPower and memory of this VM.
	 */
	public String toString() {
		return basedetails.id() + ", " + "Cores: " + getResources().getRequiredCPUs() + ", " + "ProcessingPower: "
				+ getResources().getRequiredProcessingPower() + ", " + "Memory: " + getResources().getRequiredMemory();
	}

	/**
	 * A small representation to get the id of this vm.
	 * 
	 * @return The id of this vm.
	 */
	public String toShortString() {
		return "VM " + basedetails.id();
	}

	/**
	 * Getter
	 * 
	 * @return the ResourceVector
	 */
	public ResourceConstraints getResources() {
		return basedetails.vm().getResourceAllocation().allocated;
	}

	/**
	 * Getter
	 * 
	 * @return The actual host of this VM.
	 */
	public ModelPM gethostPM() {
		return hostPM;
	}
	
	public int getHostID() {
		return hostPM.hashCode();
	}

	public void migrate(final ModelPM target) {
		hostPM.migrateVM(this, target);
	}
	
	/**
	 * Setter for the hostPM
	 * 
	 * @param bin The new host.
	 */
	public void sethostPM(final ModelPM bin) {
		this.hostPM = bin;
	}

	/**
	 * Getter.
	 * 
	 * @return The id of this vm.
	 */
	@Override
	public int hashCode() {
		return basedetails.id();
	}
}
