package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.iaasscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;

/**
 *
 * @author Simon Csaba
 */
public class RoundRobinIaasScheduler extends IaasScheduler{

	public RoundRobinIaasScheduler(IaaSService parent) {
		super(parent);
	}

	@Override
	public void increasePMIndex() {
		pmIndex++;
		if(pmIndex == iaases.size()) {
			pmIndex = 0;
		}
	}

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
