package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmallocmultitenant;

import java.util.HashMap;

public class Options {
	
	HashMap<String, String> option_map;
	
	void read(String filename){
		std::ifstream infile(filename.c_str());
		String line;
		while(std::getline(infile,line)){
			String key, value;
			std::stringstream iss(line);
			std::getline(iss,key,'=');
			std::getline(iss,value);
			option_map.insert(std::pair<std::string,std::string>(key,value));
		}
		infile.close();
	}


	String get_option_value(String option_key){
		return option_map.find(option_key).second;
	}


	void set_option(String key, String value){
		std::map<std::string,std::string>::iterator it;
		it=option_map.find(key);
		if(it==option_map.end())
			option_map.insert(std::pair<std::string,std::string>(key,value));
		else
			it.second = value;
	}
}
