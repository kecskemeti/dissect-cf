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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel;

/**
 * This class is part of the unified resource consumption model of DISSECT-CF.
 * 
 * This class provides the implementation of the core scheduling logic in the
 * simulator. The logic is based on the max-min fairness algorithm.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2015"
 * 
 */
public abstract class MaxMinFairSpreader extends ResourceSpreader {

	/**
	 * Determines the amount of processing that still remains unspent in this
	 * spreader. This value is always smaller than the perTickProcessingPower of
	 * the spreader.
	 */
	private double currentUnProcessed;
	/**
	 * The number of resource consumptions for which this spreader still did not
	 * assign temporal resource utilization limits - see: p(c,s,t) in the paper
	 * titled
	 * "DISSECT-CF: a simulator to foster energy-aware scheduling in infrastructure clouds"
	 * .
	 */
	private int unassignedNum;
	/**
	 * Offers a temporary storage for the size of the current resource
	 * consumption list. This is updated once in every freq update cycle.
	 */
	private int upLen;

	/**
	 * Constructs a generic Max Min fairness based resource spreader.
	 * 
	 * @param perSecondProcessing
	 *            determines the amount of resources this resource spreader
	 *            could handle in a single tick
	 */
	public MaxMinFairSpreader(final double perSecondProcessing) {
		super(perSecondProcessing);
	}

	/**
	 * At the beginning of a freq update cycle, every influence group member is
	 * initialised with this function.
	 * 
	 * The function assures that the private fields of this class are
	 * initialised, as well as all resource consumptions in the influence group
	 * are set as unassigned and their processing limits are set to 0. This step
	 * actually allows the max-min fairness algorithm to gradually increase the
	 * processing limits for each resource consumption that could play a role in
	 * bottleneck situations.
	 * 
	 * @return <i>true</i> if there were some resource consumptions to be
	 *         processed by this spreader.
	 */
	private boolean initializeFreqUpdate() {
		unassignedNum = upLen = underProcessing.size();
		if (unassignedNum == 0) {
			return false;
		}
		for (int i = 0; i < upLen; i++) {
			final ResourceConsumption con = underProcessing.get(i);
			con.limithelper = 0;
			con.unassigned = true;
		}
		currentUnProcessed = perTickProcessingPower;
		return true;
	}

	/**
	 * Manages the gradual increase of the processing limits for each resource
	 * consumption related to this spreader. The increase is started from the
	 * limithelper of each resource consumption. This limithelper tells to what
	 * amount the particular consumption was able to already process (i.e. it is
	 * the maximum consumption limit of some of its peers). If a resource
	 * consumption is still unassigned then its limithelper should be still
	 * lower than the maximum amount of processing possible by its
	 * provider/consumer.
	 */
	private void assignProcessingPower() {
		if (currentUnProcessed > negligableProcessing && unassignedNum > 0) {
			int currlen = unassignedNum;
			for (int i = 0; i < upLen; i++) {
				ResourceConsumption con = underProcessing.get(i);
				con.inassginmentprocess = con.unassigned;
			}
			double currentProcessable = currentUnProcessed;
			double pastProcessable;
			int firstindex = 0;
			int lastindex = upLen;
			do {
				pastProcessable = currentProcessable;
				final double maxShare = currentProcessable / currlen;
				boolean firstIndexNotSetUp = true;
				int newlastindex = -1;
				for (int i = firstindex; i < lastindex; i++) {
					final ResourceConsumption con = underProcessing.get(i);
					if (con.inassginmentprocess) {
						final double limit = con.getProcessingLimit() - con.limithelper;
						if (limit < maxShare) {
							currentProcessable -= limit;
							updateConsumptionLimit(con, limit);
							// we move an unprocessed item from the back here
							// then allow reevaluation
							// and also make sure the currlen is reduced
							con.inassginmentprocess = false;
							currlen--;
						} else {
							newlastindex = i;
							if (firstIndexNotSetUp) {
								firstindex = i;
								firstIndexNotSetUp = false;
							}
							updateConsumptionLimit(con, maxShare);
						}
					}
				}
				lastindex = newlastindex;
			} while (currlen != 0 && pastProcessable != currentProcessable);
		}
	}

	/**
	 * This function is the entrance to the lowest level scheduling in
	 * DISSECT-CF.
	 * 
	 * The function ensures that each resource consumption is assigned a
	 * processing limit and determines what is the resource consumption which
	 * will finish earliest with that particular limit. The earliest completion
	 * time is then returned to the main resource spreading logic of the
	 * simulator.
	 */
	@Override
	protected long singleGroupwiseFreqUpdater() {
		// Phase 1: preparation
		final ResourceSpreader.FreqSyncer syncer = getSyncer();
		final ResourceSpreader[] depgroup = syncer.getDepGroup();
		final int dglen = syncer.getDGLen();
		final int providerCount = syncer.getFirstConsumerId();
		for (int i = 0; i < dglen; i++) {
			((MaxMinFairSpreader) depgroup[i]).initializeFreqUpdate();
		}
		boolean someConsumptionIsStillUnderUtilized;
		// Phase 2: Progressive filling iteration
		do {
			// Phase 2a: determining maximum possible processing
			// Determining wishes for providers and consumers
			for (int i = 0; i < dglen; i++) {
				((MaxMinFairSpreader) depgroup[i]).assignProcessingPower();
			}
			// Phase 2b: Finding minimum between providers and consumers
			double minProcessing = Double.MAX_VALUE;
			for (int i = 0; i < providerCount; i++) {
				final int upLen = depgroup[i].underProcessing.size();
				for (int j = 0; j < upLen; j++) {
					final ResourceConsumption con = depgroup[i].underProcessing.get(j);
					if (con.unassigned) {
						final double currlimit = con.updateRealLimit(false);
						if (currlimit < minProcessing) {
							minProcessing = currlimit;
						}
					}
				}
			}

			// Phase 2c: single filling
			someConsumptionIsStillUnderUtilized = false;
			for (int i = 0; i < providerCount; i++) {
				MaxMinFairSpreader mmfs = (MaxMinFairSpreader) depgroup[i];
				for (int j = 0; j < mmfs.upLen; j++) {
					final ResourceConsumption con = mmfs.underProcessing.get(j);
					if (con.unassigned) {
						con.limithelper += minProcessing;
						final MaxMinFairSpreader counterpart = (MaxMinFairSpreader) mmfs.getCounterPart(con);
						mmfs.currentUnProcessed -= minProcessing;
						counterpart.currentUnProcessed -= minProcessing;
						if (Math.abs(con.getRealLimit() - minProcessing) <= minProcessing * 0.000000001) {
							con.unassigned = false;
							mmfs.unassignedNum--;
							counterpart.unassignedNum--;
						}
					}
				}
				someConsumptionIsStillUnderUtilized |= mmfs.unassignedNum > 0;
			}
		} while (someConsumptionIsStillUnderUtilized);
		// Phase 3: Determining the earliest completion time
		long minCompletionDistance = Long.MAX_VALUE;
		for (int i = 0; i < providerCount; i++) {
			final int upLen = depgroup[i].underProcessing.size();
			for (int j = 0; j < upLen; j++) {
				final ResourceConsumption con = depgroup[i].underProcessing.get(j);
				con.consumerLimit = con.providerLimit = con.limithelper;
				con.updateRealLimit(true);
				final long conDistance = con.getCompletionDistance();
				minCompletionDistance = conDistance < minCompletionDistance ? conDistance : minCompletionDistance;
			}
		}
		return minCompletionDistance;
	}

	/**
	 * Supposed to update the consumer/provider specific consumption details.
	 * 
	 * Allows differentiation between consumers and providers without
	 * conditional statement (instead it requires that this class is subclassed
	 * for both consumers and providers).
	 * 
	 * @param con
	 *            the resource consumption object to be updated with the limit
	 *            value
	 * @param limit
	 *            the limit value to be propagated to the resource consumption
	 *            object's relevant (i.e. provider/consumer specific) field
	 */
	protected abstract void updateConsumptionLimit(final ResourceConsumption con, final double limit);
}
