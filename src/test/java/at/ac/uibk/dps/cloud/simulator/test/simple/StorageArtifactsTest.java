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

package at.ac.uibk.dps.cloud.simulator.test.simple;

import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.TestFoundation;

public class StorageArtifactsTest extends TestFoundation {
	public final static long baseVASize = 100000000;
	private final static String initialID = "ID1";

	@Test(timeout = 100)
	public void vaConstructionTest() {
		Random rnd = SeedSyncer.centralRnd;
		final long bgLoad = rnd.nextLong();
		final double startproc = rnd.nextDouble();
		VirtualAppliance va1 = new VirtualAppliance(initialID, startproc, bgLoad);
		VirtualAppliance va2 = new VirtualAppliance("ID2", startproc, bgLoad, false, baseVASize);
		VirtualAppliance va3 = new VirtualAppliance("ID3", startproc, bgLoad, true, baseVASize);
		VirtualAppliance va4 = va3.newCopy("ID3Copy");
		Assert.assertEquals("Background load mismatch", bgLoad, va1.getBgNetworkLoad());
		Assert.assertEquals("Startup delay mismatch", startproc, va2.getStartupProcessing(), 0);
		Assert.assertEquals("Size mismatch", va3.size, va4.size);
		Assert.assertTrue("Size variance failure", va2.size != va3.size);
		Assert.assertTrue("Virtual appliance should contain its name in its toString",
				va1.toString().contains(initialID));
	}

	@Test(timeout = 100)
	public void saConstructionTest() {
		StorageObject so1 = new StorageObject(initialID);
		StorageObject so2 = new StorageObject("ID2", baseVASize, false);
		StorageObject so3 = new StorageObject("ID3", baseVASize, true);
		StorageObject so4 = so3.newCopy("ID3Copy");
		Assert.assertEquals("Size mismatch", so3.size, so4.size);
		Assert.assertTrue("Size variance failure", so2.size != so3.size);
		Assert.assertTrue("Size variance failure", so1.size != so2.size);
		Assert.assertTrue("Storage object should contain its name in its toString", so1.toString().contains(initialID));
	}

	@Test(timeout = 100, expected = IllegalArgumentException.class)
	public void negLenRegistration() {
		new StorageObject("NEGATIVE!!", -1, false);
	}
}
