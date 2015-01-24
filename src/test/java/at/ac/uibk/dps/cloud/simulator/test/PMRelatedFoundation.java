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

package at.ac.uibk.dps.cloud.simulator.test;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

public class PMRelatedFoundation extends ConsumptionEventFoundation {
	public final static PrintStream realStdOut = System.out;
	public final static PrintStream realStdErr = System.err;

	@BeforeClass
	public static void initStaticParts() {
		new PhysicalMachine(1, 1, 1, new Repository(1, "", 1, 1, 1, null), 1, 1);
	}

	@Before
	public void introduceRedirections() {
		try {
			System.setOut(new PrintStream(new OutputStream() {

				@Override
				public void write(int arg0) throws IOException {

				}
			}));
			System.setErr(new PrintStream(new OutputStream() {

				@Override
				public void write(int arg0) throws IOException {

				}
			}));

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@After
	public void revertRedirects() {
		System.setOut(realStdOut);
		System.setErr(realStdErr);
	}

}
