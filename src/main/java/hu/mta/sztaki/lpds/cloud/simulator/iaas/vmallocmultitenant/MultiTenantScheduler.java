package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmallocmultitenant;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;

public class MultiTenantScheduler extends Scheduler{

	public MultiTenantScheduler(IaaSService parent) {
		super(parent);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected ConstantConstraints scheduleQueued() {
		// TODO Auto-generated method stub
		return null;
	}
	
	private boolean processRequest(String tenant, CompType c, boolean crit) {
		
		boolean exist = false;	
		PhysicalMachine target;
		for(int i = 0; i < parent.machines.size(); i++) {
			
			// TODO defining comptype, then the resources of it, crit == false?			
			if(parent.machines.get(i).isHostableRequest(c.getResources()) && crit == false) {
				target = parent.machines.get(i);
				exist = true;
				break;
			}
		}
		
		if(exist) {
			target.deployVM(vm, ra, vaSource);	//comptype
		}
		else {
			//create new instance
			
			if() {	// any vm can host this comptype?
				//mapping
			}
			else {
				// create new vm and mapping
				//sort pms
				if() {	//can any pm of sorted pms host vm?
					// take the first one
					if(pm.isOff) {
						// switch on
					}
					// put vm on this pm
				}
				else {
					return false;
				}
			}
		}
		return true;
		
	}

	// defining compinstance
	private void terminateRequest(String tenant, compinstance c) {
		// remove
		if() {	// Xr is empty?
			
			// remove
			if() {	// vm also empty?
				// remove vm
				if() {	// pm also empty then?
					// switch it off
				}
			}
			
		}
	}
}
