package se.lnu.prosses.securityMonitor;

import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.ext.DOTExporter;
import org.jgrapht.ext.EdgeNameProvider;
import org.jgrapht.ext.VertexNameProvider;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.main.parser.automaton.Automaton;
import org.main.parser.automaton.Edge;
import org.main.parser.automaton.Event;
import org.main.parser.automaton.Event.EventType;
import org.main.parser.automaton.State;
import org.main.parser.automaton.Variable;
import org.main.parser.automaton.Variable.VariableType;

@SuppressWarnings("serial")
public class STS extends DefaultDirectedGraph<Integer, Transition>{
	public Set<String[]> variables;
	public Set<String> controllableEvents;
	
	public STS(Class<? extends Transition> edgeClass) {
		super(edgeClass);
	}
	public STS() {
		super(Transition.class);
		this.variables = new HashSet<>();
		this.controllableEvents = new HashSet<>(); 
		this.controllableEvents.add(Transition.START);
//		this.controllableEvents.add("se_lnu_Users_removeUser");
//		this.controllableEvents.add("se_lnu_User_getFriendAt");
//		this.controllableEvents.add("se_lnu_EstimateLocation_getDistance");
//		this.controllableEvents.add("se_lnu_EstimateLocation_estimatLocation");
//		this.controllableEvents.add("se_lnu_Users_findUserById");
//		this.controllableEvents.add("se_lnu_Users_addUser");
//		this.controllableEvents.add("se_lnu_Users_addFriend");
//		this.controllableEvents.add("se_lnu_Users_auth");
		this.controllableEvents.add("se_lnu_Test_g");
	}
	
	public STS convertToUncontrollableFreeSTS(){
		STS sts = (STS) this.clone();
		Transition transition = nextUnprocessed(sts);
		while(transition!=null){
			Set<Transition> incomingTransitions = new HashSet<>();
			incomingTransitions.addAll(sts.incomingEdgesOf(sts.getEdgeSource(transition)));
			try{
				for (Transition incomingTransition : incomingTransitions) {
					if(!controllableEvents.contains(incomingTransition.getEvent())){
						mergeTransitions(sts, transition, incomingTransition);
					}
				}
			}catch(Exception e){
				System.out.println();
			}
			sts.removeEdge(transition);
			eliminateBlockingLocation(sts);
			transition = nextUnprocessed(sts);
//			try {
//			sts.saveAsDot("p:\\model.dot");
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		}
		transition = nextUncontrollable(sts);
		while(transition!=null){
			Set<Transition> incomingTransitions = sts.incomingEdgesOf(sts.getEdgeSource(transition));
			for (Transition incomingTransition : incomingTransitions) {
				mergeTransitions(sts, transition, incomingTransition);
			}
			sts.removeEdge(transition);
			transition = nextUncontrollable(sts);
		}
		clearUpdaters(sts);
		eliminateBlockingLocation(sts);
		return sts;
	}
	
	private void clearUpdaters(STS sts) {
		for (Transition t : sts.edgeSet()) {
			t.setUpadater("");
		}
	}
	
	private void eliminateBlockingLocation(STS sts) {
		Set<Integer> locations = new HashSet<>();
		locations.addAll(sts.vertexSet());
		for (Integer location : locations) {
			if(sts.outgoingEdgesOf(location).size()==0){
				sts.removeVertex(location);
			}
		}
	}
	
	private void mergeTransitions(STS sts, Transition transition, Transition incomingTransition) {
		String guard = incomingTransition.getGuard() + " and " + getWeakestPrecondition(transition.getGuard(), incomingTransition.getUpadater());
		guard = guard.replaceAll("\\s*true\\s*and\\s*", "").replaceAll("\\s*and\\s*true\\s*", "");
		String updater = incomingTransition.getUpadater() + (transition.getUpadater().equals("") ? "" : "," + transition.getUpadater());
		Transition newTransition = new Transition(incomingTransition.getEvent(), guard, updater);
		if(sts.getEdge(sts.getEdgeSource(incomingTransition), sts.getEdgeTarget(transition))==null){
			sts.addEdge(sts.getEdgeSource(incomingTransition), sts.getEdgeTarget(transition), newTransition);
		}else{
			Integer newLocation = 0;
			for (Integer location : sts.vertexSet()) {
				if(location>newLocation){
					newLocation = location;
				}
			}
			newLocation++;
			sts.addVertex(newLocation);
			sts.addEdge(sts.getEdgeSource(incomingTransition), newLocation, newTransition);
			for (Transition t : sts.outgoingEdgesOf(sts.getEdgeTarget(transition))) {
				sts.addEdge(newLocation, sts.getEdgeTarget(t), (Transition) t.clone());
			}
		}
	}
	
	private Transition nextUncontrollable(STS sts) {
		Transition res = null;
		for (Transition transition : sts.edgeSet()) {
			if(!controllableEvents.contains(transition.getEvent())){
				res = transition;
				break;
			}
		}
		return res;
	}
	
	private String getWeakestPrecondition(String guard, String upadater) {
		String[] assignments = upadater.split(",");
		for (int i=assignments.length-1; i>=0; i--) {
			guard = replaceWithAssignment(guard, assignments[i]);
		}
		return guard;
	}

	private String replaceWithAssignment(String guard, String assignment) {
		String[] assignmentParts = assignment.split("=");
		String[] guardParts = ("@" + guard + "@").split(assignmentParts[0].replaceAll(" ", ""));
		String replace = "";
		String separator = "";
		for (int i=1; i<assignmentParts.length; i++) {
			replace += separator + assignmentParts[i];
			separator = "=";
		}
		guard = "";
		for (int i=0; i<guardParts.length-1; i++) {
			guard += guardParts[i];
			if(!Character.isJavaIdentifierPart(guardParts[i].charAt(guardParts[i].length()-1))
					&& !Character.isJavaIdentifierPart(guardParts[i+1].charAt(0))){
				guard += replace;
			}else{
				guard += assignmentParts[0];
			}
		}
		guard += guardParts[guardParts.length-1];
		guard = guard.substring(1, guard.length()-1);
		return guard;
	}
	
	private Transition nextUnprocessed(STS sts) {
		Transition res = null;
		for (Transition transition : sts.edgeSet()) {
			if(sts.getEdgeSource(transition)!=0 && !controllableEvents.contains(transition.getEvent()) /*&& !transition.getGuard().toLowerCase().equals("true")*/){
				boolean check = false;
				for (Transition incomingTransition : sts.incomingEdgesOf(sts.getEdgeSource(transition))) {
					if(!controllableEvents.contains(incomingTransition.getEvent())){
						check = true;
						break;
					}
				}
				if(check){
					res = transition;
					break;
				}
			}
		}
		return res;
	}
	public void saveAsDot(String path) throws Exception{
		DOTExporter<Integer, Transition> dotExporter = new DOTExporter<Integer, Transition>(new STSVertexNameProvider<Integer>(), null, new STSEdgeNameProvider<Transition>());
        Writer writer = new FileWriter(path);
		dotExporter.export(writer, this);
		writer.close();
	}
	
	public Automaton convertToAutomaton(){
		List<Event> events = new ArrayList<>();
		List<Variable> vars = new ArrayList<>();
		List<State> states = new ArrayList<>();
		Set<String> eventNames = new HashSet<>();
		for (Integer vertex : this.vertexSet()) {
			List<Edge> edges = new ArrayList<>();
			Set<Transition> outgoingEdges = this.outgoingEdgesOf(vertex);
			for (Transition transition : outgoingEdges) {
				edges.add(new Edge(String.valueOf(this.getEdgeTarget(transition)), transition.getGuard(), transition.getEvent(), transition.getUpadater()));
				eventNames.add(transition.getEvent());
			}
			State state = new State(String.valueOf(vertex), edges);
			states.add(state);
		}
		for (String eventName : eventNames) {
			events.add(new Event(EventType.C, eventName));
		}
		for (String[] variable : variables) {
			VariableType type;
			if(variable[0].equals("int")){
				type = VariableType.INT;
			} else if(variable[0].equals("real")){
				type = VariableType.FLOAT;
			} else if(variable[0].equals("bool")){
				type = VariableType.BOOLEAN;
			} else{
				type = VariableType.UNDEFINED;
			}
			vars.add(new Variable(type , variable[1]));
		}
		Automaton automaton = new Automaton("CaseStudy", events, vars, states);
		return automaton;
	}
}

class STSVertexNameProvider<V> implements VertexNameProvider<V>{
	@Override
	public String getVertexName(V vertex) {
		return String.valueOf(((Integer)vertex));
	}
}

class STSEdgeNameProvider<E> implements EdgeNameProvider<E>{
	@Override
	public String getEdgeName(E edge) {
		return ((Transition)edge).toString();
	}
}