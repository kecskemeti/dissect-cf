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

public class Repository extends NetworkNode {

	final HashMap<String, StorageObject> contents = new HashMap<String, StorageObject>(
			16);

	final HashSet<String> underTransfer = new HashSet<String>();

	final private long maxStorageCapacity;
	private long currentStorageUse = 0;
	private long promisedStorage = 0;

	public Repository(final long capacity, final String id, final long maxInBW,
			final long maxOutBW, final long diskBW,
			final HashMap<String, Integer> latencyMap) {
		super(id, maxInBW, maxOutBW, diskBW, latencyMap);
		maxStorageCapacity = capacity;
	}

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

	public boolean deregisterObject(final StorageObject so) {
		if (so == null) {
			return true;
		}
		return deregisterObject(so.id);
	}

	public boolean deregisterObject(final String soid) {
		if (soid == null) {
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

	public boolean requestContentDelivery(final String id,
			final Repository target,
			final ResourceConsumption.ConsumptionEvent ev)
			throws NetworkException {
		return requestContentDelivery(id, null, target, ev);
	}

	public boolean duplicateContent(final String id, final String newId,
			final ResourceConsumption.ConsumptionEvent ev)
			throws NetworkException {
		return requestContentDelivery(id, newId, this, ev);
	}

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
		final long increasedpromise = target.promisedStorage + totransfer.size;
		if (increasedpromise + target.currentStorageUse <= target.maxStorageCapacity) {
			underTransfer.add(id);
			target.promisedStorage = increasedpromise;
			initTransfer(totransfer.size,
					ResourceConsumption.unlimitedProcessing, this, target,
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
			return true;
		}
		return false;
	}

	public StorageObject lookup(final String soid) {
		return contents.get(soid);
	}

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
