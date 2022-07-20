/**
 * 
 */
/**
 * @author gabor
 *
 */
module hu.mta.sztaki.lpds.cloud.simulator.dissectcf {
	exports hu.mta.sztaki.lpds.cloud.simulator.energy;
	exports hu.mta.sztaki.lpds.cloud.simulator.energy.specialized;
	exports hu.mta.sztaki.lpds.cloud.simulator.util;
	exports hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling;
	exports hu.mta.sztaki.lpds.cloud.simulator.notifications;
	exports hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints;
	exports hu.mta.sztaki.lpds.cloud.simulator.io;
	exports hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.pmiterators;
	exports hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation;
	exports hu.mta.sztaki.lpds.cloud.simulator.iaas.statenotifications;
	exports hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling;
	exports hu.mta.sztaki.lpds.cloud.simulator.iaas;
	exports hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel;
	exports hu.mta.sztaki.lpds.cloud.simulator;
	exports hu.mta.sztaki.lpds.cloud.simulator.iaas.helpers;
	exports hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling;

	requires trove4j;
	requires java.xml;
	requires org.apache.commons.lang3;
	requires java.desktop;
	requires java.logging;
	requires it.unimi.dsi.dsiutils;
}
