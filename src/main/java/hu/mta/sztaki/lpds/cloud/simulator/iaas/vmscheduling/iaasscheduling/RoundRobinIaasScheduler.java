package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.iaasscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import java.util.ArrayList;

/**
 *
 * @author Simon Csaba
 */
public class RoundRobinIaasScheduler extends IaasScheduler {

	public RoundRobinIaasScheduler(IaaSService parent, ArrayList<Class<? extends IaasScheduler>> hierarchy, int hierarchyLevel) {
		super(parent, hierarchy, hierarchyLevel);
	}

//	@Override
//	public void increaseRegisterPMIndex() {
//		pmRegisterIndex++;
//		if (pmRegisterIndex == iaases.size()) {
//			pmRegisterIndex = 0;
//		}
//	}

	@Override
	public void increaseVMRequestIndex() {
		vmRequestIndex++;
		if (vmRequestIndex == iaases.size()) {
			vmRequestIndex = 0;
		}
	}

//	@Override
//	public void increaseRepoIndex() {
//		repoIndex++;
//		if(repoIndex == iaases.length) {
//			repoIndex = 0;
//		}
//	}


}
