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
	
	public ArithmeticVector() {
		internList = new ArrayList<Double>();
	}
	
	
	/**
	 * 
	 * @param second
	 * @return
	 */
	public ArithmeticVector add(ArithmeticVector second) {
		
		return second;		
	}
	
	/**
	 * 
	 * @param second
	 * @return
	 */
	public ArithmeticVector subtract(ArithmeticVector second) {
		
		return second;		
	}
	
	/**
	 * 
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
}