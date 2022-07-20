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
 *  (C) Copyright 2019-20, Gabor Kecskemeti, Rene Ponto, Zoltan Mann
 */
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.actions;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.IControllablePmScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelPM;

import java.util.Arrays;

/**
 * This class stores actions, which need to shut down a PM in the simulator.
 */
public class ShutDownAction extends Action {

    // Reference to the model of the PM, which needs to shut down
    public final ModelPM pmToShutDown;

    /**
     * PM scheduler
     */
    public final IControllablePmScheduler pmScheduler;

    /**
     * Constructor for an action to shut a PM down.
     *
     * @param pmToShutDown The reference to the PM inside the simulator to get shut
     *                     down.
     * @param pmScheduler  Reference to the PM scheduler of the IaaS service
     */
    public ShutDownAction(final ModelPM pmToShutDown, final IControllablePmScheduler pmScheduler) {
        super(Type.SHUTDOWN);
        this.pmToShutDown = pmToShutDown;
        this.pmScheduler = pmScheduler;
        // Logger.getGlobal().info("ShutDownAction created");
    }

    /**
     * This method determines the predecessors of this action. A predecessor of a
     * shut-down action is a migration from this PM.
     */
    @Override
    public void determinePredecessors(final Action[] actions) {
        // looking for migrations with this PM as source
        Arrays.stream(actions).filter(action ->
                action.type.equals(Type.MIGRATION) && (((MigrationAction) action).mvm.basedetails.initialHost.hashCode() == pmToShutDown.hashCode())
        ).forEach(this::addPredecessor);
    }

    @Override
    public String toString() {
        return super.toString() + pmToShutDown.toShortString();
    }

    /**
     * This method shuts the PM inside the simulator down.
     */
    @Override
    public void execute() {
        final PhysicalMachine pm = this.pmToShutDown.getPM();
        if (!pm.isHostingVMs())
            pmScheduler.switchOff(pm);
        finished();
    }

}