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

package at.ac.uibk.dps.cloud.simulator.test.simple.cloud;

import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.ConsumptionEventAssert;
import at.ac.uibk.dps.cloud.simulator.test.PMRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class NetworkNodeTest extends PMRelatedFoundation {
	public final static String completeMessage = "Complete";
	// 1 tick is assumed 1ms
	public final static long inBW = 100000; // bytes/tick
	public final static long outBW = 100000; // bytes/tick
	public final static long diskBW = 50000; // bytes/tick
	public final static int targetlat = 3; // ticks
	public final static int sourcelat = 2; // ticks
	public final static String sourceName = "Source";
	public final static String targetName = "Target";
	public final static String thirdName = "Unconnected";
	NetworkNode source, target, third;
	static final long dataToBeSent = aSecond * inBW;
	static final long dataToBeStored = aSecond * diskBW / 2;

	public static HashMap<String, Integer> setupALatencyMap() {
		HashMap<String, Integer> lm = new HashMap<String, Integer>();
		lm.put(sourceName, sourcelat);
		lm.put(targetName, targetlat);
		return lm;
	}

	@Before
	public void nodeSetup() throws NetworkException {
		HashMap<String, Integer> lm = setupALatencyMap();
		source = new NetworkNode(sourceName, inBW, outBW, diskBW, lm, defaultStorageTransitions,
				defaultNetworkTransitions);
		target = new NetworkNode(targetName, inBW, outBW, diskBW, lm, defaultStorageTransitions,
				defaultNetworkTransitions);
		third = new NetworkNode(thirdName, inBW, outBW, diskBW, lm, defaultStorageTransitions,
				defaultNetworkTransitions);
		source.setState(NetworkNode.State.RUNNING);
		target.setState(NetworkNode.State.RUNNING);
		third.setState(NetworkNode.State.RUNNING);
	}

	@Test(timeout = 100)
	public void checkConstruction() {
		Assert.assertTrue("Node toString does not contain node name:", source.toString().contains(sourceName));
		Assert.assertEquals("Unexpected incoming bandwidth", inBW, source.getInputbw());
		Assert.assertEquals("Unexpected outgoing bandwidth", outBW, source.getOutputbw());
		Assert.assertEquals("Unexpected disk bandwidth", diskBW, source.getDiskbw());
		Assert.assertEquals("Unexpected name", sourceName, source.getName());
		Assert.assertEquals("Already used some bandwidth without requesting transfers", 0,
				source.inbws.getTotalProcessed() + source.outbws.getTotalProcessed(), 0);
	}

	@Test(timeout = 100)
	public void checkConnectivity() {
		// Node source tests:
		try {
			Assert.assertEquals("Internal connectivity for node source is not without latency", 0,
					NetworkNode.checkConnectivity(source, source));
		} catch (NetworkException ex) {
			Assert.fail("Internal connectivity of node source should always work");
		}
		try {
			Assert.assertEquals("External connectivity between node spurce and target is with an unexpected latency",
					targetlat, NetworkNode.checkConnectivity(source, target));
		} catch (NetworkException ex) {
			Assert.fail("There should be no connectivity issues between node source and target");
		}
		try {
			NetworkNode.checkConnectivity(source, third);
			Assert.fail("Node 1 should not be able to connect to node third");
		} catch (NetworkException ex) {
			// Expected behavior
		}

		// Node target tests:
		try {
			Assert.assertEquals("External connectivity between node target and source is with an unexpected latency",
					sourcelat, NetworkNode.checkConnectivity(target, source));
		} catch (NetworkException ex) {
			Assert.fail("There should be no connectivity issues between node target and source");
		}
		try {
			Assert.assertEquals("Internal connectivity for node target is not without latency", 0,
					NetworkNode.checkConnectivity(target, target));
		} catch (NetworkException ex) {
			Assert.fail("Internal connectivity of node target should always work");
		}
		try {
			NetworkNode.checkConnectivity(target, third);
			Assert.fail("Node target should not be able to connect to node third");
		} catch (NetworkException ex) {
			// Expected behavior
		}

		// Node third tests:
		try {
			Assert.assertEquals("External connectivity between node third and source is with an unexpected latency",
					sourcelat, NetworkNode.checkConnectivity(third, source));
		} catch (NetworkException ex) {
			Assert.fail("There should be no connectivity issues between node third and source");
		}
		try {
			Assert.assertEquals("External connectivity between node third and target is with an unexpected latency",
					targetlat, NetworkNode.checkConnectivity(third, target));
		} catch (NetworkException ex) {
			Assert.fail("There should be no connectivity issues between node third and target");
		}
		try {
			Assert.assertEquals("Internal connectivity for node third is not without latency", 0,
					NetworkNode.checkConnectivity(third, third));
		} catch (NetworkException ex) {
			Assert.fail("Internal connectivity of node third should always work");
		}
	}

	private void setupTransfer(final long len, final NetworkNode source, final NetworkNode target,
			final long expectedDelay) throws NetworkException {
		NetworkNode.initTransfer(len, ResourceConsumption.unlimitedProcessing, source, target,
				new ConsumptionEventAssert(Timed.getFireCount() + expectedDelay, true));
	}

	private void simulateThenExpectEventNum(int eventNum) {
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Not enough consumption events received", eventNum, ConsumptionEventAssert.hits.size());
	}

	@Test(timeout = 100)
	public void interNodeTransferTest() throws NetworkException {
		setupTransfer(dataToBeSent, source, target, aSecond + targetlat);
		simulateThenExpectEventNum(1);
		Assert.assertEquals("It is not expected to have incoming transfers in source", 0,
				source.inbws.getTotalProcessed(), 0);
		Assert.assertEquals("It is not expected to have outgoing transfers in target", 0,
				target.outbws.getTotalProcessed(), 0);
		Assert.assertEquals("Outgoing transfers on source are reported badly", dataToBeSent,
				source.outbws.getTotalProcessed(), 0);
		Assert.assertEquals("Incoming transfers on target are reported badly", dataToBeSent,
				target.inbws.getTotalProcessed(), 0);
	}

	@Test(timeout = 100)
	public void intraNodeTransferTest() throws NetworkException {
		setupTransfer(dataToBeStored, source, source, aSecond);
		simulateThenExpectEventNum(1);
		Assert.assertEquals("It is not expected to have network transfers within the node", 0,
				source.inbws.getTotalProcessed(), 0);
		Assert.assertEquals("It is not expected to have network transfers within the node", 0,
				source.outbws.getTotalProcessed(), 0);
	}

	@Test(timeout = 100)
	public void noInterferenceTest() throws NetworkException {
		setupTransfer(dataToBeSent, source, target, aSecond + targetlat);
		setupTransfer(dataToBeSent, target, source, aSecond + sourcelat);
		setupTransfer(dataToBeStored, source, source, aSecond);
		simulateThenExpectEventNum(3);
		Assert.assertEquals("Incoming transfers on source are reported badly", dataToBeSent,
				source.inbws.getTotalProcessed(), 0);
		Assert.assertEquals("Outgoing transfers on source are reported badly", dataToBeSent,
				source.outbws.getTotalProcessed(), 0);
		Assert.assertEquals("Incoming transfers on target are reported badly", dataToBeSent,
				target.inbws.getTotalProcessed(), 0);
		Assert.assertEquals("Outgoing transfers on target are reported badly", dataToBeSent,
				target.outbws.getTotalProcessed(), 0);
	}

	/**
	 * Expected simulation behavior:
	 * <ul>
	 * <li>Transfer1: lat(targetlat ms), transfer(300ms), transfer(1400ms) complete.
	 * <li>Transfer2: delay(300ms) lat(targetlat ms), transfer(1400ms), transfer
	 * (300ms) complete
	 * </ul>
	 */
	@Test(timeout = 100)
	public void interferenceTest() throws NetworkException {
		final int offset = 300;
		setupTransfer(dataToBeSent, source, target, 2 * aSecond - offset + targetlat);
		Timed.simulateUntil(Timed.getFireCount() + 300);
		setupTransfer(dataToBeSent, source, target, 2 * aSecond - offset + targetlat);
		simulateThenExpectEventNum(2);
		Assert.assertEquals("Outgoing transfers on source are reported badly", 2 * dataToBeSent,
				source.outbws.getTotalProcessed(), 0);
	}

	@Test(timeout = 100)
	public void midSimulationRxTest() throws NetworkException {
		setupTransfer(dataToBeSent, source, target, aSecond + targetlat);
		Timed.simulateUntil(Timed.getFireCount() + targetlat);
		double startingPoint = source.outbws.getTotalProcessed();
		do {
			Timed.fire();
			final double current = source.outbws.getTotalProcessed();
			if (startingPoint == current && ConsumptionEventAssert.hits.isEmpty()) {
				Assert.fail("Output sent bytes should always increase after a timing event " + Timed.getFireCount());
			}
			startingPoint = current;
		} while (ConsumptionEventAssert.hits.isEmpty());
		Assert.assertEquals("The final outgoing transfer amount is reported incorrectly", dataToBeSent,
				source.outbws.getTotalProcessed(), 0);
	}
}
