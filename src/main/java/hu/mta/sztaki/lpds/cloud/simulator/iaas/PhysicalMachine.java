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

import hu.mta.sztaki.lpds.cloud.simulator.DeferredEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinProvider;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class PhysicalMachine extends MaxMinProvider implements
		VMManager<PhysicalMachine> {

	public static final int defaultAllocLen = 1000;
	public static final int migrationAllocLen = 1000000;

	public static enum State {
		OFF,
		SWITCHINGON,
		RUNNING,
		SWITCHINGOFF
	};

	public static final EnumSet<State> ToOnorRunning = EnumSet.of(
			State.SWITCHINGON, State.RUNNING);
	public static final EnumSet<State> ToOfforOff = EnumSet.of(
			State.SWITCHINGOFF, State.OFF);

	public interface StateChangeListener {
		void stateChanged(State oldState, State newState);
	}

	public static class ResourceAllocation extends DeferredEvent {
		public final PhysicalMachine host;
		public final ResourceConstraints allocated;
		private VirtualMachine user = null;
		private final int myPromisedIndex;

		private ResourceAllocation(final PhysicalMachine offerer,
				final ResourceConstraints alloc, final int until) {
			super(until);
			host = offerer;
			allocated = alloc;
			int prLen = host.promisedResources.size();
			int i = 0;
			for (i = 0; i < prLen && host.promisedResources.get(i) != null; i++)
				;
			if (i == prLen) {
				host.promisedResources.add(this);
				myPromisedIndex = prLen;
			} else {
				host.promisedResources.set(i, this);
				myPromisedIndex = i;
			}
			if (host.promisedAllocationsCount == 0) {
				host.promisedCapacities = allocated;
			} else {
				host.promisedCapacities = ResourceConstraints.add(
						host.promisedCapacities, allocated);
			}
			host.reallyFreeCapacities = ResourceConstraints.subtract(
					host.reallyFreeCapacities, allocated);
			host.promisedAllocationsCount++;
		}

		@Override
		protected void eventAction() {
			if (!isCancelled())
				System.err.println("Warning! Expiring resource allocation.");
			host.promisedResources.set(myPromisedIndex, null);
			host.promisedAllocationsCount--;
			if (host.promisedAllocationsCount == 0) {
				host.promisedResources.clear();
				host.promisedCapacities = ResourceConstraints.noResources;
			} else {
				host.promisedCapacities = ResourceConstraints.subtract(
						host.promisedCapacities, allocated);
			}
			if (isUnUsed()) {
				host.reallyFreeCapacities = ResourceConstraints.add(
						host.reallyFreeCapacities, allocated);
			}
		}

		@Override
		public void cancel() {
			super.cancel();
			eventAction();
		}

		void use(final VirtualMachine vm) throws VMManagementException {
			if (isCancelled()) {
				throw new VMManagementException(
						"Tried to use an already expired allocation");
			}
			if (user == null) {
				user = vm;
				host.availableCapacities = ResourceConstraints.subtract(
						host.availableCapacities, allocated);
				host.vms.add(vm);
				vm.subscribeStateChange(new VirtualMachine.StateChange() {
					@Override
					public void stateChanged(VirtualMachine.State oldState,
							VirtualMachine.State newState) {
						if (oldState.equals(VirtualMachine.State.RUNNING)) {
							host.vms.remove(vm);
						}
					}
				});
				cancel();
			} else {
				throw new VMManagementException(
						"Tried to use a resource allocation more than once!");
			}
		}

		void release() {
			host.vms.remove(user);
			host.completedVMs++;
			host.availableCapacities = ResourceConstraints.add(
					host.availableCapacities, allocated);
			host.reallyFreeCapacities = ResourceConstraints.add(
					host.reallyFreeCapacities, allocated);
			host.notifyFreedUpCapacityListeners();
			user = null;
		}

		public boolean isUnUsed() {
			return user == null;
		}

		public boolean isAvailable() {
			return isUnUsed() && !isCancelled();
		}

		@Override
		public String toString() {
			return "RA(Canc:" + isCancelled() + " " + allocated + ")";
		}
	}

	public class PowerStateDelayer extends DeferredEvent {
		final State newState;

		public PowerStateDelayer(final int delay, final State newPowerState) {
			super(delay);
			newState = newPowerState;
		}

		@Override
		protected void eventAction() {
			onOffEvent = null;
			setState(newState);
		}
	}

	private final ResourceConstraints totalCapacities;
	private ResourceConstraints availableCapacities;
	private ResourceConstraints promisedCapacities;
	private ResourceConstraints reallyFreeCapacities;
	public final Repository localDisk;
	final ArrayList<ResourceAllocation> promisedResources = new ArrayList<ResourceAllocation>();
	private int promisedAllocationsCount = 0;

	public final int onDelay;
	public final int offDelay;
	private State currentState = State.OFF;
	private CopyOnWriteArrayList<StateChangeListener> listeners = new CopyOnWriteArrayList<StateChangeListener>();

	private final HashSet<VirtualMachine> vms = new HashSet<VirtualMachine>();
	public final Set<VirtualMachine> publicVms = Collections
			.unmodifiableSet(vms);
	private long completedVMs = 0;
	private DeferredEvent onOffEvent = null;
	private CopyOnWriteArrayList<CapacityChangeEvent> increasingFreeCapacityListeners = new CopyOnWriteArrayList<CapacityChangeEvent>();

	public PhysicalMachine(double cores, double perCorePocessing, long memory,
			Repository disk, int onD, int offD) {
		super(cores * perCorePocessing);
		totalCapacities = new ResourceConstraints(cores, perCorePocessing,
				memory);
		availableCapacities = totalCapacities;
		reallyFreeCapacities = totalCapacities;
		localDisk = disk;

		onDelay = onD;
		offDelay = offD;
	}

	public boolean switchoff(final PhysicalMachine migrateHere)
			throws VMManagementException, NetworkException {
		if (migrateHere != null) {
			final VirtualMachine[] vmarr = vms.toArray(new VirtualMachine[vms
					.size()]);
			class MultiMigrate implements VirtualMachine.StateChange {
				private int counter = 0;

				@Override
				public void stateChanged(VirtualMachine.State oldState,
						VirtualMachine.State newState) {
					switch (newState) {
					case RUNNING:
						counter++;
						break;
					case SUSPENDED_MIG:
					default:
					}
					if (counter == vmarr.length) {
						for (int i = 0; i < vmarr.length;i++) {
							vmarr[i].unsubscribeStateChange(this);
						}
						actualSwitchOff();
					}
				}
			}
			MultiMigrate mm = new MultiMigrate();
			for (int i = 0; i < vmarr.length;i++) {
				vmarr[i].subscribeStateChange(mm);
				migrateVM(vmarr[i], migrateHere);
			}
		} else {
			if (vms.size() + promisedAllocationsCount > 0) {
				return false;
			}
			actualSwitchOff();
		}
		return true;
	}

	private void actualSwitchOff() {
		int extratime = 0;
		switch (currentState) {
		case SWITCHINGON:
			extratime = (int) onOffEvent.nextEventDistance();
			onOffEvent.cancel();
		case RUNNING:
			onOffEvent = new PowerStateDelayer(extratime + offDelay, State.OFF);
			setState(State.SWITCHINGOFF);
			break;
		case OFF:
		case SWITCHINGOFF:
			System.err
					.println("WARNING: an already off PM was tasked to switch off!");
		}
	}

	public boolean isRunning() {
		return currentState.equals(State.RUNNING);
	}

	public State getState() {
		return currentState;
	}

	public void turnon() {
		int extratime = 0;
		switch (currentState) {
		case SWITCHINGOFF:
			extratime = (int) onOffEvent.nextEventDistance();
			onOffEvent.cancel();
		case OFF:
			onOffEvent = new PowerStateDelayer(extratime + onDelay,
					State.RUNNING);
			setState(State.SWITCHINGON);
			break;
		case RUNNING:
		case SWITCHINGON:
			System.err
					.println("WARNING: an already running PM was tasked to switch on!");
		}
	}

	@Override
	public void migrateVM(final VirtualMachine vm, final PhysicalMachine target)
			throws VMManagementException, NetworkNode.NetworkException {
		if (vms.contains(vm)) {
			vm.migrate(target.allocateResources(
					vm.getResourceAllocation().allocated, true,
					migrationAllocLen));
		}

	}

	@Override
	public void reallocateResources(final VirtualMachine vm,
			final ResourceConstraints newresources) {

	}

	public ResourceAllocation allocateResources(
			final ResourceConstraints requested, final boolean strict,
			final int allocationValidityLength) throws VMManagementException {
		if (!currentState.equals(State.RUNNING)) {
			throw new VMManagementException(
					"The PM is not running and thus cannot offer resources yet");
		}
		if (reallyFreeCapacities.requiredCPUs == 0
				|| reallyFreeCapacities.requiredMemory == 0
				|| requested.requiredProcessingPower > reallyFreeCapacities.requiredProcessingPower) {
			return null;
		}
		double reqCPU = requested.requiredCPUs;
		long reqMem = requested.requiredMemory;
		if (reallyFreeCapacities.requiredCPUs >= requested.requiredCPUs) {
			if (reallyFreeCapacities.requiredMemory >= requested.requiredMemory) {
				return new ResourceAllocation(this, requested,
						allocationValidityLength);
			} else {
				reqMem = reallyFreeCapacities.requiredMemory;
			}
		} else if (reallyFreeCapacities.requiredMemory >= requested.requiredMemory) {
			reqCPU = reallyFreeCapacities.requiredCPUs;
		} else {
			reqCPU = reallyFreeCapacities.requiredCPUs;
			reqMem = reallyFreeCapacities.requiredMemory;
		}
		return strict ? null : new ResourceAllocation(this,
				new ResourceConstraints(reqCPU,
						requested.requiredProcessingPower, reqMem),
				allocationValidityLength);
	}

	private boolean checkAllocationsPresence(final ResourceAllocation allocation) {
		return promisedResources.size() > allocation.myPromisedIndex
				&& promisedResources.get(allocation.myPromisedIndex) == allocation;
	}

	public boolean cancelAllocation(final ResourceAllocation allocation) {
		if (checkAllocationsPresence(allocation)) {
			allocation.cancel();
			return true;
		}
		return false;
	}

	public boolean isHostableRequest(final ResourceConstraints requested) {
		return requested.compareTo(totalCapacities) <= 0;
	}

	public void deployVM(final VirtualMachine vm, final ResourceAllocation ra,
			final Repository vaSource) throws VMManagementException,
			NetworkNode.NetworkException {
		if (checkAllocationsPresence(ra)) {
			final VirtualAppliance va = vm.getVa();
			final StorageObject foundLocal = localDisk.lookup(va.id);
			final StorageObject foundRemote = vaSource == null ? null
					: vaSource.lookup(va.id);
			if (foundLocal != null || foundRemote != null) {
				vm.switchOn(ra, vaSource);
			} else {
				throw new VMManagementException("No VA available!");
			}
		} else {
			throw new VMManagementException(
					"Tried to deploy VM with an expired resource allocation");
		}
	}

	public VirtualMachine[] requestVM(final VirtualAppliance va,
			final ResourceConstraints rc, final Repository vaSource,
			final int count) throws VMManagementException, NetworkException {
		final VirtualMachine[] vms = new VirtualMachine[count];
		final ResourceAllocation[] ras = new ResourceAllocation[count];
		boolean canDeployAll = true;
		for (int i = 0; i < count && canDeployAll; i++) {
			ras[i] = allocateResources(rc, true, defaultAllocLen);
			vms[i] = null;
			canDeployAll &= ras[i] != null && ras[i].allocated == rc;
		}
		if (canDeployAll) {
			for (int i = 0; i < count; i++) {
				vms[i] = new VirtualMachine(va);
				deployVM(vms[i], ras[i], vaSource);
			}
		} else {
			for (int i = 0; i < count && ras[i] != null; i++) {
				ras[i].cancel();
			}
		}
		return vms;
	}

	@Override
	public VirtualMachine[] requestVM(VirtualAppliance va,
			ResourceConstraints rc, Repository vaSource, int count,
			HashMap<String, Object> schedulingConstraints)
			throws hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException,
			NetworkException {
		return requestVM(va, rc, vaSource, count);
	}

	@Override
	public void terminateVM(final VirtualMachine vm, final boolean killTasks)
			throws NoSuchVMException, VMManagementException {
		if (!vms.contains(vm)) {
			throw new NoSuchVMException(
					"Termination request was received for an unknown VM");

		}
		vm.switchoff(killTasks);
	}

	@Override
	public Collection<VirtualMachine> listVMs() {
		return publicVms;
	}

	public int numofCurrentVMs() {
		return vms.size();
	}

	@Override
	protected boolean isAcceptableConsumption(ResourceConsumption con) {
		return vms.contains((VirtualMachine) con.getConsumer()) ? super
				.isAcceptableConsumption(con) : false;
	}

	public boolean isHostingVMs() {
		return !vms.isEmpty();
	}

	public long getCompletedVMs() {
		return completedVMs;
	}

	public long getCurrentOnOffDelay() {
		if (onOffEvent == null) {
			switch (currentState) {
			case OFF:
				return onDelay;
			case RUNNING:
				return offDelay;
			default:
				throw new IllegalStateException(
						"The onOffEvent is null while doing switchon/off");
			}
		} else {
			switch (currentState) {
			case SWITCHINGOFF:
			case SWITCHINGON:
				if (onOffEvent.isSubscribed()) {
					return onOffEvent.nextEventDistance();
				}
			default:
				throw new IllegalStateException(
						"The onOffEvent is not null while not in switchon/off mode");
			}

		}
	}

	@Override
	public String toString() {
		return "Machine(S:" + currentState + " C:"
				+ reallyFreeCapacities.requiredCPUs + " M:"
				+ reallyFreeCapacities.requiredMemory + " "
				+ localDisk.toString() + " " + super.toString() + ")";
	}

	public void subscribeStateChangeEvents(final StateChangeListener sl) {
		listeners.add(sl);
	}

	public void unsubscribeStateChangeEvents(final StateChangeListener sl) {
		listeners.remove(sl);
	}

	private void setState(final State newState) {
		final State oldstate = currentState;
		currentState = newState;
		final int size = listeners.size();
		for (int i = 0; i < size; i++) {
			listeners.get(i).stateChanged(oldstate, newState);
		}
	}

	public ResourceConstraints getCapacities() {
		return totalCapacities;
	}

	public ResourceConstraints getFreeCapacities() {
		return reallyFreeCapacities;
	}

	public ResourceConstraints getAvailableCapacities() {
		return availableCapacities;
	}

	@Override
	public void subscribeToCapacityChanges(final CapacityChangeEvent e) {
	}

	@Override
	public void unsubscribeFromCapacityChanges(final CapacityChangeEvent e) {
	}

	public void subscribeToIncreasingFreeapacityChanges(
			final CapacityChangeEvent e) {
		increasingFreeCapacityListeners.add(e);
	}

	public void unsubscribeFromIncreasingFreeCapacityChanges(
			final CapacityChangeEvent e) {
		increasingFreeCapacityListeners.remove(e);
	}

	private void notifyFreedUpCapacityListeners() {
		final int size = increasingFreeCapacityListeners.size();
		for (int i = 0; i < size; i++) {
			increasingFreeCapacityListeners.get(i).capacityChanged(
					reallyFreeCapacities);
		}
	}
}
