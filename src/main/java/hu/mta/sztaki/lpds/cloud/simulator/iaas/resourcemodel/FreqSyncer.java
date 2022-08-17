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

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.util.ArrayHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This class is the core part of the unified resource consumption model of
 * DISSECT-CF.
 * <p>
 * The main purpose of this class is to create and manage influence groups from
 * resource spreaders that are connected with resource consumptions. Also, the
 * class is also expected to coordinate the processing of the resource
 * consumptions within the entire influence group. Finally, the class is
 * responsible to deliver the completion or failure events for the resource
 * consumptions deregistered from the influence group's resource spreaders.
 * <p>
 * The name FreqSyncer comes from the class's primary goal, identify the
 * earliest time there is a change in the scheduling (e.g., because a new
 * resource consumption is added to the group or because one of the consumptions
 * complete), and then make sure that all spreaders in the influence group
 * receive timing events at the same time instance.
 *
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 * of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory of Parallel and
 * Distributed Systems, MTA SZTAKI (c) 2015"
 */
public class FreqSyncer extends Timed {
    /**
     * The influence group managed by this freqsyncer object.
     * <p>
     * myDepGroup is always kept in order: first all the providers are listed, then
     * all the consumers, when dealing with this data member please keep in mind
     * this expected behavior. At the end of the array we have a padding with null
     * items.
     * <p>
     * Contents:
     * <ul>
     * <li>[0,firstConsumerId[ providers
     * <li>[firstConsumerId,depgrouplen[ consumers
     * <li>[depgrouplen,myDepGroup.length[ padding with null items
     * </ul>
     */
    ResourceSpreader[] myDepGroup;
    /**
     * The current length of the myDepGroup array. This is the actual length without
     * counting the padding at the end of the java array.
     */
    private int depgrouplen;
    /**
     * The index of the first consumer in the myDepGroup array.
     */
    private int firstConsumerId;
    /**
     * those resource spreaders that need to be added to the influence group at the
     * particular time instance
     */
    private final ArrayList<ResourceSpreader> depGroupExtension = new ArrayList<>();
    /**
     * if there are some external activities that could lead to influence group
     * changes this field will be turned to true
     * <p>
     * the tick function then ensures it to return to false once it has completed
     * its management operations on the influence groups
     */
    private boolean nudged = false;
    /**
     * if the freqsyncer identifies the need to fire events with 0 frequency then it
     * turns this mode off. This allows ResourceSpreader.doProcessing to happen
     * multiple times in a single time instance.
     */
    private boolean regularFreqMode = true;

    /**
     * Constructor of a freqsyncer to be used when neither the provider nor the
     * consumer of a resource consumption belongs to an already existing influence
     * group.
     *
     * @param provider the provider to be added to the initial influence group
     * @param consumer the consumer to be added to the initial influence group
     */
    FreqSyncer(final ResourceSpreader provider, final ResourceSpreader consumer) {
        myDepGroup = new ResourceSpreader[2];
        myDepGroup[0] = provider;
        myDepGroup[1] = consumer;
        firstConsumerId = 1;
        depgrouplen = 2;
        provider.setSyncer(this);
        consumer.setSyncer(this);
        setBackPreference(true);
    }

    /**
     * The constructor to be used when a new influence group needs to be created
     * because the original group got fragmented.
     *
     * @param myDepGroup the group members to take part in the new influence group
     * @param provcount  the number of providers in the group
     * @param dglen      the length of the influence group
     */
    private FreqSyncer(ResourceSpreader[] myDepGroup, final int provcount, final int dglen) {
        this.myDepGroup = myDepGroup;
        firstConsumerId = provcount;
        depgrouplen = dglen;
        Arrays.stream(myDepGroup).limit(dglen).forEach( rs -> rs.setSyncer(this));
        setBackPreference(true);
    }

    /**
     * Allows a single spreader object to be added to the influence group.
     *
     * <i>WARNING:</i> Should only be used from addToGroup, as there are some
     * management operations that only occure there for performance reasons
     *
     * @param rs the spreader to be added to the influence group
     */
    private void addSingleToDG(final ResourceSpreader rs) {
        try {
            myDepGroup[depgrouplen] = rs;
            depgrouplen++;
            rs.setSyncer(this);
        } catch (ArrayIndexOutOfBoundsException e) {
            ResourceSpreader[] newdg = new ResourceSpreader[myDepGroup.length * 7];
            System.arraycopy(myDepGroup, 0, newdg, 0, depgrouplen);
            myDepGroup = newdg;
            addSingleToDG(rs);
        }
    }

    /**
     * This function copies the contents of the depGroupExtension list to the array
     * representing the influence group and ensures that all newly added members of
     * the influence group know their group membership.
     */
    private void addToGroup() {
        for (final ResourceSpreader rs : depGroupExtension) {
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
            rs.setSyncer(this);
        }
    }

    /**
     * Determines if the spreader in question is part of the current influence group
     * or not
     *
     * @param lookfor the spreader in question
     * @return <i>true</i> if the group is part of the current influence group
     */
    private boolean isInDepGroup(final ResourceSpreader lookfor) {
        final int start = lookfor.isConsumer() ? firstConsumerId : 0;
        final int stop = start == 0 ? firstConsumerId : depgrouplen;
        // We will just check the part of the depgroup where
        // consumers or providers are located
        return Arrays.stream(myDepGroup).skip(start).limit(stop-start).anyMatch( d -> d==lookfor);
    }

    /**
     * To be used to initiate out of order frequency updates. This is useful when
     * one or more resource spreaders in the influence group have gone through
     * frequency altering changes (i.e., dropping or receiving a new resource
     * consumption) and the rest of the group needs to be rescheduled.
     */
    void nudge() {
        if (nudged)
            return;
        nudged = true;
        updateFrequency(0);
    }

    /**
     * Only those should get the depgroup with this function who are not planning to
     * change it's contents. This function directly offers the array handled by the
     * freqsyncer to allow external read operations on the array to perform faster.
     *
     * <i>WARNING:</i> If, for some reason, the contents of the returned array are
     * changed then the proper operation of FreqSyncer cannot be guaranteed anymore.
     *
     * @return the reference to the influence group's internal array
     */
    ResourceSpreader[] getDepGroup() {
        return myDepGroup;
    }

    /**
     * queries the number of resource spreaders that are part of the influence group
     * managed by this freqsyncer.
     *
     * @return the number of spreaders in the group
     */
    public int getDGLen() {
        return depgrouplen;
    }

    /**
     * This will always give a fresh copy of the depgroup which can be changed as
     * the user desires. Because of the always copying behavior it will reduce the
     * performance a little. Of course, it results in significant performance
     * penalties for those who only would like to read from the array.
     *
     * @return a new copy of the influence group's internal array this array can be
     * modified at the user's will
     */
    public ResourceSpreader[] getClonedDepGroup() {
        return Arrays.copyOfRange(myDepGroup, 0, depgrouplen);
    }

    /**
     * provides a textual overview of the influence group with all its members.
     * useful for debugging and tracing.
     */
    @Override
    public String toString() {
        return "FreqSyncer(" + super.toString() + " depGroup: " + Arrays.toString(myDepGroup) + ")";
    }

    /**
     * query the index of the first consumer in the influence group's internal array
     * representation
     *
     * @return the index of the first consumer
     */
    public int getFirstConsumerId() {
        return firstConsumerId;
    }

    /**
     * Goes through the entire influence group and for each member it initiates its
     * doProcessing function.
     * <p>
     * This is the actual function that does influence group wise resource
     * consumption processing.
     *
     * @param currentTime the time instance for which the processing should be done
     */
    protected final void outOfOrderProcessing(final long currentTime) {
        Arrays.stream(myDepGroup).limit(depgrouplen).forEach(rs -> rs.doProcessing(currentTime));
    }

    /**
     * Implementation of Algorithm 1 from "DISSECT-CF: a simulator to foster
     * energy-aware scheduling in infrastructure clouds"
     * <p>
     * Manages the influence group's growth and decomposition.
     * <p>
     * Sends out the notifications for completed or failed resource consumptions.
     * <p>
     * This is executed with the frequency identified by the low level scheduler.
     * The execution of this tick function is run at the very end of the event loop
     * in Timed (with the help of backpreference that is set up in the constructors
     * of this class). The backpreference ensures that frequency setup is only done
     * after all other events are handled by Timed (i.e., it is not expected that
     * new resource consumption objects and similar will occur because of other
     * activities). This actually ensures that the nudging of the freqsyncer will
     * not cause too frequent repetitions of this heavyweight algorithm.
     */
    @Override
    public void tick(final long fires) {
        if (identifyGroupMembers(fires)) {
            groupSeparation();
        } else {
            // No separation was needed we just update our freq
            updateMyFreqNow();
        }
    }

    /**
     * Phase I. Identifying new influence group members, sending out consumption notification events
     *
     * @param fires time at which we are working on this
     * @return if we need to do the group separation phase
     */
    private boolean identifyGroupMembers(long fires) {
        boolean didRemovals = false;
        boolean didExtension;
        do {
            addToGroup();
            outOfOrderProcessing(fires);
            depGroupExtension.clear();
            nudged = false;
            didExtension = false;
            for (int rsi = 0; rsi < depgrouplen; rsi++) {
                final ResourceSpreader rs = myDepGroup[rsi];
                didRemovals|=rs.handleRemovals();
                didExtension|=rs.handleAdditions(fires);
            }
        } while (didExtension || nudged);
        return didRemovals;
    }

    /**
     * Phase II. managing separation of influence groups
     */
    private void groupSeparation() {
        // Marking all current members of the depgroup as non-members
        Arrays.stream(myDepGroup).limit(depgrouplen).forEach( rs -> rs.stillInDepGroup = false);
        var notClassified = myDepGroup;
        var providerCount = firstConsumerId;
        var notClassifiedLen = depgrouplen;
        do {
            var classifiableindex = 0;
            // finding the first dependency group
            for (; classifiableindex < notClassifiedLen; classifiableindex++) {
                var rs = notClassified[classifiableindex];
                buildDepGroup(rs);
                if (rs.stillInDepGroup) {
                    break;
                }
                rs.setSyncer(null);
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
                // We now have the new groups, so we can start
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
    }

    /**
     * Calls out to the low level scheduler of the group to assign processing limits
     * for each consumption in the group and to identify the completion time of the
     * earliest terminating consumption. Then propagates this time to be the next
     * time Timed fires up our tick function. This technique reduces the number of
     * times influence group management has to be executed. Unless someone asks for
     * it explicitly, this process also reduces the frequency with which the
     * ResourceSpreader.doProcessing is called.
     */
    private void updateMyFreqNow() {
        var newFreq = myDepGroup[0].singleGroupwiseFreqUpdater();
        regularFreqMode = newFreq != 0;
        updateFrequency(newFreq);
    }

    /**
     * Determines if the influence group is processing 0 ticks long consumptions.
     *
     * @return <i>true</i> if the influence group is not processing 0 tick long
     * consumptions - this is expected to be the usual case.
     */
    public boolean isRegularFreqMode() {
        return regularFreqMode;
    }

    /**
     * Marks all resource spreaders in the currently decomposing influence group
     * that the starting spreader has connections with. Before calling this function
     * it is expected that all past members of the influence group are marked not in
     * the group anymore. This function will then change these flags back only on
     * the relevant members from the point of view of the starting item.
     *
     * @param startingItem the starting item from which point the influence group
     *                     should be constructured.
     */
    private void buildDepGroup(final ResourceSpreader startingItem) {
        if (startingItem.toProcess.isEmpty() || startingItem.stillInDepGroup) {
            return;
        }
        startingItem.stillInDepGroup = true;
        startingItem.toProcess.stream().map(startingItem::getCounterPart).forEach(this::buildDepGroup);
    }

    boolean ensureDepGroupHasCounterPart(ResourceSpreader cp) {
        // Check if counterpart is in the dependency group
        if (!isInDepGroup(cp)) {
            var cpSyncer=cp.getSyncer();
            // No it is not, we need an extension
            if (cpSyncer == null || cpSyncer == this) {
                // Just this single item is missing
                if (!depGroupExtension.contains(cp)) {
                    depGroupExtension.add(cp);
                }
            } else {
                // There are further items missing
                cpSyncer.unsubscribe(); // we will remove its old syncer
                depGroupExtension.addAll(Arrays.stream(cpSyncer.myDepGroup).filter(Predicate.not(depGroupExtension::contains)).collect(Collectors.toList()));
                // Make sure, that if we encounter this cp
                // next time we will not try to add all its
                // dep group
                cp.setSyncer(null);
            }
            return true;
        } else {
            return false;
        }
    }
}
