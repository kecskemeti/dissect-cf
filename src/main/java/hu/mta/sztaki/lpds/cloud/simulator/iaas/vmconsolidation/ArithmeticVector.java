package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;
import java.util.List;

/**
	 * @author René Ponto
	 *
	 * This class is used to create an Object for the location and the velocity of
	 * particles of the PSO-Consolidator.
	 */
@SuppressWarnings("serial")
public class ArithmeticVector extends ArrayList<Double>{

	List<Double> internList;
	double highest;
	
	public ArithmeticVector() {
		internList = new ArrayList<Double>();
	}
	
	
	/**
	 * Method to add another ArithmeticVector to this one.
	 * @param second
	 * @return
	 */
	public ArithmeticVector add(ArithmeticVector second) {
		
		highest = getHighest();		// the border to minimize the amount of used PMs
		
		ArithmeticVector erg = new ArithmeticVector();
		for(int i = 0; i < erg.size(); i++) {
			if(this.get(i) + second.get(i) > highest)
				erg.add(highest);
			else
				erg.add(this.get(i) + second.get(i));
		}
		return erg;
	}
	
	/**
	 * Method to subtract another ArithmeticVector of this one.
	 * @param second
	 * @return
	 */
	public ArithmeticVector subtract(ArithmeticVector second) {
		ArithmeticVector erg = new ArithmeticVector();
		for(int i = 0; i < erg.size(); i++) {
			if(this.get(i) - second.get(i) <= 0)
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
	
	private double getHighest() {
		double highest = 0;
		for(int i = 0; i < internList.size(); i++) {
			if(highest < internList.get(i))
				highest = internList.get(i);
		}
		
		return highest;
	}
}