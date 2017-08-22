package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmallocmultitenant;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.Consolidator;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class MultiTenantConsolidator extends Consolidator{

	public MultiTenantConsolidator(IaaSService toConsolidate, long consFreq) {
		super(toConsolidate, consFreq);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void doConsolidation(PhysicalMachine[] pmList) {
//		try {
//			reoptimize(pmList);
//		} catch (VMManagementException e) {
//			e.printStackTrace();
//		} catch (NetworkException e) {
//			e.printStackTrace();
//		}
//	}
//
//	private void reoptimize(PhysicalMachine[] pms) throws VMManagementException, NetworkException {
//		// first, check if the number of active pms can be minimized
//		for(PhysicalMachine actualPm : pms) {
//			for(int i = 0; i < pms[i].publicVms.size(); i++) {
//				// migrate vm to first fit pm
//			}
//			
//			if(actualPm.publicVms.size() == 0) {
//				// commit the tentative migrations
//				actualPm.switchoff(null);
//			}
//			else {
//				// undo migrations
//			}
//		}
//
//		// check if a secure pm can take load from two (non-secure) pms
//		while(/*bed*/) {
//			PhysicalMachine pmBoth;
//			pmBoth.turnon();
//			
//			//migrate all vms of unsecure pm1
//			for(int i = 0; i < pm1.publicVms.size(); i++) {
//				//migrate
//			}
//			pm1.switchoff(null);
//			
//			//migrate all vms of unsecure pm2
//			for(int j = 0; j < pm2.publicVms.size(); 2++) {
//				//migrate
//			}
//			pm2.switchoff(null);
//		}
//
//		// check if the load of a secure pm can be moved to a non-secure pm
//		while(/*bed*/) {
//			PhysicalMachine unsecurePm;
//			unsecurePm.turnon();
//			for(int i = 0; i < securePm.publicVms.size(); i++) {
//				//migrate
//			}
//			securePm.switchOff(null);
//		}		
	}
	
	
}
