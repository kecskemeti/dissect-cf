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
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */

package hu.mta.sztaki.lpds.cloud.simulator.iaas;

import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import java.util.Collection;
import java.util.HashMap;

public interface VMManager<E> {

	class VMManagementException extends Exception {

		private static final long serialVersionUID = 4420666848803005233L;

		public VMManagementException(final String message) {
			super(message);
		}

		public VMManagementException(final String message, final Throwable ex) {
			super(message, ex);
		}
	}

	class NoSuchVMException extends VMManagementException {
		private static final long serialVersionUID = 777996106319988828L;

		public NoSuchVMException(final String message) {
			super(message);
		}

		public NoSuchVMException(final String message, final Throwable ex) {
			super(message, ex);
		}
	}

	interface CapacityChangeEvent {
		void capacityChanged(ResourceConstraints newCapacity);
	}

	VirtualMachine[] requestVM(VirtualAppliance va, ResourceConstraints rc,
			Repository vaSource, int count,
			HashMap<String, Object> schedulingConstraints)
			throws VMManagementException, NetworkNode.NetworkException;

	void terminateVM(VirtualMachine vm, boolean killTasks)
			throws NoSuchVMException, VMManagementException;

	void migrateVM(VirtualMachine vm, E target) throws NoSuchVMException,
			VMManagementException, NetworkNode.NetworkException;

	void reallocateResources(VirtualMachine vm, ResourceConstraints newresources)
			throws NoSuchVMException, VMManagementException;

	Collection<VirtualMachine> listVMs();

	ResourceConstraints getCapacities();

	void subscribeToCapacityChanges(CapacityChangeEvent e);

	void unsubscribeFromCapacityChanges(CapacityChangeEvent e);
}
