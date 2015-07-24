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

/**
 * The instances of this class represent an individual resource consumption in
 * the system. The visibility of the class and its members are defined so the
 * compiler does not need to generate access methods for the members thus
 * allowing fast and prompt changes in its contents.
 * 
 * Upon setting up the consumption, every instance is registered at the provider
 * and the consumer resource spreaders. Once the consumption is completed these
 * registrations should be dropped.
 * 
 * WARNING this is an internal representation of the consumption. This class is
 * not supposed to be used outside of the context of the ResourceModel.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 * 
 */
public class ResourceConsumption {

	public static final Comparator<ResourceConsumption> limitComparator = new Comparator<ResourceConsumption>() {
		@Override
		public int compare(final ResourceConsumption o1, final ResourceConsumption o2) {
			final double upOth = o1.realLimit;
			final double upThis = o2.realLimit;
			return upOth < upThis ? -1 : (upOth == upThis ? 0 : 1);
		}
	};

	public static final double unlimitedProcessing = Double.MAX_VALUE;

	/**
	 * This interface allows its implementors to get notified when a consumption
	 * completes.
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
	 * 
	 */
	public interface ConsumptionEvent {
		/**
		 * This function is called when the resource consumption represented by
		 * the ResourceConsumption object is fulfilled
		 */
		void conComplete();

		/**
		 * This function is called when the resource consumption cannot be
		 * handled properly
		 */

		void conCancelled(ResourceConsumption problematic);
	}

	/**
	 * The currently processing entities (e.g., a network buffer)
	 */
	private double underProcessing;
	/**
	 * The remaining unprocessed entities (e.g., remaining bytes of a transfer)
	 */
	private double toBeProcessed;

	private double processingLimit;
	private double requestedLimit;
	private double hardLimit;

	private double realLimit;
	private double halfRealLimit;
	private long completionDistance;
	double providerLimit;
	double consumerLimit;
	double limithelper;
	boolean unassigned;
	boolean inassginmentprocess;

	/**
	 * The event to be fired when there is nothing left to process in this
	 * consumption. If this variable is null then the event was already fired.
	 */
	final ConsumptionEvent ev;

	private ResourceSpreader consumer;
	private ResourceSpreader provider;

	private boolean resumable = true;
	private boolean registered = false;

	/**
	 * This constructor describes the basic properties of an individual resource
	 * consumption.
	 * 
	 * @param total
	 *            The amount of processing to be done during the lifetime of the
	 *            just created object
	 * @param e
	 *            Specify here the event to be fired when the just created
	 *            object completes its transfers. With this event it is possible
	 *            to notify the entity who initiated the transfer.
	 */
	public ResourceConsumption(final double total, final double limit, final ResourceSpreader consumer,
			final ResourceSpreader provider, final ConsumptionEvent e) {
		underProcessing = 0;
		toBeProcessed = total;
		this.consumer = consumer;
		this.provider = provider;
		if (e == null) {
			throw new IllegalStateException("Cannot create a consumption without an event to be fired");
		}
		ev = e;
		requestedLimit = limit;
	}

	private void updateHardLimit() {
		final double provLimit = provider == null ? unlimitedProcessing : provider.perTickProcessingPower;
		final double conLimit = consumer == null ? unlimitedProcessing : consumer.perTickProcessingPower;
		hardLimit = requestedLimit < provLimit ? requestedLimit : provLimit;
		if (hardLimit > conLimit) {
			hardLimit = conLimit;
		}
		setProcessingLimit(requestedLimit);
		setRealLimit(hardLimit);
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
			throw new IllegalStateException("Attempted consumer change with a registered consumption");
		}
	}

	public void setConsumer(final ResourceSpreader consumer) {
		if (!registered) {
			this.consumer = consumer;
			updateHardLimit();
			return;
		} else {
			throw new IllegalStateException("Attempted consumer change with a registered consumption");
		}
	}

	private void calcCompletionDistance() {
		completionDistance = Math.round(getUnProcessed() / realLimit);
	}

	double doProviderProcessing(final long ticksPassed) {
		double processed = 0;
		if (toBeProcessed > 0) {
			final double possiblePush = ticksPassed * realLimit;
			processed = possiblePush < toBeProcessed ? possiblePush : toBeProcessed;
			toBeProcessed -= processed;
			underProcessing += processed;
			if (toBeProcessed < halfRealLimit) {
				// ensure that tobeprocessed is 0!
				processed += toBeProcessed;
				underProcessing += toBeProcessed;
				toBeProcessed = 0;
				return -processed;
			}
		}
		return processed;
	}

	double doConsumerProcessing(final long ticksPassed) {
		double processed = 0;
		if (underProcessing > 0) {
			final double possibleProcessing = ticksPassed * realLimit;
			processed = possibleProcessing < underProcessing ? possibleProcessing : underProcessing;
			underProcessing -= processed;
			calcCompletionDistance();
			if (completionDistance == 0) {
				// ensure that tobeprocessed is 0!
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
		processingLimit = pl < hardLimit ? pl : hardLimit;
		return pl - processingLimit;
	}

	public double getProcessingLimit() {
		return processingLimit;
	}

	public double getRealLimit() {
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

	private void setRealLimit(final double rL) {
		realLimit = rL;
		halfRealLimit = rL / 2;
	}

	double updateRealLimit(final boolean updateCD) {
		final double rlTrial = providerLimit < consumerLimit ? providerLimit : consumerLimit;
		if (rlTrial == 0) {
			throw new IllegalStateException(
					"Cannot calculate the completion distance for a consumption without a real limit! " + this);
		}
		setRealLimit(rlTrial);
		if (updateCD) {
			calcCompletionDistance();
		}
		return realLimit;
	}

	@Override
	public String toString() {
		return "RC(C:" + underProcessing + " T:" + toBeProcessed + " L:" + realLimit + ")";
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
