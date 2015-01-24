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
import java.util.Collections;
import java.util.List;

public abstract class ResourceSpreader {

	protected double perSecondProcessingPower;
	protected double negligableProcessing;
	private final ArrayList<ResourceConsumption> toProcess = new ArrayList<ResourceConsumption>();
	public final List<ResourceConsumption> underProcessing = Collections
			.unmodifiableList(toProcess);
	int underProcessingLen = 0;
	private FreqSyncer mySyncer = null;
	private ArrayList<ResourceConsumption> underAddition = new ArrayList<ResourceConsumption>();
	private ArrayList<ResourceConsumption> underRemoval = new ArrayList<ResourceConsumption>();
	public final List<ResourceConsumption> toBeAdded = Collections
			.unmodifiableList(underAddition);

	protected long lastNotifTime = 0;
	private double totalProcessed = 0;
	private boolean stillInDepGroup;

	public static class FreqSyncer extends Timed {
		private ResourceSpreader[] myDepGroup;
		private int depgrouplen;
		private int firstConsumerId;
		final ArrayList<ResourceSpreader> depGroupExtension = new ArrayList<ResourceSpreader>();
		boolean nudged = false;
		boolean groupnotchanged = true;

		private FreqSyncer(final ResourceSpreader provider,
				final ResourceSpreader consumer) {
			myDepGroup = new ResourceSpreader[2];
			myDepGroup[0] = provider;
			myDepGroup[1] = consumer;
			firstConsumerId = 1;
			depgrouplen = 2;
			provider.mySyncer = consumer.mySyncer = this;
		}

		private FreqSyncer(ResourceSpreader[] myDepGroup, final int provcount,
				final int dglen) {
			this.myDepGroup = myDepGroup;
			firstConsumerId = provcount;
			depgrouplen = dglen;
		}

		private void addSingleToDG(final ResourceSpreader rs) {
			try {
				myDepGroup[depgrouplen] = rs;
				depgrouplen++;
			} catch (ArrayIndexOutOfBoundsException e) {
				ResourceSpreader[] newdg = new ResourceSpreader[myDepGroup.length * 7];
				System.arraycopy(myDepGroup, 0, newdg, 0, depgrouplen);
				myDepGroup = newdg;
				addSingleToDG(rs);
			}
		}

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

		private boolean isInDepGroup(final ResourceSpreader lookfor) {
			final int start = lookfor.isConsumer() ? firstConsumerId : 0;
			final int stop = start == 0 ? firstConsumerId : depgrouplen;
			int i = start;
			for (; i < stop && myDepGroup[i] != lookfor; i++)
				;
			return i != stop;
		}

		void nudge() {
			if (nudged)
				return;
			nudged = true;
			updateFrequency(0);
		}

		ResourceSpreader[] getDepGroup() {
			return myDepGroup;
		}

		public int getDGLen() {
			return depgrouplen;
		}

		public ResourceSpreader[] getClonedDepGroup() {
			return Arrays.copyOfRange(myDepGroup, 0, depgrouplen);
		}

		@Override
		public String toString() {
			return "FreqSyncer(" + super.toString() + " depGroup: "
					+ myDepGroup + ")";
		}

		public int getFirstConsumerId() {
			return firstConsumerId;
		}

		protected final void outOfOrderProcessing(final long currentTime) {
			for (int i = 0; i < depgrouplen; i++) {
				myDepGroup[i].doProcessing(currentTime);
			}
		}

		@Override
		public void tick(final long fires) {
			boolean didRemovals = false;
			boolean didExtension;
			do {
				outOfOrderProcessing(fires);
				depGroupExtension.clear();
				nudged = false;
				didExtension = false;
				for (int rsi = 0; rsi < depgrouplen; rsi++) {
					final ResourceSpreader rs = myDepGroup[rsi];
					if (!rs.underRemoval.isEmpty()) {
						didRemovals = true;
						int rsuLen = rs.toProcess.size();
						int urLen = rs.underRemoval.size();
						boolean isConsumer = rs.isConsumer();
						for (int urIndex = 0; urIndex < urLen; urIndex++) {
							final ResourceConsumption con = rs.underRemoval
									.get(urIndex);
							if (ArrayHandler.removeAndReplaceWithLast(
									rs.toProcess, con)) {
								rsuLen--;
							}
							if (isConsumer) {
								if(con.getUnProcessed() == 0) {
									con.ev.conComplete();
								} else if(!con.isResumable()) {
									con.ev.conCancelled(con);
								}
							}
						}
						rs.underProcessingLen = rsuLen;
						rs.underRemoval.clear();
					}
					if (!rs.underAddition.isEmpty()) {
						if (rs.underProcessingLen == 0) {
							rs.lastNotifTime = fires;
						}
						final int uaLen = rs.underAddition.size();
						for (int i = 0; i < uaLen; i++) {
							final ResourceConsumption con = rs.underAddition
									.get(i);
							rs.toProcess.add(con);
							final ResourceSpreader cp = rs.getCounterPart(con);
							if (!isInDepGroup(cp)) {
								didExtension = true;
								if (cp.mySyncer == null || cp.mySyncer == this) {
									if (!depGroupExtension.contains(cp)) {
										depGroupExtension.add(cp);
									}
								} else {
									cp.mySyncer.unsubscribe();
									for (int j = 0; j < cp.mySyncer.depgrouplen; j++) {
										final ResourceSpreader todepgroupextension = cp.mySyncer.myDepGroup[j];
										if (!depGroupExtension
												.contains(todepgroupextension)) {
											depGroupExtension
													.add(todepgroupextension);
										}
									}
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

			if (didRemovals) {
				for (int i = 0; i < depgrouplen; i++) {
					myDepGroup[i].stillInDepGroup = false;
				}
				ResourceSpreader[] notClassified = myDepGroup;
				int providerCount = firstConsumerId;
				int notClassifiedLen = depgrouplen;
				do {
					int classifiableindex = 0;
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
						System.arraycopy(notClassified, classifiableindex,
								notClassified, 0, notClassifiedLen);
						ResourceSpreader[] stillNotClassified = null;
						int newpc = 0;
						int newlen = 0;
						for (int i = 0; i < notClassifiedLen; i++) {
							final ResourceSpreader rs = notClassified[i];
							if (!rs.stillInDepGroup) {
								notClassifiedLen--;
								if (stillNotClassified == null) {
									stillNotClassified = new ResourceSpreader[notClassifiedLen];
								}
								stillNotClassified[newlen++] = rs;
								if (rs.isConsumer()) {
									notClassified[i] = notClassified[notClassifiedLen];
								} else {
									providerCount--;
									notClassified[i] = notClassified[providerCount];
									notClassified[providerCount] = notClassified[notClassifiedLen];
									newpc++;
								}
							}
						}
						FreqSyncer subscribeMe;
						if (notClassified == myDepGroup) {
							depgrouplen = notClassifiedLen;
							firstConsumerId = providerCount;
							subscribeMe = this;
						} else {
							subscribeMe = new FreqSyncer(notClassified,
									providerCount, notClassifiedLen);
						}
						subscribeMe.updateFrequency(subscribeMe.myDepGroup[0]
								.singleGroupwiseFreqUpdater());
						if (stillNotClassified == null) {
							break;
						} else {
							notClassified = stillNotClassified;
							providerCount = newpc;
							notClassifiedLen = newlen;
						}
					} else {
						notClassifiedLen = 0;
						if (notClassified == myDepGroup) {
							depgrouplen = 0;
						}
					}
				} while (notClassifiedLen != 0);
				if (notClassified == myDepGroup && depgrouplen == 0) {
					unsubscribe();
				}
			} else {
				updateFrequency(myDepGroup[0].singleGroupwiseFreqUpdater());
			}
		}

		private void buildDepGroup(final ResourceSpreader startingItem) {
			final int upLen;
			if ((upLen = startingItem.toProcess.size()) == 0
					|| startingItem.stillInDepGroup) {
				return;
			}
			startingItem.stillInDepGroup = true;
			for (int i = 0; i < upLen; i++) {
				buildDepGroup(startingItem
						.getCounterPart(startingItem.toProcess.get(i)));
			}
		}
	}

	public ResourceSpreader(final double initialProcessingPower) {
		setPerSecondProcessingPower(initialProcessingPower);
	}

	public final FreqSyncer getSyncer() {
		return mySyncer;
	}

	protected abstract long singleGroupwiseFreqUpdater();

	protected final void removeTheseConsumptions(
			final ResourceConsumption[] conList, final int len) {
		for (int i = 0; i < len; i++) {
			underRemoval.add(conList[i]);
			ArrayHandler.removeAndReplaceWithLast(underAddition, conList[i]);
		}
		if (mySyncer != null) {
			mySyncer.nudge();
		}
	}

	static boolean registerConsumption(final ResourceConsumption con) {
		final ResourceSpreader provider = con.getProvider();
		final ResourceSpreader consumer = con.getConsumer();
		if (con.isRegistered()
				|| !(provider.isAcceptableConsumption(con) && consumer
				.isAcceptableConsumption(con))) {
			return false;
		}
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
			new FreqSyncer(provider, consumer).nudge();
		}

		return true;
	}

	protected boolean isAcceptableConsumption(final ResourceConsumption con) {
		return getSamePart(con).equals(this) && perSecondProcessingPower > 0
				&& con.getHardLimit() > 0;
	}

	static void cancelConsumption(final ResourceConsumption con) {
		final ResourceSpreader provider = con.getProvider();
		final ResourceSpreader consumer = con.getConsumer();
		ResourceConsumption[] sinlgeConsumption = new ResourceConsumption[] { con };
		provider.removeTheseConsumptions(sinlgeConsumption, 1);
		consumer.removeTheseConsumptions(sinlgeConsumption, 1);
	}

	private void doProcessing(final long currentFireCount) {
		if (currentFireCount == lastNotifTime) {
			return;
		}
		ResourceConsumption[] toRemove = null;
		boolean firsthit = true;
		int remIdx = 0;
		final double secondsPassed = (currentFireCount - lastNotifTime) / 1000d;
		for (int i = 0; i < underProcessingLen; i++) {
			final ResourceConsumption con = underProcessing.get(i);
			final double processed = processSingleConsumption(con,
					secondsPassed);
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

	protected abstract double processSingleConsumption(
			final ResourceConsumption con, final double secondsPassed);

	protected abstract ResourceSpreader getCounterPart(
			final ResourceConsumption con);

	protected abstract ResourceSpreader getSamePart(
			final ResourceConsumption con);

	protected abstract boolean isConsumer();

	public double getTotalProcessed() {
		if (mySyncer != null) {
			final long currTime = Timed.getFireCount();
			if (isConsumer()) {
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

	public double getPerSecondProcessingPower() {
		return perSecondProcessingPower;
	}

	protected void setPerSecondProcessingPower(double perSecondProcessingPower) {
		this.perSecondProcessingPower = perSecondProcessingPower;
		this.negligableProcessing = this.perSecondProcessingPower / 1000000000;
	}

	@Override
	public String toString() {
		return "RS(processing: " + toProcess.toString() + ")";
	}

	static int hashCounter = 0;
	private int myHashCode = hashCounter++;

	@Override
	public final int hashCode() {
		return myHashCode;
	}
}
