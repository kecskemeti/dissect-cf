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
	int pmRegisterIndex = 0;
	int pmDeregisterIndex = 0;
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
		iaases.get(pmRegisterIndex).registerHost(pm);

		if (iaases.get(pmRegisterIndex).machines.size() > maxNumberOfPMPerIaaS) {
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
			int baseNumberOfPMs = (int) Math.floor(((float) totalNumberOfPMs) / numberOfIaases);
			for (i = 0; i < numberOfPMsAfterReallocate.length; i++) {
				numberOfPMsAfterReallocate[i] = baseNumberOfPMs;
			}

			//a maradékot szotosztom az elsõ iaas-k között
			int remainder = totalNumberOfPMs - baseNumberOfPMs * numberOfIaases;
			for (i = 0; i < remainder; i++) {
				numberOfPMsAfterReallocate[i] += 1;
			}

			int numberOfPMsToMove = 1;
			PhysicalMachine pmToMove;
			IaaSService iaasTmp = null;
			for (i = 0; i < numberOfIaases - 1; i++) {
				iaasTmp = iaases.get(i);
				numberOfPMsToMove = iaasTmp.machines.size() - numberOfPMsAfterReallocate[i];
				for (j = 0; j < numberOfPMsToMove; j++) {
					pmToMove = iaasTmp.machines.get(0);

					try {
						iaasTmp.deregisterHost(pmToMove);
						iaases.get(iaases.size() - 1).registerHost(pmToMove);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}

			pmRegisterIndex = remainder == numberOfIaases ? 0 : remainder;

		} else {
			increaseRegisterPMIndex();
		}
	}

	@Override
	public void deregisterPM(PhysicalMachine pm) {
		try {
			iaases.get(pmDeregisterIndex).deregisterHost(pm);
			int numberOfIaases = iaases.size();
			if (iaases.get(pmDeregisterIndex).machines.size() < (numberOfIaases - 1) * maxNumberOfPMPerIaaS / numberOfIaases) {
				int totalNumberOfPMs = 0;
				int i, j;
				IaaSService iaasToDelete = iaases.get(pmDeregisterIndex);

				for (IaaSService iaas : iaases) {
					totalNumberOfPMs += iaas.machines.size();
				}

				//kiszámolom, hogy melyik iaas-be mennyi gép kell at elosztás után
				int[] numberOfPMsAfterReallocate = new int[numberOfIaases];

				//egyenlõ arányban elosztom a gépeket az iaas-k között
				int baseNumberOfPMs = (int) Math.floor(((float) totalNumberOfPMs) / (numberOfIaases - 1));
				for (i = 0; i < numberOfPMsAfterReallocate.length; i++) {
					if (i == pmDeregisterIndex) {
						numberOfPMsAfterReallocate[i] = 0;
					} else {
						numberOfPMsAfterReallocate[i] = baseNumberOfPMs;
					}
				}

				//a maradékot szétosztom az elsõ iaas-k között
				int remainder = totalNumberOfPMs - baseNumberOfPMs * (numberOfIaases - 1);
				for (i = 0; i < remainder; i++) {
					if (i != pmDeregisterIndex) {
						numberOfPMsAfterReallocate[i] += 1;
					}
				}

				int numberOfPMsToMove;
				PhysicalMachine pmToMove;
				IaaSService iaasTmp;
				for (i = 0; i < numberOfIaases - 1; i++) {
					if (i == pmDeregisterIndex) {
						continue;
					}
					
					iaasTmp = iaases.get(i);
					numberOfPMsToMove = numberOfPMsAfterReallocate[i] - iaasTmp.machines.size();
					for (j = 0; j < numberOfPMsToMove; j++) {
						pmToMove = iaasToDelete.machines.get(0);

						try {
							iaasToDelete.deregisterHost(pmToMove);
							iaasTmp.registerHost(pmToMove);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				}

			}
		} catch (Exception ex) {
			ex.printStackTrace();
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

	public abstract void increaseRegisterPMIndex();

	public abstract void increaseDeregisterPMIndex();

//	public abstract void increaseRepoIndex();
}
