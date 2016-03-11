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
import java.util.Map;

/**
 *
 * @author Simon Csaba
 */
public abstract class IaasScheduler extends FirstFitScheduler {

	protected ArrayList<IaaSService> iaases;

	protected int vmRequestIndex = 0;
	private int pmRegisterIndex = 0;
	
	private ArrayList<Class<? extends IaasScheduler>> hierarchy;
	private int maxNumberOfPMPerIaaS = 100;
	private int maxNumberOfIaaS = 10;
	private Map<Long, Integer> PMIaaSList = new HashMap<Long, Integer>();
	private schedulerType type;

	private enum schedulerType {
		IAAS, IAAS_LAST, PM;
	}

	public IaasScheduler(IaaSService parent, ArrayList<Class<? extends IaasScheduler>> hierarchy, int hierarchyLevel) {
		super(parent);

		iaases = new ArrayList<IaaSService>();

		try {
			
			if(hierarchyLevel == hierarchy.size() - 1) {
				type = schedulerType.PM;
			} else if(hierarchyLevel == hierarchy.size() - 2) {
				iaases.add(new IaaSService(hierarchy, AlwaysOnMachines.class, hierarchyLevel + 1));
				type = schedulerType.IAAS_LAST;
			} else if(hierarchyLevel < hierarchy.size() -2) {
				iaases.add(new IaaSService(hierarchy, AlwaysOnMachines.class, hierarchyLevel + 1));
				type = schedulerType.IAAS;
			} else {
				//should never go here
				throw new Exception("Hierarchylevel is bigger than hierarchy size!");
			}
			
//			if (hierarchyLevel < hierarchy.size() - 1) {
//				iaases.add(new IaaSService(hierarchy, AlwaysOnMachines.class, hierarchyLevel + 1));
//				type = schedulerType.IAAS;
//			} else {
//				type = schedulerType.PM;
//			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void registerPM(PhysicalMachine pm) {
		if (type == schedulerType.IAAS_LAST) {
			pmRegisterIndex = 0;
			if (iaases.size() != 1) {
				int min = iaases.get(0).machines.size();
				for (int i = 1; i < iaases.size(); i++) {
					if (iaases.get(i).machines.size() < min) {
						pmRegisterIndex = i;
						min = iaases.get(i).machines.size();
					}
				}
			}
			
			

			iaases.get(pmRegisterIndex).registerHost(pm);
			PMIaaSList.put(pm.id, pmRegisterIndex);

			if (iaases.get(pmRegisterIndex).machines.size() > maxNumberOfPMPerIaaS) {
				try {
					iaases.add(new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class));

					reallocatePMs();

				} catch (Exception ex) {
					ex.printStackTrace();
				}

			}
		}
	}

	@Override
	public void deregisterPM(PhysicalMachine pm) {
		if (type != schedulerType.IAAS_LAST) {
			try {
				int indexOfIaaS = PMIaaSList.get(pm.id);
				iaases.get(indexOfIaaS).deregisterHost(pm);
				PMIaaSList.remove(pm.id);
				int numberOfIaases = iaases.size();
				int min = (numberOfIaases - 1) * maxNumberOfPMPerIaaS / numberOfIaases;
				if (iaases.get(indexOfIaaS).machines.size() < min) {
					reallocatePMs(indexOfIaaS);
					iaases.remove(indexOfIaaS);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	private void reallocatePMs(int indexOfIaaSToDelete) throws Exception {
		int numberOfIaases = iaases.size();
		int totalNumberOfPMs = 0;

		int i, j;

		for (IaaSService iaas : iaases) {
			totalNumberOfPMs += iaas.machines.size();
		}

		//kiszámolom, hogy melyik iaas-be mennyi gép kell at elosztás után
		int[] numberOfPMsAfterReallocate = new int[numberOfIaases];

		//egyenlõ arányban elosztom a gépeket az iaas-k között
		int baseNumberOfPMs;
		if (indexOfIaaSToDelete == -1) {
			baseNumberOfPMs = (int) Math.floor(((float) totalNumberOfPMs) / numberOfIaases);
		} else {
			baseNumberOfPMs = (int) Math.floor(((float) totalNumberOfPMs) / (numberOfIaases - 1));
		}

		for (i = 0; i < numberOfPMsAfterReallocate.length; i++) {
			if (i == indexOfIaaSToDelete) {
				numberOfPMsAfterReallocate[i] = 0;
			} else {
				numberOfPMsAfterReallocate[i] = baseNumberOfPMs;
			}
		}

		//a maradékot szétosztom az elsõ iaas-k között
		int remainder;
		if (indexOfIaaSToDelete == -1) {
			remainder = totalNumberOfPMs - baseNumberOfPMs * (numberOfIaases);
		} else {
			remainder = totalNumberOfPMs - baseNumberOfPMs * (numberOfIaases - 1);
		}

		for (i = 0; i < remainder; i++) {
			if (i != indexOfIaaSToDelete) {
				numberOfPMsAfterReallocate[i] += 1;
			}
		}

		//átdobálom a fölös PM-eket egy ideiglenes listába
		ArrayList<PhysicalMachine> tempPMList = new ArrayList<PhysicalMachine>(100);
		PhysicalMachine pm;
		int diff;
		for (i = 0; i < numberOfIaases; i++) {
			diff = iaases.get(i).machines.size() - numberOfPMsAfterReallocate[i];
			if (iaases.get(i).machines.size() > numberOfPMsAfterReallocate[i]) {
				for (j = 0; j < diff; j++) {
					pm = iaases.get(i).machines.get(0);
					tempPMList.add(pm);
					iaases.get(i).deregisterHost(pm);
				}
			}
		}

		//a listából odaadom azoknak az IaaS-eknek akiknek kevesebb van mint kellene
		for (i = 0; i < numberOfIaases; i++) {
			if (iaases.get(i).machines.size() < numberOfPMsAfterReallocate[i]) {
				diff = numberOfPMsAfterReallocate[i] - iaases.get(i).machines.size();
				for (j = 0; j < diff; j++) {
					pm = tempPMList.get(tempPMList.size() - 1);
					iaases.get(i).registerHost(pm);
					tempPMList.remove(tempPMList.size() - 1);
					PMIaaSList.put(pm.id, i);
				}
			}
		}

	}

	private void reallocatePMs() throws Exception {
		reallocatePMs(-1);
	}

	@Override
	public void scheduleVMrequest(VirtualMachine[] vms, ResourceConstraints rc,
			Repository vaSource, HashMap<String, Object> schedulingConstraints)
			throws VMManager.VMManagementException {
		if (type == schedulerType.IAAS) {
			try {
				iaases.get(vmRequestIndex).requestVM(vms[0].getVa(), rc, vaSource, vms.length, schedulingConstraints);
				increaseVMRequestIndex();
			} catch (NetworkNode.NetworkException ex) {
				ex.printStackTrace();
			}
		} else {
			super.scheduleVMrequest(vms, rc, vaSource, schedulingConstraints);
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

//	public abstract void increaseRegisterPMIndex();
//	public abstract void increaseRepoIndex();
}
