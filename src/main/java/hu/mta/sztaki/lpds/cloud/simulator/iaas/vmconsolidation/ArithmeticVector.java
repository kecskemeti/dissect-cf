package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

/**
	 * @author Rene Ponto
	 *
	 * This class is used to create an Object for the location and the velocity of
	 * particles of the PSO-Consolidator. Despite of that there a operations like add a Vector, 
	 * subtract a Vector and multiply it with a constant.
	 */
@SuppressWarnings("serial")
public class ArithmeticVector extends ArrayList<Double>{

	double highestID;
	
	public ArithmeticVector() {
		
	}
	
	public String toString() {
		String erg = "Size: " + this.size() + ", " + this.toList();
		return erg;
	}
	
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
	 * @param second
	 * @return
	 */
	public ArithmeticVector addUp(ArithmeticVector second) {
		
		highestID = getHighest();		// the defined border
		
		ArithmeticVector erg = new ArithmeticVector();
		for(int i = 0; i < this.size(); i++) {
			if(this.get(i) + second.get(i) > highestID)		
				erg.add(highestID);
			else {
				if(this.get(i) + second.get(i) < 0)
					erg.add(0.0);
				else
					erg.add(this.get(i) + second.get(i));
			}				
		}
		return erg;
	}
	
	/**
	 * Method to subtract another ArithmeticVector of this one. There is a defined border
	 * for not using a PM with an ID lower than 0, becouse such a PM does not exist.
	 * @param second
	 * @return
	 */
	public ArithmeticVector subtract(ArithmeticVector second) {
		ArithmeticVector erg = new ArithmeticVector();
		for(int i = 0; i < this.size(); i++) {
			if(this.get(i) - second.get(i) < 0)
				erg.add(0.0);	// if the value would be lower than 0, 0 is set becouse there is no lower id than 0.
			else
				erg.add(this.get(i) - second.get(i));
			
		}
		return erg;
	}
	
	/**
	 * Method to multiply every value of this class with a constant.
	 * @param second
	 * @return
	 */
	public ArithmeticVector multiply(double constant) {
		ArithmeticVector erg = new ArithmeticVector();
		for(int i = 0; i < this.size(); i++) {
			erg.add(this.get(i) * constant);
		}
		return erg;		
	}
	
	/**
	 * Method for finding the PM with the highest ID.
	 * @return
	 */
	private double getHighest() {
		double highest = 0;
		for(int i = 0; i < this.size(); i++) {
			if(highest < this.get(i))
				highest = this.get(i);
		}
		
		return highest;
	}
}