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

import java.util.EnumMap;

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

	/**
	 * The generator function that derives the power transition and power state
	 * definitions from a few simple parameters. The generated power states will
	 * all be based on the linear consumption model (except during power off
	 * state).
	 * 
	 * @param minpower
	 *            the power (in W) to be drawn by the PM while it is completely
	 *            switched off (but plugged into the wall socket)
	 * @param idlepower
	 *            the power (in W) to be drawn by the PM's CPU while it is
	 *            running but not doing any useful tasks.
	 * @param maxpower
	 *            the power (in W) to be drawn by the PM's CPU if it's CPU is
	 *            completely utilized
	 * @param diskDivider
	 *            the ratio of the PM's disk power draw values compared to the
	 *            it's CPU's power draw values
	 * @param netDivider
	 *            the ratio of the PM's network power draw values compared to
	 *            the it's CPU's power draw values
	 * @return a power state setup useful for instantiating PMs
	 * @throws SecurityException
	 *             if the power state to be created failed to instantiate
	 *             properly
	 * @throws InstantiationException
	 *             if the power state to be created failed to instantiate
	 *             properly
	 * @throws IllegalAccessException
	 *             if the power state to be created failed to instantiate
	 *             properly
	 * @throws NoSuchFieldException
	 *             if the power state to be created failed to instantiate
	 *             properly
	 */
	public static EnumMap<PhysicalMachine.PowerStateKind, EnumMap<PhysicalMachine.State, PowerState>> generateTransitions(
			double minpower, double idlepower, double maxpower, double diskDivider, double netDivider)
					throws SecurityException, InstantiationException, IllegalAccessException, NoSuchFieldException {
		EnumMap<PhysicalMachine.PowerStateKind, EnumMap<PhysicalMachine.State, PowerState>> returner = new EnumMap<PhysicalMachine.PowerStateKind, EnumMap<PhysicalMachine.State, PowerState>>(
				PhysicalMachine.PowerStateKind.class);
		EnumMap<PhysicalMachine.State, PowerState> hostStates = new EnumMap<PhysicalMachine.State, PowerState>(
				PhysicalMachine.State.class);
		returner.put(PhysicalMachine.PowerStateKind.host, hostStates);
		EnumMap<PhysicalMachine.State, PowerState> diskStates = new EnumMap<PhysicalMachine.State, PowerState>(
				PhysicalMachine.State.class);
		returner.put(PhysicalMachine.PowerStateKind.storage, diskStates);
		EnumMap<PhysicalMachine.State, PowerState> netStates = new EnumMap<PhysicalMachine.State, PowerState>(
				PhysicalMachine.State.class);
		returner.put(PhysicalMachine.PowerStateKind.network, netStates);
		PowerState hostDefault = new PowerState(idlepower, maxpower - idlepower, LinearConsumptionModel.class);
		PowerState diskDefault = new PowerState(idlepower / diskDivider / 2, (maxpower - idlepower) / diskDivider / 2,
				LinearConsumptionModel.class);
		PowerState netDefault = new PowerState(idlepower / netDivider / 2, (maxpower - idlepower) / netDivider / 2,
				LinearConsumptionModel.class);
		for (PhysicalMachine.State aState : PhysicalMachine.StatesOfHighEnergyConsumption) {
			hostStates.put(aState, hostDefault);
			diskStates.put(aState, diskDefault);
			netStates.put(aState, netDefault);
		}

		hostStates.put(PhysicalMachine.State.OFF, new PowerState(minpower, 0, ConstantConsumptionModel.class));
		diskStates.put(PhysicalMachine.State.OFF, new PowerState(0, 0, ConstantConsumptionModel.class));
		netStates.put(PhysicalMachine.State.OFF, new PowerState(0, 0, ConstantConsumptionModel.class));
		return returner;
	}

}
