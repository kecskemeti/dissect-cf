package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

/*
 * abstract class for saving the actions done
 */
public class Action{
	
	int id;
	ArrayList<Action> previous = new ArrayList<Action>();
	
	Bin_PhysicalMachine source;
	Bin_PhysicalMachine target;
	Item_VirtualMachine vm;
	Bin_PhysicalMachine shutdownpm; 
	Bin_PhysicalMachine startpm;
	
	public Action(int id, Bin_PhysicalMachine startpm, Bin_PhysicalMachine source, Bin_PhysicalMachine target, Item_VirtualMachine vm, Bin_PhysicalMachine shutdownpm){
		this.id = id;
		this.target = target;
		this.source = source;
		this.vm = vm;
		this.startpm = startpm;
		this.shutdownpm = shutdownpm;
	}
	
	public void addPrevious(Action v){
		this.previous.add(v);
	}
	
	public ArrayList<Action> getPrevious(){
		return previous;
	}
	
	public Bin_PhysicalMachine getTarget(){
		return target;
	}
	
	public Bin_PhysicalMachine getSource(){
		return source;
	}
	
	public Item_VirtualMachine getItemVM(){
		return vm;
	}
	
	public Bin_PhysicalMachine getstartpm(){
		return startpm;
	}
	
	public Bin_PhysicalMachine getshutdownpm(){
		return shutdownpm;
	}
}
