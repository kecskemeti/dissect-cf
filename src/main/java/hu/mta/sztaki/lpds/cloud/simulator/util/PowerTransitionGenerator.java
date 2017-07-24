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
package hu.mta.sztaki.lpds.cloud.simulator.util;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.ConstantConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.LinearConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * This helper class provides a simple way to generate the necessary power
 * transition functions for physical machine behavior.
 * 
 * WARNING: this is not intended to be used for realistic modeling of physical
 * machines but allows a simplified creation of physical machines for which the
 * power model is not expected to be used.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2014"
 *
 */
public class PowerTransitionGenerator {

	public static final String defaultPowerState = "default";

	/**
	 * When defining powertransitions for the PM one has to label each transiton's
	 * properties with a kind
	 * 
	 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
	 *         MTA SZTAKI (c) 2014"
	 * 
	 */
	public static enum PowerStateKind {
		/**
		 * the powerstate definitions belong to the cpu and memory resources of the PM
		 */
		host,
		/**
		 * the powerstate definitions belong to the local disk of the PM
		 */
		storage,
		/**
		 * the powerstate definitions belong to the network interface of the PM
		 */
		network
	}

	/**
	 * The generator function that derives the power transition and power state
	 * definitions from a few simple parameters. The generated power states will all
	 * be based on the linear consumption model (except during power off state).
	 * 
	 * @param minpower
	 *            the power (in W) to be drawn by the PM while it is completely
	 *            switched off (but plugged into the wall socket)
	 * @param idlepower
	 *            the power (in W) to be drawn by the PM's CPU while it is running
	 *            but not doing any useful tasks.
	 * @param maxpower
	 *            the power (in W) to be drawn by the PM's CPU if it's CPU is
	 *            completely utilized
	 * @param diskDivider
	 *            the ratio of the PM's disk power draw values compared to the it's
	 *            CPU's power draw values
	 * @param netDivider
	 *            the ratio of the PM's network power draw values compared to the
	 *            it's CPU's power draw values
	 * @return a power state setup useful for instantiating PMs
	 * @throws SecurityException
	 *             if the power state to be created failed to instantiate properly
	 * @throws InstantiationException
	 *             if the power state to be created failed to instantiate properly
	 * @throws IllegalAccessException
	 *             if the power state to be created failed to instantiate properly
	 * @throws NoSuchFieldException
	 *             if the power state to be created failed to instantiate properly
	 */
	public static EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> generateTransitions(
			double minpower, double idlepower, double maxpower, double diskDivider, double netDivider)
			throws SecurityException, InstantiationException, IllegalAccessException, NoSuchFieldException {
		EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> returner = new EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>>(
				PowerTransitionGenerator.PowerStateKind.class);
		HashMap<String, PowerState> hostStates = new HashMap<String, PowerState>();
		returner.put(PowerTransitionGenerator.PowerStateKind.host, hostStates);
		HashMap<String, PowerState> diskStates = new HashMap<String, PowerState>();
		returner.put(PowerTransitionGenerator.PowerStateKind.storage, diskStates);
		HashMap<String, PowerState> netStates = new HashMap<String, PowerState>();
		returner.put(PowerTransitionGenerator.PowerStateKind.network, netStates);
		PowerState hostDefault = new PowerState(idlepower, maxpower - idlepower, LinearConsumptionModel.class);
		for (PhysicalMachine.State aState : PhysicalMachine.StatesOfHighEnergyConsumption) {
			hostStates.put(aState.toString(), hostDefault);
		}
		hostStates.put(PhysicalMachine.State.OFF.toString(),
				new PowerState(minpower, 0, ConstantConsumptionModel.class));
		diskStates.put(NetworkNode.State.OFF.toString(), new PowerState(0, 0, ConstantConsumptionModel.class));
		diskStates.put(NetworkNode.State.RUNNING.toString(), new PowerState(idlepower / diskDivider / 2,
				(maxpower - idlepower) / diskDivider / 2, LinearConsumptionModel.class));
		netStates.put(NetworkNode.State.OFF.toString(), new PowerState(0, 0, ConstantConsumptionModel.class));
		netStates.put(NetworkNode.State.RUNNING.toString(), new PowerState(idlepower / netDivider / 2,
				(maxpower - idlepower) / netDivider / 2, LinearConsumptionModel.class));
		return returner;
	}

	/**
	 * fetches the required power state from the corresponding power state map. If
	 * the new state is not listed, it serves back the default mapping
	 * 
	 * @param theMap
	 *            The map to look up the new power state
	 * @param newState
	 *            the textual spec of the power state
	 * @return the power state to be used in accordance to the textual spec
	 */
	public static PowerState getPowerStateFromMap(final Map<String, PowerState> theMap, final String newState) {
		PowerState returner;
		if ((returner = theMap.get(newState)) == null) {
			returner = theMap.get(defaultPowerState);
		}
		return returner;
	}

}
