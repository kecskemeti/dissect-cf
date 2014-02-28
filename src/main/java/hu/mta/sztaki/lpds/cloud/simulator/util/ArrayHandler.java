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

import java.util.ArrayList;

public class ArrayHandler {
	public static <T> boolean removeAndReplaceWithLast(
			final ArrayList<T> toRemoveFrom, final T toRemoveWhat) {
		final int size = toRemoveFrom.size();
		final int sizeMinus = size - 1;
		for (int j = 0; j < size; j++) {
			if (toRemoveFrom.get(j) == toRemoveWhat) {
				if (j == sizeMinus) {
					toRemoveFrom.remove(j);
				} else {
					toRemoveFrom.set(j, toRemoveFrom.remove(sizeMinus));
				}
				return true;
			}
		}
		return false;
	}
}
