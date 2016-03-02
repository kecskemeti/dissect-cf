package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

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
		if(pmIndex == iaases.length) {
			pmIndex = 0;
		}
	}

	@Override
	public void increaseVMRequestIndex() {
		vmRequestIndex++;
		if (vmRequestIndex == iaases.length) {
			vmRequestIndex = 0;
		}
	}

	@Override
	public void increaseRepoIndex() {
		repoIndex++;
		if(repoIndex == iaases.length) {
			repoIndex = 0;
		}
	}

	
	
	
	
	

}
