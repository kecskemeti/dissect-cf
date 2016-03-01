/*
 *  ========================================================================
 *  DIScrete event baSed Energy Consumption simulaTor 
 *    					             for Clouds and Federations (DISSECT-CF)
 *  ========================================================================
 *  
 *  This file is part of DISSECT-CF.
 *  
 *  DISSECT-CF is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or (at
 *  your option) any later version.
 *  
 *  DISSECT-CF is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 *  General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with DISSECT-CF.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */
package hu.mta.sztaki.lpds.cloud.simulator.util;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.PowerStateKind;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.PhysicalMachineController;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumMap;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class offers a simple interface to prepare an IaaSService class based on
 * data loaded from an XML cloud configuration file.
 * 
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2012"
 */
public class CloudLoader {

	/**
	 * Offers the IaaSService creator functionality by defining the sax parser
	 * for the XML cloud configuration.
	 * 
	 * @param fileName
	 *            the name of the xml file containing the configuration of the cloud (a
	 *            samlple xml can be found in
	 *            at.ac.uibk.dps.cloud.simulator.test.simple.UtilTest)
	 * @return the instantiated IaaSservice that complies with the configuration
	 *         specified in the XML file received as the parameter
	 * @throws IOException if there was some problem with finding/accessing the xml file
	 * @throws SAXException if there was some problem parsing the configuration file
	 * @throws ParserConfigurationException
	 */
	public static IaaSService loadNodes(String fileName)
			throws IOException, SAXException, ParserConfigurationException {
		Calendar c = Calendar.getInstance();
		System.out.println("Cloud Loader starts for: " + fileName + " at " + c.getTimeInMillis());
		final ArrayList<IaaSService> returner = new ArrayList<IaaSService>();
		SAXParserFactory spf = SAXParserFactory.newInstance();
		SAXParser saxParser = spf.newSAXParser();
		XMLReader xmlReader = saxParser.getXMLReader();
		xmlReader.setContentHandler(new DefaultHandler() {
			boolean incloud = false;
			boolean inmachine = false;
			boolean inrepo = false;
			String mid;
			String rid;
			double cores;
			double processing;
			long memory;
			int startuptime;
			int shutdowntime;
			long disksize;
			long inbw;
			long outbw;
			long diskbw;
			HashMap<String, Integer> latencymap;
			EnumMap<PowerStateKind, EnumMap<State, PowerState>> powerTransitions = new EnumMap<PhysicalMachine.PowerStateKind, EnumMap<State, PowerState>>(
					PhysicalMachine.PowerStateKind.class);
			PowerStateKind currentKind;

			@SuppressWarnings("unchecked")
			@Override
			public void startElement(String uri, String localName, String qName, Attributes attributes)
					throws SAXException {
				if (qName.equals("cloud")) {
					incloud = true;
					try {
						returner.add(new IaaSService(
								(Class<? extends Scheduler>) Class.forName(attributes.getValue("scheduler")),
								(Class<? extends PhysicalMachineController>) Class
										.forName(attributes.getValue("pmcontroller"))));
					} catch (Exception e) {
						throw new SAXException(
								"Cannot instantiate IaaS service because of an improper scheduler type designation", e);
					}
				}
				if (incloud) {
					if (qName.equals("machine")) {
						inmachine = true;
						latencymap = new HashMap<String, Integer>();
						cores = Double.parseDouble(attributes.getValue("cores"));
						processing = Double.parseDouble(attributes.getValue("processing"));
						memory = Long.parseLong(attributes.getValue("memory"));
						mid = attributes.getValue("id");
					}
					if (inmachine) {
						if (qName.equals("statedelays")) {
							startuptime = Integer.parseInt(attributes.getValue("startup"));
							shutdowntime = Integer.parseInt(attributes.getValue("shutdown"));
						}
					}
					if (qName.equals("repository")) {
						inrepo = true;
						disksize = Long.parseLong(attributes.getValue("capacity"));
						diskbw = Long.parseLong(attributes.getValue("diskBW"));
						inbw = Long.parseLong(attributes.getValue("inBW"));
						outbw = Long.parseLong(attributes.getValue("outBW"));
						rid = attributes.getValue("id");
					}
					if (qName.equals("latency") && inrepo) {
						latencymap.put(attributes.getValue("towards"), Integer.parseInt(attributes.getValue("value")));
					}
					if (qName.equals("powerstates")) {
						currentKind = PowerStateKind.valueOf(attributes.getValue("kind"));
						powerTransitions.put(currentKind,
								new EnumMap<PhysicalMachine.State, PowerState>(PhysicalMachine.State.class));
					}
					if (qName.equals("power") && currentKind != null) {
						EnumMap<PhysicalMachine.State, PowerState> stateSet = powerTransitions.get(currentKind);
						String currentStateString = attributes.getValue("inState");
						// Divider is needed so input and output spreaders are
						// symmetrically consuming energy
						int currentDivider = currentKind.equals(PhysicalMachine.PowerStateKind.host) ? 1 : 2;
						try {
							double idleCon = Double.parseDouble(attributes.getValue("idle")) / currentDivider;
							double maxCon = Double.parseDouble(attributes.getValue("max")) / currentDivider;
							if ("default".equals(currentStateString)) {
								PowerState defaultState = new PowerState(idleCon, maxCon - idleCon,
										(Class<? extends PowerState.ConsumptionModel>) Class
												.forName(attributes.getValue("model")));
								for (PhysicalMachine.State aState : PhysicalMachine.State.values()) {
									if (stateSet.containsKey(aState)) {
										continue;
									}
									stateSet.put(aState, defaultState);
								}
							} else {
								stateSet.put(PhysicalMachine.State.valueOf(currentStateString),
										new PowerState(idleCon, maxCon - idleCon,
												(Class<? extends PowerState.ConsumptionModel>) Class
														.forName(attributes.getValue("model"))));
							}
						} catch (Exception e) {
							throw new SAXException(
									"Cannot instantiate PowerStatee because of a consumption model type designation",
									e);
						}
					}
				}
			}

			@Override
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if (qName.equals("cloud")) {
					incloud = false;
				}
				if (incloud) {
					if (qName.equals("repository")) {
						inrepo = false;
						if (!inmachine) {
							Repository newRepo = new Repository(disksize, rid, inbw, outbw, diskbw, latencymap);
							returner.get(0).registerRepository(newRepo);
							// TODO: if the repository class also implements
							// proper power state management then this must move
							// there
							EnumMap<PhysicalMachine.State, PowerState> storagePowerBehavior = powerTransitions
									.get(PowerStateKind.storage);
							newRepo.diskinbws
									.setCurrentPowerBehavior(storagePowerBehavior.get(PhysicalMachine.State.RUNNING));
							newRepo.diskoutbws
									.setCurrentPowerBehavior(storagePowerBehavior.get(PhysicalMachine.State.RUNNING));
							EnumMap<PhysicalMachine.State, PowerState> networkPowerBehavior = powerTransitions
									.get(PowerStateKind.network);
							newRepo.inbws
									.setCurrentPowerBehavior(networkPowerBehavior.get(PhysicalMachine.State.RUNNING));
							newRepo.outbws
									.setCurrentPowerBehavior(networkPowerBehavior.get(PhysicalMachine.State.RUNNING));
							powerTransitions = new EnumMap<PhysicalMachine.PowerStateKind, EnumMap<State, PowerState>>(
									PhysicalMachine.PowerStateKind.class);
						}
					}
					if (qName.equals("machine")) {
						inmachine = false;
						returner.get(0)
								.registerHost(new PhysicalMachine(cores, processing, memory,
										new Repository(disksize, rid, inbw, outbw, diskbw, latencymap), startuptime,
										shutdowntime, powerTransitions));
						powerTransitions = new EnumMap<PhysicalMachine.PowerStateKind, EnumMap<State, PowerState>>(
								PhysicalMachine.PowerStateKind.class);
					}
					if (qName.equals("powerstates")) {
						currentKind = null;
					}
				}
			}
		});
		BufferedReader br = new BufferedReader(new FileReader(new File(fileName)));
		xmlReader.parse(new InputSource(br));
		br.close();
		c = Calendar.getInstance();
		System.out.println("Cloud Loader stops for: " + fileName + " at " + c.getTimeInMillis());
		return returner.get(0);
	}
}
