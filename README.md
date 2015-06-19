<p align="center">
<img src="http://users.iit.uni-miskolc.hu/~kecskemeti/DISSECT-CF/logo.jpg"/>
</p>

## Overview

This package represents the DIScrete event baSed Energy Consumption simulaTor
for Clouds and Federations (DISSECT-CF).

It is intended as a lightweight cloud simulator that enables easy customization
and experimentation. It was designed for research purposes only so researchers
could experiment with various internal cloud behavior changes without the need
to actually have one or even multiple cloud infrastructures at hand.

The development of DISSECT-CF started in MTA SZTAKI in 2012, major contirbutions
and design elements were incorporated at the University of Innsbruck. 

When using the simulator for scientific research please cite the following paper:
[Gabor Kecskemeti: DISSECT-CF: A simulator to foster energy-aware scheduling in infrastructure clouds. In Simulation Modelling Practice and Theory. 2015.](http://www.sciencedirect.com/science/article/pii/S1569190X15000842)

Website:
https://github.com/kecskemeti/dissect-cf

Licensing:
GNU Lesser General Public License 3 and later

## Compilation & Installation

Prerequisites: [Apache Maven 3.](http://maven.apache.org/), Java 1.6

After cloning, run the following in the main dir of the checkout:

`mvn clean install javadoc:javadoc`

This command will download all other prerequisites for compilation and testing. Then it will compile the complete sources of DISSECT-CF as well as its test classes. If the compilation is successful, then the tests are executed. In case no test fails, maven proceeds with the packaging and istallation.

The installed simulator will be located in the default maven repository's (e.g., `~/.m2/repository`) following directory: 

`at/ac/uibk/dps/cloud/simulator/dissect-cf/[VERSION]/dissect-cf-[VERSION].jar`

Where `[VERSION]` stands for the currently installed version of the simulator.

The documentation for the simulator's java API will be generated in the following subfolder of the main dir of the checkout:

`target/site/apidocs`

## Getting started

The test cases of the simulator contain many useful examples so one can start working right away with the simulator. In the following list one can find the most essential bits to get to know the internals and the behavior of the simulator:
* Basic time and event management:
  * `at.ac.uibk.dps.cloud.simulator.test.simple.TimedTest.singleEventFire`
  * `at.ac.uibk.dps.cloud.simulator.test.simple.TimedTest.repeatedEventFire`
* Basic use of one time events:
  * `at.ac.uibk.dps.cloud.simulator.test.simple.DeferredEventTest`
* Simple physical machine management and use:
  * `at.ac.uibk.dps.cloud.simulator.test.simple.cloud.PMTest.constructionTest`
  * `at.ac.uibk.dps.cloud.simulator.test.simple.cloud.PMTest.simpleTwoPhasedSmallVMRequest`
* Simple IaaS construction and use:
  * `at.ac.uibk.dps.cloud.simulator.test.simple.cloud.IaaSServiceTest.repoRegistrationTest` - to add storage
  * `at.ac.uibk.dps.cloud.simulator.test.simple.cloud.IaaSServiceTest.capacityMaintenanceTest` - pm addition and overall capacity monitoring
  * `at.ac.uibk.dps.cloud.simulator.test.simple.cloud.IaaSServiceTest.vmRequestTest` - scheduled vm creation
* Starter kit for VM operations:
  *  `at.ac.uibk.dps.cloud.simulator.test.simple.cloud.VMTest.subscriptionTest` - VM state monitoring
  *  `at.ac.uibk.dps.cloud.simulator.test.simple.cloud.VMTest.taskKillingSwitchOff` - New compute task creation
* General use of the resource sharing foundation:
  *  `at.ac.uibk.dps.cloud.simulator.test.simple.cloud.ResourceConsumptionTest.testConsumption`
* Networking basics:
  *  `at.ac.uibk.dps.cloud.simulator.test.simple.cloud.NetworkNodeTest.intraNodeTransferTest`
 
More elaborate examples can be found in the [dissect-cf-examples project](https://github.com/kecskemeti/dissect-cf-examples).

Also, the [wiki](https://github.com/kecskemeti/dissect-cf/wiki) provides further insights to advanced topics (like creating custom schedulers) and offers a [FAQ](https://github.com/kecskemeti/dissect-cf/wiki/Frequently-Asked-Questions). Apart from contributing with code, feel free to contribute there with documentation as well.

Do you still have some questions? If so, then please share them with the simulator's user and developer community at our [Q&A forum](https://groups.google.com/forum/#!forum/dissect-cf-discuss) - a registration is needed to send your questions in.

## Remarks

##### Warning: the master branch of the simulator is intended as a development branch, and might not contain a functional version!

<p align="left">
Build and testing status of the code in the repository:
<img src="https://travis-ci.org/kecskemeti/dissect-cf.svg?branch=master"/>
</p>
