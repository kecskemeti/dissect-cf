package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;

public class ImmutablePMComponents {
	public final PhysicalMachine pm; // the real PM inside the simulator
	public final int number;
	public final ConstantConstraints lowerThrResources, upperThrResources;

	public ImmutablePMComponents(final PhysicalMachine pm, final int number, final double lowerThreshold, final double upperThreshold) {
		this.pm = pm;
		this.number = number;
		final AlterableResourceConstraints rc = new AlterableResourceConstraints(pm.getCapacities());
		rc.multiply(lowerThreshold);
		this.lowerThrResources = new ConstantConstraints(rc);
		rc.multiply(upperThreshold / lowerThreshold);
		this.upperThrResources = new ConstantConstraints(rc);
	}
}
