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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.statenotifications.PowerStateChangeNotificationHandler;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.StateDependentEventHandler;
import hu.mta.sztaki.lpds.cloud.simulator.util.ArrayHandler;

/**
 * This class is part of the unified resource consumption model of DISSECT-CF.
 * 
 * This class provides a foundation for ensuring equal access to resource
 * limited devices (such as network interfaces, cpus or disks) for all ongoing
 * resource consumptions.
 * 
 * Resource processing is actually handled by the processSingleConsumption
 * function which must be implemented externally for performance. This allows
 * the resource consumption simulation code to run with less conditional
 * statements in its core. While it also allows to efficiently add new features
 * later on.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory of Parallel and
 *         Distributed Systems, MTA SZTAKI (c) 2012"
 * 
 */
public abstract class ResourceSpreader {

	// These final variables define the base behavior of the class:
	/**
	 * Maximum amount of resources to be shared among the consumption objects
	 * during a single tick.
	 */
	protected double perTickProcessingPower;
	/**
	 * For floating point operations using the perTickProcessingPower this
	 * defines the precision, ie. the amount of processing to be considered 0.
	 * It is defined to be 1 billionth of the perTickProcessingPower.
	 */
	protected double negligableProcessing;
	/**
	 * The array of consumption objects that will share the processing power of
	 * this spreader. The order is not guaranteed!
	 */
	private final ArrayList<ResourceConsumption> toProcess = new ArrayList<ResourceConsumption>();
	/**
	 * The unalterable array of resource consumption objects.
	 */
	public final List<ResourceConsumption> underProcessing = Collections.unmodifiableList(toProcess);
	/**
	 * the length of the list of toProcess. This is updated for performance
	 * reasons.
	 */
	int underProcessingLen = 0;
	/**
	 * The influence group in which this resource spreader belongs.
	 */
	private FreqSyncer mySyncer = null;
	/**
	 * The resource consumptions that got registered to this spreader in the
	 * last tick
	 */
	private ArrayList<ResourceConsumption> underAddition = new ArrayList<ResourceConsumption>();
	/**
	 * The resource consumptions that got deregistered from this spreader in the
	 * last tick
	 */
	private ArrayList<ResourceConsumption> underRemoval = new ArrayList<ResourceConsumption>();
	/**
	 * Public, unmodifiable list of just registered resource consumptions
	 */
	public final List<ResourceConsumption> toBeRemoved = Collections.unmodifiableList(underRemoval);
	/**
	 * Public, unmodifiable list of just deregistered resource consumptions
	 */
	public final List<ResourceConsumption> toBeAdded = Collections.unmodifiableList(underAddition);

	/**
	 * The power behavior that currently models the resource spreader's energy
	 * characteristics.
	 */
	private PowerState currentPowerBehavior;
	/**
	 * This is the notification handler object that belongs to this particular
	 * resource spreader. Resource spreaders use this object to handle power
	 * state related events.
	 */
	private StateDependentEventHandler<PowerBehaviorChangeListener, Pair<ResourceSpreader, PowerState>> powerBehaviorListenerManager;

	/**
	 * The last time there were some processing operations done by this object.
	 * The notification time is used to stop infinite loops in the doProcessing
	 * function.
	 */
	protected long lastNotifTime = 0;
	/**
	 * Shows how much processing this spreader done in its lifetime.
	 */
	private double totalProcessed = 0;
	/**
	 * A helper field that allows the rapid discovery of influence groups by the
	 * group's freq syncer object
	 */
	private boolean stillInDepGroup;

	/**
	 * This class is the core part of the unified resource consumption model of
	 * DISSECT-CF.
	 * 
	 * The main purpose of this class is to create and manage influence groups
	 * from resource spreaders that are connected with resource consumptions.
	 * Also the class is also expected to coordinate the processing of the
	 * resource consumptions within the entire influence group. Finally, the
	 * class is responsible to deliver the completion or failure events for the
	 * resource consumptions deregistered from the influence group's resource
	 * spreaders.
	 * 
	 * The name FreqSyncer comes from the class's primary goal, identify the
	 * earliest time there is a change in the scheduling (e.g., because a new
	 * resource consumption is added to the group or because one of the
	 * consumptions complete), and then make sure that all spreaders in the
	 * influence group receive timing events at the same time instance.
	 * 
	 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group,
	 *         University of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory
	 *         of Parallel and Distributed Systems, MTA SZTAKI (c) 2015"
	 *
	 */
	public static class FreqSyncer extends Timed {
		/**
		 * The influence group managed by this freqsyncer object.
		 * 
		 * myDepGroup is always kept in order: first all the providers are
		 * listed, then all the consumers, when dealing with this data member
		 * please keep in mind this expected behavior. At the end of the array
		 * we have a padding with null items.
		 * 
		 * Contents:
		 * <ul>
		 * <li>[0,firstConsumerId[ providers
		 * <li>[firstConsumerId,depgrouplen[ consumers
		 * <li>[depgrouplen,myDepGroup.length[ padding with null items
		 * </ul>
		 */
		private ResourceSpreader[] myDepGroup;
		/**
		 * The current length of the myDepGroup array. This is the actual length
		 * without counting the padding at the end of the java array.
		 */
		private int depgrouplen;
		/**
		 * The index of the first consumer in the myDepGroup array.
		 */
		private int firstConsumerId;
		/**
		 * those resource spreaders that need to be added to the influence group
		 * at the particular time instance
		 */
		private final ArrayList<ResourceSpreader> depGroupExtension = new ArrayList<ResourceSpreader>();
		/**
		 * if there are some external activities that could lead to influence
		 * group changes this field will be turned to true
		 * 
		 * the tick function then ensures it to return to false once it has
		 * completed its management operations on the influence groups
		 */
		private boolean nudged = false;
		/**
		 * if the freqsyncer identifies the need to fire events with 0 frequency
		 * then it turns this mode off. This allows
		 * ResourceSpreader.doProcessing to happen multiple times in a single
		 * time instance.
		 */
		private boolean regularFreqMode = true;

		/**
		 * Constructor of a freqsyncer to be used when neither the provider nor
		 * the consumer of a resource consumption belongs to an already existing
		 * influence group.
		 * 
		 * @param provider
		 *            the provider to be added to the initial influence group
		 * @param consumer
		 *            the consumer to be added to the initial influence group
		 */
		private FreqSyncer(final ResourceSpreader provider, final ResourceSpreader consumer) {
			myDepGroup = new ResourceSpreader[2];
			myDepGroup[0] = provider;
			myDepGroup[1] = consumer;
			firstConsumerId = 1;
			depgrouplen = 2;
			provider.mySyncer = consumer.mySyncer = this;
			setBackPreference(true);
		}

		/**
		 * The constructor to be used when a new influence group needs to be
		 * created because the original group got fragmented.
		 * 
		 * @param myDepGroup
		 *            the group members to take part in the new influence group
		 * @param provcount
		 *            the number of providers in the group
		 * @param dglen
		 *            the length of the influence group
		 */
		private FreqSyncer(ResourceSpreader[] myDepGroup, final int provcount, final int dglen) {
			this.myDepGroup = myDepGroup;
			firstConsumerId = provcount;
			depgrouplen = dglen;
			for (int i = 0; i < dglen; i++) {
				myDepGroup[i].mySyncer = this;
			}
			setBackPreference(true);
		}

		/**
		 * Allows a single spreader object to be added to the influence group.
		 * 
		 * <i>WARNING:</i> Should only be used from addToGroup, as there are
		 * some management operations that only occure there for performance
		 * reasons
		 * 
		 * @param rs
		 *            the spreader to be added to the influence group
		 */
		private void addSingleToDG(final ResourceSpreader rs) {
			try {
				myDepGroup[depgrouplen] = rs;
				depgrouplen++;
				rs.mySyncer = this;
			} catch (ArrayIndexOutOfBoundsException e) {
				ResourceSpreader[] newdg = new ResourceSpreader[myDepGroup.length * 7];
				System.arraycopy(myDepGroup, 0, newdg, 0, depgrouplen);
				myDepGroup = newdg;
				addSingleToDG(rs);
			}
		}

		/**
		 * This function copies the contents of the depGroupExtension list to
		 * the array representing the influence group and ensures that all newly
		 * added members of the influence group know their group membership.
		 */
		private void addToGroup() {
			int size = depGroupExtension.size();
			for (int i = 0; i < size; i++) {
				final ResourceSpreader rs = depGroupExtension.get(i);
				if (isInDepGroup(rs))
					continue;
				if (rs.isConsumer()) {
					addSingleToDG(rs);
				} else {
					if (firstConsumerId >= depgrouplen) {
						addSingleToDG(rs);
					} else {
						addSingleToDG(myDepGroup[firstConsumerId]);
						myDepGroup[firstConsumerId] = rs;
					}
					firstConsumerId++;
				}
				rs.mySyncer = this;
			}
		}

		/**
		 * Determines if the spreader in question is part of the current
		 * influence group or not
		 * 
		 * @param lookfor
		 *            the spreader in question
		 * @return <i>true</i> if the group is part of the current influence
		 *         group
		 */
		private boolean isInDepGroup(final ResourceSpreader lookfor) {
			final int start = lookfor.isConsumer() ? firstConsumerId : 0;
			final int stop = start == 0 ? firstConsumerId : depgrouplen;
			// We will just check the part of the depgroup where
			// consumers or providers are located
			int i = start;
			for (; i < stop && myDepGroup[i] != lookfor; i++)
				;
			return i != stop;
		}

		/**
		 * To be used to initiate out of order frequency updates. This is useful
		 * when one or more resource spreaders in the influence group have gone
		 * through frequency altering changes (i.e., dropping or receiving a new
		 * resource consumption) and the rest of the group needs to be
		 * rescheduled.
		 */
		void nudge() {
			if (nudged)
				return;
			nudged = true;
			updateFrequency(0);
		}

		/**
		 * Only those should get the depgroup with this function who are not
		 * planning to change it's contents. This function directly offers the
		 * array handled by the freqsyncer to allow external read operations on
		 * the array to perform faster.
		 * 
		 * <i>WARNING:</i> If, for some reason, the contents of the returned
		 * array are changed then the proper operation of FreqSyncer cannot be
		 * guaranteed anymore.
		 * 
		 * @return the reference to the influence group's internal array
		 */
		ResourceSpreader[] getDepGroup() {
			return myDepGroup;
		}

		/**
		 * queries the number of resource spreaders that are part of the
		 * influence group managed by this freqsyncer.
		 * 
		 * @return the number of spreaders in the group
		 */
		public int getDGLen() {
			return depgrouplen;
		}

		/**
		 * This will always give a fresh copy of the depgroup which can be
		 * changed as the user desires. Because of the always copying behavior
		 * it will reduce the performance a little. Of course it results in
		 * significant performance penalties for those who only would like to
		 * read from the array.
		 * 
		 * @return a new copy of the influence group's internal array this array
		 *         can be modified at the user's will
		 */
		public ResourceSpreader[] getClonedDepGroup() {
			return Arrays.copyOfRange(myDepGroup, 0, depgrouplen);
		}

		/**
		 * provides a textual overview of the influence group with all its
		 * members. useful for debugging and tracing.
		 */
		@Override
		public String toString() {
			return "FreqSyncer(" + super.toString() + " depGroup: " + Arrays.toString(myDepGroup) + ")";
		}

		/**
		 * query the index of the first consumer in the influence group's
		 * internal array representation
		 * 
		 * @return the index of the first consumer
		 */
		public int getFirstConsumerId() {
			return firstConsumerId;
		}

		/**
		 * Goes through the entire influence group and for each member it
		 * initiates its doProcessing function.
		 * 
		 * This is the actual function that does influence group wise resource
		 * consumption processing.
		 * 
		 * @param currentTime
		 *            the time instance for which the processing should be done
		 */
		protected final void outOfOrderProcessing(final long currentTime) {
			for (int i = 0; i < depgrouplen; i++) {
				myDepGroup[i].doProcessing(currentTime);
			}
		}

		/**
		 * Implementation of Algorithm 1 from "DISSECT-CF: a simulator to foster
		 * energy-aware scheduling in infrastructure clouds"
		 * 
		 * Manages the influence group's growth and decomposition.
		 * 
		 * Sends out the notifications for completed or failed resource
		 * consumptions.
		 * 
		 * This is executed with the frequency identified by the low level
		 * scheduler. The execution of this tick function is run at the very end
		 * of the event loop in Timed (with the help of backpreference that is
		 * set up in the constructors of this class). The backpreference ensures
		 * that frequency setup is only done after all other events are handled
		 * by Timed (i.e., it is not expected that new resource consumption
		 * objects and similar will occur because of other activities). This
		 * actually ensures that the nudging of the freqsyncer will not cause
		 * too frequent repetitions of this heavyweight algorithm.
		 * 
		 */
		@Override
		public void tick(final long fires) {
			// Phase I. Identifying new influence group members, sending out
			// consumption notification events
			boolean didRemovals = false;
			boolean didExtension;
			do {
				outOfOrderProcessing(fires);
				depGroupExtension.clear();
				nudged = false;
				didExtension = false;
				for (int rsi = 0; rsi < depgrouplen; rsi++) {
					final ResourceSpreader rs = myDepGroup[rsi];
					// managing removals
					if (!rs.underRemoval.isEmpty()) {
						didRemovals = true;
						final int urLen = rs.underRemoval.size();
						final boolean isConsumer = rs.isConsumer();
						for (int urIndex = 0; urIndex < urLen; urIndex++) {
							final ResourceConsumption con = rs.underRemoval.get(urIndex);
							if (ArrayHandler.removeAndReplaceWithLast(rs.toProcess, con)) {
								rs.underProcessingLen--;
							}
							if (isConsumer) {
								if (con.getUnProcessed() == 0) {
									con.fireCompleteEvent();
								} else if (!con.isResumable()) {
									con.fireCancelEvent();
								}
							}
						}
						rs.underRemoval.clear();
					}
					// managing additions
					if (!rs.underAddition.isEmpty()) {
						if (rs.underProcessingLen == 0) {
							rs.lastNotifTime = fires;
						}
						final int uaLen = rs.underAddition.size();
						for (int i = 0; i < uaLen; i++) {
							final ResourceConsumption con = rs.underAddition.get(i);
							rs.toProcess.add(con);
							final ResourceSpreader cp = rs.getCounterPart(con);
							// Check if counterpart is in the dependency group
							if (!isInDepGroup(cp)) {
								// No it is not, we need an extension
								didExtension = true;
								if (cp.mySyncer == null || cp.mySyncer == this) {
									// Just this single item is missing
									if (!depGroupExtension.contains(cp)) {
										depGroupExtension.add(cp);
									}
								} else {
									// There are further items missing
									cp.mySyncer.unsubscribe();
									for (int j = 0; j < cp.mySyncer.depgrouplen; j++) {
										final ResourceSpreader todepgroupextension = cp.mySyncer.myDepGroup[j];
										if (!depGroupExtension.contains(todepgroupextension)) {
											depGroupExtension.add(todepgroupextension);
										}
									}
									// Make sure, that if we encounter this cp
									// next time we will not try to add all its
									// dep group
									cp.mySyncer = null;
								}
							}
						}
						rs.underProcessingLen += uaLen;
						rs.underAddition.clear();
					}
				}
				if (didExtension) {
					addToGroup();
				}
			} while (didExtension || nudged);

			// Phase II. managing separation of influence groups
			if (didRemovals) {
				// Marking all current members of the depgroup as non members
				for (int i = 0; i < depgrouplen; i++) {
					myDepGroup[i].stillInDepGroup = false;
				}
				ResourceSpreader[] notClassified = myDepGroup;
				int providerCount = firstConsumerId;
				int notClassifiedLen = depgrouplen;
				do {
					int classifiableindex = 0;
					// finding the first dependency group
					for (; classifiableindex < notClassifiedLen; classifiableindex++) {
						final ResourceSpreader rs = notClassified[classifiableindex];
						buildDepGroup(rs);
						if (rs.stillInDepGroup) {
							break;
						}
						rs.mySyncer = null;
					}
					if (classifiableindex < notClassifiedLen) {
						notClassifiedLen -= classifiableindex;
						providerCount -= classifiableindex;
						// Remove the unused front
						System.arraycopy(notClassified, classifiableindex, notClassified, 0, notClassifiedLen);
						// Remove the not classified items
						ResourceSpreader[] stillNotClassified = null;
						int newpc = 0;
						int newlen = 0;
						for (int i = 0; i < notClassifiedLen; i++) {
							final ResourceSpreader rs = notClassified[i];
							if (!rs.stillInDepGroup) {
								notClassifiedLen--;
								// Management of the new group
								if (stillNotClassified == null) {
									stillNotClassified = new ResourceSpreader[notClassifiedLen];
								}
								stillNotClassified[newlen++] = rs;
								// Removals from the old group
								if (rs.isConsumer()) {
									notClassified[i] = notClassified[notClassifiedLen];
								} else {
									providerCount--;
									notClassified[i] = notClassified[providerCount];
									notClassified[providerCount] = notClassified[notClassifiedLen];
									newpc++;
								}
								notClassified[notClassifiedLen] = null;
								i--;
							}
						}
						// We now have the new groups so we can start
						// subscribing
						FreqSyncer subscribeMe;
						if (notClassified == myDepGroup) {
							depgrouplen = notClassifiedLen;
							firstConsumerId = providerCount;
							subscribeMe = this;
						} else {
							subscribeMe = new FreqSyncer(notClassified, providerCount, notClassifiedLen);
						}
						// Ensuring freq updates for every newly created group
						subscribeMe.updateMyFreqNow();
						if (stillNotClassified == null) {
							// No further spreaders to process
							break;
						} else {
							// let's work on the new spreaders
							notClassified = stillNotClassified;
							providerCount = newpc;
							notClassifiedLen = newlen;
						}
					} else {
						// nothing left in notclassified that can be use in
						// dependency groups
						notClassifiedLen = 0;
						if (notClassified == myDepGroup) {
							depgrouplen = 0;
						}
					}
				} while (notClassifiedLen != 0);
				if (notClassified == myDepGroup && depgrouplen == 0) {
					// No group was created we have to unsubscribe
					unsubscribe();
				}
			} else {
				// No separation was needed we just update our freq
				updateMyFreqNow();
			}
		}

		/**
		 * Calls out to the low level scheduler of the group to assign
		 * processing limits for each consumption in the group and to identify
		 * the completion time of the earliest terminating consumption. Then
		 * propagates this time to be the next time Timed fires up our tick
		 * function. This technique reduces the number of times influence group
		 * management has to be executed. Unless someone asks for it explicitly,
		 * this process also reduces the frequency with which the
		 * ResourceSpreader.doProcessing is called.
		 */
		private void updateMyFreqNow() {
			final long newFreq = myDepGroup[0].singleGroupwiseFreqUpdater();
			regularFreqMode = newFreq != 0;
			updateFrequency(newFreq);
		}

		/**
		 * Determines if the influence group is processing 0 ticks long
		 * consumptions.
		 * 
		 * @return <i>true</i> if the influence group is not processing 0 tick
		 *         long consumptions - this is expected to be the usual case.
		 */
		public boolean isRegularFreqMode() {
			return regularFreqMode;
		}

		/**
		 * Marks all resource spreaders in the currently decomposing influence
		 * group that the starting spreader has connections with. Before calling
		 * this function it is expected that all past members of the influence
		 * group are marked not in the group anymore. This function will then
		 * change these flags back only on the relevant members from the point
		 * of view of the starting item.
		 * 
		 * @param startingItem
		 *            the starting item from which point the influence group
		 *            should be constructured.
		 */
		private void buildDepGroup(final ResourceSpreader startingItem) {
			if (startingItem.underProcessingLen == 0 || startingItem.stillInDepGroup) {
				return;
			}
			startingItem.stillInDepGroup = true;
			for (int i = 0; i < startingItem.underProcessingLen; i++) {
				buildDepGroup(startingItem.getCounterPart(startingItem.toProcess.get(i)));
			}
		}
	}

	/**
	 * This constructor just saves the processing power that can be spread in
	 * every tick by the newly instantiated spreader.
	 * 
	 * @param initialProcessingPower
	 *            Maximum usable bandwidth in a during a single timing event
	 */
	public ResourceSpreader(final double initialProcessingPower) {
		setPerTickProcessingPower(initialProcessingPower);
	}

	/**
	 * Determines the influence group this resource spreader is participating
	 * in. If null, then this spreader is not having any resource consumptions
	 * registered.
	 * 
	 * @return the object representing this spreader's influence group.
	 */
	public final FreqSyncer getSyncer() {
		return mySyncer;
	}

	/**
	 * The entry to the lowest level schedulers in DISSECT-CF. This function is
	 * expected to calculate for each resource consumption of this spreader the
	 * amount of resource share it could receive.
	 * 
	 * @return the duration (in ticks) one has to wait before any of the
	 *         resource consumptions in this spreader complete
	 */
	protected abstract long singleGroupwiseFreqUpdater();

	/**
	 * Allows the management of the underRemoval list. When some objects are
	 * removed the influence groups are reevaluated.
	 * 
	 * @param conList
	 *            the resource consumptions that must be dropped (either because
	 *            they complete or because they are cancelled)
	 * @param len
	 *            the number of items in the consumption list.
	 */
	protected final void removeTheseConsumptions(final ResourceConsumption[] conList, final int len) {
		for (int i = 0; i < len; i++) {
			if(!underRemoval.contains(conList[i])) {
				underRemoval.add(conList[i]);
			}
			ArrayHandler.removeAndReplaceWithLast(underAddition, conList[i]);
		}
		if (mySyncer != null) {
			mySyncer.nudge();
		}
	}

	/**
	 * When a new consumption is initiated it must be registered to the
	 * corresponding spreader with this function.
	 * 
	 * The consumption object is added to the array of current consumptions.
	 * This function also makes sure that the timing events arrive if this is
	 * the first object in the array.
	 * 
	 * WARNING: This function should not be called by anyone else but the
	 * registration function of the resource consumption! (Otherwise duplicate
	 * registrations could happen!)
	 * 
	 * @param con
	 *            The consumption object to be registered
	 * @return
	 *         <ul>
	 *         <li><i>true</i> if the registration was successful
	 *         <li><i>false</i> if the consumption was already registered or if
	 *         the consumption is not acceptable by its set provider or
	 *         consumer.
	 *         </ul>
	 */
	static boolean registerConsumption(final ResourceConsumption con) {
		final ResourceSpreader provider = con.getProvider();
		final ResourceSpreader consumer = con.getConsumer();
		if (con.isRegistered() || !(provider.isAcceptableConsumption(con) && consumer.isAcceptableConsumption(con))) {
			return false;
		}
		// ResourceConsumption synchronization
		ArrayHandler.removeAndReplaceWithLast(provider.underRemoval, con);
		ArrayHandler.removeAndReplaceWithLast(consumer.underRemoval, con);

		provider.underAddition.add(con);
		consumer.underAddition.add(con);

		boolean notnudged = true;
		if (provider.mySyncer != null) {
			provider.mySyncer.nudge();
			notnudged = false;
		}

		if (consumer.mySyncer != null) {
			consumer.mySyncer.nudge();
			notnudged = false;
		}
		if (notnudged) {
			// We just form our new influence group
			new FreqSyncer(provider, consumer).nudge();
		}

		return true;
	}

	/**
	 * Allows the rejection of the registration of some resource consumptions.
	 * By default this function this function only checks whether the
	 * registration is actually happening for the resource spreader that the
	 * consumption refers to. Also the default function stops registrations if
	 * the processing power of this spreader is 0.
	 * 
	 * <i>NOTE:</i> by further implementing this function one can block
	 * registrations in even more cases (e.g. no registration of CPU utilization
	 * is possible for DESTROYED virtual machines).
	 * 
	 * <i>WARNING:</i> when overriding this method, always combine the
	 * original's decision
	 * 
	 * @param con
	 *            the consumption that is asked to be registered
	 * @return <i>true</i> if the consumption can be registered in this
	 *         spreader's underprocessing list
	 */
	protected boolean isAcceptableConsumption(final ResourceConsumption con) {
		return getSamePart(con).equals(this) && perTickProcessingPower > 0 && con.getHardLimit() > 0;
	}

	/**
	 * Organizes the coordinated removal of this consumption from the
	 * underProcessing list.
	 * 
	 * @param con
	 *            the consumption to be removed from its processing
	 *            consumer/provider pair.
	 */
	static void cancelConsumption(final ResourceConsumption con) {
		final ResourceSpreader provider = con.getProvider();
		final ResourceSpreader consumer = con.getConsumer();
		ResourceConsumption[] sinlgeConsumption = new ResourceConsumption[] { con };
		provider.removeTheseConsumptions(sinlgeConsumption, 1);
		consumer.removeTheseConsumptions(sinlgeConsumption, 1);
	}

	/**
	 * The main resource processing loop. This loop is responsible for actually
	 * offering resources to consumers in case of providers or in case of
	 * consumers it is responsible to actually utilize the offered resources.
	 * The loop does not make decisions on how much resources to use/offer for a
	 * particular resource consumption, it is assumed that by the time this
	 * function is called all resource consumption objects are allocated their
	 * share of the complete available resource set.
	 * 
	 * The loop also uses the lastNotiftime field to determine how much time
	 * passed since it last shared the resources to all its consumption objects.
	 * 
	 * @param currentFireCount
	 *            the time at which this processing task must take place.
	 */
	private void doProcessing(final long currentFireCount) {
		if (currentFireCount == lastNotifTime && mySyncer.isRegularFreqMode()) {
			return;
		}
		ResourceConsumption[] toRemove = null;
		boolean firsthit = true;
		int remIdx = 0;
		final long ticksPassed = currentFireCount - lastNotifTime;
		for (int i = 0; i < underProcessingLen; i++) {
			final ResourceConsumption con = underProcessing.get(i);
			final double processed = processSingleConsumption(con, ticksPassed);
			if (processed < 0) {
				totalProcessed -= processed;
				if (firsthit) {
					toRemove = new ResourceConsumption[underProcessingLen - i];
					firsthit = false;
				}
				toRemove[remIdx++] = con;
			} else {
				totalProcessed += processed;
			}
		}
		if (remIdx > 0) {
			removeTheseConsumptions(toRemove, remIdx);
		}
		lastNotifTime = currentFireCount;
	}

	/**
	 * This function is expected to realign the underProcessing and
	 * toBeProcessed fields of the ResourceConsumption object it receives. It is
	 * expected to do the realignment depending on whether this resource
	 * spreader is a consumer or a provider.
	 * 
	 * All subclasses that embody consumer/provider roles must implement this
	 * method so the doProcessing function above could uniformly process all
	 * resource consumptions independently from being a provider/consumer.
	 * 
	 * @param con
	 *            the resource consumption to be realigned
	 * @param ticksPassed
	 *            the time passed since the last processing request
	 * @return Its absolute value represents the amount of actually processed
	 *         resources. If negative then the 'con' has completed its
	 *         processing.
	 */
	protected abstract double processSingleConsumption(final ResourceConsumption con, final long ticksPassed);

	/**
	 * If it is unknown whether we are a provider or a consumer (this is usually
	 * the case in the generic resource spreader class or anyone outsed the
	 * actual provider/consumer implementations) then it is useful to figure out
	 * the counter part who also participates in the same resource consumption
	 * processing operation.
	 * 
	 * @param con
	 *            The consumption object for which we would like to know the
	 *            other party that participates in the processing with us.
	 * @return the other resource spreader that does the processing with us
	 */
	protected abstract ResourceSpreader getCounterPart(final ResourceConsumption con);

	/**
	 * The function gets that particular resource spreader from the given
	 * resource consumption object that is the same kind (i.e.,
	 * provider/consumer) as the resource spreader that calls for this function.
	 * 
	 * This function is useful to collect the same kind of resource spreaders
	 * identified through a set of resource consumption objects. Or just to know
	 * if a particular resource spreader is the one that is set in the expected
	 * role in a resource consumption object (e.g., am I really registered as a
	 * consumer as I expected?).
	 * 
	 * @param con
	 *            The consumption object on which this call will operate
	 * 
	 * @return the resource spreader set to be in the same role
	 *         (provider/consumer) as this resource spreader object (i.e., the
	 *         spreader on which the function was calleD)
	 */
	protected abstract ResourceSpreader getSamePart(final ResourceConsumption con);

	/**
	 * Determines if a particular resource spreader is acting as a consumer or
	 * not.
	 * 
	 * @return <i>true</i> if this resource spreader is a consumer, <i>false</i>
	 *         otherwise
	 */
	protected abstract boolean isConsumer();

	/**
	 * Returns the total amount of resources processed (i.e., via all past and
	 * present resource consumption objects) by this resource spreader object at
	 * the time instance this call is made.
	 * 
	 * <i>WARNING:</i> this operation could be rather expensive to call as it
	 * first has to ensure all consumption processing is done before the query
	 * to the totalProcessed field can be actually accomplished.
	 * 
	 * @return the amount of processing done so far. The unit of the processed
	 *         value is application specific here it is not relevant. For
	 *         example if this function is used on a PhysicalMachine then it
	 *         will report the amount of instructions executed so far by the PM.
	 */
	public double getTotalProcessed() {
		if (mySyncer != null) {
			final long currTime = Timed.getFireCount();
			if (isConsumer()) {
				// We first have to make sure the providers provide the
				// stuff that this consumer might need
				final int len = mySyncer.getFirstConsumerId();
				final ResourceSpreader[] dg = mySyncer.myDepGroup;
				for (int i = 0; i < len; i++) {
					dg[i].doProcessing(currTime);
				}
			}
			doProcessing(currTime);
		}
		return totalProcessed;
	}

	/**
	 * Determines the current processing power of this resource spreader
	 * 
	 * @return the processing power of this resource spreader. The returned
	 *         value has no unit, the unit depends on the user of the spreader
	 *         (e.g. in networking it could be bytes/tick)
	 */
	public double getPerTickProcessingPower() {
		return perTickProcessingPower;
	}

	/**
	 * Allows to set the current processing power of this resource spreader
	 * 
	 * <i>WARNING:</i> this is not intended to be used for altering the
	 * performance of the spreader while it participates in resource consumption
	 * processing
	 * 
	 * @param perTickProcessingPower
	 *            the new processing power of this resource spreader. The new
	 *            value has no unit, the unit depends on the user of the
	 *            spreader (e.g. in networking it could be bytes/tick)
	 */
	protected void setPerTickProcessingPower(double perTickProcessingPower) {
		// if (isSubscribed()) {
		// // TODO: this case might be interesting to support.
		// throw new IllegalStateException(
		// "It is not possible to change the processing power of a spreader
		// while it is subscribed!");
		// }
		this.perTickProcessingPower = perTickProcessingPower;
		this.negligableProcessing = this.perTickProcessingPower / 1000000000;
	}

	/**
	 * Queries the current power behavior object
	 * 
	 * @return the current power behavior
	 */
	public PowerState getCurrentPowerBehavior() {
		return currentPowerBehavior;
	}

	/**
	 * Allows to change the power behavior of the resource spreader. Once the
	 * change is done it notifies the listeners interested in power behavior
	 * changes.
	 * 
	 * @param newPowerBehavior
	 *            the new power behavior to be set. Null values are not allowed!
	 */
	public void setCurrentPowerBehavior(final PowerState newPowerBehavior) {
		// FIXME: this might be protected later on.
		if (newPowerBehavior == null) {
			throw new IllegalStateException("Trying to set an unknown power behavior");
		}
		if (currentPowerBehavior == null) {
			powerBehaviorListenerManager = PowerStateChangeNotificationHandler.getHandlerInstance();
		}
		if (currentPowerBehavior != newPowerBehavior) {
			currentPowerBehavior = newPowerBehavior;
			powerBehaviorListenerManager.notifyListeners(Pair.of(this, newPowerBehavior));
		}
	}

	/**
	 * allows interested parties to receive power behavior change events by
	 * subscribing with a listener object.
	 * 
	 * <i>Note:</i> Internally the state dependent event handling framework is
	 * used for this operation.
	 * 
	 * @param pbcl
	 *            the new listener object
	 */
	public void subscribePowerBehaviorChangeEvents(final PowerBehaviorChangeListener pbcl) {
		powerBehaviorListenerManager.subscribeToEvents(pbcl);
	}

	/**
	 * allows parties that got uninterested to cancel the reception of new power
	 * behavior change events by unsubscribing with a listener object.
	 * 
	 * <i>Note:</i> Internally the state dependent event handling framework is
	 * used for this operation.
	 * 
	 * @param pbcl
	 *            the old listener object
	 */
	public void unsubscribePowerBehaviorChangeEvents(final PowerBehaviorChangeListener pbcl) {
		powerBehaviorListenerManager.unsubscribeFromEvents(pbcl);
	}

	/**
	 * Provides a nice formatted single line representation of the spreader. It
	 * lists the currently processed resource consumptions and the power
	 * behavior as well. Useful for debugging and tracing purposes.
	 */
	@Override
	public String toString() {
		return "RS(processing: " + toProcess.toString() + " in power state: "
				+ (currentPowerBehavior == null ? "-" : currentPowerBehavior.toString()) + ")";
	}

	/**
	 * A continuously increasing simple hash value to be used by the next
	 * resource spreader object created
	 */
	static int hashCounter = 0;
	/**
	 * The hashcode of the actual resource spreader to be used in java's built
	 * in hashCode function
	 */
	private final int myHashCode = getHashandIncCounter();

	/**
	 * Manages the increment of the hashCounter and offers the latest hash code
	 * for new objects
	 * 
	 * <i>WARNING:</i> as this function does not check if a hash value is
	 * already given or not there might be hash collisions if there are so many
	 * resource spreaders created that the hashcounter overflows.
	 * 
	 * @return the hash code to be used by the newest object
	 */
	static int getHashandIncCounter() {
		// FIXME
		// WARNING: some possible hash collisions!
		return hashCounter++;
	}

	/**
	 * Returns the constant hashcode that was generated for this object during
	 * its instantiation.
	 */
	@Override
	public final int hashCode() {
		return myHashCode;
	}
}
