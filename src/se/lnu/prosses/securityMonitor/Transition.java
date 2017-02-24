package se.lnu.prosses.securityMonitor;

import org.jgrapht.graph.DefaultEdge;

@SuppressWarnings("serial")
public class Transition extends DefaultEdge {
	String action;
	String guard;
	String update;
	static final public String TAU = "TAU";
	public static final String START = "START";
	public static final String RETURN = "RETURN";
	public static final String PARAMETER = "PARAMETER";
	
	public Transition(){
		
	}
	
	public Transition(String action, String guard, String update){
		this.action = action;
		this.guard = guard;
		this.update = update;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getGuard() {
		return guard;
	}

	public void setGuard(String guard) {
		this.guard = guard;
	}

	public String getUpdate() {
		return update;
	}

	public void setUpdate(String update) {
		this.update = update;
	}
	
	@Override
	public String toString() {
		return "<" + action + ", " + guard.replaceAll("\"", "'") + ", " + update.replaceAll("\"", "'") + ">";
	}
	
	public String toFullString() {
		return "[" + this.getSource() + "->" + this.getTarget() + "] : <" + action + ", " + guard.replaceAll("\"", "'") + ", " + update.replaceAll("\"", "'") + ">";
	}

}
