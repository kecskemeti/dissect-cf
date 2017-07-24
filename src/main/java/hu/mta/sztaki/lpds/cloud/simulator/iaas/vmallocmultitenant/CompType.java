package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmallocmultitenant;

import java.util.ArrayList;

public class CompType {

	private String name;
	private String provided_by;
	private Multi_size base_size;
	//Multi_size incr_size;
	private ArrayList<CompInst> instances;
	private int inst_ctr;
	private boolean sec_hw_capable;
	
	public CompType(String name, String provided_by, Multi_size base_size, /*Multi_size incr_size,*/ boolean sec_hw_capable){
		this.name = name;
		this.provided_by = provided_by;
		this.base_size = base_size;
		//this->incr_size=incr_size;
		this.sec_hw_capable = sec_hw_capable;
		inst_ctr = 0;
	}

	public String get_name(){
		return name;
	};
	
	public String get_provided_by() {
		return provided_by;
	};

	/*
	Multi_size CompType::calc_size(int nr_tenants)
	{
		return base_size.plus(incr_size.times(nr_tenants));
	}
	*/

	public Multi_size get_base_size() {
		return base_size;
	};
	
	ArrayList<CompInst> get_instances(){
		return instances;
	};
	
	public CompInst create_instance(boolean crit){
		CompInst inst;
		String inst_name = name + inst_ctr;
		inst = new CompInst(inst_name, crit, this);
		instances.add(inst);
		inst_ctr++;
		return inst;
	}


	public void remove_instance(CompInst ci){
		//Log::info("Removing an instance of type "+name);
		System.out.println("Removing an instance of type "+name);
		instances.remove(ci);
		Provider.remove_comp_inst(ci);
	}
	
	public boolean is_sec_hw_capable() {
		return sec_hw_capable;
	}
}
