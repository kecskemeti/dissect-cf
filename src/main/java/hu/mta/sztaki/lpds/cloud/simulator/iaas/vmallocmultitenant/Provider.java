package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmallocmultitenant;

import java.util.ArrayList;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;

public class Provider {
	
	ArrayList<PhysicalMachine> pms;
	ArrayList<VirtualMachine> vms;
	ArrayList<CompInst> comp_insts;
	ArrayList<CompType> comp_types;
	int vm_counter;
		
	void init_pms(){
	/**	std::set<PM*>::iterator it1;
		for(it1=pms.begin();it1!=pms.end();it1++)
		{
			PM *pm=*it1;
			delete pm;
		}*/
		pms.clear();

		//Multi_size capacity(Options.get_option_value("pm_size"));
		Multi_size capacity = new Multi_size(Options.get_option_value("pm_size"));
		int number_of_pms = atoi(Options.get_option_value("pm_num"));
		int number_of_secure_pms = atoi(Options.get_option_value("sec_pm_num"));
		//std::cout << "pm_num: " << number_of_pms << ", secure: " << number_of_secure_pms << std::endl;
		for(int i=0;i<number_of_pms;i++){
			boolean secure = (i < number_of_secure_pms);
			PhysicalMachine pm = new PhysicalMachine(capacity, i, secure);
		//	Log::info("Created PM: "+capacity.str()+", "+Str(i)+", "+Str(secure));
			System.out.println("Created PM: "+capacity.str()+", "+Str(i)+", "+Str(secure));
			pms.add(pm);
		}
	}

	void init_vms(){
	/**	std::set<VM*>::iterator it2;
		for(it2=vms.begin();it2!=vms.end();it2++)
		{
			VM *vm=*it2;
			delete vm;
		}*/
		vms.clear();
		vm_counter = 0;
	}

	void init_comp_insts(){
		/**	std::set<CompInst*>::iterator it3;
		for(it3=comp_insts.begin();it3!=comp_insts.end();it3++)
		{
			CompInst *ci=*it3;
			delete ci;
		}*/
		comp_insts.clear();
	}

	void init_comp_types(){
		/**	std::set<CompType*>::iterator it4;
		for(it4=comp_types.begin();it4!=comp_types.end();it4++)
		{
			CompType *ct=*it4;
			delete ct;
		}*/
		comp_types.clear();
	}

	void init(){
		init_pms();
		init_vms();
		init_comp_insts();
		init_comp_types();
	}

	PhysicalMachine new_pm(){
		Multi_size capacity(Options::get_option_value("pm_size"));
		int number_of_pms=atoi(Options::get_option_value("pm_num").c_str());
		int number_of_secure_pms=atoi(Options::get_option_value("sec_pm_num").c_str());
		bool secure = (rand()%number_of_pms<number_of_secure_pms);
		int pm_id=pms.size();
		PM * pm=new PM(capacity, pm_id, secure);
		Log::info("Created PM: "+capacity.str()+", "+Str(pm_id)+", "+Str(secure));
		pms.insert(pm);
		return pm;
	}

	void add_comp_type(String name, String provided_by, Multi_size base_size, boolean sec_hw_capable){
		CompType ct = new CompType(name, provided_by, base_size, sec_hw_capable);
		comp_types.insert(ct);
		Log::info("Added component type: "+name+", "+provided_by+", "+base_size.str());
	}


	boolean pm_less(PhysicalMachine a, PhysicalMachine b){
		//std::cerr << "Comparing PMs " << a->get_id() << ", " << b->get_id() << std::endl;
		if(a.isRunning() && !(b.isRunning()))
			return true;
		if(b.isRunning() && !(a.isRunning()))
			return false;
		//from here, either both are on or both are off
		if(a->is_secure() && !(b->is_secure()))
			return false;
		if(b->is_secure() && !(a->is_secure()))
			return true;
		//no significant difference between the two -> don't care
		return false;
	}


	CompInst process_request(Request req, String comp_type_name, boolean crit){
		Statistics.start_algorithm();
		CompType ctype = null;
	//	std::set<CompType*>::iterator it;
	//	for(it = comp_types.begin(); it!= comp_types.end(); it++){
		for(int i = 0; i < comp_types.size(); i++){
			CompType ct = comp_types.get(i);
			if(ct.get_name().equals(comp_type_name))
				ctype=ct;
		}
		if(ctype==null){
			std::cerr << "Non-existing component type requested: " << comp_type_name << std::endl;
			abort();
		}

		//check for reusable component instance
		Multi_size max_allowed_size = Multi_size(Options.get_option_value("pm_size"));
		CompInst chosen_ci = null;
		VirtualMachine chosen_vm = null;
		PhysicalMachine chosen_pm = null;
		boolean new_ci=false, new_vm=false;
		ArrayList<CompInst> instances = ctype.get_instances();
	//	std::set<CompInst *>::iterator it_inst;
	//	for(it_inst=instances.begin();it_inst!=instances.end();it_inst++){
		for(int i = 0; i < instances.size(); i++){
			CompInst ci = instances.get(i);
			VirtualMachine vm = ci.get_vm();
			Multi_size new_size = vm.get_pm().get_resource_usage().plus(req.get_size());
			if(ci->may_be_used_by(req->get_tenant(),crit) && new_size.less_or_equal(max_allowed_size)){
				chosen_ci=ci;
			//	Log::info("Reusing component instance "+ci->get_name());
				System.out.println("Reusing component instance "+ci->get_name());
				break;
			}
		}
		//if no reuse of existing instances possible, create new one
		if(chosen_ci==NULL)
		{
			chosen_ci=ctype->create_instance(crit);
			comp_insts.insert(chosen_ci);
			Log::info("Created component instance of type "+ctype->get_name()+", crit: "+(crit?"yes":"no"));
			new_ci=true;
		}
		chosen_ci->add_request(req);

		if(new_ci)
		{
			//check for reusable VM
			std::set<VM*>::iterator it_vm;
			for(it_vm=vms.begin();it_vm!=vms.end();it_vm++)
			{
				VM * vm=*it_vm;
				Multi_size new_size=vm->get_pm()->get_resource_usage().plus(chosen_ci->get_size());
				if(vm->may_host(chosen_ci) && new_size.less_or_equal(max_allowed_size))
				{
					chosen_vm=vm;
					Log::info("Reusing VM "+Str(vm->get_id()));
					break;
				}
				else if(!(vm->may_host(chosen_ci)))
					Log::info("VM "+Str(vm->get_id())+" skipped because of may_host");
				else
					Log::info("VM "+Str(vm->get_id())+" skipped because of its size");
			}
			if(chosen_vm==NULL)
			{
				chosen_vm=new VM(Multi_size(Options::get_option_value("vm_overhead")),vm_counter);
				vms.insert(chosen_vm);
				Log::info("Created VM "+Str(vm_counter));
				new_vm=true;
				vm_counter++;
				Statistics::vm_launch();
			}
			chosen_ci->set_vm(chosen_vm);
			chosen_vm->add_comp_inst(chosen_ci);
		}

		if(new_vm)
		{
			/*
			std::set<PM*> awake_pms=get_awake_pms();
			std::set<PM*>::iterator it_pm;
			for(it_pm=awake_pms.begin();it_pm!=awake_pms.end();it_pm++)
			{
				PM *pm=*it_pm;
				if(pm->may_host(chosen_vm) && chosen_vm->get_size().less_or_equal(pm->get_free_capac()))
				{
					chosen_pm=pm;
					Log::info("Reusing PM "+Str(pm->get_id()));
					break;
				}
			}
			if(chosen_pm==NULL)
			{
				for(it_pm=pms.begin();it_pm!=pms.end();it_pm++)
				{
					PM *pm=*it_pm;
					if(!(pm->is_on()))
					{
						chosen_pm=pm;
						Log::info("Switching on PM "+Str(pm->get_id()));
						pm->switch_on();
						break;
					}
				}
				if(chosen_pm==NULL)
				{
					Log::warning("No available PM");
					abort();
				}
			}
			chosen_pm->add_vm(chosen_vm);
			chosen_vm->set_pm(chosen_pm);
			*/
			std::vector<PM *> sorted_pms(pms.begin(),pms.end());
			std::sort(sorted_pms.begin(),sorted_pms.end(),pm_less);
			std::vector<PM *>::iterator it_pm;
			for(it_pm=sorted_pms.begin();it_pm!=sorted_pms.end();it_pm++)
			{
				PM *pm=*it_pm;
				if(pm->may_host(chosen_vm) && chosen_vm->get_size().less_or_equal(pm->get_free_capac()))
				{
					if(pm->is_on())
					{
						Log::info("Reusing PM "+Str(pm->get_id()));
					}
					else
					{
						pm->switch_on();
						Log::info("Switching on PM "+Str(pm->get_id()));
					}
					chosen_pm=pm;
					break;
				}
			}
			if(chosen_pm==NULL)
			{
				Log::info("New PM needed");
				chosen_pm=new_pm();
				chosen_pm->switch_on();
			}
			chosen_pm->add_vm(chosen_vm);
			chosen_vm->set_pm(chosen_pm);
		}
		Statistics::stop_algorithm("process_request");
		Log::dump_system_state();
		return chosen_ci;
	}


	void end_request(Request *req, CompInst *ci)
	{
		Statistics::start_algorithm();
		ci->remove_request(req);
		Statistics::stop_algorithm("end_request");
		Log::dump_system_state();
	}


	static void remove_comp_inst(CompInst ci){
		comp_insts.remove(ci);
		VirtualMachine vm = ci.get_vm();
		vm.remove_comp_inst(ci);
		if(vm.is_empty()){
			vm.get_pm().remove_vm(vm,true);
			vms.erase(vm);
		//	Log::info("Removing VM "+Str(vm->get_id()));
			System.out.println("Removing VM "+Str(vm.get_id()));
		}
	}


	ArrayList<PhysicalMachine> get_awake_pms(){
		ArrayList<PhysicalMachine> awake_pms;
	//	std::set<PM*>::iterator it;
	//	for(it=pms.begin();it!=pms.end();it++){
		for(int i = 0; i < pms.size(); i++){
			PhysicalMachine pm = pms.get(i);
			if(pm.isRunning())
				awake_pms.add(pm);
		}
		return awake_pms;
	}


	void migrate(PM * source, PM * target, VM * vm, bool switch_off_if_empty, bool notify_stat)
	{
		Log::info("Migrating VM "+Str(vm->get_id())+" from PM "+Str(source->get_id())+" to PM "+Str(target->get_id()));
		source->remove_vm(vm,switch_off_if_empty);
		target->add_vm(vm);
		vm->set_pm(target);
		if(notify_stat)
			Statistics::migration();
	}


	typedef struct
	{
		VM * vm;
		PM * source_pm;
		PM * target_pm;
	} migr_action_type;


	void re_optimize()
	{
		Statistics::start_algorithm();
		std::vector<migr_action_type> actions; // for undoing tentative migrations
		// check if an active PM can be emptied
		std::set<PM*> awake_pms=get_awake_pms();
		std::set<PM*>::iterator it_pm;
		for(it_pm=awake_pms.begin();it_pm!=awake_pms.end();it_pm++)
		{
			PM *pm=*it_pm;
			std::set<VM*> vms_of_pm=pm->get_vms();
			std::set<VM*>::iterator it_vm;
			for(it_vm=vms_of_pm.begin();it_vm!=vms_of_pm.end();it_vm++)
			{
				VM *vm=*it_vm;
				bool success_for_this_vm=false;
				std::set<PM*>::iterator it_pm2;
				for(it_pm2=awake_pms.begin();it_pm2!=awake_pms.end();it_pm2++)
				{
					PM *pm2=*it_pm2;
					if(pm2==pm)
						continue;
					if(pm2->may_host(vm) && vm->get_size().less_or_equal(pm2->get_free_capac()))
					{
						success_for_this_vm=true;
						migrate(pm,pm2,vm,false,false);
						migr_action_type migr_action;
						migr_action.source_pm=pm;
						migr_action.target_pm=pm2;
						migr_action.vm=vm;
						actions.push_back(migr_action);
						break;
					}
				}
				if(!success_for_this_vm)
					break;
			}
			if(pm->is_empty()) // PM successfully emptied -> migrations are firm
			{
				Log::info("Successfully emptied PM "+Str(pm->get_id()));
				pm->switch_off();
				awake_pms.erase(pm);
				for(unsigned i=0;i<actions.size();i++)
					Statistics::migration();
				actions.clear();
			}
			else // PM not successfully emptied -> undo tentative migrations
			{
				Log::info("Could not empty PM "+Str(pm->get_id()));
				std::vector<migr_action_type>::iterator it_ac;
				for(it_ac=actions.begin();it_ac!=actions.end();it_ac++)
				{
					migr_action_type action=*it_ac;
					migrate(action.target_pm,action.source_pm,action.vm,false,false);
				}
				actions.clear();
			}
		}

		// check if a secure PM can take the load from two (non-secure) PMs
		bool changed=true;
		std::set<PM*>::iterator it_pm1=awake_pms.begin();
		it_pm=pms.begin();
		while(changed)
		{
			changed=false;
			PM * secure_sleeping_pm=NULL;
			for(;it_pm!=pms.end();it_pm++)
			{
				PM *pm=*it_pm;
				if(pm->is_secure() && (!(pm->is_on())))
				{
					secure_sleeping_pm=pm;
					break;
				}
			}
			if(secure_sleeping_pm!=NULL)
			{
				PM * chosen_pm1=NULL,*chosen_pm2=NULL;
				std::set<PM*>::iterator it_pm2;
				for(;it_pm1!=awake_pms.end()&&chosen_pm1==NULL;it_pm1++)
				{
					PM *pm1=*it_pm1;
					if(pm1->is_secure())
						continue;
					for(it_pm2=awake_pms.begin();it_pm2!=awake_pms.end()&&chosen_pm1==NULL;it_pm2++)
					{
						PM *pm2=*it_pm2;
						if(pm2->is_secure())
							continue;
						if(pm1==pm2)
							continue;
						if(pm1->get_resource_usage().plus(pm2->get_resource_usage()).less_or_equal(secure_sleeping_pm->get_capacity()))
						{
							chosen_pm1=pm1;
							chosen_pm2=pm2;
						}
					}
				}
				if(chosen_pm1!=NULL)
				{
					Log::info("Secure PM "+Str(secure_sleeping_pm->get_id())+" takes load from PMs "+Str(chosen_pm1->get_id())+" and "+Str(chosen_pm2->get_id()));
					secure_sleeping_pm->switch_on();
					awake_pms.insert(secure_sleeping_pm);
					std::set<VM*>::iterator it_vm;
					std::set<VM*> vms_of_pm=chosen_pm1->get_vms();
					for(it_vm=vms_of_pm.begin();it_vm!=vms_of_pm.end();it_vm++)
					{
						VM *vm=*it_vm;
						migrate(chosen_pm1,secure_sleeping_pm,vm,false,true);
					}
					vms_of_pm=chosen_pm2->get_vms();
					for(it_vm=vms_of_pm.begin();it_vm!=vms_of_pm.end();it_vm++)
					{
						VM *vm=*it_vm;
						migrate(chosen_pm2,secure_sleeping_pm,vm,false,true);
					}
					chosen_pm1->switch_off();
					chosen_pm2->switch_off();
					awake_pms.erase(chosen_pm1);
					awake_pms.erase(chosen_pm2);
					changed=true;
				}
			}
		}

		if(atoi(Options::get_option_value("sec_pm_num").c_str())<0.3*atoi(Options::get_option_value("pm_num").c_str()))
		{
			// check if the load of a secure PM can be moved to a non-secure PM
			for(it_pm=pms.begin();it_pm!=pms.end();it_pm++)
			{
				PM *pm=*it_pm;
				if(pm->is_on() && pm->is_secure())
				{
					std::set<std::string> providers_in_pm;
					std::set<std::string> users_of_crit_components_in_pm;
					std::set<VM *> vms_of_pm=pm->get_vms();
					std::set<VM *>::iterator it_vm;
					for(it_vm=vms_of_pm.begin();it_vm!=vms_of_pm.end();it_vm++)
					{
						VM * vm=*it_vm;
						std::set<CompInst *> cis=vm->get_comp_insts();
						std::set<CompInst *>::iterator it_ci;
						for(it_ci=cis.begin();it_ci!=cis.end();it_ci++)
						{
							CompInst *ci=*it_ci;
							providers_in_pm.insert(ci->get_type()->get_provided_by());
							if(ci->is_critical())
							{
								std::set<std::string> tenants=ci->get_tenants();
								//a critical component instance is used by 0 or 1 tenant
								if(tenants.size()>0)
									users_of_crit_components_in_pm.insert(*(tenants.begin()));
							}
						}
					}
					bool no_sec_pm_needed=true;
					std::set<std::string>::iterator it_usr;
					std::set<std::string>::iterator it_prov;
					for(it_usr=users_of_crit_components_in_pm.begin();it_usr!=users_of_crit_components_in_pm.end();it_usr++)
					{
						std::string user=*it_usr;
						for(it_prov=providers_in_pm.begin();it_prov!=providers_in_pm.end();it_prov++)
						{
							std::string prov=*it_prov;
							if(prov!="Provider" && prov!=user)
								no_sec_pm_needed=false;
						}
					}
					if(no_sec_pm_needed)
					{
						std::set<PM*>::iterator it_pm2;
						for(it_pm2=pms.begin();it_pm2!=pms.end();it_pm2++)
						{
							PM *pm2=*it_pm2;
							if(pm2->is_on() || pm2->is_secure())
								continue;
							Log::info("Non-secure PM "+Str(pm2->get_id())+" takes load from PM "+Str(pm->get_id()));
							pm2->switch_on();
							std::set<VM*>::iterator it_vm;
							std::set<VM*> vms_of_pm=pm->get_vms();
							for(it_vm=vms_of_pm.begin();it_vm!=vms_of_pm.end();it_vm++)
							{
								VM *vm=*it_vm;
								migrate(pm,pm2,vm,false,true);
							}
							pm->switch_off();
							break;
						}
					}
				}
			}
		}
		Statistics::stop_algorithm("re_optimize");
	}
}
