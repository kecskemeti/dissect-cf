package at.ac.uibk.dps.cloud.simulator.test.complex;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import at.ac.uibk.dps.cloud.simulator.test.simple.cloud.PMTest;
import at.ac.uibk.dps.cloud.simulator.test.simple.cloud.VMTest;

@RunWith(Suite.class)
@SuiteClasses({ 
		VMTest.class, PMTest.class })
public class VMPMTestCrossDependencyCheck {

}
