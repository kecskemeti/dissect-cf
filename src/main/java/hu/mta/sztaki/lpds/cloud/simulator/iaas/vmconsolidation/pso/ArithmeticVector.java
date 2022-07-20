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
package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.pso;

import java.util.Arrays;
import java.util.function.Function;

/**
 * @author Rene Ponto
 * <p>
 * This class is used to create an Object for the location and the
 * velocity of particles of the pso-consolidator. Despite of that there
 * are operations like add an ArithmeticVector, subtract an
 * ArithmeticVector and multiply it with a constant.
 */
public final class ArithmeticVector {

    final double[] data;

    /**
     * @param baseData is not copied, but referenced from the class. It is assumed
     *                 that the passed array is not modified outside after receiving
     *                 it via the constructor! For making an actual copy of the data
     *                 passed, use the copy constructor.
     */
    public ArithmeticVector(final double[] baseData) {
        data = baseData;
    }

    public ArithmeticVector(final ArithmeticVector toCopy) {
        data = Arrays.copyOf(toCopy.data, toCopy.data.length);
    }

    /**
     * Method to add another ArithmeticVector to this one. There is a defined border
     * so there can no PM be used which is not in the IaaS.
     *
     * @param addMe The second ArithmeticVector, the velocity.
     */
    public void addUp(final ArithmeticVector addMe) {
        applyOnVector(i -> data[i] += addMe.data[i]);
    }

    /**
     * Method to subtract another ArithmeticVector of this one. There is a defined
     * border for not using a PM with an ID lower than 1, because such a PM does not
     * exist.
     *
     * @param subtractMe The second ArithmeticVector, the velocity.
     */
    public void subtract(final ArithmeticVector subtractMe) {
        applyOnVector(i -> data[i] - subtractMe.data[i]);
    }

    /**
     * Method to multiply every value of this class with a constant.
     *
     * @param scale The double Value to multiply with.
     */
    public void scale(final double scale) {
        applyOnVector(i -> data[i] * scale);
    }

    private void applyOnVector(Function<Integer, Double> op) {
        for (int i = 0; i < data.length; i++) {
            data[i] = op.apply(i);
        }
    }
}