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
[Gabor Kecskemeti: DISSECT-CF: A simulator to foster energy-aware scheduling in infrastructure clouds. In Simulation Modelling Practice and Theory. 2015.](https://www.researchgate.net/publication/277297657_DISSECT-CF_a_simulator_to_foster_energy-aware_scheduling_in_infrastructure_clouds) DOI: [10.1016/j.simpat.2015.05.009](http://dx.doi.org/10.1016/j.simpat.2015.05.009)

Website:
https://github.com/kecskemeti/dissect-cf

Documentation website:
https://kecskemeti.github.io/dissect-cf

Archive for past (even pre-github) releases:
http://users.iit.uni-miskolc.hu/~kecskemeti/DISSECT-CF

Licensing:
GNU Lesser General Public License 3 and later

Optimisations on the code were done using the java profiler called [jprofiler](http://www.ej-technologies.com/products/jprofiler/overview.html). 

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

### Minimum runtime dependencies
DISSECT-CF depends on the following libraries during its runtime: 
* Java 1.6
* [GNU trove4j 3.0.3](http://trove.starlight-systems.com)
* [Apache Commons Lang3 3.4](https://commons.apache.org/proper/commons-lang/)

###### Hint:
Although these dependencies can be collected individually. If one installed the simulator according to description above, then except for Java6, all dependencies are located in the local maven repository (e.g., `~/.m2/repository`).  

### Overview of the basic functionalities  

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
 
More elaborate examples can be found in the [dissect-cf-examples project](https://github.com/kecskemeti/dissect-cf-examples) and in the [dcf-exercises project](https://github.com/kecskemeti/dcf-exercises).

Also, the [wiki](https://github.com/kecskemeti/dissect-cf/wiki) provides further insights to advanced topics (like creating custom schedulers) and offers a [FAQ](https://github.com/kecskemeti/dissect-cf/wiki/Frequently-Asked-Questions). Apart from contributing with code, feel free to contribute there with documentation as well.

Do you still have some questions? If so, then please share them with the simulator's user and developer community at our [Q&A forum](https://groups.google.com/forum/#!forum/dissect-cf-discuss) - a registration is needed to send your questions in.

## Remarks

##### Warning: the master branch of the simulator is intended as a development branch, and might not contain a functional version!

<p align="left">
Build and testing status of the code in the repository:
<img src="https://travis-ci.org/kecskemeti/dissect-cf.svg?branch=master"/>
</p>
