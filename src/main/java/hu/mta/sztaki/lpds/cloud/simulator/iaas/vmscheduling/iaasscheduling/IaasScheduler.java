package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.iaasscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Simon Csaba
 */
public abstract class IaasScheduler extends FirstFitScheduler {
	
	ArrayList<IaaSService> iaases;
	
	int vmRequestIndex = 0;
	int pmIndex = 0;
	private int maxNumberOfPMPerIaaS = 100;
	//int repoIndex = 0;

	public IaasScheduler(IaaSService parent) {
		super(parent);
		
		iaases = new ArrayList<IaaSService>();
		
		try {
			iaases.add(new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	@Override
	public void registerPM(PhysicalMachine pm) {
		iaases.get(pmIndex).registerHost(pm);
		
		if (iaases.get(pmIndex).machines.size() > maxNumberOfPMPerIaaS) {
			//pmIndex = 0;
			int i, j;
			try {
				iaases.add(new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class));
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			
			int numberOfIaases = iaases.size();
			int totalNumberOfPMs = 0;
			
			for (IaaSService iaas : iaases) {
				totalNumberOfPMs += iaas.machines.size();
			}
			
			//kiszámolom, hogy melyik iaas-be mennyi gép kell at elosztás után
			int[] numberOfPMsAfterReallocate = new int[numberOfIaases];
			
			//egyenlõ arányban elosztom a gépeket az iaas-k között
			
			int baseNumberOfPMs = (int)Math.floor(((float) totalNumberOfPMs) / numberOfIaases);
			for (i = 0; i < numberOfPMsAfterReallocate.length; i++) {
				numberOfPMsAfterReallocate[i] = baseNumberOfPMs;
			}
			
			//a maradékot szotosztom a zelsõ iaas-k között
			int remainder = totalNumberOfPMs - baseNumberOfPMs * numberOfIaases;
			for(i=0; i<remainder; i++) {
				numberOfPMsAfterReallocate[i] += 1;
			}
			
			int numberOfPMsToMove = 1;
			PhysicalMachine pmToMove;
			IaaSService iaasTmp = null;
			for(i=0; i<numberOfIaases-1; i++) {
				iaasTmp = iaases.get(i);
				numberOfPMsToMove = iaasTmp.machines.size() - numberOfPMsAfterReallocate[i];
				for(j=0; j<numberOfPMsToMove; j++) {
					pmToMove = iaasTmp.machines.get(0);
					
					try {
						iaasTmp.deregisterHost(pmToMove);
						iaases.get(iaases.size()-1).registerHost(pmToMove);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
			
			pmIndex = remainder == numberOfIaases ? 0 : remainder;
			
		} else {
			increasePMIndex();
		}
		
		
	}
	
	@Override
	public void scheduleVMrequest(VirtualMachine[] vms, ResourceConstraints rc,
			Repository vaSource, HashMap<String, Object> schedulingConstraints)
			throws VMManager.VMManagementException {
		
		try {
			iaases.get(vmRequestIndex).requestVM(vms[0].getVa(), rc, vaSource, vms.length, schedulingConstraints);
			increaseVMRequestIndex();
		} catch (NetworkNode.NetworkException ex) {
			Logger.getLogger(IaasScheduler.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
	
	@Override
	public ArrayList<IaaSService> getIaases() {
		return iaases;
	}

//	@Override
//	public void registerRepository(Repository repo) {
//		iaases[repoIndex].registerRepository(repo);
//		
//	}
	public abstract void increaseVMRequestIndex();
	
	public abstract void increasePMIndex();

//	public abstract void increaseRepoIndex();
}
