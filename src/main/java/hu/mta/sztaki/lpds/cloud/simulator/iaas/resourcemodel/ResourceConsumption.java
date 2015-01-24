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

import java.util.Comparator;

public class ResourceConsumption {

	public static final Comparator<ResourceConsumption> limitComparator = new Comparator<ResourceConsumption>() {
		@Override
		public int compare(final ResourceConsumption o1,
				final ResourceConsumption o2) {
			final double upOth = o1.realLimit;
			final double upThis = o2.realLimit;
			return upOth < upThis ? -1 : (upOth == upThis ? 0 : 1);
		}
	};

	public static final double unlimitedProcessing = Double.MAX_VALUE;

	public interface ConsumptionEvent {
		void conComplete();
		void conCancelled(ResourceConsumption problematic);
	}

	private double underProcessing;
	private double toBeProcessed;

	private double processingLimit;
	private double requestedLimit;
	private double hardLimit;
	private final double negligableProcessing;

	private double realLimit;
	private long completionDistance;
	double providerLimit;
	double consumerLimit;
	double limithelper;
	boolean unassigned;
	boolean inassginmentprocess;

	final ConsumptionEvent ev;

	private ResourceSpreader consumer;
	private ResourceSpreader provider;

	private boolean resumable = true;
	private boolean registered = false;

	public ResourceConsumption(final double total, final double limit,
			final ResourceSpreader consumer, final ResourceSpreader provider,
			final ConsumptionEvent e) {
		underProcessing = 0;
		toBeProcessed = total;
		this.negligableProcessing = total / 1000000000;
		this.consumer = consumer;
		this.provider = provider;
		if (e == null) {
			throw new IllegalStateException(
					"Cannot create a consumption without an event to be fired");
		}
		ev = e;
		requestedLimit = limit;
	}

	private void updateHardLimit() {
		final double provLimit = provider == null ? unlimitedProcessing
				: provider.perSecondProcessingPower;
		final double conLimit = consumer == null ? unlimitedProcessing
				: consumer.perSecondProcessingPower;
		hardLimit = requestedLimit < provLimit ? requestedLimit : provLimit;
		if (hardLimit > conLimit) {
			hardLimit = conLimit;
		}
		setProcessingLimit(requestedLimit);
		realLimit = hardLimit;
	}

	public boolean registerConsumption() {
		if (!registered) {
			if (getUnProcessed() == 0) {
				ev.conComplete();
				return true;
			} else if (resumable && provider != null && consumer != null) {
				updateHardLimit();
				if (ResourceSpreader.registerConsumption(this)) {
					registered = true;
					return true;
				}
			}
		}
		return false;
	}

	public double getUnProcessed() {
		return underProcessing + toBeProcessed;
	}

	public void cancel() {
		suspend();
		resumable = false;
	}

	public void suspend() {
		if (registered) {
			ResourceSpreader.cancelConsumption(this);
			registered = false;
		}
	}

	public void setProvider(final ResourceSpreader provider) {
		if (!registered) {
			this.provider = provider;
			updateHardLimit();
			return;
		} else {
			throw new IllegalStateException(
					"Attempted consumer change with a registered consumption");
		}
	}

	public void setConsumer(final ResourceSpreader consumer) {
		if (!registered) {
			this.consumer = consumer;
			updateHardLimit();
			return;
		} else {
			throw new IllegalStateException(
					"Attempted consumer change with a registered consumption");
		}
	}

	private void calcCompletionDistance() {
		final double freqFl = getUnProcessed() * 1000 / realLimit;
		final long freqFix = (long) freqFl;
		completionDistance = freqFl == freqFix ? freqFix : freqFix + 1;
	}

	double doProviderProcessing(final double secondsPassed) {
		double processed = 0;
		if (toBeProcessed > 0) {
			final double possiblePush = secondsPassed * realLimit;
			processed = possiblePush < toBeProcessed ? possiblePush
					: toBeProcessed;
			toBeProcessed -= processed;
			underProcessing += processed;
			if (toBeProcessed <= negligableProcessing) {
				processed += toBeProcessed;
				toBeProcessed = 0;
				return -processed;
			}
		}
		return processed;
	}

	double doConsumerProcessing(final double secondsPassed) {
		double processed = 0;
		if (underProcessing > 0) {
			final double possibleProcessing = secondsPassed * realLimit;
			processed = possibleProcessing < underProcessing ? possibleProcessing
					: underProcessing;
			underProcessing -= processed;
			calcCompletionDistance();
			if (getUnProcessed() <= negligableProcessing) {
				processed += underProcessing;
				underProcessing = 0;
				return -processed;
			}
		}
		return processed;
	}

	public double getToBeProcessed() {
		return toBeProcessed;
	}

	public double getUnderProcessing() {
		return underProcessing;
	}

	double setProcessingLimit(final double pl) {
		if(pl < hardLimit) {
			processingLimit = pl;
			return 0;
	}
		processingLimit = hardLimit;
		return pl - hardLimit;
	}

	public double getProcessingLimit() {
		return processingLimit;
	}

	public double getRealLimitPerSecond() {
		return realLimit;
	}

	public long getCompletionDistance() {
		return completionDistance;
	}

	public ResourceSpreader getConsumer() {
		return consumer;
	}

	public ResourceSpreader getProvider() {
		return provider;
	}

	double updateRealLimit() {
		realLimit = providerLimit < consumerLimit ? providerLimit
				: consumerLimit;
		if (realLimit == 0) {
			throw new IllegalStateException(
					"Cannot calculate the completion distance for a consumption without a real limit! "
							+ this);
		}
		calcCompletionDistance();
		return realLimit;
	}

	@Override
	public String toString() {
		return "RC(C:" + underProcessing + " T:" + toBeProcessed + " L:"
				+ realLimit + ")";
	}

	public boolean isRegistered() {
		return registered;
	}
	
	public boolean isResumable() {
		return resumable;
	}

	public double getHardLimit() {
		return hardLimit;
	}
}
