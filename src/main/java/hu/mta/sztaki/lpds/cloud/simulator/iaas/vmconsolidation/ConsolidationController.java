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

	/**
	 * @author Rene Ponto
	 * 
	 * This class manages the consolidation algorithms, which means do several test cases with different
	 * values for the constants of the consolidators. The results are going to be saved inside a seperate
	 * file. There are also methods to set the needed values for consolidation of some algorithms.
	 */
public class ConsolidationController {
	
	Properties props;		// the properties-file, contains the constants of the pso-, abc- and ga-consolidator
	
	private String psoDefaultSwarmSize = "20";
	private String psoDefaultNrIterations = "50";
	private String psoDefaultC1 = "2";
	private String psoDefaultC2 = "2";
	private String abcDefaultPopulationSize = "10";
	private String abcDefaultNrIterations = "50";
	private String abcDefaultLimitTrials = "5";
	private String gaDefaultPopulationSize = "10";
	private String gaDefaultNrIterations = "50";
	private String gaDefaultNrCrossovers = "10";
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
		
		setPsoProperties(psoDefaultSwarmSize, psoDefaultNrIterations, psoDefaultC1, psoDefaultC2);
		setAbcProperties(abcDefaultPopulationSize, abcDefaultNrIterations, abcDefaultLimitTrials);
		setGaProperties(gaDefaultPopulationSize, gaDefaultNrIterations, gaDefaultNrCrossovers);
		
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
	 * 
	 * @param test
	 * 				If set to true, default values are taken, otherwise the lists get filled and all 
	 * 				combinations out of these values are taken
	 */
	public void runTestcaseOne(boolean test) {		
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
		
		if(!test) {
			for(int i = 10; i < 26; i++) {
				psoSwarmSizeValues.add(i);
				gaPopulationSizeValues.add(i);
				gaNrCrossoversValues.add(i);
				abcPopulationSizeValues.add(i);
			}	
			
			for(int i = 20; i < 51; i++) {
				psoNrIterationsValues.add(i);
				gaNrIterationsValues.add(i);
				abcNrIterationsValues.add(i);
			}
			
			int x = 0;
			while(x < 6) {
				psoC1Values.add(x);
				psoC2Values.add(x);
				x++;
			}		

			for(int i = 2; i < 11; i++) {
				abcLimitTrialsValues.add(i);
			}	
		}
		
		//test values, only one run with defaults
		if(test) {
			psoSwarmSizeValues.add(20);
			psoNrIterationsValues.add(50);
			psoC1Values.add(2);
			psoC2Values.add(2);
						
			gaPopulationSizeValues.add(10);
			gaNrIterationsValues.add(50);
			gaNrCrossoversValues.add(10);			
			
			abcPopulationSizeValues.add(10);
			abcNrIterationsValues.add(50);
			abcLimitTrialsValues.add(5);			
		}
		
		//now run the consolidators with every possible combination of their parameters
		//and save the results afterwards
		
		String name = "consolidationResultsOne.csv";
		File file = new File(name);
		
		StringBuilder s = new StringBuilder();
		s.append("consolidator;parameter 1; parameter 2; parameter 3; parameter 4;total power consumption;migrations;active pms;time;performance"); 
		s.append(System.getProperty("line.separator"));
		
		BufferedWriter writer = null;
		//instantiate the writer and write first line
		try {
			writer = new BufferedWriter(new FileWriter(file));
			writer.append(s.toString());
		} catch (IOException e1) {
			throw new IllegalArgumentException("writer is null");
		}
		
		//pso consolidator
		for(int first : psoSwarmSizeValues) {
			for(int second : psoNrIterationsValues) {
				for(int third : psoC1Values) {
					for(int fourth : psoC2Values) {
						
						setPsoProperties(psoSwarmSizeValues.get(psoSwarmSizeValues.indexOf(first)).toString(), psoNrIterationsValues.get(psoNrIterationsValues.indexOf(second)).toString(), 
								psoC1Values.get(psoC1Values.indexOf(third)).toString(), psoC2Values.get(psoC2Values.indexOf(fourth)).toString());
						
						String[] jobStart = {"C://Users//r-pon//Desktop//GWA-T-1-DAS2.gwf", "1000", "50@16@1", "5000", "pso"};		
						try {
							JobDispatchingDemo.main(jobStart);
							//here is an error, the method can only be called once
						} catch (Exception e) {
							throw new RuntimeException("JobDispatchingDemo.main() could not be started.");
						}
						//load the results
						
						Properties psoResult = new Properties();
						File psoData = new File("consolidationResults.xml");
						FileInputStream psoFileInput;
						try {
							psoFileInput = new FileInputStream(psoData);
							psoResult.loadFromXML(psoFileInput);
							psoFileInput.close();
						} catch (Exception e1) {
							throw new RuntimeException("Results could not be read.");
						}
						
						String results = "" + psoResult.getProperty("total power consumption") + ";" + psoResult.getProperty("migrations") + ";" +
						psoResult.getProperty("active pms") + ";" + psoResult.getProperty("time") + ";" + psoResult.getProperty("performance") + "";
						
						//save the results with the rest of the information
						
						String parameters = "SwarmSize: " + psoSwarmSizeValues.get(psoSwarmSizeValues.indexOf(first)) + "; NrOfIterations: " + 
								psoNrIterationsValues.get(psoNrIterationsValues.indexOf(second)) + "; C1: " + psoC1Values.get(psoC1Values.indexOf(third)) + "; C2: " + 
								psoC2Values.get(psoC2Values.indexOf(fourth)) + ";";
						try {
							saveResults(writer, "pso;", parameters, results);
						} catch (IOException e) {
							throw new RuntimeException("An error occured while saving.");
						}
					}
				}
			}
		}
		
		//ga consolidator

		for(int first : gaPopulationSizeValues) {
			for(int second : gaNrIterationsValues) {
				for(int third : gaNrCrossoversValues) {
					
					setGaProperties(gaPopulationSizeValues.get(gaPopulationSizeValues.indexOf(first)).toString(), gaNrIterationsValues.get(gaNrIterationsValues.indexOf(second)).toString(), 
							gaNrCrossoversValues.get(gaNrCrossoversValues.indexOf(third)).toString());
					
					String[] jobStart = {"C://Users//r-pon//Desktop//GWA-T-1-DAS2.gwf", "1000", "50@16@1", "5000", "ga"};		
					try {
						JobDispatchingDemo.main(jobStart);
					} catch (Exception e) {
						throw new RuntimeException("JobDispatchingDemo.main() could not be started.");
					}
					
					//load the results
					
					Properties gaResult = new Properties();
					File gaData = new File("consolidationResults.xml");
					FileInputStream gaFileInput;
					try {
						gaFileInput = new FileInputStream(gaData);
						gaResult.loadFromXML(gaFileInput);
						gaFileInput.close();
					} catch (Exception e1) {
						throw new RuntimeException("Results could not be read.");
					}
					
					String results = "" + gaResult.getProperty("total power consumption") + ";" + gaResult.getProperty("migrations") + ";" +
					gaResult.getProperty("active pms") + ";" + gaResult.getProperty("time") + ";" + gaResult.getProperty("performance") + "";
					
					//save the results with the rest of the information
					
					String parameters = "PopulationSize: " + gaPopulationSizeValues.get(gaPopulationSizeValues.indexOf(first)) + "; NrOfIterations: " + 
							gaNrIterationsValues.get(gaNrIterationsValues.indexOf(second)) + "; NrOfCrossovers: " + 
							gaNrCrossoversValues.get(gaNrCrossoversValues.indexOf(third)) + "; ;";
					try {
						saveResults(writer, "ga;", parameters, results);
					} catch (IOException e) {
						throw new RuntimeException("An error occured while saving.");
					}
				}
			}
		}
		
		//abc consolidator

		for(int first : abcPopulationSizeValues) {
			for(int second : abcNrIterationsValues) {
				for(int third : abcLimitTrialsValues) {
					
					setAbcProperties(abcPopulationSizeValues.get(abcPopulationSizeValues.indexOf(first)).toString(), abcNrIterationsValues.get(abcNrIterationsValues.indexOf(second)).toString(), 
							abcLimitTrialsValues.get(abcLimitTrialsValues.indexOf(third)).toString());
					
					String[] jobStart = {"C://Users//r-pon//Desktop//GWA-T-1-DAS2.gwf", "1000", "50@16@1", "5000", "abc"};		
					try {
						JobDispatchingDemo.main(jobStart);
					} catch (Exception e) {
						throw new RuntimeException("JobDispatchingDemo.main() could not be started.");
					}
					
					//load the results
					
					Properties abcResult = new Properties();
					File abcData = new File("consolidationResults.xml");
					FileInputStream abcFileInput;
					try {
						abcFileInput = new FileInputStream(abcData);
						abcResult.loadFromXML(abcFileInput);
						abcFileInput.close();
					} catch (Exception e1) {
						throw new RuntimeException("Results could not be read.");
					} 
					
					String results = "" + abcResult.getProperty("total power consumption") + ";" + abcResult.getProperty("migrations") + ";" +
					abcResult.getProperty("active pms") + ";" + abcResult.getProperty("time") + ";" + abcResult.getProperty("performance") + "";
					
					//save the results with the rest of the information
					
					String parameters = "PopulationSize: " + abcPopulationSizeValues.get(abcPopulationSizeValues.indexOf(first)) + "; NrOfIterations: " + 
							abcNrIterationsValues.get(abcNrIterationsValues.indexOf(second)) + "; LimitTrials: " + abcLimitTrialsValues.get(abcLimitTrialsValues.indexOf(third)) + "; ;";
					try {
						saveResults(writer, "abc;", parameters, results);
					} catch (IOException e) {
						throw new RuntimeException("An error occured while saving.");
					}
				}
			}
		}
		
		try {
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * This testcase is to compare the different consolidators with the best configuration of the
	 * parameters found in testcase one. The results are saved in a csf file, too.
	 */
	public void runTestcaseTwo() {
		//defining the best values after running the first test
		
		String psoSwarmSize = "";
		String psoNrIterations = "";
		String psoC1 = "";
		String psoC2 = "";
		String abcPopulationSize = "";
		String abcNrIterations = "";
		String abcLimitTrials = "";
		String gaPopulationSize = "";
		String gaNrIterations = "";
		String gaNrCrossovers = "";
		
		setPsoProperties(psoSwarmSize, psoNrIterations, psoC1, psoC2);
		setAbcProperties(abcPopulationSize, abcNrIterations, abcLimitTrials);
		setGaProperties(gaPopulationSize, gaNrIterations, gaNrCrossovers);
		
		//test the three consolidators and save the results
		//abc consolidator
		
		String name = "consolidationResultsTwo.csv";
		File file = new File(name);
		BufferedWriter writer = null;
		StringBuilder s = new StringBuilder();
		s.append("consolidator;parameter 1; parameter 2; parameter 3; parameter 4;energy needs;migrations;active pms;overAllocated pms"); 
		s.append(System.getProperty("line.separator"));
		
		//instantiate the writer and write first line
		try {
			writer = new BufferedWriter(new FileWriter(file));
			writer.append(s.toString());
		} catch (IOException e1) {
			throw new IllegalArgumentException("writer is null");
		}
	    
		//abc consolidator
	    
		String[] abcJobStart = {"C://Users//r-pon//Desktop//GWA-T-1-DAS2.gwf", "1000", "50@16@1", "5000", "abc"};		
		try {
			JobDispatchingDemo.main(abcJobStart);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//load the results
		
		Properties abcResult = new Properties();
		File abcData = new File("consolidationResults.xml");
		FileInputStream abcFileInput;
		try {
			abcFileInput = new FileInputStream(abcData);
			abcResult.loadFromXML(abcFileInput);
			abcFileInput.close();
		} catch (Exception e1) {
			e1.printStackTrace();
		} 
		
		String abcResults = "" + abcResult.getProperty("total power consumption") + ";" + abcResult.getProperty("migrations") + ";" +
		abcResult.getProperty("active pms") + ";" + abcResult.getProperty("time") + ";" + abcResult.getProperty("performance") + "";
		
		String abcParameters = "PopulationSize: " + abcPopulationSize + "; NrOfIterations: " + abcNrIterations + "; LimitTrials: " + abcLimitTrials + "; ;";
		try {
			saveResults(writer, "abc;", abcParameters, abcResults);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//ga consolidator
		
		String[] gaJobStart = {"C://Users//r-pon//Desktop//GWA-T-1-DAS2.gwf", "1000", "50@16@1", "5000", "ga"};		
		try {
			JobDispatchingDemo.main(gaJobStart);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//load the results
		
		Properties gaResult = new Properties();
		File gaData = new File("consolidationResults.xml");
		FileInputStream gaFileInput;
		try {
			gaFileInput = new FileInputStream(gaData);
			gaResult.loadFromXML(gaFileInput);
			gaFileInput.close();
		} catch (Exception e1) {
			e1.printStackTrace();
		} 
		
		String gaResults = "" + gaResult.getProperty("total power consumption") + ";" + gaResult.getProperty("migrations") + ";" +
		gaResult.getProperty("active pms") + ";" + gaResult.getProperty("time") + ";" + gaResult.getProperty("performance") + "";
		
		String gaParameters = "PopulationSize: " + gaPopulationSize + "; NrOfIterations: " + gaNrIterations + "; NrOfCrossovers: " + gaNrCrossovers + "; ;";
		try {
			saveResults(writer, "ga;", gaParameters, gaResults);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//pso consolidator
		
		String[] psoJobStart = {"C://Users//r-pon//Desktop//GWA-T-1-DAS2.gwf", "1000", "50@16@1", "5000", "pso"};		
		try {
			JobDispatchingDemo.main(psoJobStart);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//load the results
		
		Properties psoResult = new Properties();
		File psoData = new File("consolidationResults.xml");
		FileInputStream psoFileInput;
		try {
			psoFileInput = new FileInputStream(psoData);
			psoResult.loadFromXML(psoFileInput);
			psoFileInput.close();
		} catch (Exception e1) {
			e1.printStackTrace();
		} 
		
		String psoResults = "" + psoResult.getProperty("total power consumption") + ";" + psoResult.getProperty("migrations") + ";" +
		psoResult.getProperty("active pms") + ";" + psoResult.getProperty("time") + ";" + psoResult.getProperty("performance") + "";
		
		String psoParameters = "SwarmSize: " + psoSwarmSize + "; NrOfIterations: " + psoNrIterations + "; C1: " + psoC1 + "; C2: " + psoC2 + ";";
		try {
			saveResults(writer, "pso;", psoParameters, psoResults);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Save results inside a csv file.
	 * @param i
	 * 			Needed for defining test cases.
	 * @throws IOException 
	 */
	public void saveResults(BufferedWriter writer, String consolidator, String parameters, String results) throws IOException {
		
		// now everything has to be written inside the file		
		
		String[] all = {consolidator, parameters, results};
		
		StringBuilder sb = new StringBuilder();
	    for (String value : all) {
	                   
	        sb.append(value);
	        //sb.append(',');
	    }
	    sb.append(System.getProperty("line.separator"));
	    writer.append(sb.toString());
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
