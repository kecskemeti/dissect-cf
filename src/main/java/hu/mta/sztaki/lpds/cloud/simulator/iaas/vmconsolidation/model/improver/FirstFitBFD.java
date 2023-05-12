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
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.improver;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.InfrastructureModel;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelPM;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model.ModelVM;

/**
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 * Moores University, (c) 2018"
 */
public class FirstFitBFD extends InfrastructureModel implements InfrastructureModel.Improver {
    public static final FirstFitBFD singleton = new FirstFitBFD();
    final private static Comparator<ModelVM> mvmComp = (vm1, vm2) -> Double.compare(vm2.getResources().getTotalProcessingPower(),
            vm1.getResources().getTotalProcessingPower());

    final private static Comparator<ModelPM> mpmComp = (pm1, pm2) -> Double.compare(pm2.consumed.getTotalProcessingPower(), pm1.consumed.getTotalProcessingPower());

    private FirstFitBFD() {
        super(new PhysicalMachine[0], 0, false, 0);
    }

    /**
     * Improving a solution by relieving overloaded PMs, emptying underloaded PMs,
     * and finding new hosts for the thus removed VMs using BFD.
     */
    @Override
    public void improve(final InfrastructureModel toImprove) {
        Stream.Builder<ModelVM> potentialVMstoMove= Stream.builder();
        // relieve overloaded PMs + empty underloaded PMs
        Arrays.stream(toImprove.bins).forEach(pm -> {
            var vmsOfPm = pm.getVMs();
            while (!vmsOfPm.isEmpty() && (pm.isOverAllocated() || pm.isUnderAllocated())) {
                var vm = vmsOfPm.get(vmsOfPm.size() - 1);
                potentialVMstoMove.accept(vm);
                pm.removeVM(vm);
            }
        });
        // find new host for the VMs to migrate using BFD
        potentialVMstoMove.build().sorted(mvmComp).forEach(vm ->
                Arrays.stream(toImprove.bins).sorted(mpmComp)
                        .filter(pm -> pm.isMigrationPossible(vm))
                        .findFirst().ifPresentOrElse(
                                targetpm -> targetpm.addVM(vm),
                                () -> vm.prevPM.addVM(vm))
        );
    }
}