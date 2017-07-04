package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

public class StartAction extends Action{

	ModelPM startpm;
	
	public StartAction(int id, ModelPM startpm) {
		super(id);
		this.startpm = startpm;
		// TODO Auto-generated constructor stub
	}
	public ModelPM getstartpm(){
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
