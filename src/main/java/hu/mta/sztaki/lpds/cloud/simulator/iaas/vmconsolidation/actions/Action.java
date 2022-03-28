package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.actions;

import java.util.ArrayList;
import java.util.List;

/**
 * This abstract class stores the actions that need to be done inside the
 * simulator.
 */
public abstract class Action {
	public static final Action[] actArrSample = new Action[0];

	public static enum Type {
		/**
		 * The current action needs to start another PM.
		 */
		START,

		/**
		 * The current action needs to migrate a VM.
		 */
		MIGRATION,

		/**
		 * The current action needs to shut down a PM.
		 */
		SHUTDOWN
	}

	private static int idSequence;

	public final int id;
	public final Type type;
	// List of actions, which need to start after completion of this one
	protected List<Action> successors;
	private int remainingPredecessors;
	private boolean finished = false;

	/**
	 * Constructor. Instantiates the empty lists for its predecessors and
	 * successors.
	 * 
	 * @param t the type of the action
	 */
	public Action(final Type t) {
		this.id = idSequence++;
		this.type = t;
		remainingPredecessors = 0;
		successors = new ArrayList<>();
	}

	/**
	 * Adds v as a predecessor of this action AND also this action as a successor of
	 * v.
	 */
	public void addPredecessor(final Action v) {
		remainingPredecessors++;
		v.successors.add(this);
	}

	/**
	 * This Method determines the predecessors of this action, based on its type.
	 */
	public abstract void determinePredecessors(Action[] actions);

	/**
	 * This method is individualized by every subclass and performs the actions.
	 */
	public abstract void execute();

	/**
	 * This method is to be called when the action has been finished.
	 */
	public void finished() {
		finished = true;
		for (final Action a : successors) {
			a.finishedPredecessor();
		}
	}

	public boolean isFinished() {
		return finished;
	}

	private void finishedPredecessor() {
		remainingPredecessors--;
		if (isReady())
			execute();
	}

	public boolean isReady() {
		return remainingPredecessors == 0;
	}

	public String toString() {
		return "Action: " + type + "  :";
	}
}
