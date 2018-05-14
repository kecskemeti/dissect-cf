package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

/**
	 * @author Rene Ponto
	 *
	 * This class is used to create an Object for the location and the velocity of particles of the 
	 * pso-consolidator. Despite of that there are operations like add an ArithmeticVector, 
	 * subtract an ArithmeticVector and multiply it with a constant.
	 */
@SuppressWarnings("serial")
public class ArithmeticVector extends ArrayList<Double>{

	double highestID;
	
	/**
	 * Empty constructor, because all values of this class are created randomly at the beginning of the PSO
	 * and get added to this.
	 */
	public ArithmeticVector(double highestID) {
		this.highestID = highestID;
	}
	
	/**
	 * The toString-method, used for debugging.
	 * 
	 * @return 
	 */
	public String toString() {
		String erg = "Size: " + this.size() + ", " + this.toList();
		return erg;
	}
	
	/**
	 * Creates an artificial list of the values of this class, used inside the toString-method.
	 * 
	 * @return
	 */
	private String toList() {
		String erg = "[";
		for(int i = 0; i < this.size(); i++) {
			erg = erg + this.get(i) + ", ";
		}
		if (erg != null && erg.length() > 2) {
	        erg = erg.substring(0, erg.length() - 2);
	    }
		erg = erg + "]";
		return erg;
	}
	
	/**
	 * Method to add another ArithmeticVector to this one. There is a defined border
	 * so there can no PM be used which is not in the IaaS.
	 * 
	 * @param second The second ArithmeticVector, the velocity.
	 * @return The solution of this operation as a new ArithmeticVector.
	 */
	public ArithmeticVector addUp(ArithmeticVector second) {
		
		ArithmeticVector erg = new ArithmeticVector(highestID);
		for(int i = 0; i < this.size(); i++) {
			if(this.get(i) + second.get(i) > highestID)		
				erg.add(highestID);
			else {
				if(this.get(i) + second.get(i) < 1)
					erg.add(1.0);	// if the actual value is 1.0 and the actual value of the velcity is smaller than 1.0,
									// 1.0 is set becouse there is no lower ID than 1.
				else
					erg.add(this.get(i) + second.get(i));
			}				
		}
		return erg;
	}
	
	/**
	 * Method to subtract another ArithmeticVector of this one. There is a defined border
	 * for not using a PM with an ID lower than 1, because such a PM does not exist.
	 * 
	 * @param second The second ArithmeticVector, the velocity.
	 * @return The solution of this operation as a new ArithmeticVector.
	 */
	public ArithmeticVector subtract(ArithmeticVector second) {
		ArithmeticVector erg = new ArithmeticVector(highestID);
		for(int i = 0; i < this.size(); i++) {
			if(this.get(i) - second.get(i) < 1)
				erg.add(1.0);	// if the value would be lower than 1, 1 is set because there is no lower id than 1.
			else
				erg.add(this.get(i) - second.get(i));
			
		}
		return erg;
	}
	
	/**
	 * Method to multiply every value of this class with a constant.
	 * 
	 * @param constant The double Value to multiply with.
	 * @return The solution of this operation as a new ArithmeticVector.
	 */
	public ArithmeticVector multiply(double constant) {
		ArithmeticVector erg = new ArithmeticVector(highestID);
		for(int i = 0; i < this.size(); i++) {
			erg.add(this.get(i) * constant);
		}
		return erg;		
	}
}