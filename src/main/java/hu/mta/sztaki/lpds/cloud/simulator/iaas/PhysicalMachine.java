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
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.UnalterableConstraintsPropagator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinConsumer;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinProvider;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceSpreader;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 
 * @author 
 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 */
public class PhysicalMachine extends MaxMinProvider implements
		VMManager<PhysicalMachine, ResourceConstraints> {

	public static final int defaultAllocLen = 1000;
	public static final int migrationAllocLen = 1000000;
	public static final double smallUtilization = 0.001;

	/**
	 * Represents the possible states of the physical machines modeled in the
	 * system
	 * 
	 * @author 
	 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
	 * 
	 */
	public static enum State {
		/**
		 * The machine is completely switched off, minimal consumption is
		 * recorded.
		 */
		OFF,
		/**
		 * The machine is under preparation to serve VMs. Some consumption is
		 * recorded already.
		 */
		SWITCHINGON,
		/**
		 * The machine is currently serving VMs. The machine and its VMs are
		 * consuming energy.
		 */
		RUNNING,
		/**
		 * The machine is about to be switched off. It no longer accepts VM
		 * requests but it still consumes energy.
		 */
		SWITCHINGOFF
	};

	public static final EnumSet<State> ToOnorRunning = EnumSet.of(
			State.SWITCHINGON, State.RUNNING);
	public static final EnumSet<State> ToOfforOff = EnumSet.of(
			State.SWITCHINGOFF, State.OFF);
	public static final EnumSet<State> StatesOfHighEnergyConsumption = EnumSet
			.of(State.SWITCHINGON, State.RUNNING, State.SWITCHINGOFF);

	public interface StateChangeListener {
		void stateChanged(State oldState, State newState);
	}

	public static class ResourceAllocation extends DeferredEvent {
		public final PhysicalMachine host;
		public final ResourceConstraints allocated;
		private final ResourceConstraints realAllocated;
		private VirtualMachine user = null;
		private final int myPromisedIndex;

		private ResourceAllocation(final PhysicalMachine offerer,
				final ResourceConstraints realAlloc,
				final ResourceConstraints alloc, final int until) {
			super(until);
			host = offerer;
			allocated = alloc;
			realAllocated = realAlloc;
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
				host.promisedCapacities = new AlterableResourceConstraints(
						realAllocated);
			} else {
				host.promisedCapacities.add(realAllocated);
			}
			host.internalReallyFreeCaps.subtract(realAllocated);
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
				host.promisedCapacities = AlterableResourceConstraints
						.getNoResources();
			} else {
				host.promisedCapacities.subtract(realAllocated);
			}
			if (isUnUsed()) {
				host.internalReallyFreeCaps.add(realAllocated);
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
				host.internalAvailableCaps.subtract(realAllocated);
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
			host.internalAvailableCaps.add(realAllocated);
			host.internalReallyFreeCaps.add(realAllocated);
			host.notifyFreedUpCapacityListeners(realAllocated);
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

	public class PowerStateDelayer extends ConsumptionEventAdapter {
		private State newState;
		// The end of the list is the upcoming task
		final ArrayList<Double> tasksDue;
		public final long transitionStart;
		ResourceConsumption currentConsumption = null;

		public PowerStateDelayer(final double[] tasklist,
				final State newPowerState) {
			onOffEvent = this;
			newState = newPowerState;
			tasksDue = new ArrayList<Double>(tasklist.length);
			for (int i = tasklist.length - 1; i >= 0; i--) {
				tasksDue.add(tasklist[i]);
			}
			sendTask();
			transitionStart = Timed.getFireCount();
		}

		private void sendTask() {
			// Did we finish all the tasks for the state change?
			if (tasksDue.size() == 0) {
				// Mark the completion of the state change
				onOffEvent = null;
				setState(newState);
				return;
			}

			// No we did not, lets send some more to our direct consumer
			final double totalConsumption = tasksDue
					.remove(tasksDue.size() - 1);
			final double limit = tasksDue.remove(tasksDue.size() - 1);
			currentConsumption = new ResourceConsumption(totalConsumption,
					limit, directConsumer, PhysicalMachine.this, this);
			if (!currentConsumption.registerConsumption()) {
				throw new IllegalStateException(
						"PowerStateChange was not successful because resource consumption could not be registered");
			}
		}

		@Override
		public void conComplete() {
			sendTask();
		}

		@Override
		public void conCancelled(ResourceConsumption problematic) {
			throw new IllegalStateException(
					"Unexpected termination of one of the state changing tasks");
		}

		public void addFurtherTasks(final double[] tasklist) {
			tasksDue.ensureCapacity(tasklist.length + tasksDue.size());
			for (int i = tasklist.length - 1; i >= 0; i -= 2) {
				// Maintaining the end to front order of the due list
				tasksDue.add(0, tasklist[i - 1]);
				tasksDue.add(0, tasklist[i]);
			}
		}

		public void setNewState(State newState) {
			this.newState = newState;
		}
	}

	private final ConstantConstraints totalCapacities;
	// Available physical resources:
	private final AlterableResourceConstraints internalAvailableCaps;
	/**
	 * This field can automatically update between two checks! If you need
	 * unaltered data please make a copy.
	 */
	public final UnalterableConstraintsPropagator availableCapacities;
	private AlterableResourceConstraints promisedCapacities;
	private final AlterableResourceConstraints internalReallyFreeCaps;
	/**
	 * This field can automatically update between two checks! If you need
	 * unaltered data please make a copy.
	 */
	public final UnalterableConstraintsPropagator freeCapacities;
	public final Repository localDisk;
	final ArrayList<ResourceAllocation> promisedResources = new ArrayList<ResourceAllocation>();
	private int promisedAllocationsCount = 0;

	// Internal state management
	private State currentState = null;
	private final double[] onTransition;
	private final double[] offTransition;
	private final long onDelayEstimate;
	private final long offDelayEstimate;

	public static enum PowerStateKind {
		host, storage, network
	};

	private final EnumMap<State, PowerState> hostPowerBehavior;
	private final EnumMap<State, PowerState> storagePowerBehavior;
	private final EnumMap<State, PowerState> networkPowerBehavior;
	private CopyOnWriteArrayList<StateChangeListener> listeners = new CopyOnWriteArrayList<StateChangeListener>();

	// Managed VMs
	private final HashSet<VirtualMachine> vms = new HashSet<VirtualMachine>(); // current
	public final Set<VirtualMachine> publicVms = Collections
			.unmodifiableSet(vms);
	private long completedVMs = 0; // Past
	// The onOffEvent here is managed by the delayer itself.
	private PowerStateDelayer onOffEvent = null;
	private CopyOnWriteArrayList<CapacityChangeEvent<ResourceConstraints>> increasingFreeCapacityListeners = new CopyOnWriteArrayList<CapacityChangeEvent<ResourceConstraints>>();

	// The "hidden" - non VM - consumer (representing the VMM's actions and the
	// PM's own operations
	public final MaxMinConsumer directConsumer;
	private boolean directConsumerUsageMoratory = true;

	/**
	 * Defines a new physical machine, ensures that there are no VMs running so
	 * far
	 * 
	 * @param cores
	 *            defines the number of CPU cores this machine has under control
	 * @param perCorePocessing
	 *            defines the processing capabilities of a single CPU core in
	 *            this machine (in instructions/tick)
	 * @param memory
	 *            defines the total physical memory this machine has under
	 *            control (in bytes)
	 * @param disk
	 *            defines the local physical disk & networking this machine has
	 *            under control
	 * @param onD
	 *            defines the time delay between the machine's switch on and the
	 *            first time it can serve VM requests
	 * @param offD
	 *            defines the time delay the machine needs to shut down all of
	 *            its operations while it does not serve any more VMs
	 * @param powerTransitions
	 *            determines the applied power state transitions while the
	 *            physical machine state changes. This is the principal way to
	 *            alter a PM's energy consumption behavior.
	 */
	public PhysicalMachine(double cores, double perCorePocessing, long memory,
			Repository disk, int onD, int offD,
			EnumMap<PowerStateKind, EnumMap<State, PowerState>> powerTransitions) {
		this(cores, perCorePocessing, memory, disk, new double[] {
				onD * perCorePocessing * smallUtilization,
				perCorePocessing * smallUtilization }, new double[] {
				offD * perCorePocessing * smallUtilization,
				perCorePocessing * smallUtilization }, powerTransitions);
	}

	/**
	 * 
	 * @param on
	 *            which taskset needs to be prepared
	 * @param array
	 *            the task array
	 * @return the estimated runtime of all tasks in the array
	 */
	private long prepareTransitionalTasks(boolean on, double[] array) {
		final double[] writeHere = on ? onTransition : offTransition;
		System.arraycopy(array, 0, writeHere, 0, array.length);
		long odSum = 0;
		for (int i = 0; i < array.length; i += 2) {
			odSum += (long) (array[i] / array[i + 1]);
		}
		return odSum;
	}

	public PhysicalMachine(double cores, double perCorePocessing, long memory,
			Repository disk, double[] turnonOperations,
			double[] switchoffOperations,
			EnumMap<PowerStateKind, EnumMap<State, PowerState>> powerTransitions) {
		super(cores * perCorePocessing);
		// Init resources:
		totalCapacities = new ConstantConstraints(cores, perCorePocessing,
				memory);
		internalAvailableCaps = new AlterableResourceConstraints(
				totalCapacities);
		availableCapacities = new UnalterableConstraintsPropagator(
				internalAvailableCaps);
		internalReallyFreeCaps = new AlterableResourceConstraints(
				totalCapacities);
		freeCapacities = new UnalterableConstraintsPropagator(
				internalReallyFreeCaps);
		localDisk = disk;

		hostPowerBehavior = powerTransitions.get(PowerStateKind.host);
		storagePowerBehavior = powerTransitions.get(PowerStateKind.storage);
		networkPowerBehavior = powerTransitions.get(PowerStateKind.network);
		onTransition = new double[turnonOperations.length];
		onDelayEstimate = prepareTransitionalTasks(true, turnonOperations);
		offTransition = new double[switchoffOperations.length];
		offDelayEstimate = prepareTransitionalTasks(false, switchoffOperations);

		if (hostPowerBehavior == null || storagePowerBehavior == null
				|| networkPowerBehavior == null) {
			throw new IllegalStateException(
					"Cannot initialize physical machine without a complete power behavior set");
		}

		setState(State.OFF);
		directConsumer = new MaxMinConsumer(getPerTickProcessingPower());
	}

	/**
	 * Starts the turn off procedure for the physical machine so it no longer
	 * accepts VM requests but it does not consume anymore
	 * 
	 * @param migrateHere
	 *            the physical machine where the currently hosted VMs of this VM
	 *            should go before the actual switch off operation will happen
	 * @return <ul>
	 *         <li>true if the switch off procedure has started
	 *         <li>false if there are still VMs running and migration target was
	 *         not specified thus the switch off is not possible
	 *         </ul>
	 * @throws NetworkException
	 * @throws VMManager.VMManagementException
	 */
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
						// There is a problem but we cannot do here anything
						// right now...
					default:
					}
					if (counter == vmarr.length) {
						for (int i = 0; i < vmarr.length; i++) {
							vmarr[i].unsubscribeStateChange(this);
						}
						actualSwitchOff();
					}
				}
			}
			MultiMigrate mm = new MultiMigrate();
			for (int i = 0; i < vmarr.length; i++) {
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
		switch (currentState) {
		case SWITCHINGON:
			setState(State.SWITCHINGOFF);
			onOffEvent.addFurtherTasks(offTransition);
			onOffEvent.setNewState(State.OFF);
			break;
		case RUNNING:
			setState(State.SWITCHINGOFF);
			new Timed() {
				@Override
				public void tick(final long fires) {
					ResourceSpreader.FreqSyncer syncer = getSyncer();
					// Ensures that the switching off activities are only
					// started once all runtime activities complete for the
					// directConsumer
					if (syncer != null
							&& syncer.isSubscribed()
							&& (underProcessing.size() + toBeAdded.size()
									- toBeRemoved.size() > 0)) {
						updateFrequency(syncer.getNextEvent() - fires + 1);
					} else {
						unsubscribe();
						new PowerStateDelayer(offTransition, State.OFF);
					}
				}
			}.tick(Timed.getFireCount());
			break;
		case OFF:
		case SWITCHINGOFF:
			// Nothing to do
			System.err
					.println("WARNING: an already off PM was tasked to switch off!");
		}
	}

	/**
	 * Determines if the machine can be used for VM instantiation.
	 * 
	 * @return <ul>
	 *         <li>true if the machine is ready to accept VM requests
	 *         <li>false otherwise
	 *         </ul>
	 */
	public boolean isRunning() {
		return currentState.equals(State.RUNNING);
	}

	public State getState() {
		return currentState;
	}

	/**
	 * Turns on the physical machine so it allows energy and resource
	 * consumption and opens the possibility to receive VM requests.
	 */
	public void turnon() {
		switch (currentState) {
		case SWITCHINGOFF:
		case OFF:
			if (onOffEvent == null) {
				new PowerStateDelayer(onTransition, State.RUNNING);
			} else {
				onOffEvent.addFurtherTasks(onTransition);
				onOffEvent.setNewState(State.RUNNING);
			}

			setState(State.SWITCHINGON);
			break;
		case RUNNING:
		case SWITCHINGON:
			// Nothing to do
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

	/**
	 * Ensures the requested amount of resources are going to be available in
	 * the foreseeable future on this physical machine.
	 * 
	 * @param requested
	 *            The amount of resources needed by the caller
	 * @return With a time limited offer on the requested resources. If the
	 *         requested resourceconstraints cannot be met by the function then
	 *         it returns with the maximum amount of resources it can serve. If
	 *         there are no available resources it returns with <i>null</i>! If
	 *         the requested resources are available then the original requested
	 *         resourceconstraints object is stored in the returned resource
	 *         allocation. If the resourceconstraints only specified a minimum
	 *         resource limit, then a new resourceconstraints object is returned
	 *         with details of the maximum possible resource constraints that
	 *         can fit into the machine.
	 */
	public ResourceAllocation allocateResources(
			final ResourceConstraints requested, final boolean strict,
			final int allocationValidityLength) throws VMManagementException {
		if (!currentState.equals(State.RUNNING)) {
			throw new VMManagementException(
					"The PM is not running and thus cannot offer resources yet");
		}
		// Basic tests for resource availability for the host
		if (internalReallyFreeCaps.getRequiredCPUs() == 0
				|| internalReallyFreeCaps.getRequiredMemory() == 0
				|| requested.getRequiredProcessingPower() > internalReallyFreeCaps
						.getRequiredProcessingPower()) {
			return null;
		}
		// Allocation type test (i.e. do we allow underprovisioning?)
		final int prLen = promisedResources.size();
		for (int i = 0; i < prLen; i++) {
			ResourceAllocation olderAllocation = promisedResources.get(i);
			if (olderAllocation != null) {
				if (olderAllocation.allocated.isRequiredProcessingIsMinimum() == requested
						.isRequiredProcessingIsMinimum()) {
					break;
				} else {
					return null;
				}
			}
		}
		// Promised resources for the virtual machine
		double vmCPU = requested.getRequiredCPUs();
		long vmMem = requested.getRequiredMemory();
		final double vmPrPow = requested.isRequiredProcessingIsMinimum() ? totalCapacities
				.getRequiredProcessingPower() : requested
				.getRequiredProcessingPower();

		// Actually allocated resources (memory is equivalent in both cases)
		final double allocPrPow = totalCapacities.getRequiredProcessingPower();
		final double allocCPU = vmCPU * requested.getRequiredProcessingPower()
				/ allocPrPow;
		if (0 <= internalReallyFreeCaps.getRequiredCPUs() - allocCPU) {
			if (0 <= internalReallyFreeCaps.getRequiredMemory()
					- requested.getRequiredMemory()) {
				return new ResourceAllocation(
						this,
						new ConstantConstraints(allocCPU, allocPrPow, vmMem),
						requested.isRequiredProcessingIsMinimum() ? new ConstantConstraints(
								vmCPU, vmPrPow, true, vmMem) : requested,
						allocationValidityLength);
			} else {
				vmMem = internalReallyFreeCaps.getRequiredMemory();
			}
		} else if (0 <= internalReallyFreeCaps.getRequiredMemory()
				- requested.getRequiredMemory()) {
			vmCPU = internalReallyFreeCaps.getRequiredCPUs();
		} else {
			vmCPU = internalReallyFreeCaps.getRequiredCPUs();
			vmMem = internalReallyFreeCaps.getRequiredMemory();
		}
		if (strict) {
			return null;
		} else {
			final ResourceConstraints updatedConstraints = new ConstantConstraints(vmCPU, vmPrPow,
							requested.isRequiredProcessingIsMinimum(), vmMem);
			return new ResourceAllocation(this, updatedConstraints,
					updatedConstraints, allocationValidityLength);
		}
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

	/**
	 * Initiates a VM on this physical machine. If the physical machine cannot
	 * host VMs for some reason an exception is thrown, if the machine cannot
	 * host this particular VA then a null VM is returned.
	 * 
	 * @param va
	 *            The appliance for the VM to be created.
	 * @param rc
	 *            The resource requirements of the VM
	 * @param vaSource
	 *            The storage where the VA resides.
	 * @param count
	 *            The number of VMs to be created with the above specification
	 * @return The virtual machine(s) that will be instantiated on the PM. Null
	 *         if the constraints specify VMs that cannot fit the available
	 *         resources of the machine.
	 * 
	 * @throws VMManagementException
	 *             If the machine is not accepting requests currently.<br>
	 *             If the VM startup has failed.
	 */
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

	/**
	 * Scheduling constraints are ignored currently! As this is too low level to
	 * handle them in the current state of the simulator.
	 */
	@Override
	public VirtualMachine[] requestVM(VirtualAppliance va,
			ResourceConstraints rc, Repository vaSource, int count,
			HashMap<String, Object> schedulingConstraints)
			throws VMManagementException, NetworkException {
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
	protected boolean isAcceptableConsumption(final ResourceConsumption con) {
		final ResourceSpreader consumer = con.getConsumer();
		final boolean internalConsumer = (consumer == directConsumer)
				&& (directConsumerUsageMoratory ? (onOffEvent == null ? false
						: onOffEvent.currentConsumption == con) : true);
		final boolean runningVirtualMachine;
		if (internalConsumer) {
			runningVirtualMachine = false;
		} else {
			if (consumer instanceof VirtualMachine) {
				runningVirtualMachine = vms.contains((VirtualMachine) consumer);
			} else {
				runningVirtualMachine = false;
			}
		}
		return internalConsumer || runningVirtualMachine ? super
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
				return onDelayEstimate;
			case RUNNING:
				return offDelayEstimate;
			default:
				throw new IllegalStateException(
						"The onOffEvent is null while doing switchon/off");
			}
		} else {
			long remainingTime = onOffEvent.transitionStart
					- Timed.getFireCount();
			switch (currentState) {
			case SWITCHINGOFF:
				remainingTime += offDelayEstimate;
				break;
			case SWITCHINGON:
				remainingTime += onDelayEstimate;
				break;
			default:
				throw new IllegalStateException(
						"The onOffEvent is not null while not in switchon/off mode");
			}
			return remainingTime;

		}
	}

	@Override
	public String toString() {
		return "Machine(S:" + currentState + " C:"
				+ internalReallyFreeCaps.getRequiredCPUs() + " M:"
				+ internalReallyFreeCaps.getRequiredMemory() + " "
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
		directConsumerUsageMoratory = newState != State.RUNNING;
		final int size = listeners.size();
		for (int i = 0; i < size; i++) {
			listeners.get(i).stateChanged(oldstate, newState);
		}

		// Power state management:
		setCurrentPowerBehavior(hostPowerBehavior.get(newState));
		// TODO: if the repository class also implements proper power state
		// management then this must move there
		localDisk.diskinbws.setCurrentPowerBehavior(storagePowerBehavior
				.get(newState));
		localDisk.diskoutbws.setCurrentPowerBehavior(storagePowerBehavior
				.get(newState));
		localDisk.inbws.setCurrentPowerBehavior(networkPowerBehavior
				.get(newState));
		localDisk.outbws.setCurrentPowerBehavior(networkPowerBehavior
				.get(newState));
	}

	public ResourceConstraints getCapacities() {
		return totalCapacities;
	}

	@Override
	public void subscribeToCapacityChanges(
			final CapacityChangeEvent<ResourceConstraints> e) {
		// FIXME: not important yet
	}

	@Override
	public void unsubscribeFromCapacityChanges(
			final CapacityChangeEvent<ResourceConstraints> e) {
		// FIXME: not important yet
	}

	public void subscribeToIncreasingFreeapacityChanges(
			final CapacityChangeEvent<ResourceConstraints> e) {
		increasingFreeCapacityListeners.add(e);
	}

	public void unsubscribeFromIncreasingFreeCapacityChanges(
			final CapacityChangeEvent<ResourceConstraints> e) {
		increasingFreeCapacityListeners.remove(e);
	}

	private void notifyFreedUpCapacityListeners(
			final ResourceConstraints freedUpResources) {
		final int size = increasingFreeCapacityListeners.size();
		final List<ResourceConstraints> freed = Collections
				.singletonList(freedUpResources);
		for (int i = 0; i < size; i++) {
			increasingFreeCapacityListeners.get(i).capacityChanged(
					freeCapacities, freed);
		}
	}

	public boolean isDirectConsumerUsageMoratory() {
		return directConsumerUsageMoratory;
	}
}
