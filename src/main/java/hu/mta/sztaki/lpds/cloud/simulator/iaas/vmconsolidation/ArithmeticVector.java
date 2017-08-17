package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.List;

/**
	 * @author Rene Ponto
	 *
	 * This class is used to create an Object for the location and the velocity of
	 * particles of the PSO-Consolidator. Despite of that there a operations like add a Vector, 
	 * subtract a Vector and multiply it with a constant.
	 */
@SuppressWarnings("serial")
public class ArithmeticVector extends ArrayList<Double>{

	List<Double> internList;
	double highestID;
	
	public ArithmeticVector() {
		internList = new ArrayList<Double>();
	}
	
	
	/**
	 * Method to add another ArithmeticVector to this one. There is a defined border
	 * so there can no PM used which is not in the IaaS.
	 * @param second
	 * @return
	 */
	public ArithmeticVector addUp(ArithmeticVector second) {
		
		highestID = getHighest();		// the defined border
		
		ArithmeticVector erg = new ArithmeticVector();
		for(int i = 0; i < erg.size(); i++) {
			if(this.get(i) + second.get(i) > highestID)
				erg.add(highestID);
			else
				erg.add(this.get(i) + second.get(i));
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
		for(int i = 0; i < erg.size(); i++) {
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
		for(int i = 0; i < erg.size(); i++) {
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
		for(int i = 0; i < internList.size(); i++) {
			if(highest < internList.get(i))
				highest = internList.get(i);
		}
		
		return highest;
	}
}