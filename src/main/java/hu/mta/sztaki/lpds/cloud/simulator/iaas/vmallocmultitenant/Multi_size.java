package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmallocmultitenant;

import java.util.ArrayList;

public class Multi_size {

	ArrayList<Integer> vec = new ArrayList<Integer>(); //<int> vec;
	
	//"comparison result type" for the metric_compare method
	public static enum comprestype {
		FIRST_LESS_SECOND, 
		
		FIRST_EQUAL_SECOND, 
		
		FIRST_GREATER_SECOND		
	}
	
	public Multi_size(){
	}


	public Multi_size(ArrayList<Integer> size_vec){
		this.vec = size_vec;
	}


	// str must be of the form "[10 10 10]" or "[10,10,10]"
	public Multi_size(String str){
		str.replace(',',' ');
		str.substring(1,str.length()-1);
	//	str.erase(str.find(']'));
	/**	std::stringstream ss(str);
		String s;
		while(getline(ss,s,' '))
		{
			vec.push_back(atoi(s.c_str()));
		}*/
		String[] vec = str.split("\\s");
		for(int i = 0; i < vec.length; i++){
			this.vec.add(Integer.parseInt(vec[i]));  
		}
	}


	public boolean less_or_equal(Multi_size other){
		assert(vec.size() == other.vec.size());
		for(int i=0; i<vec.size(); i++){
			if(vec.get(i) > other.vec.get(i))
				return false;
		}
		return true;
	}


	public boolean equal(Multi_size other){
		assert(vec.size() == other.vec.size());
		for(int i=0;i<vec.size();i++){
			if(vec.get(i) != other.vec.get(i))
				return false;
		}
		return true;
	}


	public Multi_size times(double c){
		//Vector<Integer> vec2;
		for(int i=0; i < vec.size(); i++){
			int mult = (int) (vec.get(i) * c);
			vec.remove(i);
			vec.add(i, mult);
		}
		Multi_size result = new Multi_size(vec);
		return result;
	}


	public void increase(Multi_size other){
		if(vec.isEmpty()){
			vec = other.vec;
			return;
		}
		assert(vec.size() == other.vec.size());
		for(int i=0; i < vec.size(); i++){
		//	vec.get(i) += other.vec.get(i);
			int summe = vec.get(i) + other.vec.get(i);
			vec.remove(i);
			vec.add(i, summe);
		}
	}


	public void decrease(Multi_size other){
		assert(vec.size() == other.vec.size());
		for(int i=0; i < vec.size(); i++){
		//	vec[i] -= other.vec[i];
			int dif = vec.get(i) - other.vec.get(i);
			vec.remove(i);
			vec.add(i, dif);
		}
	}


	public Multi_size plus(Multi_size other){
	//	Multi_size result(vec);	
		Multi_size result = new Multi_size(vec);
		result.increase(other);
		return result;
	}


	Multi_size minus(Multi_size other){
	//	Multi_size result(vec);
		Multi_size result = new Multi_size(vec);
		result.decrease(other);
		return result;
	}


	public Multi_size percentage_of(Multi_size other){
		assert(vec.size()==other.vec.size());
		ArrayList<Integer> result_vec = new ArrayList<Integer>();      //vector<int>
		for(int i=0; i < vec.size(); i++){
			result_vec.add((int) (100.0*vec.get(i)/other.vec.get(i)));
		}
	//	Multi_size result(result_vec);
		Multi_size result = new Multi_size(result_vec);
		return result;
	}


	public String str(){
		String s = "";
		s += "[";
		for(int i=0; i < vec.size(); i++){
			if(i > 0)
				s += " ";
			s += vec.get(i);
		}
		s += "]";
	//	std::string result(s.str());
		return s;
	}

	public int get_dim() {
		return vec.size();
	}

	public Multi_size zero(int dim){
		//std::vector<int> vec(dim);
		ArrayList<Integer> vec = new ArrayList<Integer>(dim);
		Multi_size result = new Multi_size(vec);
		return result;
	}

	//public int& Multi_size::operator[](unsigned index){
	public int operator(int index){
		return vec.get(index);
	}


	private double metric_value(String metric){
		double val = -1;
		if(metric.equals("sum")){
			val = 0;
			for(int i=0; i < vec.size();i++)
				val += vec.get(i);
		}
		else if(metric.equals("product")){
			val = 1;
			for(int i=0; i<vec.size(); i++)
				val *= vec.get(i);
		}
		else if(metric.equals("maximum")){
			val = 0;
			for(int i=0; i<vec.size(); i++)
				if(vec.get(i) > val) val = vec.get(i);
		}
		else if(metric.equals("minimum")){
			val = Integer.MAX_VALUE;
			for(int i=0; i<vec.size(); i++)
				if(vec.get(i) < val) val = vec.get(i);
		}
		else if(metric.equals("imbalance")){
			double val_min = Integer.MAX_VALUE;
			double val_max=0;
			for(int i=0; i<vec.size(); i++){
				if(vec.get(i) < val_min) val_min = vec.get(i);
				if(vec.get(i) > val_max) val_max = vec.get(i);
			}
			val=val_max-val_min;
		}
		else if(metric.equals("length")){
			val=0;
			for(int i=0; i < vec.size(); i++)
				val += vec.get(i)*vec.get(i);
			val = Math.sqrt(val);
		}
		return val;
	}


	public comprestype metric_compare(Multi_size other, String metric){
		double a_val=metric_value(metric), b_val=other.metric_value(metric);
		if(a_val<b_val)
			return comprestype.FIRST_LESS_SECOND;
		else if(a_val>b_val)
			return comprestype.FIRST_GREATER_SECOND;
		else // a_val==b_val)
			return comprestype.FIRST_EQUAL_SECOND;
	}


	private Multi_size normalize(Multi_size max_size){
	//	Log::info("normalizing "+str()+" with max_size "+max_size.str());
		System.out.println("normalizing "+str()+" with max_size "+max_size.str());
		ArrayList<Integer> vec2 = new ArrayList<Integer>();
		assert(vec.size() == max_size.vec.size());
		for(int i=0; i < vec.size(); i++){
			vec2.add((int) (1000.0*vec.get(i)/max_size.vec.get(i)));
		}
		Multi_size result = new Multi_size(vec2);
		//Log::info("result: "+result.str());
		System.out.println("result: "+result.str());
		return result;
	}


	public comprestype metric_compare_normalized(Multi_size other, String metric, Multi_size max_size){
		return normalize(max_size).metric_compare(other.normalize(max_size),metric);
	}


}
