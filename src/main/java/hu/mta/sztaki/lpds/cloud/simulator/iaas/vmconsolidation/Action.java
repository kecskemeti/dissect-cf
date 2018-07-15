package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.List;

/**
 * This abstract class stores the actions that need to be done inside the simulator.
 */
public abstract class Action{

	public static enum Type{
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

	protected int id;
	//List of actions, which need to be completed before the action starts
	protected List<Action> predecessors;
	//List of actions, which need to start after completion of this one
	protected List<Action> successors;

	/**
	 * Constructor. Instantiates the empty lists for its predecessors and successors.
	 * @param id
	 */
	public Action(int id){
		this.id = id;
		predecessors=new ArrayList<>();
		successors=new ArrayList<>();
	}

	/**
	 * Adds v as a predecessor of this action AND also this action as a 
	 * successor of v.
	 */
	public void addPredecessor(Action v){
		predecessors.add(v);
		v.addSuccessor(this);
	}

	/**
	 * Removes v as a predecessor of this action AND also this action as a 
	 * successor of v.
	 */
	public void removePredecessor(Action v){
		predecessors.remove(v);
		//v.removeSuccessor(this); //commented out to avoid concurrent modification exception
	}
	
	public List<Action> getPredecessors(){
		return predecessors;
	}

	public void addSuccessor(Action v){
		successors.add(v);
	}

	public void removeSuccessor(Action v){
		successors.remove(v);
	}

	public List<Action> getSuccessors(){
		return successors;
	}

	/**
	 * This Method determines the predecessors of this action, based on its type.
	 */
	public abstract void determinePredecessors(List<Action> actions);
	
	/**
	 * This method is individualized by every subclass and performs the actions.
	 */
	public abstract void execute();

	/**
	 * This method is to be called when the action has been finished.
	 */
	public void finished() {
		for(Action a : successors) {
			a.removePredecessor(this);
			if(a.getPredecessors().isEmpty())
				a.execute();
		}
	}

	/**
	 * This Method returns the type of the action.
	 */
	public abstract Type getType();

	public abstract String toString();
}
