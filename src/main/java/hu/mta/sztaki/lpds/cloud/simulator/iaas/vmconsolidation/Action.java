package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

/*
 * abstract class for saving the actions done
 */
public abstract class Action{
	
	public static enum Type{
		START,
		
		MIGRATION,
		
		SHUTDOWN
	}
	
	int id;
	ArrayList<Action> previous = new ArrayList<Action>();
	
	public Action(int id){
		this.id = id;	
	}
	
	public void addPrevious(Action v){
		this.previous.add(v);
	}
	
	public ArrayList<Action> getPrevious(){
		return previous;
	}
	
	public abstract void createGraph(ArrayList<Action> actions);
	
	public abstract Type getType();
	
}
