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

package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.PriorityQueue;

public class SmallestFirstScheduler extends FirstFitScheduler {
	public static final Comparator<QueueingData> vmQueueSmallestFirstComparator = new Comparator<QueueingData>() {
		@Override
		public int compare(final QueueingData o1, final QueueingData o2) {
			final int compRC = o1.cumulativeRC.compareTo(o2.cumulativeRC);
			return compRC == 0 ? Long.signum(o1.receivedTime - o2.receivedTime)
					: compRC;
		}
	};

	public static class SFQueue extends PriorityQueue<QueueingData> implements
			List<QueueingData> {
		private static String UFCmessage="Unexpected function call";
		private static final long serialVersionUID = 2693241597335321816L;

		public SFQueue() {
			super(5, vmQueueSmallestFirstComparator);
		}

		@Override
		public boolean addAll(int index, Collection<? extends QueueingData> c) {
			throw new IllegalStateException(UFCmessage);
		}

		@Override
		public QueueingData get(int index) {
			if (index == 0)
				return peek();
			throw new IllegalStateException(UFCmessage);
		}

		@Override
		public QueueingData set(int index, QueueingData element) {
			throw new IllegalStateException(UFCmessage);
		}

		@Override
		public void add(int index, QueueingData element) {
			throw new IllegalStateException(UFCmessage);
		}

		@Override
		public QueueingData remove(int index) {
			if (index == 0)
				return poll();
			throw new IllegalStateException(UFCmessage);
		}

		@Override
		public int indexOf(Object o) {
			throw new IllegalStateException(UFCmessage);
		}

		@Override
		public int lastIndexOf(Object o) {
			throw new IllegalStateException(UFCmessage);
		}

		@Override
		public ListIterator<QueueingData> listIterator() {
			throw new IllegalStateException(UFCmessage);
		}

		@Override
		public ListIterator<QueueingData> listIterator(int index) {
			throw new IllegalStateException(UFCmessage);
		}

		@Override
		public List<QueueingData> subList(int fromIndex, int toIndex) {
			throw new IllegalStateException(UFCmessage);
		}

	}

	public SmallestFirstScheduler(final IaaSService parent) {
		super(parent);
		queue = new SFQueue();
	}
}
