package se.lnu.prosses.securityMonitor;

import org.jgrapht.graph.DefaultEdge;

@SuppressWarnings("serial")
public class Transition extends DefaultEdge {
	String event;
	String guard;
	String upadater;
	static final public String TAU = "TAU";
	public static final String START = "START";
	
	public Transition(){
		
	}
	
	public Transition(String event,	String guard, String upadater){
		this.event = event;
		this.guard = guard;
		this.upadater = upadater;
	}

	public String getEvent() {
		return event;
	}

	public void setEvent(String event) {
		this.event = event;
	}

	public String getGuard() {
		return guard;
	}

	public void setGuard(String guard) {
		this.guard = guard;
	}

	public String getUpadater() {
		return upadater;
	}

	public void setUpadater(String upadater) {
		this.upadater = upadater;
	}
	
	@Override
	public String toString() {
		return event + " (" + guard.replaceAll("\"", "'") + ") [" + upadater.replaceAll("\"", "'") + "]";
	}

}
