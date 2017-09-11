package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import hu.mta.sztaki.lpds.cloud.simulator.examples.jobhistoryprocessor.JobDispatchingDemo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

	/**
	 * @author Rene Ponto
	 * 
	 * This class manages the consolidation algorithms, which means do several test cases with different
	 * values for the constants of the consolidators. The results are going to be saved inside a seperate
	 * file. There are also methods to set the needed values for consolidation of some algorithms.
	 */
public class ConsolidationController {
	
	Properties props;		// the properties-file, contains the constants of the pso-, abc- and ga-consolidator
	
	private String psoSwarmSize = "20";
	private String psoNrIterations = "50";
	private String psoC1 = "2";
	private String psoC2 = "2";
	private String abcPopulationSize = "10";
	private String abcNrIterations = "50";
	private String abcLimitTrials = "5";
	private String gaPopulationSize = "10";
	private String gaNrIterations = "50";
	private String gaNrCrossovers = "10";
	private String upperThreshold = "0.75";
	private String lowerThreshold = "0.25";
	
	/**
	 * Sets all default values (which are the origin ones) and reads the properties-file.
	 * The file is saved in .xml in the root of the simulator.
	 * @throws IOException 
	 */
	public ConsolidationController() throws IOException {
		
		props = new Properties();
		File file = new File("consolidationProperties.xml");
		FileInputStream fileInput = new FileInputStream(file);
		props.loadFromXML(fileInput);
		fileInput.close();
		
		// set the default values
		
		setPsoProperties(psoSwarmSize, psoNrIterations, psoC1, psoC2);
		setAbcProperties(abcPopulationSize, abcNrIterations, abcLimitTrials);
		setGaProperties(gaPopulationSize, gaNrIterations, gaNrCrossovers);
		
		props.setProperty("upperThreshold", upperThreshold);
		props.setProperty("lowerThreshold", lowerThreshold);
		
		FileOutputStream fileOutput = new FileOutputStream(file);
		props.storeToXML(fileOutput, null);
		fileOutput.close();
	}
	
	/**
	 * This testcase is to find the best configuration of the parameters of the consolidators. For that, 
	 * we define a list of values to test and this method runs the appropriate consolidators for all 
	 * possible combinations of the values. All results are saved inside a csv file.
	 */
	public void runTestcaseOne() {
		//this.initializeTest("Case 1");
		
		//defining lists with values for each parameter of each relevant algorithm
		List<Integer> psoSwarmSizeValues = new ArrayList<>();
		List<Integer> psoNrIterationsValues = new ArrayList<>();
		List<Integer> psoC1Values = new ArrayList<>();
		List<Integer> psoC2Values = new ArrayList<>();
		
		List<Integer> gaPopulationSizeValues = new ArrayList<>();
		List<Integer> gaNrIterationsValues = new ArrayList<>();
		List<Integer> gaNrCrossoversValues = new ArrayList<>();
		
		List<Integer> abcPopulationSizeValues = new ArrayList<>();
		List<Integer> abcNrIterationsValues = new ArrayList<>();
		List<Integer> abcLimitTrialsValues = new ArrayList<>();

		//fill the lists with values
		
		//TODO
		
		//now run the consolidators with every possible combination of their parameters
		//and save the results afterwards
		
		String name = "consolidationResultsOne.csv";
		File file = new File(name);
		boolean firstEntry = true;
		
		//pso consolidator
		for(int first : psoSwarmSizeValues) {
			for(int second : psoNrIterationsValues) {
				for(int third : psoC1Values) {
					for(int fourth : psoC2Values) {
						String[] jobStart = {""};		//TODO
						try {
							JobDispatchingDemo.main(jobStart);
						} catch (Exception e) {
							e.printStackTrace();
						}
						String[] results = null;		//TODO
						String parameters = "SwarmSize: " + psoSwarmSizeValues.get(first) + "; NrOfIterations: " + 
						psoNrIterationsValues.get(second) + "; C1: " + psoC1Values.get(third) + "; C2: " + psoC2Values.get(fourth);
						try {
							this.saveResults(firstEntry, file, "pso", parameters, results);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
		
		//ga consolidator
		firstEntry = false;

		for(int first : gaPopulationSizeValues) {
			for(int second : gaNrIterationsValues) {
				for(int third : gaNrCrossoversValues) {
					String[] jobStart = {""};		//TODO
					try {
						JobDispatchingDemo.main(jobStart);
					} catch (Exception e) {
						e.printStackTrace();
					}
					String[] results = null;		//TODO
					String parameters = "PopulationSize: " + gaPopulationSizeValues.get(first) + "; NrOfIterations: " + 
					gaNrIterationsValues.get(second) + "; NrOfCrossovers: " + gaNrCrossoversValues.get(third);
					try {
						this.saveResults(firstEntry, file, "pso", parameters, results);
					} catch (IOException e) {
						e.printStackTrace();
					}
					firstEntry = false;
				}
			}
		}
		
		//abc consolidator
		firstEntry = false;

		for(int first : abcPopulationSizeValues) {
			for(int second : abcNrIterationsValues) {
				for(int third : abcLimitTrialsValues) {
					String[] jobStart = {""};		//TODO
					try {
						JobDispatchingDemo.main(jobStart);
					} catch (Exception e) {
						e.printStackTrace();
					}
					String[] results = null;		//TODO
					String parameters = "PopulationSize: " + abcPopulationSizeValues.get(first) + "; NrOfIterations: " + 
							abcNrIterationsValues.get(second) + "; LimitTrials: " + abcLimitTrialsValues.get(third);
					try {
						this.saveResults(firstEntry, file, "pso", parameters, results);
					} catch (IOException e) {
						e.printStackTrace();
					}
					firstEntry = false;
				}
			}
		}
		
	}
	
	/**
	 * This testcase is to compare the different consolidators with the best configuration of the
	 * parameters found in testcase one. The results are saved in a csf file, too.
	 */
	public void runTestcaseTwo() {
		//this.initializeTest("Case 1");
		
	}
	
	/**
	 * Save results inside a csv file.
	 * @param i
	 * 			Needed for defining test cases.
	 * @throws IOException 
	 */
	public void saveResults(boolean first, File file, String consolidator, String parameters, String[] results) throws IOException {
		
		BufferedWriter writer = null;
		writer = new BufferedWriter(new FileWriter(file));
		// if this shall be the first entry, the title line has to be written before
		if(first) {
			String[] s = {"consolidator", "parameters", "energy needs", "migrations", "active pms", "overAllocated pms"}; 
			writeLine(writer, s);			
		}
			
		// now everything has to be written inside the file		
		writeLine(writer, results);
		
		writer.flush();
		writer.close();
	}
	
	/**
	 * Writes a line inside the csv- file.
	 * @param w
	 * @param s
	 * @throws IOException
	 */
	private void writeLine(Writer w, String[] s) throws IOException {

		boolean first = true;
	    StringBuilder sb = new StringBuilder();
	    for (String value : s) {
	        if (!first) {
	            sb.append(',');
	        }	            
	        sb.append(value);
	        first = false;
	    }
	    sb.append(System.getProperty("line.separator"));
	    w.append(sb.toString());
	}
	
	/**
	 * Here we define which values are taken for the tests. 
	 */
	private void initializeTest(String string) {
		//example 1 : default values
		if(string == "Case 1") {
			
		}		
		//example 2 : doubled the population, halved the iterations of the algortihms
		if(string == "Case 2") {
			this.setPsoProperties("100", "25", "2", "2");
			this.setAbcProperties("20", "25", "5");
			this.setGaProperties("20", "25", "20");
		}
		
		//example 3 : halved the population, doubled the iterations
		if(string == "Case 3") {
			this.setPsoProperties("25", "100", "2", "2");
			this.setAbcProperties("5", "100", "5");
			this.setGaProperties("5", "100", "5");
		}		
	}
	
	/**
	 * Setter for the constant values of the pso algorithm.
	 * @param swarmSize
	 * 			This value defines the amount of particles.
	 * @param iterations
	 * 			This value defines the number of iterations.
	 * @param c1
	 * 			This value defines the first learning factor.
	 * @param c2
	 * 			This value defines the second learning factor.
	 */
	private void setPsoProperties(String swarmSize, String iterations, String c1, String c2) {
//		this.psoSwarmSize = Integer.parseInt(swarmSize);
//		this.psoNrIterations = Integer.parseInt(iterations);
//		this.psoC1 = Integer.parseInt(c1);
//		this.psoC2 = Integer.parseInt(c2);
		
		File file = new File("consolidationProperties.xml");
		
		props.setProperty("psoSwarmSize", swarmSize);
		props.setProperty("psoNrIterations", iterations);
		props.setProperty("psoC1", c1);
		props.setProperty("psoC2", c2);
		
		try {
			this.saveProps(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Setter for the constant values of the abc algorithm.
	 * @param populationSize
	 * 			This value defines the amount of individuals in the population.
	 * @param iterations
	 * 			This value defines the number of iterations.
	 * @param limitTrials
	 * 			This value defines the maximum number of trials for improvement before a solution is abandoned.
	 */
	private void setAbcProperties(String populationSize, String iterations, String limitTrials) {
//		this.abcPopulationSize = Integer.parseInt(populationSize);
//		this.abcNrIterations = Integer.parseInt(iterations);
//		this.abcLimitTrials = Integer.parseInt(limitTrials);
		
		File file = new File("consolidationProperties.xml");
		
		props.setProperty("abcPopulationSize", populationSize);
		props.setProperty("abcNrIterations", iterations);
		props.setProperty("abcLimitTrials", limitTrials);
		
		try {
			this.saveProps(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Setter for the constant values of the ga algorithm.
	 * @param populationSize
	 * 			This value defines the amount of individuals in the population.
	 * @param iterations
	 * 			This value defines the number of iterations.
	 * @param crossovers
	 * 			This value defines the number of recombinations to perform in each generation.
	 */
	private void setGaProperties(String populationSize, String iterations, String crossovers) {
//		this.gaPopulationSize = Integer.parseInt(populationSize);
//		this.gaNrIterations = Integer.parseInt(iterations);
//		this.gaNrCrossovers = Integer.parseInt(crossovers);
		
		File file = new File("consolidationProperties.xml");
		
		props.setProperty("gaPopulationSize", populationSize);
		props.setProperty("gaNrIterations", iterations);
		props.setProperty("gaNrCrossovers", crossovers);
		
		try {
			this.saveProps(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Saves the properties in the data after changing them.
	 * @param file
	 * @throws IOException
	 */
	private void saveProps(File file) throws IOException {
		FileOutputStream fileOutput = new FileOutputStream(file);
		props.storeToXML(fileOutput, null);
		fileOutput.close();
	}
	
}
