package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Simon Csaba
 */
public abstract class IaasScheduler extends FirstFitScheduler {

	IaaSService[] iaases;

	int vmRequestIndex = 0;
	int pmIndex = 0;
	int repoIndex = 0;

	int numberOfIaas = 5;

	public IaasScheduler(IaaSService parent) {
		super(parent);

		iaases = new IaaSService[numberOfIaas];
		try {
			for (int i = 0; i < iaases.length; i++) {
				iaases[i] = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);
			}
		} catch (InstantiationException ex) {
			ex.printStackTrace();
		} catch (IllegalAccessException ex) {
			ex.printStackTrace();
		} catch (IllegalArgumentException ex) {
			ex.printStackTrace();
		} catch (InvocationTargetException ex) {
			ex.printStackTrace();
		} catch (NoSuchMethodException ex) {
			ex.printStackTrace();
		} catch (SecurityException ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void scheduleVMrequest(VirtualMachine[] vms, ResourceConstraints rc,
			Repository vaSource, HashMap<String, Object> schedulingConstraints)
			throws VMManager.VMManagementException {

		try {
			iaases[vmRequestIndex].requestVM(vms[0].getVa(), rc, vaSource, vms.length, schedulingConstraints);
			increaseVMRequestIndex();
		} catch (NetworkNode.NetworkException ex) {
			Logger.getLogger(IaasScheduler.class.getName()).log(Level.SEVERE, null, ex);
		}

	}

	@Override
	public void registerPM(PhysicalMachine pm) {
		iaases[pmIndex].registerHost(pm);
		increasePMIndex();
	}

	@Override
	public void registerRepository(Repository repo) {
		iaases[repoIndex].registerRepository(repo);
		
	}
	
	
	

	public abstract void increaseVMRequestIndex();
	
	public abstract void increasePMIndex();
	
	public abstract void increaseRepoIndex();

}
