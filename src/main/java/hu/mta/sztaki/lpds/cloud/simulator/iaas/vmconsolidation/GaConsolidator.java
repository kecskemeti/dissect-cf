package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;

/**
 * VM consolidator using a genetic algorithm.
 * 
 * This is a fairly basic GA. Ideas for further improvement:
 * - Use a local search procedure to improve individuals
 * - Improve performance by caching fitness values (or their components)
 * - Make fitness more sophisticated, e.g. by incorporating the skewness of 
 * PMs' load
 * - Make fitness more sophisticated, e.g. by allowing a large decrease in the 
 * number of migrations to compensate for a small increase in the number of 
 * active PMs.
 * - Every time and again, introduce new random individuals into the population 
 * to increase diversity
 * - Use more sophisticated termination criteria
 * 
 * @author Zoltan Mann
 */
public class GaConsolidator extends ModelBasedConsolidator {

	/** For generating random numbers */
	private Random random;
	/** Number of individuals in the population */
	private static final int populationSize=10;
	/** Terminate the GA after this many generations */
	private static final int nrIterations=50;
	/** Each gene is replaced by a random value with this probability during mutation */ 
	private static final double mutationProb=0.2;
	/** Number of recombinations to perform in each generation */
	private static final int nrCrossovers=populationSize;

	/**
	 * Representation of the fitness value of an individual. Since VM
	 * consolidation is a multi-objective optimization problem, the fitness of
	 * an individual is not a single number, but rather consists of multiple
	 * objective function values.
	 */
	private class Fitness {
		/** Total amount of PM overloads, aggregated over all PMs and all resource types */
		double totalOverload;
		/** Number of PMs that are on */
		int nrActivePms;
		/** Number of migrations necessary from original placement of the VMs */
		int nrMigrations;

		/**
		 * Decides if this fitness value is better than the other. Note that
		 * this relation is not a total order: it is possible that from two
		 * fitness values, no one is better than the other.
		 * @param other Another fitness value
		 * @return true if this is better than other
		 */
		boolean isBetterThan(Fitness other) {
			//The primary objective is the total overload. If there is a clear
			//difference (>1%) in that, this decides which is better.
			if(this.totalOverload<other.totalOverload*0.99)
				return true;
			if(other.totalOverload<this.totalOverload*0.99)
				return false;
			//If there is no significant difference in the total overload, then
			//the number of active PMs decides.
			if(this.nrActivePms<other.nrActivePms)
				return true;
			if(other.nrActivePms<this.nrActivePms)
				return false;
			//If there is no significant difference in the total overload, nor
			//in the number of active PMs, then the number of migrations decides.
			if(this.nrMigrations<other.nrMigrations)
				return true;
			return false;
		}

		public String toString() {
			String result="("+totalOverload+","+nrActivePms+","+nrMigrations+")";
			return result;
		}
	}

	/**
	 * Represents a possible solution of the VM consolidation problem, i.e., a
	 * mapping of VMs to PMs. Can be used as an individual in the population.
	 */
	private class Solution {
		/** Mapping of VMs to PMs */
		private Map<ModelVM,ModelPM> mapping;

		/** 
		 * Creates a solution with an empty mapping that will need to be filled
		 * somehow, e.g., using #fillRandomly().
		 */
		Solution() {
			mapping=new HashMap<>();
		}

		/**
		 * Creates a random mapping: for each VM, one PM is chosen uniformly
		 * randomly.
		 */
		void fillRandomly() {
			for(ModelPM pm : bins) {
				for(ModelVM vm : pm.getVMs()) {
					ModelPM randPm=bins.get(random.nextInt(bins.size()));
					mapping.put(vm,randPm);
				}
			}
		}

		/**
		 * Computes the total of PM overloads, aggregated over all PMs and all
		 * resource types. This is a component of the fitness.
		 */
		double getTotalOverload() {
			double result=0;
			//First determine the load of each PM under our mapping.
			Map<ModelPM,ResourceVector> loads=new HashMap<>();
			for(ModelPM pm : bins) {
				loads.put(pm,new ResourceVector(0,0,0));
			}
			for(ModelVM vm : mapping.keySet()) {
				ModelPM pm=mapping.get(vm);
				loads.get(pm).add(vm.getResources());
			}
			//For each PM, see if it is overloaded; if yes, increase the result accordingly.
			for(ModelPM pm : bins) {
				ResourceVector load=loads.get(pm);
				ConstantConstraints cap=pm.getTotalResources();
				if(load.getTotalProcessingPower()>cap.getTotalProcessingPower())
					result+=load.getTotalProcessingPower()/cap.getTotalProcessingPower();
				if(load.getRequiredMemory()>cap.getRequiredMemory())
					result+=load.getRequiredMemory()/cap.getRequiredMemory();
			}
			return result;
		}

		/**
		 * Compute the number of PMs that should be on, given our mapping. This
		 * is a component of the fitness.
		 */
		int getNrActivePms() {
			int result=0;
			//Decide for each PM whether it is used by at least one VM.
			Map<ModelPM,Boolean> used=new HashMap<>();
			for(ModelPM pm : bins) {
				used.put(pm,false);
			}
			for(ModelVM vm : mapping.keySet()) {
				ModelPM pm=mapping.get(vm);
				used.put(pm,true);
			}
			//Count the number of PMs that are in use.
			for(ModelPM pm : bins) {
				if(used.get(pm))
					result++;
			}
			return result;
		}

		/**
		 * Compute the number of migrations needed, given our mapping. This
		 * is a component of the fitness.
		 */
		int getNrMigrations() {
			int result=0;
			//See for each VM whether it must be migrated.
			for(ModelVM vm : mapping.keySet()) {
				if(mapping.get(vm)!=vm.gethostPM())
					result++;
			}
			return result;
		}

		/**
		 * Compute the fitness value belonging to this solution. Note that this
		 * is a rather computation-intensive operation.
		 */
		Fitness evaluate() {
			Fitness result=new Fitness();
			result.totalOverload=getTotalOverload();
			result.nrActivePms=getNrActivePms();
			result.nrMigrations=getNrMigrations();
			return result;
		}

		/**
		 * Create a new solution by mutating the current one. Each gene (i.e.,
		 * the mapping of each VM) is replaced by a random one with probability
		 * mutationProb and simply copied otherwise. Note that the current 
		 * solution (this) is not changed.
		 */
		Solution mutate() {
			Solution result=new Solution();
			for(ModelVM vm : mapping.keySet()) {
				ModelPM pm;
				if(random.nextDouble()<mutationProb)
					pm=bins.get(random.nextInt(bins.size()));
				else
					pm=mapping.get(vm);
				result.mapping.put(vm,pm);
			}
			return result;
		}

		/**
		 * Create a new solution by recombinating this solution with another.
		 * Each gene (i.e., the mapping of each VM) is taken randomly either
		 * from this or the other parent. Note that the two parents are not
		 * changed.
		 * @param other The other parent for the recombination
		 * @return A new solution resulting from the recombination
		 */
		Solution recombinate(Solution other) {
			Solution result=new Solution();
			for(ModelVM vm : mapping.keySet()) {
				ModelPM pm;
				if(random.nextBoolean())
					pm=other.mapping.get(vm);
				else
					pm=this.mapping.get(vm);
				result.mapping.put(vm,pm);
			}
			return result;
		}

		/**
		 * String representation of both the mapping and the fitness of the
		 * given solution.
		 */
		public String toString() {
			String result="m=(";
			boolean first=true;
			for(ModelVM vm : mapping.keySet()) {
				if(!first)
					result=result+",";
				result=result+vm.id+"->"+mapping.get(vm).getNumber();
				first=false;
			}
			result=result+"),f="+evaluate().toString();
			return result;
		}
	}

	/** Population for the GA, consisting of solutions=individuals */
	private Vector<Solution> population;

	/**
	 * Creates GaConsolidator with empty population.
	 */
	public GaConsolidator(IaaSService toConsolidate, double upperThreshold, double lowerThreshold, long consFreq) {
		super(toConsolidate, upperThreshold, lowerThreshold, consFreq);
		random=new Random();
		population=new Vector<>();
	}

	/**
	 * Initializes the population with populationSize random solutions.
	 */
	private void initializePopulation() {
		for(int i=0;i<populationSize;i++) {
			Solution s=new Solution();
			s.fillRandomly();
			population.add(s);
		}
	}

	/**
	 * Take two random individuals of the population and let them recombinate
	 * to create a new individual. If the new individual is better than one of
	 * its parents, then it replaces that parent in the population, otherwise
	 * it is discarded.
	 */
	private void crossover() {
		int i1=random.nextInt(population.size());
		int i2=random.nextInt(population.size());
		Solution s1=population.get(i1);
		Solution s2=population.get(i2);
		Solution s3=s1.recombinate(s2);
		Fitness f1=s1.evaluate();
		Fitness f2=s2.evaluate();
		Fitness f3=s3.evaluate();
		if(f3.isBetterThan(f1))
			population.set(i1,s3);
		else if(f3.isBetterThan(f2))
			population.set(i2,s3);
	}

	/**
	 * At the end of the GA, update the model of the consolidator to reflect the
	 * mapping corresponding to the best found solution.
	 */
	private void implementBestSolution() {
		//Determine "best" solution (i.e. a solution, compared to which there is no better one)
		Solution bestSol=population.get(0);
		Fitness bestFitness=bestSol.evaluate();
		for(int i=1;i<populationSize;i++) {
			Fitness fitness=population.get(i).evaluate();
			if(fitness.isBetterThan(bestFitness)) {
				bestSol=population.get(i);
				bestFitness=fitness;
			}
		}
		//Implement solution in the model
		for(ModelVM vm : bestSol.mapping.keySet()) {
			ModelPM oldPm=vm.gethostPM();
			ModelPM newPm=bestSol.mapping.get(vm);
			if(newPm!=oldPm)
				oldPm.migrateVM(vm,newPm);
		}
		adaptPmStates();
	}

	/**
	 * Perform the genetic algorithm to optimize the mapping of VMs to PMs.
	 */
	@Override
	protected void optimize() {
		initializePopulation();
		//Logger.getGlobal().info("Population after initialization: "+populationToString());
		for(int iter=0;iter<nrIterations;iter++) {
			//From each individual in the population, create an offspring using
			//mutation. If the child is better than its parent, it replaces it
			//in the population, otherwise it is discarded.
			for(int i=0;i<populationSize;i++) {
				Solution parent=population.get(i);
				Solution child=parent.mutate();
				if(child.evaluate().isBetterThan(parent.evaluate()))
					population.set(i,child);
			}
			//Perform the given number of crossovers.
			for(int i=0;i<nrCrossovers;i++) {
				crossover();
			}
			//Logger.getGlobal().info("Population after iteration "+iter+": "+populationToString());
		}
		implementBestSolution();
	}

	/**
	 * String representation of the whole population (for debugging purposes).
	 */
	public String populationToString() {
		String result="";
		boolean first=true;
		for(int i=0;i<populationSize;i++) {
			if(!first)
				result=result+" ";
			result=result+population.get(i).toString();
			first=false;
		}
		return result;
	}
}
