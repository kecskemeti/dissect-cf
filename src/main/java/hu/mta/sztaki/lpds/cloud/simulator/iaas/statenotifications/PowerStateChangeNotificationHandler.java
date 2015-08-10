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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.statenotifications;

import org.apache.commons.lang3.tuple.Pair;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.PowerBehaviorChangeListener;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceSpreader;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.SingleNotificationHandler;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.StateDependentEventHandler;

public class PowerStateChangeNotificationHandler
		implements SingleNotificationHandler<PowerBehaviorChangeListener, Pair<ResourceSpreader, PowerState>> {

	private static final PowerStateChangeNotificationHandler handlerSingleton = new PowerStateChangeNotificationHandler();

	private PowerStateChangeNotificationHandler() {
	}

	public static StateDependentEventHandler<PowerBehaviorChangeListener, Pair<ResourceSpreader, PowerState>> getHandlerInstance() {
		return new StateDependentEventHandler<PowerBehaviorChangeListener, Pair<ResourceSpreader, PowerState>>(
				handlerSingleton);
	}

	@Override
	public void sendNotification(final PowerBehaviorChangeListener onObject,
			final Pair<ResourceSpreader, PowerState> newPowerBehavior) {
		onObject.behaviorChanged(newPowerBehavior.getLeft(), newPowerBehavior.getRight());
	}
}
