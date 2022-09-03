module hu.mta.sztaki.lpds.cloud.simulator.dissectcf.test {
    exports at.ac.uibk.dps.cloud.simulator.test.simple.cloud;
    exports at.ac.uibk.dps.cloud.simulator.test;
    exports at.ac.uibk.dps.cloud.simulator.test.complex;
    exports at.ac.uibk.dps.cloud.simulator.test.simple;
    exports at.ac.uibk.dps.cloud.simulator.test.simple.cloud.pmscheduler;
    exports at.ac.uibk.dps.cloud.simulator.test.simple.cloud.vmscheduler;

    requires hu.mta.sztaki.lpds.cloud.simulator.dissectcf;
    requires org.junit.jupiter.api;
    requires java.logging;
}