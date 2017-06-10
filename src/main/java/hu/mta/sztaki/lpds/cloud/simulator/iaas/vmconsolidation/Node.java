package hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation;

import java.util.ArrayList;

/*
 * Abstrakte Klasse zum Speichern der Aktionen
 */
public abstract class Node {
	
	int id;
	ArrayList<Node> Vorgaenger = new ArrayList<Node>();
	Node Nachfolger = null;
	boolean ready = false;;
	
	public Node(int id){
		this.id = id;
	}
	
	public void setNachfolger(Node n){
		this.Nachfolger = n;
	}
	
	public Node getNachfolger(){
		return Nachfolger;
	}
	
	public void addVorgaenger(Node v){
		this.Vorgaenger.add(v);
	}
	
	public ArrayList<Node> getVorgaenger(){
		return Vorgaenger;
	}
	
	public boolean getready(){
		return ready;
	}
	
	public boolean getVorgaengerready(){
		boolean readyVorgaenger = true;
		for(int i = 0; i < Vorgaenger.size(); i++){
			if(Vorgaenger.get(i).getready() != ready){
				readyVorgaenger = false;
			}
		}
		return readyVorgaenger;
	}
}
