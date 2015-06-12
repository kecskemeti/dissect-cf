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

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.MaxMinConsumer;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 
 * This class represents a single virtual machine in the system. It simulates
 * its behavior. Currently it supports three approaches for VM image storage:
 * <ol>
 * <li>Local: The VA is expected to be in the PM's repository (caching), the
 * class ensures its cloning and operates on the clone for its entire lifetime.
 * During suspend the memory state is also stored in the PM's repository.
 * <li>Mixed: The VA is expected in a remote repository (e.g. it could reside in
 * the central service in an IaaS). The class creates a local copy of this VA
 * (in the PM's repository) so it can use it during the VM's lifetime. During
 * suspend the memory state is also stored in the PM's repository.
 * <li>Remote: The VA is expected in a remote repository where it is cloned. The
 * VM operates on the remote clone for its lifetime. During suspend the memory
 * state is also stored in the remote repository.
 * </ol>
 * 
 * Comparison of the 3 approaches:
 * <ul>
 * <li>Startup: Local could be the slowest if the PM first have to download the
 * VA to its local repo then cloning is needed. However, in case the VA is
 * already in the PM's repo then the Local approach is faster than Mixed because
 * it does not imply external network operations anymore. Remote starts up
 * immediately because there is no transfers are required.
 * <li>Execution: Local and Mixed approaches execute without any disruptions,
 * while the Remote approach continuously uses the network for its disk
 * operations, thus disk&network intensive VMs will suffer significantly.
 * 
 * WARNING: the current implementation does not really reduce the processing
 * speed of a Remote storage based VM. This is future work.
 * <li>Migration: Local and Mixed approaches need to transfer both the memory
 * state and the disk image for the VM. In contrast, the Remote approach only
 * transfers the memory state between the new PM and the old one. Thus migration
 * is almost instant compared to the other two approaches.
 * </ul>
 * 
 * @author 
 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 */
public class VirtualMachine extends MaxMinConsumer {

	/**
	 * This class is defined to ensure one can differentiate errors that were
	 * caused because the functions on the VM class are called in an improper
	 * order. E.g. migration cannot be done if the VM is not running already.
	 * 
	 * @author 
	 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
	 * 
	 */
	public static class StateChangeException extends VMManagementException {
		private static final long serialVersionUID = 2950595344006507672L;

		/**
		 * The constructor allows a textual message so users of this class can
		 * see the reason of the exception more clearly without debugging.
		 * 
		 * @param e
		 *            the message to be sent for the users of the simulator
		 */
		public StateChangeException(final String e) {
			super(e);
		}

	}

	/**
	 * This interface helps to receive events on status changes in virtual
	 * machines. One can subscribe to these events by calling the
	 * subscribeStateChange function. Afterwards whenever the VM changes its
	 * state it will automatically notify the subscribed entities.
	 * 
	 * @author 
	 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
	 * 
	 */
	public interface StateChange {
		/**
		 * If the state of a VM is changed this function is called on all
		 * subscribing implementations.
		 * 
		 * @param oldState
		 *            the state before the change was issued
		 * @param newState
		 *            the state after the change took effect
		 */
		void stateChanged(State oldState, State newState);
	}

	/**
	 * This internal interface is used to customize internal state change
	 * actions in the VM class.
	 * 
	 * @author 
	 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
	 * 
	 */
	private class EventSetup {

		public final State expectedState;

		public EventSetup(final State eState) {
			expectedState = eState;
		}

		/**
		 * Implementing this function allows the implementor to provide a custom
		 * VM state change function
		 */
		public void changeEvents() {
			setState(expectedState);
		}
	}

	private final EventSetup sdEvent = new EventSetup(State.SHUTDOWN);
	private final EventSetup susEvent = new EventSetup(State.SUSPENDED);

	private VirtualAppliance va;
	private PhysicalMachine.ResourceAllocation ra = null;
	private StorageObject disk = null;
	private StorageObject savedmemory = null;
	private Repository vasource = null;
	private Repository vatarget = null;

	public static enum State {
		/**
		 * The VA of the machine is arranged to be usable for the execution. The
		 * VM is not consuming energy. There is no used storage.
		 */
		INITIAL_TR,
		/**
		 * The VM is booting up, and already consumes energy although it does
		 * not offer useful services for its user. The VM stores a clone of the
		 * VA in a repository.
		 */
		STARTUP,
		/**
		 * The VM is operating according to the user's needs. The VM consumes
		 * energy. The VM stores a clone of the VA in a repository.
		 */
		RUNNING,
		/**
		 * The VM is about to be suspended, and its memory is under
		 * serialization. The VM does not consume energy anymore. The VM stores
		 * a clone of the VA in a repository.
		 */
		SUSPEND_TR,
		/**
		 * The VM is awaiting to be resumed. It can be resumed fast and it can
		 * skip the bootup procedure. The VM does not consume energy. The VM
		 * stores a clone of the VA and its serialized memory in a repository.
		 */
		SUSPENDED,
		/**
		 * This state signs that there was a problem with a migration operation.
		 * Otherwise it is equivalent to a regular suspended state.
		 */
		SUSPENDED_MIG,
		/**
		 * The VM is about to be running. Its memory is transferred and
		 * deserialized. The VM still stores a clone of the VA and its
		 * serialized memory in a repository. The VM starts to consume energy
		 * for the deserialization.
		 */
		RESUME_TR,
		/**
		 * The VM is on the move between two Phisical machines. During this
		 * operation it could happen that the VM and its serialized memory
		 * occupies disk space in two repositories. The VM starts to consume
		 * energy during the deserialization of its memory on the target PM.
		 */
		MIGRATING,
		/**
		 * The VM is not running. It's disk image (but not its memory state) can
		 * be found in the repository. So it is possible to start the VM up
		 * without the need for initial transfer. The VM is not consuming
		 * energy.
		 */
		SHUTDOWN,
		/**
		 * The VM is not running and it does not have any storage requirements
		 * in any of the repositories. The VM is not consuming energy.
		 */
		DESTROYED,
		/**
		 * The VM is destroyed, and it is not possible to instantiate it in the
		 * current cloud infrastructure (or the VM was terminated on user
		 * request before it was possible to instantiate it in the cloud)
		 */
		NONSERVABLE
	};

	public final static EnumSet<State> consumingStates = EnumSet.of(
			State.STARTUP, State.RUNNING, State.MIGRATING, State.RESUME_TR);
	public final static EnumSet<State> transferringStates = EnumSet.of(
			State.INITIAL_TR, State.SUSPEND_TR, State.RESUME_TR,
			State.MIGRATING);
	public final static EnumSet<State> suspendedStates = EnumSet.of(
			State.SUSPENDED, State.SUSPENDED_MIG);
	public final static EnumSet<State> preStartupStates = EnumSet.of(
			State.DESTROYED, State.SHUTDOWN);
	public final static EnumSet<State> preScheduleState = EnumSet.of(
			State.DESTROYED, State.NONSERVABLE);

	private final CopyOnWriteArrayList<StateChange> subscribers = new CopyOnWriteArrayList<StateChange>();

	private State currState = State.DESTROYED;

	public static final float loadwhilenotrunning = 0.2f;

	private final ArrayList<ResourceConsumption> suspendedTasks = new ArrayList<ResourceConsumption>();

	/**
	 * Instantiates a VM object
	 * 
	 * @param va
	 *            the virtual appliance that should be the base for this VM
	 */
	public VirtualMachine(final VirtualAppliance va) {
		super(0);
		if (va == null) {
			throw new IllegalStateException(
					"Cannot accept nonexistent virtual appliances on instantiation");
		}
		this.va = va;
	}

	/**
	 * Always use this function to set the current VM state. This way all the
	 * interested parties get notified on the changes.
	 * 
	 * @param newstate
	 *            The new state the VM is in.
	 */
	private void setState(final State newstate) {
		final State oldstate = currState;
		currState = newstate;
		for (StateChange sc : subscribers) {
			sc.stateChanged(oldstate, newstate);
		}
	}

	/**
	 * Queries the VA used by this VM
	 * 
	 * @return the va used by this VM
	 */
	public VirtualAppliance getVa() {
		return va;
	}

	/**
	 * Query the current state of the VM
	 * 
	 * @return the current vm state
	 */
	public State getState() {
		return currState;
	}

	/**
	 * Prepares the VM so it can be started without the need to clone its VA
	 * first. This function is useful in advanced scheduling situations.
	 * 
	 * @param vatarget
	 *            the disk that will host the VM.
	 * @param vasource
	 *            the repository where the VA for this VM is found. If null, the
	 *            function assumes it is found in the hosting PM's repository.
	 * @throws StateChangeException
	 *             if the VM is not destroyed
	 * @throws VMManagementException
	 *             if the VA transfer failed and the state change was reverted
	 */
	public void prepare(final Repository vasource, final Repository vatarget)
			throws VMManagementException, NetworkNode.NetworkException {
		if (currState != State.DESTROYED) {
			throw new StateChangeException("The VM is not destroyed");
		}
		initialTransfer(vasource, vatarget, sdEvent);
	}

	class InitialTransferEvent extends ConsumptionEventAdapter {
		final Repository target;
		final EventSetup esetup;
		final String diskid;

		public InitialTransferEvent(final Repository t, final EventSetup event,
				final String did) {
			target = t;
			esetup = event;
			diskid = did;
		}

		@Override
		public void conComplete() {
			disk = target.lookup(diskid);
			esetup.changeEvents();
		}
	}

	/**
	 * Ensures the transfer of the VM to the appropriate location. The location
	 * is determined based on the VM storage approach used. While transferring
	 * it maintains the INITIAL_TR state. If there is a fault it falls back to
	 * the state beforehand. If successful it allows the caller to change the
	 * event on the way it sees fit.
	 * 
	 * @param pm
	 *            the physical machine that hosts the VM.
	 * @param vasource
	 *            the repository where the VA for this VM is found. If null, the
	 *            function assumes it is found in the hosting PM's repository.
	 * @param es
	 *            The way the VM's state should be changed the function will
	 *            fire an event on this channel if the VA is cloned properly
	 * @throws VMManagementException
	 *             if the VA transfer failed and the state change was reverted
	 */
	private void initialTransfer(final Repository vasource,
			final Repository vatarget, final EventSetup es)
			throws VMManagementException, NetworkNode.NetworkException {
		final State oldState = currState;
		final long bgnwload = va.getBgNetworkLoad();
		if (bgnwload > 0 && vasource == vatarget) {
			throw new VMManagementException(
					"Cannot initiate a transfer for remotely running VM on the remote site!");
		}
		setState(State.INITIAL_TR);
		this.vasource = vasource;
		this.vatarget = vatarget;
		final String diskid = "VMDisk-of-" + Integer.toString(hashCode());
		final boolean noerror;
		if (bgnwload > 0) {
			// Remote scenario
			noerror = vasource.duplicateContent(va.id, diskid,
					new InitialTransferEvent(vasource, es, diskid));
		} else {
			if (vasource == null) {
				// Entirely local scenario
				noerror = vatarget == null ? false : vatarget.duplicateContent(
						va.id, diskid, new InitialTransferEvent(vatarget, es,
								diskid));
			} else {
				// Mixed scenario
				noerror = vasource.requestContentDelivery(va.id, diskid,
						vatarget,
						new InitialTransferEvent(vatarget, es, diskid));
			}
		}
		if (!noerror) {
			setState(oldState);
			throw new VMManagementException("Initial transfer failed");
		}
	}

	final EventSetup switchonEvent = new EventSetup(State.STARTUP) {
		@Override
		public void changeEvents() {
			final State preEventState = currState;
			super.changeEvents();
			try {
				newComputeTask(va.getStartupProcessing(),
						ra.allocated.requiredProcessingPower,
						new ConsumptionEventAdapter() {
							@Override
							public void conComplete() {
								super.conComplete();
								setState(State.RUNNING);
							}
						});
			} catch (StateChangeException e) {
				setState(preEventState);
			} catch (NetworkException e) {
				setState(preEventState);
			}
		}
	};

	/**
	 * Initiates the startup procedure of a VM. If the VM is in destroyed state
	 * then it ensures the disk image for the VM is ready to be used.
	 * 
	 * @param pm
	 *            the physical machine that hosts the VM.
	 * @param vasource
	 *            the repository where the VA for this VM is found. If null, the
	 *            function assumes it is found in the hosting PM's repository.
	 * @throws StateChangeException
	 *             if the VM is not destroyed or shutdown
	 * @throws VMManagementException
	 *             if the VA transfer failed and the state change was reverted
	 */
	public void switchOn(final PhysicalMachine.ResourceAllocation allocation,
			final Repository vasource) throws VMManagementException,
			NetworkNode.NetworkException {
		switch (currState) {
		case DESTROYED:
			setResourceAllocation(allocation);
			initialTransfer(vasource, allocation.host.localDisk, switchonEvent);
			break;
		case SHUTDOWN:
			// Shutdown has already done the transfer, we just need to make sure
			// the VM will get through its boot procedure
			if (allocation.host.localDisk != vatarget) {
				// TODO: maybe we can switch back to destroyed
				throw new VMManagementException(
						"VM was not prepared for this PM");
			}
			setResourceAllocation(allocation);
			switchonEvent.changeEvents();
			break;
		default:
			throw new StateChangeException(
					"The VM is not shut down or destroyed");
		}
	}

	/**
	 * This function is called after the disk and memory images of the VM are
	 * located on its new hosting repository. The new location allows the VMs to
	 * be resumed on their new host machines.
	 * 
	 * WARNING: After executing this function the VM might remain in
	 * SUSPENDED_MIG state if the resume on the new machine fails! For possible
	 * causes of this failure please check the documentation of the resume
	 * funciton.
	 * 
	 * @param target
	 *            the new resource allocation on which the resume operation
	 *            should take place
	 * @throws VMManagementException
	 *             in case some errors were reported during the resume operation
	 *             at the new physical machine
	 */
	private void resumeAfterMigration(
			final PhysicalMachine.ResourceAllocation target)
			throws NetworkNode.NetworkException {
		try {
			vatarget.deregisterObject(disk);
			vatarget.deregisterObject(savedmemory);
			setState(State.SUSPENDED);
			setResourceAllocation(target);
			vatarget = target.host.localDisk;
			realResume();
		} catch (StateChangeException e) {
			// Should not happen!
			System.err.println("IMPROPER STATE DURING MIGRATION!");
		} catch (VMManagementException e) {
			ra = null;
			setState(State.SUSPENDED_MIG);
		}
	}

	/**
	 * This function is responsible for the actual transfer between the old
	 * physical machine and the new one. This function ensures the transfer for
	 * both the disk and memory states.
	 * 
	 * WARNING: in case an error occurs (e.g. there is not enough space on the
	 * target physical machine's repository) then this function leaves the VM in
	 * SUSPENDED_MIG state.
	 * 
	 * @param target
	 *            the new resource allocation on which the resume operation
	 *            should take place
	 */
	private void actualMigration(final PhysicalMachine.ResourceAllocation target)
			throws NetworkNode.NetworkException {
		final boolean[] cancelMigration = new boolean[1];
		cancelMigration[0] = false;
		final Repository to = target.host.localDisk;
		class MigrationEvent extends ConsumptionEventAdapter {
			int eventcounter = 1;

			@Override
			public void conComplete() {
				if (cancelMigration[0]) {
					// Cleanup after faulty transfer
					to.deregisterObject(savedmemory.id);
				} else {
					eventcounter--;
					if (eventcounter == 0) {
						// Both the disk and memory state has completed its
						// transfer.
						try {
							resumeAfterMigration(target);
						} catch (NetworkException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}

		for (final ResourceConsumption con : suspendedTasks) {
			con.setProvider(target.host);
		}

		final MigrationEvent mp = new MigrationEvent();
		// inefficiency: the memory is moved twice to the
		// target pm because of the way resume works currently
		if (vatarget.requestContentDelivery(savedmemory.id, to, mp)) {
			if (va.getBgNetworkLoad() > 0) {
				// Remote scenario
				return;
			} else {
				// Local&Mixed scenario
				mp.eventcounter++;
				if (vatarget.requestContentDelivery(disk.id, to, mp)) {
					return;
				}
				// The disk could not be transferred:
				cancelMigration[0] = true;
			}
		}
		setState(State.SUSPENDED_MIG);
	}

	/**
	 * Moves all data necessary for the VMs execution from its current physical
	 * machine to another.
	 * 
	 * WARNING: in cases when the migration cannot complete, the VM will be left
	 * in a special suspended state: the SUSPENDED_MIG. This allows users to
	 * recover VMs and initiate the migration procedure to someplace else.
	 * 
	 * 
	 * @param target
	 *            the new resource allocation on which the resume operation
	 *            should take place
	 * @throws StateChangeException
	 *             if the VM is not in running state currently
	 * @throws VMManagementException
	 *             if the system have had troubles during the suspend operation.
	 */
	public void migrate(final PhysicalMachine.ResourceAllocation target)
			throws VMManagementException, NetworkNode.NetworkException {
		// Cross cloud migration needs an update on the vastorage also,
		// otherwise the VM will use a long distance repository for its
		// background network load!
		if (suspendedStates.contains(currState)) {
			setState(State.MIGRATING);
			actualMigration(target);
		} else {
			if (va.getBgNetworkLoad() <= 0 && ra != null) {
				NetworkNode.checkConnectivity(ra.host.localDisk,
						target.host.localDisk);
			}
			suspend(new EventSetup(State.MIGRATING) {
				@Override
				public void changeEvents() {
					super.changeEvents();
					try {
						actualMigration(target);
					} catch (NetworkException e) {
						// Ignore. This should never happen we have checked for
						// the connection beforehand.
					}
				}
			});
		}
	}

	/**
	 * Destroys the VM, and cleans up all repositories that could contain disk
	 * or memory states.
	 * 
	 * @throws StateChangeException
	 *             if some parts of the VM are under transfer so it cannot be
	 *             determined what to clean up.
	 */
	public void destroy(final boolean killTasks) throws VMManagementException {
		if (transferringStates.contains(currState)) {
			throw new StateChangeException(
					"Parts of the VM are under transfer."
							+ "This transfer should be finished before destruction.");
		}
		if (ra != null) {
			switchoff(killTasks);
		}
		if (vasource != null) {
			vasource.deregisterObject(disk);
			vasource.deregisterObject(savedmemory);
			vasource = null;
		}
		if (vatarget != null) {
			vatarget.deregisterObject(disk);
			vatarget.deregisterObject(savedmemory);
		}
		setState(State.DESTROYED);
	}

	/**
	 * Switches off an already running machine. After the successful execution
	 * of this function, the VM's state will be shutdown.
	 * 
	 * @throws StateChangeException
	 *             if is not running currently
	 */
	public void switchoff(final boolean killTasks) throws StateChangeException {
		if (currState != State.RUNNING) {
			throw new StateChangeException(
					"Cannot switch off a not running machine");
		}
		if (killTasks) {
			suspendedTasks.addAll(underProcessing);
			for (final ResourceConsumption con : suspendedTasks) {
				con.cancel();
			}
			suspendedTasks.clear();
		} else if (!underProcessing.isEmpty()) {
			throw new StateChangeException(
					"Cannot switch off a running machine with running tasks");
		}
		ra.release();
		ra = null;
		setState(State.SHUTDOWN);
	}

	/**
	 * Suspends an already running VM. During the suspend operation, the VM's
	 * memory state is stored to enable the resume operation for fast VM
	 * startup.
	 * 
	 * The VM's memory state is first created as a storage object on the PM's
	 * local repository then transferred to its designated location.
	 * 
	 * @throws StateChangeException
	 *             if the machine is not in running state
	 * @throws VMManagementException
	 *             if there is not enough space to store the memory state (first
	 *             locally, then locally/remotely depending on the VM storage
	 *             scenario
	 */
	public void suspend() throws VMManagementException,
			NetworkNode.NetworkException {
		suspend(susEvent);
	}

	/**
	 * Just like regular suspend but allows eventsetup hooks.
	 * 
	 * @param ev
	 *            the eventsetup hook to be used when the suspend operation is
	 *            complete.
	 * @throws StateChangeException
	 *             see at regular suspend
	 * @throws VMManagementException
	 *             see at regular suspend
	 */
	private void suspend(final EventSetup ev) throws VMManagementException,
			NetworkNode.NetworkException {
		if (currState != State.RUNNING) {
			throw new StateChangeException(
					"Cannot suspend a not running machine");
		}
		@SuppressWarnings("unchecked")
		List<ResourceConsumption>[] completeConlist = new List[] {
				underProcessing, new ArrayList<ResourceConsumption>(toBeAdded) };

		for (int i = 0; i < completeConlist.length; i++) {
			final int currlistsize = completeConlist[i].size();
			for (int idx = 0; idx < currlistsize; idx++) {
				final ResourceConsumption con = completeConlist[i].get(idx);
				con.suspend();
				suspendedTasks.add(con);
			}
		}
		final String memid = "VM-Memory-State-of-" + hashCode();
		final String tmemid = "Temp-" + memid;
		final Repository pmdisk = ra.host.localDisk;
		savedmemory = new StorageObject(tmemid, ra.allocated.requiredMemory,
				false);
		if (!pmdisk.registerObject(savedmemory)) {
			throw new VMManagementException(
					"Not enough space on localDisk for the suspend operation of "
							+ savedmemory);
		}
		setState(State.SUSPEND_TR);
		class SuspendComplete extends ConsumptionEventAdapter {
			@Override
			public void conComplete() {
				// Deregister temp content
				pmdisk.deregisterObject(savedmemory);
				// Save real content
				savedmemory = pmdisk.lookup(memid);
				ra.release();
				ra = null;
				ev.changeEvents();
			}
		}
		if (!pmdisk.duplicateContent(tmemid, memid, new SuspendComplete())) {
			// Set back the status so it is possible to try again
			setState(State.RUNNING);
			pmdisk.deregisterObject(savedmemory.id);
			savedmemory = null;
			throw new VMManagementException(
					"Not enough space on localDisk for the suspend operation of "
							+ memid);
		}
	}

	private void realResume() throws VMManagementException,
			NetworkNode.NetworkException {
		State priorState = currState;
		setState(State.RESUME_TR);
		final String tmemid = "Temp-" + savedmemory.id;
		final Repository pmdisk = ra.host.localDisk;
		class ResumeComplete extends ConsumptionEventAdapter {
			@Override
			public void conComplete() {
				// Deregister temp content
				pmdisk.deregisterObject(tmemid);
				// Deregister saved memory
				pmdisk.deregisterObject(savedmemory);
				savedmemory = null;
				setState(State.RUNNING);

				int size = suspendedTasks.size();
				for (int i = 0; i < size; i++) {
					suspendedTasks.get(i).registerConsumption();
				}
				suspendedTasks.clear();
			}
		}
		if (!pmdisk.duplicateContent(savedmemory.id, tmemid,
				new ResumeComplete())) {
			// Set back the status so it is possible to try again
			setState(priorState);
			throw new VMManagementException("Not enough space on "
					+ pmdisk.getName() + " for the resume operation of "
					+ hashCode());
		}
	}

	/**
	 * Resumes an already suspended VM. During the resume operation, the VM's
	 * memory state is used for fast VM startup.
	 * 
	 * The VM's memory state is first transferred to the PM's repository
	 * (simulating its loading to the VM's memory). Afterwards, this function
	 * cleans up the memory states as after startup the VM already changes them.
	 * 
	 * @throws StateChangeException
	 *             if the machine is not in suspended state
	 * @throws VMManagementException
	 *             if there is not enough space to retreive the memory state to
	 *             the PM's repository
	 */
	public void resume() throws VMManagementException,
			NetworkNode.NetworkException {
		switch (currState) {
		case SUSPENDED:
			realResume();
			break;
		case SUSPENDED_MIG:
			throw new StateChangeException(
					"One should use migrate to resume a VM from a SUSPENDED_MIG state");
		default:
			throw new StateChangeException(
					"Cannot resume a not suspended machine");
		}
	}

	/**
	 * Use this function to get notified about state changes on this VM
	 * 
	 * @param consumer
	 *            the party to be notified when the state changes
	 */
	public void subscribeStateChange(final StateChange consumer) {
		subscribers.add(consumer);
	}

	/**
	 * Use this function to be no longer notified about state changes on this VM
	 * 
	 * @param consumer
	 *            the party who previously received notifications
	 */
	public void unsubscribeStateChange(final StateChange consumer) {
		subscribers.remove(consumer);
	}

	@Override
	protected boolean isAcceptableConsumption(ResourceConsumption con) {
		return consumingStates.contains(currState) ? super
				.isAcceptableConsumption(con) : false;
	}

	public ResourceConsumption newComputeTask(final double total,
			final double limit, final ResourceConsumption.ConsumptionEvent e)
			throws StateChangeException, NetworkException {
		if (ra == null) {
			return null;
		}
		ResourceConsumption cons = new ResourceConsumption(total, limit, this,
				ra.host, e);
		if (cons.registerConsumption()) {
			final long bgnwload = va.getBgNetworkLoad();
			if (bgnwload > 0) {
				final long minBW = Math.min(
						bgnwload,
						Math.min(ra.host.localDisk.getOutputbw(),
								vasource.getInputbw()));
				NetworkNode.initTransfer(minBW * cons.getCompletionDistance(),
						minBW, ra.host.localDisk, vasource,
						new ConsumptionEventAdapter());
			}
			return cons;
		} else {
			return null;
		}
	}

	public void setResourceAllocation(PhysicalMachine.ResourceAllocation newRA)
			throws VMManagementException {
		switch (currState) {
		case DESTROYED:
		case SUSPENDED:
		case SHUTDOWN:
		case SUSPENDED_MIG:
			ra = newRA;
			ra.use(this);
			setPerTickProcessingPower(ra.allocated.totalProcessingPower);
			break;
		default:
			throw new StateChangeException(
					"The VM is already bound to a host please first resolve the VM-Host association!");
		}
	}

	public PhysicalMachine.ResourceAllocation getResourceAllocation() {
		return ra;
	}

	/**
	 * If there are not enough resources for the VM currently, to recover from
	 * this state (and allow the VM to be rescheduled) just issue a destroy
	 * command on the VM
	 */
	public void setNonservable() {
		setState(State.NONSERVABLE);
	}

	@Override
	public String toString() {
		return "VM(" + currState + " " + ra + " " + super.toString() + ")";
	}
}
