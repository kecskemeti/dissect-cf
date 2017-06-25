package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

public class StartAction extends Action{

	Bin_PhysicalMachine startpm;
	
	public StartAction(int id, Bin_PhysicalMachine startpm) {
		super(id);
		this.startpm = startpm;
		// TODO Auto-generated constructor stub
	}
	public Bin_PhysicalMachine getstartpm(){
		return startpm;
	}
	@Override
	public void createGraph(ArrayList<Action> actions) {
		
	}
	@Override
	public Type getType() {
		return Type.START;
	}
}
