package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

/*
 * Abstrakte Klasse zum Speichern der Aktionen
 */
public abstract class Node {
	
	int id;
	Node Vorgaenger = null;
	Node Nachfolger = null;
	
	public Node(int id){
		this.id = id;
	}
	
	public void setNachfolger(Node n){
		this.Nachfolger = n;
	}
	
	public Node getNachfolger(){
		return Nachfolger;
	}
	
	public void setVorgaenger(Node v){
		this.Vorgaenger = v;
	}
	
	public Node getVorgaenger(){
		return Vorgaenger;
	}
}
