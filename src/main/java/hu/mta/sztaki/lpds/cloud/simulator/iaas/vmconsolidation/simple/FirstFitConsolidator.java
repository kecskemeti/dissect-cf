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
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.simple;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.ModelBasedConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelPM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelVM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.PreserveAllocations;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.improver.NonImprover;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * @author Rene Ponto
 * <p>
 * This class is used to do the consolidation with first fit, i.e. the
 * target PM for a VM is selected using first fit, the VMs on a PM are
 * selected using first fit etc.
 */
public class FirstFitConsolidator extends ModelBasedConsolidator {

    private final HashSet<ModelPM> unchangeableBins=new HashSet<>();

    /**
     * The constructor for the First-Fit-Consolidator. Only the consolidation has to
     * be done. All things like creating the model, doing the changes in the
     * simulator etc. are done by the ModelBasedConsolidator.
     *
     * @param toConsolidate The IaaSService of the superclass Consolidator.
     * @param consFreq      This value determines, how often the consolidation
     *                      should run.
     */
    public FirstFitConsolidator(final IaaSService toConsolidate, final long consFreq) {
        super(toConsolidate, consFreq);
    }

    @Override
    protected void processProps() {
        // since the properties are not needed, nothing has to be done here.
    }

    /**
     * The method for doing the consolidation, which means start PMs, stop PMs,
     * migrate VMs, ... To do that, every action is saved as an Action-Node inside a
     * graph, which does the changes inside the simulator.
     */
    @Override
    public InfrastructureModel optimize(final InfrastructureModel sol) {
        unchangeableBins.clear();
        if (isOverAllocated(sol) || isUnderAllocated(sol)) {
            Arrays.stream(sol.bins).forEach( pm -> {
                if (isChangeable(pm)) {
                    if (pm.isUnderAllocated()) {
                        migrateUnderAllocatedPM(pm, sol);
                    }
                    if (pm.isOverAllocated()) {
                        migrateOverAllocatedPM(pm, sol);
                    }
                }
            });
        }

        /*
         * //clears the VMlist of each PM, so no VM is in the list more than once
         * for(ModelPM pm : getBins()) { List<ModelVM> allVMsOnPM = pm.getVMs();
         * Set<ModelVM> setItems = new LinkedHashSet<ModelVM>(allVMsOnPM);
         * allVMsOnPM.clear(); allVMsOnPM.addAll(setItems); }
         */
        return new InfrastructureModel(sol, PreserveAllocations.singleton, NonImprover.singleton);
    }

    private static final Predicate<ModelPM> oaChecker = ModelPM::isOverAllocated;

    private static final Predicate<ModelPM> uaChecker = ModelPM::isUnderAllocated;

    private boolean checkStateExists(final Predicate<ModelPM> chk, final InfrastructureModel sol) {
        return Arrays.stream(sol.bins).anyMatch(chk);
    }

    /**
     * Identifies PMs of the bins-ArrayList with the State OVERALLOCATED_RUNNING.
     *
     * @return true, if there is an overAllocated PM.
     */
    private boolean isOverAllocated(final InfrastructureModel sol) {
        return checkStateExists(oaChecker, sol);
    }

    /**
     * Identifies PMs of the bins-ArrayList with the State UNDERALLOCATED_RUNNING.
     *
     * @return true, if there is an underAllocated PM.
     */
    private boolean isUnderAllocated(final InfrastructureModel sol) {
        return checkStateExists(uaChecker, sol);
    }

    /**
     * This method is written to get a PM where a given VM can be migrated without
     * changing the status of the PM to 'overAllocated'. This is done by first fit.
     * We focus first on reusing an existing PM rather than starting a new one.
     *
     * @param toMig The VM which shall be migrated.
     * @return A PM where the given VM can be migrated; starts a new PM if there is
     * no running VM with the needed resources; null is returned if no
     * appropriate PM was found.
     */
    private Optional<ModelPM> getMigPm(final ModelVM toMig, final InfrastructureModel sol) {
        // Logger.getGlobal().info("vm="+toMig.toString());
        // now we have to search for a fitting pm
        var possiblePM = Arrays.stream(sol.bins).filter(pm ->
                pm != toMig.gethostPM() && !unchangeableBins.contains(pm) && pm.isHostingVMs() && !pm.isOverAllocated() && pm.isMigrationPossible(toMig)
        ).findFirst();
        if (possiblePM.isEmpty()) {
            // now we have to take an empty PM if possible, because no running PM is
            // possible to take the load of the VM
            possiblePM = startPM(toMig.getResources(), sol); // start an empty_off PM
        }
        return possiblePM;
    }

    /**
     * Starts a PM which contains the necessary resources for hosting the previous
     * VM. This is done by first-fit. If no PM can be started, a warning is thrown
     * to show that.
     *
     * @param vmConstraints The ResourceConstraints of the VM, which shall be hosted
     *                      on a not running PM
     * @return A PM with the needed resources or null if no appropriate PM was
     * found.
     */
    private Optional<ModelPM> startPM(final ResourceConstraints vmConstraints, final InfrastructureModel sol) {
        return Arrays.stream(sol.bins).filter(pm -> !pm.isHostingVMs() && vmConstraints.compareTo(pm.getTotalResources()) <= 0).findAny();
    }

    private boolean isChangeable(final ModelPM pm) {
        return !unchangeableBins.contains(pm);
    }

    /**
     * This method handles the migration of all VMs of an OverAllocated PM, till the
     * state changes to NORMAL_RUNNING. To do that, a targetPM will be found for
     * every VM on this PM and then the migrations will be performed. If not enough
     * VMs can be migrated, the state of this PM will be changed to
     * UNCHANGEABLE_OVERALLOCATED.
     *
     * @param source The source PM which host the VMs to migrate.
     */
    private void migrateOverAllocatedPM(final ModelPM source, final InfrastructureModel sol) {
        // Logger.getGlobal().info("source="+source.toString());
        while (isChangeable(source) && source.isOverAllocated()) {
            var actual = source.getVMs().get(0); // now taking the first VM on this PM and try to migrate it
            // to a
            // target
            var pm = getMigPm(actual, sol);
            // if there is no PM to host the actual VM of the source PM, change the state
            // depending on its actual state
            if (pm.isEmpty()) {
                unchangeableBins.add(source);
            } else {
                actual.migrate(pm.get()); // do the migration
            }
        }
    }

    /**
     * This method handles the migration of all VMs of an underAllocated PM. To do
     * that, a targetPM will be found for every VM on this PM and then the migrations
     * will be performed, but if not all the hosted VMs can be migrated (after
     * this process the PM would still host running VMs), nothing will be changed.
     *
     * @param source The source PM which host the VMs to migrate.
     */
    private void migrateUnderAllocatedPM(final ModelPM source, final InfrastructureModel sol) {
        var mvms = new ArrayDeque<>(source.getVMs());
        try {
            mvms.descendingIterator().forEachRemaining(v -> source.migrateVM(v, getMigPm(v, sol).orElseThrow()));
        } catch (NoSuchElementException rex) {
            var incorrectlyMigrated = mvms.stream().filter(v -> v.gethostPM() != source).map(v -> {
                v.migrate(source);
                return 1;
            }).count();
            if (incorrectlyMigrated == 0) {
                unchangeableBins.add(source);
            }
        }
    }
}