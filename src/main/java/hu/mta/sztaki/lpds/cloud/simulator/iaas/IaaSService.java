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

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.StateChangeListener;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.PhysicalMachineController;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.util.ArrayHandler;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class IaaSService implements VMManager<IaaSService> {

	public static class IaaSHandlingException extends Exception {

		private static final long serialVersionUID = 2580735547805541590L;

		public IaaSHandlingException(final String s) {
			super(s);
		}

		public IaaSHandlingException(final String s, final Throwable ex) {
			super(s, ex);
		}
	}

	protected class MachineListener implements StateChangeListener {
		public final PhysicalMachine pm;

		public MachineListener(PhysicalMachine pm) {
			this.pm = pm;
			pm.subscribeStateChangeEvents(this);
		}

		@Override
		public void stateChanged(State oldState, State newState) {
			switch (newState) {
				case RUNNING:
					internalRunningMachines.add(pm);
					runningCapacity = ResourceConstraints.add(runningCapacity,
							pm.getCapacities());
					return;
				default:
					if (oldState.equals(PhysicalMachine.State.RUNNING)) {
						ArrayHandler.removeAndReplaceWithLast(internalRunningMachines, pm);
						//internalRunningMachines.remove(pm);
						runningCapacity = ResourceConstraints.subtract(
								runningCapacity, pm.getCapacities());
					}
			}
		}
	}

	private ArrayList<PhysicalMachine> internalMachines = new ArrayList<PhysicalMachine>();
	private ArrayList<PhysicalMachine> internalRunningMachines = new ArrayList<PhysicalMachine>();

	private CopyOnWriteArrayList<MachineListener> listeners = new CopyOnWriteArrayList<MachineListener>();

	public List<PhysicalMachine> machines = Collections
			.unmodifiableList(internalMachines);
	public List<PhysicalMachine> runningMachines = Collections
			.unmodifiableList(internalRunningMachines);

	private ResourceConstraints totalCapacity = new ResourceConstraints(0, 0, 0);
	private ResourceConstraints runningCapacity = new ResourceConstraints(0, 0,
			0);

	private CopyOnWriteArrayList<CapacityChangeEvent> capacityListeners = new CopyOnWriteArrayList<CapacityChangeEvent>();

	private ArrayList<Repository> internalRepositories = new ArrayList<Repository>();
	public List<Repository> repositories = Collections
			.unmodifiableList(internalRepositories);

	public final Scheduler sched;
	public final PhysicalMachineController pmcontroller;

	public IaaSService(Class<? extends Scheduler> s,
			Class<? extends PhysicalMachineController> c)
			throws InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException {
		sched = s.getConstructor(getClass()).newInstance(this);
		pmcontroller = c.getConstructor(getClass()).newInstance(this);
	}

	@Override
	public void migrateVM(final VirtualMachine vm, final IaaSService target) {
	}

	private PhysicalMachine checkVMHost(final VirtualMachine vm)
			throws NoSuchVMException {
		ResourceAllocation ra = vm.getResourceAllocation();
		PhysicalMachine host;
		if (!(ra != null && runningMachines.contains(host = ra.host))) {
			throw new NoSuchVMException(
					"This VM is not run by any of the managed PMs in this IaaS service");
		}
		return host;
	}

	@Override
	public void reallocateResources(VirtualMachine vm,
			ResourceConstraints newresources) throws NoSuchVMException,
			VMManagementException {
		checkVMHost(vm).reallocateResources(vm, newresources);
	}

	public VirtualMachine[] requestVM(final VirtualAppliance va,
			final ResourceConstraints rc, final Repository vaSource,
			final int count) throws VMManagementException,
			NetworkNode.NetworkException {
		return requestVM(va, rc, vaSource, count, null);
	}

	@Override
	public VirtualMachine[] requestVM(VirtualAppliance va,
			ResourceConstraints rc, Repository vaSource, int count,
			HashMap<String, Object> schedulingConstraints)
			throws hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException,
			NetworkException {
		if (machines.isEmpty()) {
			throw new VMManagementException(
					"There are no phyisical machines that can run VMs!");
		}
		VirtualMachine[] vms = new VirtualMachine[count];
		for (int i = 0; i < count; i++) {
			vms[i] = new VirtualMachine(va);
		}
		sched.scheduleVMrequest(vms, rc, vaSource, schedulingConstraints);
		return vms;
	}

	@Override
	public void terminateVM(final VirtualMachine vm, final boolean killTasks)
			throws NoSuchVMException, VMManagementException {
		checkVMHost(vm).terminateVM(vm, killTasks);
	}

	@Override
	public Collection<VirtualMachine> listVMs() {
		final ArrayList<VirtualMachine> completeList = new ArrayList<VirtualMachine>();
		int imLen = internalMachines.size();
		for (int i = 0; i < imLen; i++) {
			completeList.addAll(internalMachines.get(i).listVMs());
		}
		return completeList;
	}

	public void registerHost(final PhysicalMachine pm) {
		internalMachines.add(pm);
		listeners.add(new MachineListener(pm));
		totalCapacity = ResourceConstraints.add(totalCapacity,
				pm.getCapacities());
		notifyCapacityListeners();
	}

	private void realDeregistration(PhysicalMachine pm) {
		for (final MachineListener sl : listeners) {
			if (sl.pm == pm) {
				listeners.remove(sl);
				pm.unsubscribeStateChangeEvents(sl);
			}
		}
		totalCapacity = ResourceConstraints.subtract(totalCapacity,
				pm.getCapacities());
		notifyCapacityListeners();
	}

	public void deregisterHost(final PhysicalMachine pm)
			throws IaaSHandlingException {
		if (ArrayHandler.removeAndReplaceWithLast(internalMachines, pm)) {
			if (pm.isHostingVMs()) {
				ResourceConstraints needed = ResourceConstraints.subtract(
						pm.getCapacities(), pm.getFreeCapacities());
				PhysicalMachine receiver = null;
				for (PhysicalMachine curr : machines) {
					if (needed.compareTo(curr.getFreeCapacities()) <= 0) {
						receiver = curr;
						break;
					}
				}
				if (receiver == null) {
					throw new IaaSHandlingException(
							"You cannot deregister the host before all its VMs are terminated, or if all VMs can be migrated to somewhere else");
				}
				try {
					pm.subscribeStateChangeEvents(new PhysicalMachine.StateChangeListener() {
						@Override
						public void stateChanged(State oldState, State newState) {
							if (newState.equals(PhysicalMachine.State.OFF)) {
								realDeregistration(pm);
							}
						}
					});
					if (receiver.isRunning()) {
						pm.switchoff(receiver);
					} else {
						final PhysicalMachine rcopy = receiver;
						receiver.subscribeStateChangeEvents(new PhysicalMachine.StateChangeListener() {
							@Override
							public void stateChanged(State oldState,
									State newState) {
								try {
									if (newState
											.equals(PhysicalMachine.State.RUNNING)) {
										pm.switchoff(rcopy);
									}
								} catch (VMManagementException e) {
									e.printStackTrace();
								} catch (NetworkNode.NetworkException e) {
									e.printStackTrace();
								}
							}
						});
						receiver.turnon();
					}
				} catch (VMManagementException e) {
					throw new IaaSHandlingException(
							"Some error has happened during the switchoff procedure",
							e);
				} catch (NetworkNode.NetworkException e) {
					throw new IaaSHandlingException(
							"All PMs should be able to connect to each other in the IaaS",
							e);
				}
			} else {
				realDeregistration(pm);
			}
		} else {
			throw new IaaSHandlingException(
					"No such registered physical machine");
		}
	}

	public void registerRepository(final Repository r) {
		internalRepositories.add(r);
	}

	public void deregisterRepository(final Repository r)
			throws IaaSHandlingException {
		ArrayHandler.removeAndReplaceWithLast(internalRepositories, r);
	}

	public ResourceConstraints getCapacities() {
		return totalCapacity;
	}

	public ResourceConstraints getRunningCapacities() {
		return runningCapacity;
	}

	@Override
	public void subscribeToCapacityChanges(final CapacityChangeEvent e) {
		capacityListeners.add(e);
	}

	@Override
	public void unsubscribeFromCapacityChanges(final CapacityChangeEvent e) {
		capacityListeners.remove(e);
	}

	private void notifyCapacityListeners() {
		int size = capacityListeners.size();
		for (int i = 0; i < size; i++) {
			capacityListeners.get(i).capacityChanged(totalCapacity);
		}
	}

	@Override
	public String toString() {
		return "IaaS(Machines(" + machines + "), Repositories(" + repositories
				+ "))";
	}
}
