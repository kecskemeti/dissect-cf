package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

public class ShutDownAction extends Action{

	Bin_PhysicalMachine shutdownpm; 
	
	public ShutDownAction(int id, Bin_PhysicalMachine shutdownpm) {
		super(id);
		this.shutdownpm = shutdownpm;
	}

	public Bin_PhysicalMachine getshutdownpm(){
		return shutdownpm;
	}

	@Override
	public void createGraph(ArrayList<Action> actions) {
		
		//looking for migrations with this PM as source, which needs to get shut down
		for(int i = 0; i < actions.size(); i++) {
			if(actions.get(i).getType().equals(Type.MIGRATION)){
				if((((MigrationAction) actions.get(i)).getSource()).equals(this.getshutdownpm())){
					this.addPrevious(actions.get(i));
				}
			}
		}
	}

	@Override
	public Type getType() {
		return Type.SHUTDOWN;
	}
}
