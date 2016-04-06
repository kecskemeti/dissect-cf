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
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

/**
 * This class is part of the unified resource consumption model of DISSECT-CF.
 * 
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
 * not supposed to be used outside of the context of the Unified resource
 * consumption model of DISSECT-CF. Instead one should use:
 * VirtualMachine.newComputeTask(), NetworkNode.initTransfer() and similar
 * functions.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University of Innsbruck (c) 2013"
 * 
 */
public class ResourceConsumption {

	/**
	 * This comparator class provides a simple comparison tool for two resource
	 * consumptions based on their real limits. Useful for sorting the
	 * consumptions.
	 */
	public static final Comparator<ResourceConsumption> limitComparator = new Comparator<ResourceConsumption>() {
		@Override
		public int compare(final ResourceConsumption o1, final ResourceConsumption o2) {
			final double upOth = o1.realLimit;
			final double upThis = o2.realLimit;
			return upOth < upThis ? -1 : (upOth == upThis ? 0 : 1);
		}
	};

	/**
	 * If a resource consumption is not supposed to be limited by anything but
	 * the actual resource providers/consumers then this limit could be used in
	 * its constructor.
	 */
	public static final double unlimitedProcessing = Double.MAX_VALUE;

	/**
	 * This interface allows its implementors to get notified when a consumption
	 * completes. Note: the objects will only receive a single call on the below
	 * interfaces depending on the outcome of the resouece consumption's
	 * execution.
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
		 * handled properly - if a consumption is suspended (allowing its
		 * migration to some other consumers/providers then this function is
		 * <b>not</b> called.
		 */
		void conCancelled(ResourceConsumption problematic);
	}
	
	/**
	 * Represents the current state of the consumption or null if the current state has
	 * not been accessed yet.
	 */
	private ConsumptionState state;
	
	/**
	 * This class contains the necessary information to recreate a consumption that 
	 * behaves exactly the same way as this consumption, together with the other
	 * spreaders and consumptions in the dependency group.
	 * 
	 * TODO: Currently restoring always creates a new object. Make restoring
	 *       possible into already existing objects, too.
	 */
	public static class ConsumptionState {
		
		/**
		 * The amount of processing to be done during the lifetime of the
	     * restored object
		 */
		private double total;
		
		/**
		 * the requestedLimit of the original consumption
		 */
		private double requestedLimit;
		
		/**
		 * the provider of the original consumpion
		 */
		private ResourceSpreader.SpreaderState provider;
		
		/**
		 * the consumer of the original consumption
		 */
		private ResourceSpreader.SpreaderState consumer;
		
		/**
		 * the ev field of the parent
		 */
		private ConsumptionEvent ev;
		
		/**
		 * Shows whether the original consumption was registered or not
		 */
		private boolean registered;
		
		/**
		 * Shows whether the original consumption is resumable or not
		 */
		private boolean resumable;
		
		/**
		 * this helper field is used when restoring the consumption, and will
		 * contain the restored consumption
		 */
		private ResourceConsumption consumption;
		
		/**
		 * Create a ConsumptionState instance that represents the current
		 * state of the given consumption object.
		 * 
		 * This constructor should only be called from the getConsumptionState
		 * method of the original consumption, since the provider and consumer
		 * will be set from that method, to avoid infinite recursion when
		 * resetting them.
		 * 
		 * @param consumption
		 *            the ResourceConsumption instance whose actual state we
		 *            want to get.
		 */
		private ConsumptionState(ResourceConsumption consumption) {
			
			resumable = consumption.resumable;
			registered = consumption.registered;
			total = consumption.getUnProcessed();
			requestedLimit = consumption.requestedLimit;
			ev = consumption.ev;
				
		}
		
		/**
		 * Restore this consumption into a ResourceConsumption instance
		 * (and restore every other consumption and spreader in the 
		 * dependency group) 
		 * 
		 * @param restoreEvent 
		 *            set to true to use the same ConsumerEvent instance as
		 *            the original consumption for every consumption in the
		 *            dependency group
		 * 
		 * @return the new ResourceConsumption instance which will behave the same
		 *         way as the original one
		 */
		public ResourceConsumption restore(boolean restoreEvent) {
			if (consumption != null) {
				return consumption;
			}
			
			ConsumptionEvent event = restoreEvent ? ev : new ConsumptionEventAdapter();
			consumption = new ResourceConsumption(
					total,requestedLimit,null,null,event);
			
			if (provider != null) {
				consumption.setProvider(provider.restore(restoreEvent));
			}
			
			if (consumer != null) {
				consumption.setConsumer(consumer.restore(restoreEvent));
			}	
			
			consumption.resumable = resumable;
			if (registered) {
				consumption.registerConsumption();
			}
			
			consumption.updateHardLimit();
			
			return consumption;
		}
		
	}

	/**
	 * The currently processing entities (e.g., a network buffer)
	 * 
	 * <i>NOTE:</i> as this consumption is generic, it is actually the
	 * provider/consumer pair that determines what is the unit of this field
	 */
	private double underProcessing;
	/**
	 * The remaining unprocessed entities (e.g., remaining bytes of a transfer)
	 *
	 * <i>NOTE:</i> as this consumption is generic, it is actually the
	 * provider/consumer pair that determines what is the unit of this field
	 */
	private double toBeProcessed;

	/**
	 * the maximum amount of processing that could be sent from toBeProcessed to
	 * underProcessing in a tick
	 */
	private double processingLimit;
	/**
	 * the user requested processing limit
	 */
	private double requestedLimit;
	/**
	 * the minimum of the perTickProcessing power of the provider/consumer
	 */
	private double hardLimit;
	/**
	 * The processing limit at the particular moment of time (this is set by the
	 * scheduler)
	 * 
	 * This limit is derived from the provider/consumerLimits.
	 * 
	 * <i>WARNING:</i> this is necessary for the internal behavior of
	 * MaxMinFairSpreader
	 */
	private double realLimit;
	/**
	 * 1/2*realLimit
	 */
	private double halfRealLimit;
	/**
	 * The number of ticks it is expected to take that renders both
	 * underProcessing and toBeProcessed as 0 (i.e., the time when the initially
	 * specified amount of resources are completely utilized).
	 */
	private long completionDistance;
	/**
	 * The processing limit imposed because of the provider
	 * 
	 * <i>WARNING:</i> this is necessary for the internal behavior of
	 * MaxMinFairSpreader
	 */
	double providerLimit;
	/**
	 * the processing limit imposed because of the consumer
	 * 
	 * <i>WARNING:</i> this is necessary for the internal behavior of
	 * MaxMinFairSpreader
	 */
	double consumerLimit;
	/**
	 * the amount of processing that can be surely done by both the provider and
	 * the consumer. This is a temporary variable used by the MaxMinFairSpreader
	 * to determine the provider/consumerLimits.
	 * 
	 * <i>WARNING:</i> this is necessary for the internal behavior of
	 * MaxMinFairSpreader
	 */
	double limithelper;
	/**
	 * A helper field to show if the particular resource consumption still
	 * participates in the scheduling process or if it has already finalized its
	 * realLimit value.
	 * 
	 * <i>WARNING:</i> this is necessary for the internal behavior of
	 * MaxMinFairSpreader
	 */
	boolean unassigned;
	/**
	 * A helper field to show if the consumer/providerLimit fields are under
	 * update by MaxMinFairSpreader.assignProcessingPower()
	 * 
	 * <i>WARNING:</i> this is necessary for the internal behavior of
	 * MaxMinFairSpreader
	 */
	boolean inassginmentprocess;

	/**
	 * The event to be fired when there is nothing left to process in this
	 * consumption.
	 */
	final ConsumptionEvent ev;

	/**
	 * The consumer which receives the resources of this consumption.
	 * 
	 * If null, then the consumer must be set before proper operation of the
	 * consumption. As this can be null the resource consumption objects could
	 * be created in multiple phases allowing to first determine the amount of
	 * consumption to be made before actually assigning to a particular
	 * consumer.
	 */
	private ResourceSpreader consumer;
	/**
	 * The provider which offers the resources for this consumption.
	 * 
	 * If null, then the provider must be set before proper operation of the
	 * consumption. As this can be null the resource consumption objects could
	 * be created in multiple phases allowing to first determine the amount of
	 * consumption to be made before actually assigning to a particular
	 * provider.
	 */
	private ResourceSpreader provider;

	/**
	 * shows if the consumption was suspended (<i>true</i>) or not.
	 */
	private boolean resumable = true;
	/**
	 * shows if the consumption object actually participates in the resource
	 * sharing machanism.
	 */
	private boolean registered = false;

	/**
	 * This constructor describes the basic properties of an individual resource
	 * consumption.
	 * 
	 * @param total
	 *            The amount of processing to be done during the lifetime of the
	 *            just created object
	 * @param limit
	 *            the maximum amount of processing allowable for this particular
	 *            resource consumption (this allows the specification of an
	 *            upper limit of any consumption). If there is no upper limit
	 *            needed then this value should be set with the value of the
	 *            unlimitedProcessing field.
	 * @param consumer
	 *            the consumer that will benefit from the resource consumption.
	 *            This field could be null, then the consumer must be set with
	 *            the setConsumer() function.
	 * @param provider
	 *            the provider which will offer its resources for the consumer.
	 *            This field could be null, then the provider must be set with
	 *            the setProvider() function.
	 * @param e
	 *            Specify here the event to be fired when the just created
	 *            object completes its transfers. With this event it is possible
	 *            to notify the entity who initiated the transfer. This event
	 *            object cannot be null. If there is no special event handling
	 *            is needed then just create a ConsumptionEventAdapter.
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

	/**
	 * Provides a unified update method for the hard processing limit (which
	 * will become the minimum of the provider's/consumer's per tick processing
	 * power) of this consumption. All values that depend on hard limit (the
	 * real and processing limits) are also updated.
	 * 
	 * This function can be called even if the provider/consumer is not set yet
	 * in that case the processing limit for the non-set spreader will be
	 * unlimited.
	 */
	private void updateHardLimit() {
		final double provLimit = provider == null ? unlimitedProcessing : provider.perTickProcessingPower;
		final double conLimit = consumer == null ? unlimitedProcessing : consumer.perTickProcessingPower;
		hardLimit = requestedLimit < provLimit ? requestedLimit : provLimit;
		if (hardLimit > conLimit) {
			hardLimit = conLimit;
		}
		processingLimit = requestedLimit < hardLimit ? requestedLimit : hardLimit;
		setRealLimit(hardLimit);
	}

	/**
	 * Initiates the processing of a resource consumption. By calling this
	 * function the resource consumption object will be participating in the
	 * unified resource sharing mechanism's scheduling and spreading operations.
	 * 
	 * Before the registration actually happens, it updates the hard limit of
	 * the consumption so the spreaders could now operate using its values.
	 * 
	 * <i>NOTE:</i> this function is also used for resuming a suspended
	 * consumption
	 * 
	 * @return
	 * 		<ul>
	 *         <li><i>true</i> if the registration was successful
	 *         <li><i>false</i> otherwise. For example: if the provider/consumer
	 *         is not yet set, if the consumption cannot be registered between
	 *         the particular provider/consumer pair or if the consumption was
	 *         already registered.
	 *         </ul>
	 */
	public boolean registerConsumption() {
		if (!registered) {
			if (getUnProcessed() == 0) {
				ev.conComplete();
				return true;
			} else if (resumable && provider != null && consumer != null) {
				updateHardLimit();
				if (ResourceSpreader.registerConsumption(this)) {
					registered = true;
					invalidateState();
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns the amount of processing still remaining in this resource
	 * consumption.
	 * 
	 * @return the remaining processing
	 */
	public double getUnProcessed() {
		return underProcessing + toBeProcessed;
	}

	/**
	 * terminates the consumption, deregisters it from its consumer/provider
	 * pair and ensures that it can no longer be registered
	 */
	public void cancel() {
		suspend();
		resumable = false;
		invalidateState();
	}

	/**
	 * Terminates the consumption but ensures that it will be resumable later
	 * on. If a consumption needs to be resumed then it must be re-registered,
	 * there is no special function for resume.
	 */
	public void suspend() {
		if (registered) {
			ResourceSpreader.cancelConsumption(this);
			registered = false;
			invalidateState();
		}
	}

	/**
	 * Allows to set a provider for the consumption if the consumption is not
	 * yet under way.
	 * 
	 * @param provider
	 *            the provider to be used for offering the resources for the
	 *            consumption
	 */
	public void setProvider(final ResourceSpreader provider) {
		if (!registered) {
			this.provider = provider;
			updateHardLimit();
			invalidateState();
			return;
		} else {
			throw new IllegalStateException("Attempted consumer change with a registered consumption");
		}
	}

	/**
	 * Allows to set a consumer for the consumption if the consumption is not
	 * yet under way.
	 * 
	 * @param consumer
	 *            the consumer to be used for utilizing the resources received
	 *            through this resource consumption object.
	 */
	public void setConsumer(final ResourceSpreader consumer) {
		if (!registered) {
			this.consumer = consumer;
			updateHardLimit();
			invalidateState();
			return;
		} else {
			throw new IllegalStateException("Attempted consumer change with a registered consumption");
		}
	}

	/**
	 * Updates the completion distance field, should be called every time the
	 * real limit is updated or when the amount of unprocessed consumption
	 * changes.
	 */
	private void calcCompletionDistance() {
		completionDistance = Math.round(getUnProcessed() / realLimit);
	}

	/**
	 * This function simulates how the provider offers the resources for its
	 * consumer. The offered resources are put in the underprocessing field from
	 * the toBeprocessed.
	 * 
	 * If the processing is really close to completion (determined by using
	 * halfreallimit), then this function cheats a bit and offers the resources
	 * for the last remaining processable consumption. This is actually ensuring
	 * that we don't need to simulate sub-tick processing operations.
	 * 
	 * <i>WARNING:</i> this is necessary for the internal behavior of
	 * MaxMinFairSpreader
	 * 
	 * @param ticksPassed
	 *            the number of ticks to be simulated (i.e. how many times we
	 *            should multiply realLimit) before offering the resources to
	 *            the underprocessing field.
	 * @return the amount of resources actually offered for consumption.
	 *         Negative values mark the end of this resource consumption (i.e.
	 *         when there is no more processing to be done for this
	 *         consumption). Albeit such values are negative, their negativeness
	 *         is just used as a flag and their absolute value still represent
	 *         the amount of offered resources.
	 */
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

	/**
	 * This function simulates how the consumer utilizes the resources from its
	 * provider. The utilized resources are used from the underprocessing field.
	 * 
	 * If the processing is really close to completion (determined by
	 * calculating the completion distance), then this function cheats a bit and
	 * utilizes the resources for the last remaining underprocessing. This is
	 * actually ensuring that we don't need to simulate sub-tick processing
	 * operations.
	 * 
	 * <i>WARNING:</i> this is necessary for the internal behavior of
	 * MaxMinFairSpreader
	 * 
	 * @param ticksPassed
	 *            the number of ticks to be simulated (i.e. how many times we
	 *            should multiply realLimit) before utilizing the resources from
	 *            the underprocessing field.
	 * @return the amount of resources actually utilized by the consumer.
	 *         Negative values mark the end of this resource consumption (i.e.
	 *         when there is no more processing to be done for this
	 *         consumption). Albeit such values are negative, their negativeness
	 *         is just used as a flag and their absolute value still represent
	 *         the amount of utilized resources.
	 */
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
			invalidateState();
		}
		return processed;
	}

	/**
	 * Determines the amount of processing for which no resources were offered
	 * from the provider so far.
	 * 
	 * @return the tobeprocessed value
	 */
	public double getToBeProcessed() {
		return toBeProcessed;
	}

	/**
	 * Determines the amount of resoruces already offered by the provider but
	 * not yet used by the consumer.
	 * 
	 * @return the underprocessing value
	 */
	public double getUnderProcessing() {
		return underProcessing;
	}

	/**
	 * Retrieves the maximum amount of processing that could be sent from
	 * toBeProcessed to underProcessing in a single tick
	 */

	public double getProcessingLimit() {
		return processingLimit;
	}

	/**
	 * Retrieves the processing limit at the particular moment of time (just
	 * queries the value last set by the scheduler)
	 */
	public double getRealLimit() {
		return realLimit;
	}

	/**
	 * Retrieves the number of ticks it is expected to take that renders both
	 * underProcessing and toBeProcessed as 0 (i.e., the time when the initially
	 * specified amount of resources are completely utilized). This is again
	 * just the value that is derived from the real limit last set by the
	 * scheduler.
	 */
	public long getCompletionDistance() {
		return completionDistance;
	}

	/**
	 * Queries the consumer associated with this resource consumption.
	 * 
	 * @return the consumer which will utilize the resources received through
	 *         this consumption object
	 */
	public ResourceSpreader getConsumer() {
		return consumer;
	}

	/**
	 * Queries the provider associated with this resource consumption.
	 * 
	 * @return the provider which will offer the resources for this particular
	 *         resource consumption object.
	 */
	public ResourceSpreader getProvider() {
		return provider;
	}

	/**
	 * Simultaneously updates the real limit (the instantaneous processing limit
	 * determined by the low level scheduler of the unified resoruce sharing
	 * model of DISSECT-CF) value as well as the halfreallimit field.
	 * 
	 * @param rL
	 *            the value to be set as real limit
	 */
	private void setRealLimit(final double rL) {
		realLimit = rL;
		halfRealLimit = rL / 2;
	}

	/**
	 * Sets the real limit based on the scheduler set provider and consumer
	 * limits (the smaller is used as real).
	 * 
	 * Updates the completion distance if instructed.
	 * 
	 * @param updateCD
	 *            <i>true</i> tells the system to update the completion distance
	 *            alongside the real limit setup. This more frequent update on
	 *            the real limit than the completion distance is calculated.
	 * 
	 *            <i>IMPORTANT:</i> if set to false then it is expected that the
	 *            scheduler will call the updateRealLimit at least once more
	 *            with a true parameter. Failing to do so the consumption object
	 *            will become broken.
	 * @return the real limit that was actually determined and set by this
	 *         function
	 * @throws IllegalStateException
	 *             if the real limit would become 0
	 */
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
	
	/**
	 * This method must be called after changes to this consumption are made.
	 * (including processing the given resources)
	 * It will ensure that the state member never contains an object that 
	 * does not represent the current state of the consumption.
	 */
	protected void invalidateState() {
		if (state == null) {
			return;
		}
		/* 
		 * Recursively invalidate every other spreader and consumption in the
		 * dependency group.
		 */
		state = null;
		if (provider != null) {
			provider.invalidateState();
		} 
		if (consumer != null) {
			consumer.invalidateState();
		}
	}
	
	/**
	 * Returns the ConsumptionState which represents this consumption at the current
	 * time instance.
	 * 
	 * @throws IllegalStateException if called during the processing cycle
	 * @return the state of this consumption
	 */
	public ConsumptionState getConsumptionState() throws IllegalStateException {
		if (provider != null) {
			provider.updateState();
		}
		if (state == null) {
			state = new ConsumptionState(this);
			if (provider != null) {
				state.provider = provider.getSpreaderState();
			}			
			if (consumer != null) {
				state.consumer = consumer.getSpreaderState();
			}		
		}
		return state;
	}

	/**
	 * Provides a nice formatted output of the resource consumption showing how
	 * much processing is under way, how much is still held back and what is the
	 * current real limit set by the scheduler.
	 * 
	 * Intended for debugging and tracing outputs.
	 */
	@Override
	public String toString() {
		return "RC(C:" + underProcessing + " T:" + toBeProcessed + " L:" + realLimit + ")";
	}

	/**
	 * Determines if the object is registered and resources are used because of
	 * this consumption object
	 * 
	 * @return <i>true</i> if the object is registered.
	 */
	public boolean isRegistered() {
		return registered;
	}

	/**
	 * Allows to query whether this resource consumption was cancelled or not
	 * 
	 * @return <i>false</i> if the resource consumption was cancelled and it can
	 *         no longer be registered within the unified resource sharing model
	 *         of the simulator.
	 */
	public boolean isResumable() {
		return resumable;
	}

	/**
	 * Determines the hard processing limit for this resource consumption.
	 * 
	 * @return the hard limit
	 */
	public double getHardLimit() {
		return hardLimit;
	}
}
