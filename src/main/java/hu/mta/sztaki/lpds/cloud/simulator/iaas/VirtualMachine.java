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

public class VirtualMachine extends MaxMinConsumer {

	public static class StateChangeException extends VMManagementException {
		private static final long serialVersionUID = 2950595344006507672L;

		public StateChangeException(final String e) {
			super(e);
		}

	}

	public interface StateChange {
		void stateChanged(State oldState, State newState);
	}

	private class EventSetup {

		public final State expectedState;

		public EventSetup(final State eState) {
			expectedState = eState;
		}

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
		INITIAL_TR,
		STARTUP,
		RUNNING,
		SUSPEND_TR,
		SUSPENDED,
		SUSPENDED_MIG,
		RESUME_TR,
		MIGRATING,
		SHUTDOWN,
		DESTROYED,
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

	private final CopyOnWriteArrayList<StateChange> subscribers = new CopyOnWriteArrayList<StateChange>();

	private State currState = State.DESTROYED;

	public static final float loadwhilenotrunning = 0.2f;

	private final ArrayList<ResourceConsumption> suspendedTasks = new ArrayList<ResourceConsumption>();

	public VirtualMachine(final VirtualAppliance va) {
		super(0);
		if (va == null) {
			throw new IllegalStateException(
					"Cannot accept nonexistent virtual appliances on instantiation");
		}
		this.va = va;
	}

	private void setState(final State newstate) {
		final State oldstate = currState;
		currState = newstate;
		for (StateChange sc : subscribers) {
			sc.stateChanged(oldstate, newstate);
		}
	}

	public VirtualAppliance getVa() {
		return va;
	}

	public State getState() {
		return currState;
	}

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
			noerror = vasource.duplicateContent(va.id, diskid,
					new InitialTransferEvent(vasource, es, diskid));
		} else {
			if (vasource == null) {
				noerror = vatarget == null ? false : vatarget.duplicateContent(
						va.id, diskid, new InitialTransferEvent(vatarget, es,
								diskid));
			} else {
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
				newComputeTask(va.getStartupdelay() / 1000f
						/ perSecondProcessingPower,
						ResourceConsumption.unlimitedProcessing,
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

	public void switchOn(final PhysicalMachine.ResourceAllocation allocation,
			final Repository vasource) throws VMManagementException,
			NetworkNode.NetworkException {
		switch (currState) {
		case DESTROYED:
			setResourceAllocation(allocation);
			initialTransfer(vasource, allocation.host.localDisk, switchonEvent);
			break;
		case SHUTDOWN:
			if (allocation.host.localDisk != vatarget) {
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
			System.err.println("IMPROPER STATE DURING MIGRATION!");
		} catch (VMManagementException e) {
			ra = null;
			setState(State.SUSPENDED_MIG);
		}
	}

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
					to.deregisterObject(savedmemory.id);
				} else {
					eventcounter--;
					if (eventcounter == 0) {
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
		if (vatarget.requestContentDelivery(savedmemory.id, to, mp)) {
			if (va.getBgNetworkLoad() > 0) {
				return;
			} else {
				mp.eventcounter++;
				if (vatarget.requestContentDelivery(disk.id, to, mp)) {
					return;
				}
				cancelMigration[0] = true;
			}
		}
		setState(State.SUSPENDED_MIG);
	}

	public void migrate(final PhysicalMachine.ResourceAllocation target)
			throws VMManagementException, NetworkNode.NetworkException {
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
					}
				}
			});
		}
	}

	public void destroy(final boolean killTasks) throws VMManagementException {
		if (transferringStates.contains(currState)) {
			throw new StateChangeException(
					"Parts of the VM are under transfer."
							+ "This transfer should be finished before destruction.");
		}
		if (currState.equals(State.RUNNING)) {
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

	public void suspend() throws VMManagementException,
			NetworkNode.NetworkException {
		suspend(susEvent);
	}

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
				pmdisk.deregisterObject(savedmemory);
				savedmemory = pmdisk.lookup(memid);
				ra.release();
				ra = null;
				ev.changeEvents();
			}
		}
		if (!pmdisk.duplicateContent(tmemid, memid, new SuspendComplete())) {
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
				pmdisk.deregisterObject(tmemid);
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
			setState(priorState);
			throw new VMManagementException("Not enough space on "
					+ pmdisk.getName() + " for the resume operation of "
					+ hashCode());
		}
	}

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

	public void subscribeStateChange(final StateChange consumer) {
		subscribers.add(consumer);
	}

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
			setPerSecondProcessingPower(ra.allocated.totalProcessingPower);
			break;
		default:
			throw new StateChangeException(
					"The VM is already bound to a host please first resolve the VM-Host association!");
		}
	}

	public PhysicalMachine.ResourceAllocation getResourceAllocation() {
		return ra;
	}

	public void setNonservable() {
		setState(State.NONSERVABLE);
	}

	@Override
	public String toString() {
		return "VM(" + currState + " " + super.toString() + ")";
	}
}
