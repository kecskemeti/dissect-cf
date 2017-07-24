package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmallocmultitenant;

public class Statistics {

	int nr_migrations;
	int nr_vm_launches;
	int nr_pm_launches;

	typedef struct
	{
		int nr_pms;
		int nr_vms;
		int nr_cis;
		int power;
		Multi_size total_pm_cap;
		Multi_size total_vm_size;
		Multi_size total_ci_size;
		Multi_size avg_pm_util;
		Multi_size avg_vm_util;
		int nr_overloads;
	} record_t;

	std::multimap<timestamp_t,record_t*> records;

	//milliseconds
	std::vector<double> times_new_req;
	std::vector<double> times_terminate_req;
	std::vector<double> times_reopt;

	clock_t alg_start;


	void init()
	{
		nr_migrations=0;
		nr_vm_launches=0;
		nr_pm_launches=0;
		std::multimap<timestamp_t,record_t*>::iterator iter;
		for(iter=records.begin();iter!=records.end();iter++)
		{
			record_t *record=(*iter).second;
			delete record;
		}
		records.clear();
	}

	void migration()
	{
		nr_migrations++;
	}

	void vm_launch()
	{
		nr_vm_launches++;
	}

	void pm_launch()
	{
		nr_pm_launches++;
	}

	void collect_data(Timeline * tl)
	{
		timestamp_t now=tl->get_curr_time();
		record_t * record=new record_t;
		record->nr_cis=0;
		record->nr_vms=0;
		record->nr_pms=0;
		record->power=0;
		record->nr_overloads=0;
		std::set<PM *> pms=Provider::get_awake_pms();
		std::set<PM *>::iterator it_pm;
		for(it_pm=pms.begin();it_pm!=pms.end();it_pm++)
		{
			PM * pm=*it_pm;
			record->nr_pms++;
			record->power+=pm->get_power();
			record->total_pm_cap.increase(pm->get_capacity());
			record->avg_pm_util.increase(pm->get_utilization());
			if(!(pm->get_utilization().less_or_equal(pm->get_capacity())))
				record->nr_overloads++;
			std::set<VM *> vms=pm->get_vms();
			std::set<VM *>::iterator it_vm;
			for(it_vm=vms.begin();it_vm!=vms.end();it_vm++)
			{
				VM * vm=*it_vm;
				record->nr_vms++;
				record->total_vm_size.increase(vm->get_size());
				record->avg_vm_util.increase(vm->get_utilization());
				std::set<CompInst *> cis=vm->get_comp_insts();
				std::set<CompInst *>::iterator it_ci;
				for(it_ci=cis.begin();it_ci!=cis.end();it_ci++)
				{
					CompInst * ci=*it_ci;
					record->nr_cis++;
					record->total_ci_size.increase(ci->get_size());
				}
			}
		}
		record->avg_pm_util=record->avg_pm_util.times(1.0/record->nr_pms);
		record->avg_vm_util=record->avg_vm_util.times(1.0/record->nr_vms);
		records.insert(std::pair<timestamp_t,record_t*>(now,record));
	}

	void dump_data()
	{
		std::cout << "\nCollected statistics data:" << std::endl;
		std::multimap<timestamp_t,record_t*>::iterator iter;
		for(iter=records.begin();iter!=records.end();iter++)
		{
			timestamp_t t=(*iter).first;
			record_t *record=(*iter).second;
			std::cout << "  Time " << t << ":" << std::endl;
			std::cout << "    " << record->nr_pms << " active PMs with total capacity " << record->total_pm_cap.str() << std::endl;
			std::cout << "    " << record->nr_vms << " active VMs with total size " << record->total_vm_size.str() << std::endl;
			std::cout << "    " << record->nr_cis << " active component instances with total size " << record->total_ci_size.str() << std::endl;
		}
		std::cout << "  " << nr_pm_launches << " PM launches" << std::endl;
		std::cout << "  " << nr_vm_launches << " VM launches" << std::endl;
		std::cout << "  " << nr_migrations << " migrations" << std::endl;
	}

	void evaluate(std::string filename, std::string configname)
	{
		//std::cout << "\nCollected statistics data:" << std::endl;
		std::multimap<timestamp_t,record_t*>::iterator iter;
		int prev_pm_nr=0;
		int prev_power=0;
		int max_nr_pms=0;
		int max_nr_vms=0;
		long tot_pm_nr=0;
		int tot_overloads=0;
		long long tot_energy=0;
		Multi_size prev_pm_util, tot_pm_util;
		Multi_size prev_vm_util, tot_vm_util;
		timestamp_t prev_t=0;
		timestamp_t first_timestamp=-1, last_timestamp=-1;
		for(iter=records.begin();iter!=records.end();iter++)
		{
			timestamp_t t=(*iter).first;
			record_t *record=(*iter).second;
			if(iter!=records.begin())
			{
				tot_pm_nr+=(t-prev_t)*prev_pm_nr;
				tot_energy+=(t-prev_t)*prev_power;
				//Log::info("nr_pms: "+Str(record.nr_pms)+", tot_pm_util: "+tot_pm_util.str()+", prev_pm_util: "+prev_pm_util.str());
				if(prev_pm_util.get_dim()>0)
					tot_pm_util.increase(prev_pm_util.times(t-prev_t));
				if(prev_vm_util.get_dim()>0)
					tot_vm_util.increase(prev_vm_util.times(t-prev_t));
				if(first_timestamp==-1)
					first_timestamp=t;
			}
			prev_t=t;
			prev_pm_nr=record->nr_pms;
			prev_power=record->power;
			prev_pm_util=record->avg_pm_util;
			prev_vm_util=record->avg_vm_util;
			if(record->nr_pms>max_nr_pms)
				max_nr_pms=record->nr_pms;
			if(record->nr_vms>max_nr_vms)
				max_nr_vms=record->nr_vms;
			last_timestamp=t;
			tot_overloads+=record->nr_overloads;
		}
		std::vector<double>::iterator itd;
		double sum_times_new_req=0;
		for(itd=times_new_req.begin();itd!=times_new_req.end();itd++)
			sum_times_new_req+=(*itd);
		double sum_times_terminate_req=0;
		for(itd=times_terminate_req.begin();itd!=times_terminate_req.end();itd++)
			sum_times_terminate_req+=(*itd);
		double sum_times_reopt=0;
		for(itd=times_reopt.begin();itd!=times_reopt.end();itd++)
			sum_times_reopt+=(*itd);
	/*
		std::cout << "  " << nr_pm_launches << " PM launches" << std::endl;
		std::cout << "  " << nr_vm_launches << " VM launches" << std::endl;
		std::cout << "  " << nr_migrations << " migrations" << std::endl;
		//std::cout << "  aggregated PM number: " << tot_pm_nr << std::endl;
		std::cout << "  average PM number: " << ((double)tot_pm_nr/(last_timestamp-first_timestamp)) << std::endl;
		std::cout << "  average power: " << ((double)tot_energy/(last_timestamp-first_timestamp)) << std::endl;
		std::cout << "  average of PM utilizations: " << tot_pm_util.times(1.0/(last_timestamp-first_timestamp)).str() << std::endl;
		std::cout << "  average of VM utilizations: " << tot_vm_util.times(1.0/(last_timestamp-first_timestamp)).str() << std::endl;
	*/
		std::cout << "  total simulation time (sec): " << (last_timestamp-first_timestamp) << std::endl;

		std::ofstream out;
		out.open(filename.c_str(), std::ofstream::app);
		out << configname;
		//out << ";" << tot_energy; //conversion from Ws to kWh
		out << ";" << ((double)tot_energy/(3600*1000)); //conversion from Ws to kWh
		out << ";" << ((double)tot_pm_nr/(last_timestamp-first_timestamp)); //avg pm num
		out << ";" << nr_migrations;
		out << ";" << nr_vm_launches;
		out << ";" << max_nr_pms;
		out << ";" << max_nr_vms;
		out << ";" << tot_overloads;
		out << ";" << (sum_times_new_req/times_new_req.size());
		out << ";" << (sum_times_terminate_req/times_terminate_req.size());
		out << ";" << (sum_times_reopt/times_reopt.size());
		out << ";" << last_timestamp-first_timestamp;
		out << std::endl;
		out.close();
	}


	void write_detailed_csv(std::string filename)
	{
		std::ofstream out;
		out.open(filename.c_str(), std::ofstream::out);
		out << "Time;PM#;VM#;CompInst#;Power";
		int nr_dim=atoi(Options::get_option_value("dim").c_str());
		for(int i=1;i<=nr_dim;i++)
		{
			out << ";PM_cap_" << i;
			out << ";VM_size_" << i;
			out << ";CompInst_size_" << i;
			out << ";PM_util_" << i;
			out << ";VM_util_" << i;
		}
		out << std::endl;
		std::multimap<timestamp_t,record_t*>::iterator iter;
		timestamp_t first_timestamp=-1;
		for(iter=records.begin();iter!=records.end();iter++)
		{
			timestamp_t t=(*iter).first;
			if(first_timestamp==-1)
				first_timestamp=t;
			record_t *record=(*iter).second;
			out << (t-first_timestamp);
			out << ";" << record->nr_pms;
			out << ";" << record->nr_vms;
			out << ";" << record->nr_cis;
			out << ";" << record->power;
			for(int i=0;i<nr_dim;i++)
			{
				out << ";";
				if(record->total_pm_cap.get_dim()>i)
					out << record->total_pm_cap[i];
				out << ";";
				if(record->total_vm_size.get_dim()>i)
					out << record->total_vm_size[i];
				out << ";";
				if(record->total_ci_size.get_dim()>i)
					out << record->total_ci_size[i];
				out << ";";
				if(record->avg_pm_util.get_dim()>i)
					out << record->avg_pm_util[i];
				out << ";";
				if(record->avg_vm_util.get_dim()>i)
					out << record->avg_vm_util[i];
			}
			out << std::endl;
		}
		out.close();
	}


	void start_algorithm()
	{
		alg_start=clock();
	}


	void stop_algorithm(std::string alg)
	{
		clock_t time=clock()-alg_start;
		double msec=(((float)time)/CLOCKS_PER_SEC)*1000;
		if(alg=="process_request")
			times_new_req.push_back(msec);
		if(alg=="end_request")
			times_terminate_req.push_back(msec);
		if(alg=="re_optimize")
			times_reopt.push_back(msec);
	}
}
