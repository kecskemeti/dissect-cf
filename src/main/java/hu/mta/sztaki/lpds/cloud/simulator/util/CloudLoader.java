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

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.PhysicalMachineController;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class CloudLoader {
	public static IaaSService loadNodes(String fileName) throws IOException,
			SAXException, ParserConfigurationException {
		Calendar c = Calendar.getInstance();
		System.out.println("Node Loader starts for: " + fileName + " at "
				+ c.getTimeInMillis());
		final Vector<IaaSService> returner = new Vector<IaaSService>();
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
			int idlecons;
			int maxcons;
			int startuptime;
			int shutdowntime;
			long disksize;
			long inbw;
			long outbw;
			long diskbw;
			HashMap<String, Integer> latencymap;

			@Override
			public void startElement(String uri, String localName,
					String qName, Attributes attributes) throws SAXException {
				if (qName.equals("cloud")) {
					incloud = true;
					try {
						returner.add(new IaaSService(
								(Class<? extends Scheduler>) Class
										.forName(attributes
												.getValue("scheduler")),
								(Class<? extends PhysicalMachineController>) Class
										.forName(attributes
												.getValue("pmcontroller"))));
					} catch (Exception e) {
						throw new SAXException(
								"Cannot instantiate IaaS service because of an improper scheduler type designation",
								e);
					}
				}
				if (incloud) {
					if (qName.equals("machine")) {
						inmachine = true;
						latencymap = new HashMap<String, Integer>();
						cores = Double.parseDouble(attributes.getValue("cores"));
						processing = Double.parseDouble(attributes
								.getValue("processing"));
						memory = Long.parseLong(attributes.getValue("memory"));
						mid = attributes.getValue("id");
					}
					if (inmachine) {
						if (qName.equals("power")) {
							idlecons = Integer.parseInt(attributes
									.getValue("idle"));
							maxcons = Integer.parseInt(attributes
									.getValue("max"));
						}
						if (qName.equals("statedelays")) {
							startuptime = Integer.parseInt(attributes
									.getValue("startup"));
							shutdowntime = Integer.parseInt(attributes
									.getValue("shutdown"));
						}
					}
					if (qName.equals("repository")) {
						inrepo = true;
						disksize = Long.parseLong(attributes
								.getValue("capacity"));
						diskbw = Long.parseLong(attributes.getValue("diskBW"));
						inbw = Long.parseLong(attributes.getValue("inBW"));
						outbw = Long.parseLong(attributes.getValue("outBW"));
						rid = attributes.getValue("id");
					}
					if (qName.equals("latency") && inrepo) {
						latencymap.put(attributes.getValue("towards"),
								Integer.parseInt(attributes.getValue("value")));
					}
				}
			}

			@Override
			public void endElement(String uri, String localName, String qName)
					throws SAXException {
				if (qName.equals("cloud")) {
					incloud = false;
				}
				if (incloud) {
					if (qName.equals("repository")) {
						inrepo = false;
						if (!inmachine) {
							returner.get(0).registerRepository(
									new Repository(disksize, rid, inbw, outbw,
											diskbw, latencymap));
						}
					}
					if (qName.equals("machine")) {
						inmachine = false;
						returner.get(0).registerHost(
								new PhysicalMachine(cores, processing, memory,
										new Repository(disksize, rid, inbw,
												outbw, diskbw, latencymap),
										/* idlecons, maxcons, */startuptime,
										shutdowntime));

					}
				}
			}
		});
		BufferedReader br = new BufferedReader(new FileReader(
				new File(fileName)));
		xmlReader.parse(new InputSource(br));
		br.close();
		c = Calendar.getInstance();
		System.out.println("Node Loader stops for: " + fileName + " at "
				+ c.getTimeInMillis());
		return returner.get(0);
	}
}
