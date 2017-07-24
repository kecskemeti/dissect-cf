package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmallocmultitenant;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;

public class CompInst {
	
	private String name;
	private boolean crit;
	private VirtualMachine vm;
	private CompType type;
	private ArrayList<Request> requests;
	private Multi_size size;
	
	public CompInst(String name, boolean crit, CompType type){
		this.name = name;
		this.vm = null;
		this.crit = crit;
		this.type = type;
		size = type.get_base_size();
	}


	public Multi_size get_size(){
		return size;
	}


	/*
	Multi_size CompInst::get_size_increase()
	{
		int nr_tenants=tenants.size();
		return type->calc_size(nr_tenants+1).minus(type->calc_size(nr_tenants));
	}
	*/
	
	public VirtualMachine get_vm() {
		return vm;
	}
	
	public void set_vm(VirtualMachine vm) {
		this.vm = vm;
	}
	
	public String get_name() {
		return name;
	}
	
	public CompType get_type() {
		return type;
	}
	
	public boolean is_critical() {
		return crit;
	}

	/*
	void CompInst::add_tenant(std::string tenant)
	{
		tenants.insert(tenant);
		if(vm!=NULL)
			vm->size_increase(get_size_increase());
	}
	*/


	public void add_request(Request r){
		requests.add(r);
		size.increase(r.get_size());
		if(vm != null){
			vm.size_increase(r.get_size());
		}
	}


	/*
	void CompInst::remove_tenant(std::string tenant)
	{
		tenants.erase(tenants.find(tenant));
		if(vm!=NULL)
			vm->size_decrease(get_size_increase());
		if(tenants.empty())
			type->remove_instance(this);
	}
	*/


	public void remove_request(Request r){
		requests.remove(r);
		size.decrease(r.get_size());
		if(vm!=null)
			vm.size_decrease(r.get_size());
		if(requests.isEmpty())
			type.remove_instance(this);
	}


	public boolean may_be_used_by(String tenant, boolean crit_for_tenant){
		if((!crit) && (!crit_for_tenant))
			return true;
		boolean only_serves_tenant = true;
		//std::set<Request*>::iterator it;
		//for(it=requests.begin();it!=requests.end();it++){
		for(int i = 0; i < requests.size(); i++){
			Request r = requests.get(i);
			if(r.get_tenant() != tenant)
				only_serves_tenant = false;
		}
		return only_serves_tenant;
	}


	public ArrayList<String> get_tenants(){
		ArrayList<String> result = new ArrayList<String>();
	//	std::set<Request*>::iterator it_r;
	//	for(it_r=requests.begin();it_r!=requests.end();it_r++){
		for(int i = 0; i < requests.size(); i++){
			Request r = requests.get(i);
			result.add(r.get_tenant());
		}
		return result;
	}


	public String get_tenants_str(){
		String s = "";
		//std::set<Request*>::iterator it;
		//for(it=requests.begin();it!=requests.end();it++){
		for(int i = 0; i < requests.size(); i++){
			Request r = requests.get(i);
			if(s != "")
				s += ", ";
			s += r.get_tenant();
		}
		return s;
	}

}
