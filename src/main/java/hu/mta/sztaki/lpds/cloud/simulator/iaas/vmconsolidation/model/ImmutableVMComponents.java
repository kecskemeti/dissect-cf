package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.model;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;

public class ImmutableVMComponents {
	final public VirtualMachine vm;
	final public ModelPM initialHost;
	final public int id;

	public ImmutableVMComponents(final VirtualMachine vm, final ModelPM initialHost, final int id) {
		this.vm = vm;
		this.initialHost = initialHost;
		this.id = id;
	}
}
