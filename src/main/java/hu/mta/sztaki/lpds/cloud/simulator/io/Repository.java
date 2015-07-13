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

package hu.mta.sztaki.lpds.cloud.simulator.io;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

/**
 * This class represents the storage entities in the system. Data transfers
 * could take place among these entities. Data is simulated to be stored in the
 * repositories.
 * 
 * To allow initial contents in a repository without delays, the class contains
 * the registerObject function. For regular use during the simulation, please
 * use its requestContentDelivery, and duplicateContent functions.
 * 
 * @author 
 *         "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012"
 */
public class Repository extends NetworkNode {

	/**
	 * Stuff that is already in the current repository
	 */
	final HashMap<String, StorageObject> contents = new HashMap<String, StorageObject>(
			16);

	/**
	 * Contents that are under transfer, to ensure that we are not allowing the
	 * deletion of contents that are being transferred currently
	 */

	final HashSet<String> underTransfer = new HashSet<String>();

	/**
	 * The total possible size of the contents in the current repository
	 */
	final private long maxStorageCapacity;
	/**
	 * The current total size of the contents in the actual repository object
	 */
	private long currentStorageUse = 0;
	/**
	 * The amount of contents that are about to arrive.
	 */
	private long promisedStorage = 0;

	/**
	 * Constructor for repository objects
	 * 
	 * @param capacity
	 *            the storage capacity of the repository
	 * @param maxInBW
	 *            the input network bandwidth of the repository
	 * @param maxOutBW
	 *            the output network bandwidth of the repository
	 * @param diskBW
	 *            the disk bandwidth of the repository
	 */

	public Repository(final long capacity, final String id, final long maxInBW,
			final long maxOutBW, final long diskBW,
			final HashMap<String, Integer> latencyMap) {
		super(id, maxInBW, maxOutBW, diskBW, latencyMap);
		maxStorageCapacity = capacity;
	}

	/**
	 * This function is designed to initially set up the repository contents. It
	 * does not simulate data movement, for data movement simulation please use
	 * the request content delivery function. The function also ensures that the
	 * given StorageObject is only stored once.
	 * 
	 * @param so
	 *            is the object to be stored
	 * @return true if the reuqested object was stored, false when there is not
	 *         enough space to store the object
	 */
	public boolean registerObject(final StorageObject so) {
		if (!contents.containsKey(so.id)) {
			final long futureFree = getFreeStorageCapacity() - so.size;
			if (futureFree < 0) {
				return false;
			}
			contents.put(so.id, so);
			currentStorageUse += so.size;
		}
		return true;
	}

	/**
	 * This function is designed to simulate the erase function of the
	 * repository given that its user knows the StorageObject to be dropped.
	 * 
	 * @param so
	 *            The storage object to be removed from the repository
	 * @return <ul>
	 *         <li>true, if the requested object was dropped from the repo,
	 *         <li>false if there are ongoing transfers involving the object
	 *         </ul>
	 */
	public boolean deregisterObject(final StorageObject so) {
		if (so == null) {
			// Nothing to remove so we can report success.
			return true;
		}
		return deregisterObject(so.id);
	}

	/**
	 * This function is designed to simulate the erase function of the
	 * repository given that its user knows the identifier of the content to be
	 * dropped.
	 * 
	 * @param soid
	 *            The storage object identifier
	 * @return <ul>
	 *         <li>true, if the requested object was dropped from the repo,
	 *         <li>false if there are ongoing transfers involving the object
	 *         </ul>
	 */
	public boolean deregisterObject(final String soid) {
		if (soid == null) {
			// Nothing to remove so we can report success.
			return true;
		}
		if (!underTransfer.contains(soid)) {
			StorageObject removed = contents.remove(soid);
			if (removed != null) {
				currentStorageUse -= removed.size;
				return true;
			}
		}
		return false;
	}

	/**
	 * Initiates transfer from a remote location
	 * 
	 * @param id
	 *            The storage object id that will be transferred
	 * @param target
	 *            The target repository where the transferred data will reside
	 * @param ev
	 *            the event to be fired if the transfer is completed
	 * @return true if the transfer was successfully initiated, false otherwise
	 */
	public boolean requestContentDelivery(final String id,
			final Repository target,
			final ResourceConsumption.ConsumptionEvent ev)
			throws NetworkException {
		return requestContentDelivery(id, null, target, ev);
	}

	/**
	 * Initiates the duplication of some repository content <br>
	 * 
	 * @param id
	 *            The storage object id that will be duplicated
	 * @param newId
	 *            The name of the copied storage object id if the target is the
	 *            same repository where the request is made.
	 * @param ev
	 *            the event to be fired if the transfer is completed
	 * @return true if the duplication request was successfully initiated, false
	 *         otherwise
	 */
	public boolean duplicateContent(final String id, final String newId,
			final ResourceConsumption.ConsumptionEvent ev)
			throws NetworkException {
		return requestContentDelivery(id, newId, this, ev);
	}

	/**
	 * This function registers a storage object for transfer. This object must
	 * be already stored in the requested repository. After the transfer is
	 * completed the function ensures that the target repository registers the
	 * transferred object.
	 * 
	 * @param id
	 *            The storage object id that will be transferred
	 * @param newId
	 *            The name of the copied storage object id if it needs to be
	 *            changed. If the target is the same repository this must be
	 *            specified. If the caller needs the same storage id then null
	 *            can be specified here.
	 * @param target
	 *            The target repository where the transferred data will reside.
	 *            If the target is the same repository please check the specific
	 *            requirements for newId!
	 * @param ev
	 *            the event to be fired if the transfer is completed
	 * @return true if the transfer was successfully initiated, false otherwise
	 *         (the system will not fire a transfer event if false is returned!)
	 */
	public boolean requestContentDelivery(final String id, final String newId,
			final Repository target,
			final ResourceConsumption.ConsumptionEvent ev)
			throws NetworkException {
		if (target == null
				|| (this == target && (newId == null || newId.equals(id))))
			return false;
		final StorageObject totransfer = contents.get(id);
		if (totransfer == null) {
			return false;
		}
		return manageStoragePromise(totransfer.size, id, target,
				new MainStorageActivity() {
					@Override
					public void doStorage() throws NetworkException {
						underTransfer.add(id);
						initTransfer(totransfer.size,
								ResourceConsumption.unlimitedProcessing,
								Repository.this, target,
								new ConsumptionEventAdapter() {
									@Override
									public void conComplete() {
										underTransfer.remove(id);
										final StorageObject toRegister = (target == Repository.this || newId != null) ? totransfer
												.newCopy(newId) : totransfer;

										target.promisedStorage -= totransfer.size;
										target.registerObject(toRegister);
										if (ev != null) {
											ev.conComplete();
										}
									}
								});
					}
				});
	}

	private static interface MainStorageActivity {
		void doStorage() throws NetworkException;
	}

	private static boolean manageStoragePromise(final long size,
			final String id, final Repository target,
			final MainStorageActivity mainActivity) throws NetworkException {
		final long increasedpromise = target.promisedStorage + size;
		if (increasedpromise + target.currentStorageUse <= target.maxStorageCapacity) {
			target.promisedStorage = increasedpromise;
			mainActivity.doStorage();
			return true;
		}
		return false;

	}

	public boolean storeInMemoryObject(final StorageObject so,
			final ResourceConsumption.ConsumptionEvent ev)
			throws NetworkException {
		if (lookup(so.id) != null) {
			return false;
		}
		return manageStoragePromise(so.size, so.id, this,
				new MainStorageActivity() {
					@Override
					public void doStorage() throws NetworkException {
						pushFromMemory(so.size,
								ResourceConsumption.unlimitedProcessing, true,
								new ConsumptionEventAdapter() {
									@Override
									public void conComplete() {
										promisedStorage -= so.size;
										registerObject(so);
										if (ev != null) {
											ev.conComplete();
										}
									}
								});
					}
				});
	}

	public boolean fetchObjectToMemory(final StorageObject so,
			final ResourceConsumption.ConsumptionEvent ev) {
		if (lookup(so.id) == null) {
			return false;
		}
		underTransfer.add(so.id);
		readToMemory(so.size, ResourceConsumption.unlimitedProcessing, true,
				new ConsumptionEventAdapter() {
					@Override
					public void conComplete() {
						underTransfer.remove(so.id);
						if (ev != null) {
							ev.conComplete();
						}
					}
				});
		return true;
	}

	/**
	 * Searches and returns the storage object with a given identifier
	 * 
	 * @param soid
	 *            the id of the storage object in question
	 * @return if found, the storage object, otherwise null
	 */
	public StorageObject lookup(final String soid) {
		return contents.get(soid);
	}

	/**
	 * Offers an unmodifiable list of contents.
	 * 
	 * @return the list of storage objects stored in this repository
	 */
	public Collection<StorageObject> contents() {
		return Collections.unmodifiableCollection(contents.values());
	}

	@Override
	public String toString() {
		return "Repo(DS:" + maxStorageCapacity + " Used:" + currentStorageUse
				+ " " + super.toString() + ")";
	}

	public long getMaxStorageCapacity() {
		return maxStorageCapacity;
	}

	public long getFreeStorageCapacity() {
		return maxStorageCapacity - currentStorageUse - promisedStorage;
	}
}
