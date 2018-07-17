package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.List;

/**
 * This abstract class stores the actions that need to be done inside the
 * simulator.
 */
public abstract class Action {
	public static final Action[] actArrSample=new Action[0];

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
	// List of actions, which need to be completed before the action starts
	protected List<Action> predecessors;
	// List of actions, which need to start after completion of this one
	protected List<Action> successors;

	/**
	 * Constructor. Instantiates the empty lists for its predecessors and
	 * successors.
	 * 
	 * @param t the type of the action
	 */
	public Action(final Type t) {
		this.id = idSequence++;
		this.type = t;
		predecessors = new ArrayList<>();
		successors = new ArrayList<>();
	}

	/**
	 * Adds v as a predecessor of this action AND also this action as a successor of
	 * v.
	 */
	public void addPredecessor(final Action v) {
		predecessors.add(v);
		v.addSuccessor(this);
	}

	/**
	 * Removes v as a predecessor of this action AND also this action as a successor
	 * of v.
	 */
	public void removePredecessor(final Action v) {
		predecessors.remove(v);
		// v.removeSuccessor(this); //commented out to avoid concurrent modification
		// exception
	}

	public List<Action> getPredecessors() {
		return predecessors;
	}

	public void addSuccessor(final Action v) {
		successors.add(v);
	}

	public void removeSuccessor(final Action v) {
		successors.remove(v);
	}

	public List<Action> getSuccessors() {
		return successors;
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
		for (final Action a : successors) {
			a.removePredecessor(this);
			if (a.getPredecessors().isEmpty())
				a.execute();
		}
	}

	public String toString() {
		return "Action: " + type + "  :";
	}
}
