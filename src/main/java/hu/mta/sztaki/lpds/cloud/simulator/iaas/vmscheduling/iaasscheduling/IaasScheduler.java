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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
	private final Map<Long, Integer> PMIaaSList = new HashMap<Long, Integer>();
	private schedulerType type;
	private int hierarchyLevel;

	public enum schedulerType {
		IAAS, IAAS_TOP, IAAS_LAST, PM;
	}

	public IaasScheduler(IaaSService parent, ArrayList<Class<? extends IaasScheduler>> hierarchy, int hierarchyLevel) {
		super(parent);

		this.hierarchy = hierarchy;
		this.hierarchyLevel = hierarchyLevel;

		iaases = new ArrayList<IaaSService>();

		try {
			if (hierarchyLevel == 0) {
				type = schedulerType.IAAS_TOP;
				iaases.add(new IaaSService(hierarchy, AlwaysOnMachines.class, hierarchyLevel + 1));
			} else if (hierarchyLevel == hierarchy.size() - 1) {
				type = schedulerType.PM;
			} else if (hierarchyLevel == hierarchy.size() - 2) {
				iaases.add(new IaaSService(hierarchy, AlwaysOnMachines.class, hierarchyLevel + 1));
				type = schedulerType.IAAS_LAST;
			} else if (hierarchyLevel < hierarchy.size() - 2) {
				iaases.add(new IaaSService(hierarchy, AlwaysOnMachines.class, hierarchyLevel + 1));
				type = schedulerType.IAAS;
			} else {
				//should never go here
				throw new Exception("Hierarchylevel is bigger than hierarchy size!");
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	static int registerNumber = 0;

	@Override
	public void registerPM(PhysicalMachine pm) throws MaxNumberOfPMsReachedException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, IaaSService.IaaSHandlingException {
		if (type == schedulerType.PM) {
			if (parent.machines.size() == maxNumberOfPMPerIaaS) {
				throw new MaxNumberOfPMsReachedException();
			} else {
				return;
			}
		} else if (type == schedulerType.IAAS_LAST || (type == schedulerType.IAAS_LAST || (hierarchy.size() == 2 && type == schedulerType.IAAS_TOP))) {
			//megnézem, hogy melyik IaaS-ben van a legkevesebb gép
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
		} else if (type == schedulerType.IAAS || type == schedulerType.IAAS_TOP) {
			//megnézem, hogy melyik IaaS-ben van a legkevesebb gyerek IaaS
			pmRegisterIndex = 0;
			if (iaases.size() != 1) {
				int min = iaases.get(0).getIaases().size();
				for (int i = 1; i < iaases.size(); i++) {
					if (iaases.get(i).getIaases().size() < min) {
						pmRegisterIndex = i;
						min = iaases.get(i).getIaases().size();
					}
				}
			}
		}

		//ha az tele van, akkor végigmegyek a listán és megpróbálom beletenni valamelyikbe
		boolean registered = false;
		try {
			iaases.get(pmRegisterIndex).registerHostDinamyc(pm);
			PMIaaSList.put(pm.id, pmRegisterIndex);
			registered = true;
		} catch (MaxNumberOfPMsReachedException ex) {
			for (pmRegisterIndex = 0; pmRegisterIndex < iaases.size(); pmRegisterIndex++) {
				try {
					iaases.get(pmRegisterIndex).registerHostDinamyc(pm);
					PMIaaSList.put(pm.id, pmRegisterIndex);
					registered = true;
					return;
				} catch (MaxNumberOfPMsReachedException ex2) {
					//System.out.println("");
				}
			}
		}

		//ha mind tele van
		if (!registered) {
			if ((type == schedulerType.IAAS || type == schedulerType.IAAS_LAST) && iaases.size() >= maxNumberOfIaaS) {
				throw new MaxNumberOfPMsReachedException();
			} else {

				iaases.add(new IaaSService(hierarchy, AlwaysOnMachines.class, hierarchyLevel + 1));
				if (type == schedulerType.IAAS_LAST || (hierarchy.size() == 2 && type == schedulerType.IAAS_TOP)) {
					try {
						reallocatePMs();
					} catch (IaaSService.IaaSHandlingException ex) {
						Logger.getLogger(IaasScheduler.class.getName()).log(Level.SEVERE, null, ex);
					}
					iaases.get(0).registerHostDinamyc(pm);
					PMIaaSList.put(pm.id, 0);
				} else {
					iaases.get(iaases.size() - 1).registerHostDinamyc(pm);
					PMIaaSList.put(pm.id, iaases.size() - 1);
				}

			}
		}

	}

	@Override
	public void deregisterPM(PhysicalMachine pm) {

		if (type != schedulerType.PM) {
			try {
				int indexOfIaaS = PMIaaSList.get(pm.id);
				iaases.get(indexOfIaaS).deregisterHost(pm);
				PMIaaSList.remove(pm.id);

				if (type == schedulerType.IAAS_LAST || (hierarchy.size() == 2 && type == schedulerType.IAAS_TOP)) {
					int numberOfIaases = iaases.size();
					int min = (numberOfIaases - 1) * maxNumberOfPMPerIaaS / numberOfIaases;
					if (iaases.get(indexOfIaaS).machines.size() < min) {
						reallocatePMs(indexOfIaaS);
						iaases.remove(indexOfIaaS);
					}
				}

			} catch (IaaSService.IaaSHandlingException ex) {
				ex.printStackTrace();
			}
		}

	}

	private void reallocatePMs(int indexOfIaaSToDelete) throws IaaSService.IaaSHandlingException {
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
					PMIaaSList.put(pm.id, -1);
					//System.out.println("");
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
					PMIaaSList.put(pm.id, i);
					tempPMList.remove(tempPMList.size() - 1);

				}
			}
		}

	}

	private void reallocatePMs() throws IaaSService.IaaSHandlingException {
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

	public schedulerType getType() {
		return type;
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
