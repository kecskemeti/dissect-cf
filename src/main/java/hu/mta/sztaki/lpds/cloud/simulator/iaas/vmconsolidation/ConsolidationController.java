package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.Properties;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
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
	
	int psoSwarmSize = 20;
	int psoNrIterations = 50;
	int psoC2 = 2;
	int psoC1 = 2;
	int abcPopulationSize = 10;
	int abcNrIterations = 50;
	int abcLimitTrials = 5;
	int gaPopulationSize = 10;
	int gaNrIterations = 50;
	int gaNrCrossovers = 10;
	double upperThreshold = 0.75;
	double lowerThreshold = 0.25;
	
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
		
		// the default values
		
		setPsoProperties("20", "50", "2", "2");
		setAbcProperties("10", "50", "5");
		setGaProperties("10", "50", "10");
	}
	
	/**
	 * This testcase is to find the best configuration of the parameters of the consolidators. For that, 
	 * we define a list of values to test and this method runs the appropriate consolidators for all 
	 * possible combinations of the values. All results are saved inside a csv file.
	 */
	public void runTestcaseOne() {
		this.initializeTest("Case 1");
		
		
	}
	
	/**
	 * This testcase is to compare the different consolidators with an explicit configuration of the
	 * parameters. The results are saved in a csf file, too.
	 */
	public void runTestcaseTwo() {
		this.initializeTest("Case 1");
		
	}
	
	/**
	 * Save results inside a csv file.
	 * @param i
	 * 			Needed for defining test cases.
	 * @throws IOException 
	 */
	public void saveResults(String test, String[] results) throws IOException {
		
		String[] s = {"consolidator", "parameters", "energy needs", "migrations", "active pms", "overAllocated pms"}; 
		String name = "consolidationResults" + test + ".csv";
		File file = new File(name);
		BufferedWriter writer = null;
		writer = new BufferedWriter(new FileWriter(file));
		writeLine(writer, s);
		
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
		this.psoSwarmSize = Integer.parseInt(swarmSize);
		this.psoNrIterations = Integer.parseInt(iterations);
		this.psoC1 = Integer.parseInt(c1);
		this.psoC2 = Integer.parseInt(c2);
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
		this.abcPopulationSize = Integer.parseInt(populationSize);
		this.abcNrIterations = Integer.parseInt(iterations);
		this.abcLimitTrials = Integer.parseInt(limitTrials);
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
		this.gaPopulationSize = Integer.parseInt(populationSize);
		this.gaNrIterations = Integer.parseInt(iterations);
		this.gaNrCrossovers = Integer.parseInt(crossovers);
	}
	
}
