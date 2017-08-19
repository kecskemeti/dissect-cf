package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

/**
 * Representation of the fitness value of a solution. Since VM
 * consolidation is a multi-objective optimization problem, the fitness of
 * a solution is not a single number, but rather consists of multiple
 * objective function values.
 */
public class Fitness {
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

