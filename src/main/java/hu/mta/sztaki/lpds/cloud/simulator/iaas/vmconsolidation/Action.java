package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

/*
 * This abstract class stores the actions that need to be committed in the simulator
 */
public abstract class Action{
	
	public static enum Type{
		/**
		 * The current action needs to start another PM
		 */
		START,
		
		/**
		 * The current action needs to migrate a VM
		 */
		MIGRATION,
		
		/**
		 * The current action needs to shut down a PM
		 */
		SHUTDOWN
	}
	
	int id;
	//List of actions, which need to be completed before the action starts
	ArrayList<Action> previous = new ArrayList<Action>();
	//List of actions, which need to start after completion of this one
	ArrayList<Action> successors = new ArrayList<Action>();
	
	public Action(int id){
		this.id = id;	
	}
	
	public void addPrevious(Action v){
		this.previous.add(v);
	}
	
	public void removePrevious(Action v){
		previous.remove(v);
	}
	
	public ArrayList<Action> getPrevious(){
		return previous;
	}
	
	public void addSuccessor(Action v){
		this.successors.add(v);
	}
	
	public void removeSuccessor(Action v){
		successors.remove(v);
	}
	
	public ArrayList<Action> getSuccessors(){
		return successors;
	}
	
	/**
	 * This method determines the successors of this aciton, based on the predecessors 
	 * of every other action
	 * @param actions
	 */
	public void determineSuccessors(ArrayList<Action> actions){
		for(int i = 0; i < actions.size(); i++){
			for(int j = 0; j < actions.get(i).getPrevious().size(); j++){
				if(actions.get(i).getPrevious().get(j).equals(this)){
					this.addSuccessor(actions.get(i));
				}
			}
		}		
	}
	/**
	 * This Method determines the predecessors of this action, based on its type
	 */
	public abstract void determinePredecessors(ArrayList<Action> actions);
	
	/**
	 * This Method returns the type of the action
	 */
	public abstract Type getType();
	
	public abstract String toString();
}