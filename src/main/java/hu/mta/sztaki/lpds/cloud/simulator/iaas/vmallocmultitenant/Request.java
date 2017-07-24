package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmallocmultitenant;

public class Request {
	
	private String tenant;
	private Multi_size size;
	
	public Request(String tenant, Multi_size size){
		this.tenant=tenant;
		this.size=size;
	}

	public Multi_size get_size(){
		return size;
	}
	
	public String get_tenant(){
		return tenant;
	}
}
