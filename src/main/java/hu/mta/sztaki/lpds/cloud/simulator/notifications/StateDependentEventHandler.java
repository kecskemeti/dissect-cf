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

package hu.mta.sztaki.lpds.cloud.simulator.notifications;

import java.util.ArrayList;

/**
 * The main non-time dependent event handling mechanism in DISSECT-CF. This
 * class is kept generic, and every user could either derive from it or use this
 * as an embedded object. It is expected that the user of this class will want
 * to deliver notifications about its internal state changes. The kind of state
 * changes possible are not known by this generic mechanism, but the user of
 * this class is expected to express this via the generic parameter of the
 * class.
 * 
 * The individual notifications can be customized via the class's constructor.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2015"
 *
 * @param <T>
 *            the kind of state change for which this handler is prepared to
 *            notify about.
 * @param
 * 			<P>
 *            the kind of data to be passed on to the notified party
 */
public class StateDependentEventHandler<T, P> {

	/**
	 * The listeners that will receive notifications if the notify listeners
	 * function is called
	 */
	final ArrayList<T> listeners = new ArrayList<T>();
	/**
	 * if the notificaiton process is underway, then new listeners are
	 * registered here (they will not receive notifications in the current
	 * notification round as their registration is actually a result of the
	 * current notification round)
	 */
	final private ArrayList<T> newListeners = new ArrayList<T>();
	/**
	 * if the notificaiton process is underway, then to be removed listeners are
	 * registered here (they will still receive notifications in the current
	 * notification round as their de-registration is actually a result of the
	 * current notification round)
	 */
	final private ArrayList<T> cancelledListeners = new ArrayList<T>();
	/**
	 * a marker to show if there is a notification process underway
	 */
	private boolean noEventDispatchingInProcess = true;
	/**
	 * the entity that is actually used to perform the notifications
	 */
	final SingleNotificationHandler<T, P> myHandler;
	/**
	 * the dispatcher to be used when events need to be fired.
	 */
	EventDispatcherCore eventing = NullDispatcher.instance;

	/**
	 * Initialization of the event handling mechanism.
	 * 
	 * @param handler
	 *            via this interface's implementation will the actual observed
	 *            object be capable of dispatching custom events abouts its
	 *            state change
	 */
	public StateDependentEventHandler(final SingleNotificationHandler<T, P> handler) {
		myHandler = handler;
	}

	/**
	 * To get state dependent events one must subscribe to them via this
	 * function. The listener will be called every time when the observed object
	 * changes its particular state and uses the notifylisteners function below.
	 * 
	 * @param listener
	 *            who should receive notifications upon state changes in the
	 *            observed object
	 */
	public void subscribeToEvents(final T listener) {
		if (noEventDispatchingInProcess) {
			eventing.add(StateDependentEventHandler.this, listener);
		} else {
			newListeners.add(listener);
		}
	}

	/**
	 * If there are no events needed for a particular listener then they can be
	 * cancelled here.
	 * 
	 * @param listener
	 *            the listener which does not need the state related events
	 *            anymore
	 */
	public void unsubscribeFromEvents(final T listener) {
		if (noEventDispatchingInProcess) {
			eventing.remove(StateDependentEventHandler.this, listener);
		} else {
			cancelledListeners.add(listener);
		}
	}

	/**
	 * Sends out the notifications via the user defined event handler for all
	 * currently listed listeners. If there are new/cancelled subscriptions then
	 * they are added/removed to/from the list of listeners at the end of the
	 * event dispatching loop.
	 * 
	 * @param payload
	 *            to be sent out for all interested parties upon receiving this
	 *            notification
	 */
	public void notifyListeners(final P payload) {
		if (eventing != NullDispatcher.instance) {
			if (noEventDispatchingInProcess) {
				noEventDispatchingInProcess = false;
				eventing.mainNotificationLoop(StateDependentEventHandler.this, payload);
				// Complete dispatching is done (even if there were some nested
				// events)
				noEventDispatchingInProcess = true;

				// Additions and deletions are handled only in the outermost
				// call
				if (newListeners.size() != 0) {
					eventing.addAll(StateDependentEventHandler.this, newListeners);
				}
				if (cancelledListeners.size() != 0) {
					eventing.removeAll(StateDependentEventHandler.this, cancelledListeners);
				}
			} else {
				// Nested call handling
				eventing.mainNotificationLoop(StateDependentEventHandler.this, payload);
			}
		}
	}

}
