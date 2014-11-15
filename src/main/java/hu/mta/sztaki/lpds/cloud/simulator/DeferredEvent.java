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

package hu.mta.sztaki.lpds.cloud.simulator;

public abstract class DeferredEvent extends Timed {

	private boolean cancelled = false;

	public DeferredEvent(int delay) {
		if (delay > 0) {
			subscribe(delay);
		} else {
			eventAction();
		}
	}

	@Override
	public void tick(final long fires) {
		eventAction();
		unsubscribe();
	}

	public void cancel() {
		cancelled = isSubscribed() ? true : cancelled;
		unsubscribe();
	}

	public boolean isCancelled() {
		return cancelled;
	}

	protected abstract void eventAction();
}
