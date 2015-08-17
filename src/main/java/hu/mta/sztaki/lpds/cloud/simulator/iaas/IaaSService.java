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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.UnalterableConstraintsPropagator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.PhysicalMachineController;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.SingleNotificationHandler;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.StateDependentEventHandler;
import hu.mta.sztaki.lpds.cloud.simulator.util.ArrayHandler;

/**
 * This class represents a single IaaS service. It's tasks are the maintenance
 * and management of the physical machines and the scheduling of the VM requests
 * among the PMs.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 * 
 */
public class IaaSService implements VMManager<IaaSService, PhysicalMachine>, PhysicalMachine.StateChangeListener {

	/**
	 * This class represents a generic error that occurred during the operation
	 * of the IaaS service.
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
	 * 
	 */
	public static class IaaSHandlingException extends Exception {

		private static final long serialVersionUID = 2580735547805541590L;

		/**
		 * Only a generic constructor is defined so a textual message can be
		 * propagated around the system
		 * 
		 * @param s
		 *            the reason this exception was raised
		 */
		public IaaSHandlingException(final String s) {
			super(s);
		}

		public IaaSHandlingException(final String s, final Throwable ex) {
			super(s, ex);
		}
	}

	/**
	 * The order of internal machines is not guaranteed
	 */
	private final ArrayList<PhysicalMachine> internalMachines = new ArrayList<PhysicalMachine>();
	private final ArrayList<PhysicalMachine> internalRunningMachines = new ArrayList<PhysicalMachine>();

	public final List<PhysicalMachine> machines = Collections.unmodifiableList(internalMachines);
	public final List<PhysicalMachine> runningMachines = Collections.unmodifiableList(internalRunningMachines);

	private AlterableResourceConstraints totalCapacity = AlterableResourceConstraints.getNoResources();
	private ResourceConstraints publicTCap = new UnalterableConstraintsPropagator(totalCapacity);
	private AlterableResourceConstraints runningCapacity = AlterableResourceConstraints.getNoResources();
	private ResourceConstraints publicRCap = new UnalterableConstraintsPropagator(runningCapacity);

	private final StateDependentEventHandler<CapacityChangeEvent<PhysicalMachine>, List<PhysicalMachine>> capacityListenerManager = new StateDependentEventHandler<CapacityChangeEvent<PhysicalMachine>, List<PhysicalMachine>>(
			new SingleNotificationHandler<CapacityChangeEvent<PhysicalMachine>, List<PhysicalMachine>>() {
				@Override
				public void sendNotification(CapacityChangeEvent<PhysicalMachine> onObject,
						List<PhysicalMachine> pmsetchange) {
					onObject.capacityChanged(totalCapacity, pmsetchange);
				}
			});

	/**
	 * The order of internal repositories is not guaranteed
	 */
	private final ArrayList<Repository> internalRepositories = new ArrayList<Repository>();
	public final List<Repository> repositories = Collections.unmodifiableList(internalRepositories);

	public final Scheduler sched;
	public final PhysicalMachineController pmcontroller;

	public IaaSService(Class<? extends Scheduler> s, Class<? extends PhysicalMachineController> c)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException {
		sched = s.getConstructor(getClass()).newInstance(this);
		pmcontroller = c.getConstructor(getClass()).newInstance(this);
	}

	@Override
	public void migrateVM(final VirtualMachine vm, final IaaSService target) {
	}

	private PhysicalMachine checkVMHost(final VirtualMachine vm) throws NoSuchVMException {
		ResourceAllocation ra = vm.getResourceAllocation();
		PhysicalMachine host;
		if (!(ra != null && runningMachines.contains(host = ra.getHost()))) {
			throw new NoSuchVMException("This VM is not run by any of the managed PMs in this IaaS service");
		}
		return host;
	}

	@Override
	public void reallocateResources(VirtualMachine vm, ResourceConstraints newresources)
			throws NoSuchVMException, VMManagementException {
		checkVMHost(vm).reallocateResources(vm, newresources);
	}

	public VirtualMachine[] requestVM(final VirtualAppliance va, final ResourceConstraints rc,
			final Repository vaSource, final int count) throws VMManagementException, NetworkNode.NetworkException {
		return requestVM(va, rc, vaSource, count, null);
	}

	@Override
	public VirtualMachine[] requestVM(VirtualAppliance va, ResourceConstraints rc, Repository vaSource, int count,
			HashMap<String, Object> schedulingConstraints)
					throws hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException, NetworkException {
		if (machines.isEmpty()) {
			throw new VMManagementException("There are no phyisical machines that can run VMs!");
		}
		VirtualMachine[] vms = new VirtualMachine[count];
		for (int i = 0; i < count; i++) {
			vms[i] = new VirtualMachine(va);
		}
		sched.scheduleVMrequest(vms, rc, vaSource, schedulingConstraints);
		return vms;
	}

	/**
	 * Requesting the destruction of a VM in a DESTROYED state will dequeue the
	 * VM from the scheduler's request queue. If the VM was not requested from
	 * this IaaSService then a nosuchvmexception is thrown.
	 */
	@Override
	public void terminateVM(final VirtualMachine vm, final boolean killTasks)
			throws NoSuchVMException, VMManagementException {
		if (VirtualMachine.State.DESTROYED.equals(vm.getState())) {
			// The VM is still under scheduling, the queue needs to be cleared
			if (!sched.dropVMrequest(vm)) {
				throw new NoSuchVMException("This VM is not queued in this IaaS service");
			}
			return;
		}
		checkVMHost(vm).terminateVM(vm, killTasks);
	}

	@Override
	public Collection<VirtualMachine> listVMs() {
		final ArrayList<VirtualMachine> completeList = new ArrayList<VirtualMachine>();
		int imLen = internalMachines.size();
		for (int i = 0; i < imLen; i++) {
			completeList.addAll(internalMachines.get(i).listVMs());
		}
		completeList.addAll(sched.getQueuedVMs());
		return completeList;
	}

	/**
	 * This function allows the IaaS to grow in size
	 * 
	 * @param pm
	 *            the new physical machine to be utilized within the system
	 */
	public void registerHost(final PhysicalMachine pm) {
		bulkHostRegistration(Collections.singletonList(pm));
	}

	public void bulkHostRegistration(final List<PhysicalMachine> newPMs) {
		internalMachines.addAll(newPMs);
		final int size = newPMs.size();
		final ResourceConstraints[] caps = new ResourceConstraints[size];
		for (int i = 0; i < size; i++) {
			final PhysicalMachine pm = newPMs.get(i);
			pm.subscribeStateChangeEvents(this);
			caps[i] = pm.getCapacities();
		}
		totalCapacity.add(caps);
		capacityListenerManager.notifyListeners(newPMs);
	}

	private void realDeregistration(PhysicalMachine pm) {
		pm.unsubscribeStateChangeEvents(this);
		totalCapacity.subtract(pm.getCapacities());
		capacityListenerManager.notifyListeners(Collections.singletonList(pm));
	}

	/**
	 * This function allows the IaaS to reduce in size. <br/>
	 * This function might migrate VMs from the deregistered host to ones
	 * remaining in the system. If the deregistered host contains VMs that
	 * cannot be migrated, or there is nowhere to migrate the VMs then the
	 * function throws an exception.
	 * 
	 * Currently there is no migration implemented!
	 * 
	 * @param pm
	 *            the physical machine to be dropped from the control of the
	 *            system
	 */
	public void deregisterHost(final PhysicalMachine pm) throws IaaSHandlingException {
		if (ArrayHandler.removeAndReplaceWithLast(internalMachines, pm)) {
			if (pm.isHostingVMs()) {
				AlterableResourceConstraints needed = new AlterableResourceConstraints(pm.getCapacities());
				needed.subtract(pm.freeCapacities);
				PhysicalMachine receiver = null;
				for (PhysicalMachine curr : machines) {
					if (needed.compareTo(curr.freeCapacities) <= 0) {
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
						public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
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
							public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
								try {
									if (newState.equals(PhysicalMachine.State.RUNNING)) {
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
					throw new IaaSHandlingException("Some error has happened during the switchoff procedure", e);
				} catch (NetworkNode.NetworkException e) {
					throw new IaaSHandlingException("All PMs should be able to connect to each other in the IaaS", e);
				}
			} else {
				realDeregistration(pm);
			}
		} else {
			throw new IaaSHandlingException("No such registered physical machine");
		}
	}

	/**
	 * This function allows the IaaS to grow its storage capacities
	 * 
	 * @param r
	 *            the new repository to be utilized within the system
	 */
	public void registerRepository(final Repository r) {
		internalRepositories.add(r);
	}

	/**
	 * This function allows the IaaS to reduce its storage capacities.
	 * 
	 * This function might transfer contents from the deregistered repository to
	 * ones remaining in the system. If the deregistered repository contains
	 * storage objects that cannot be transferred, or there is nowhere to
	 * transfer a few storage objects then the function throws an exception.
	 * 
	 * Currently there is no transfer implemented!
	 * 
	 * @param pm
	 *            the physical machine to be dropped from the control of the
	 *            system
	 */
	public void deregisterRepository(final Repository r) throws IaaSHandlingException {
		ArrayHandler.removeAndReplaceWithLast(internalRepositories, r);
	}

	public ResourceConstraints getCapacities() {
		return publicTCap;
	}

	public ResourceConstraints getRunningCapacities() {
		return publicRCap;
	}

	@Override
	public void subscribeToCapacityChanges(final CapacityChangeEvent<PhysicalMachine> e) {
		capacityListenerManager.subscribeToEvents(e);
	}

	@Override
	public void unsubscribeFromCapacityChanges(final CapacityChangeEvent<PhysicalMachine> e) {
		capacityListenerManager.unsubscribeFromEvents(e);
	}

	public boolean isRegisteredHost(PhysicalMachine pm) {
		return internalMachines.contains(pm);
	}

	@Override
	public String toString() {
		return "IaaS(Machines(" + machines + "), Repositories(" + repositories + "))";
	}

	@Override
	public void stateChanged(PhysicalMachine pm, State oldState, State newState) {
		switch (newState) {
		case RUNNING:
			internalRunningMachines.add(pm);
			runningCapacity.singleAdd(pm.getCapacities());
			return;
		default:
			if (oldState.equals(PhysicalMachine.State.RUNNING)) {
				internalRunningMachines.remove(pm);
				runningCapacity.subtract(pm.getCapacities());
			}
		}
	}
}
