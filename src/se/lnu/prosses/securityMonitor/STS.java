package se.lnu.prosses.securityMonitor;

import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.DirectedGraph;
import org.jgrapht.EdgeFactory;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.ext.EdgeNameProvider;
import org.jgrapht.ext.VertexNameProvider;
import org.jgrapht.graph.AbstractBaseGraph;
import org.main.parser.automaton.Automaton;
import org.main.parser.automaton.Edge;
import org.main.parser.automaton.Event;
import org.main.parser.automaton.Event.EventType;
import org.main.parser.automaton.State;
import org.main.parser.automaton.Variable;
import org.main.parser.automaton.Variable.VariableType;

@SuppressWarnings("serial")
public class STS extends AbstractBaseGraph<Integer, Transition> implements DirectedGraph<Integer, Transition>{
	public Set<String[]> variables;
	public Set<String> controllableEvents;
	
	static EdgeFactory<Integer, Transition> ef = new EdgeFactory<Integer, Transition>() {
		@Override
		public Transition createEdge(Integer sourceVertex, Integer targetVertex) {
			return new Transition();
		}
	};
	
	public STS(Class<? extends Transition> edgeClass) {
		super(ef, true, true);
	}
	public STS() {
		super(ef, true, true);
		this.variables = new HashSet<>();
		this.controllableEvents = new HashSet<>(); 
		this.controllableEvents.add(Transition.START);
		this.controllableEvents.add("se_lnu_Users_removeUser");
		this.controllableEvents.add("se_lnu_User_getFriendAt");
		this.controllableEvents.add("se_lnu_EstimateLocation_getDistance");
		this.controllableEvents.add("se_lnu_EstimateLocation_estimatLocation");
		this.controllableEvents.add("se_lnu_Users_findUserById");
		this.controllableEvents.add("se_lnu_Users_addUser");
		this.controllableEvents.add("se_lnu_Users_addFriend");
		this.controllableEvents.add("se_lnu_Users_auth");
//		this.controllableEvents.add("se_lnu_Test_g");
	}
	
	public STS convertToUncontrollableFreeSTS(){
		STS sts = (STS) this.clone();
		Transition transition = nextUncontrollable(sts);
		while(transition!=null){
			Set<Transition> incomingTransitions = new HashSet<>();
			incomingTransitions.addAll(sts.incomingEdgesOf(sts.getEdgeSource(transition)));
			for (Transition incomingTransition : incomingTransitions) {
				mergeTransitions(sts, incomingTransition, transition);
			}
			sts.removeEdge(transition);
			if(sts.outgoingEdgesOf(sts.getEdgeSource(transition)).size()==0){
				sts.removeVertex(sts.getEdgeSource(transition));
			}
			transition = nextUncontrollable(sts);
		}
		clearUpdaters(sts);
		return sts;
	}
	
	private void clearUpdaters(STS sts) {
		for (Transition t : sts.edgeSet()) {
			t.setUpadater("");
		}
	}

	private void mergeTransitions(STS sts, Transition incomingTransition, Transition outgoingTransition) {
		if(controllableEvents.contains(incomingTransition.getEvent()) || sts.getEdgeSource(incomingTransition)!=sts.getEdgeTarget(outgoingTransition)){
			String guard = incomingTransition.getGuard() + " and " + getWeakestPrecondition(outgoingTransition.getGuard(), incomingTransition.getUpadater());
			guard = guard.replaceAll("\\s*true\\s*and\\s*", "").replaceAll("\\s*and\\s*true\\s*", "");
			String updater = incomingTransition.getUpadater() + "," + outgoingTransition.getUpadater();
			Transition newTransition = new Transition(incomingTransition.getEvent(), guard, updater);
			sts.addEdge(sts.getEdgeSource(incomingTransition), sts.getEdgeTarget(outgoingTransition), newTransition);
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
				guard += assignmentParts[0].replaceAll(" ", "");
			}
		}
		guard += guardParts[guardParts.length-1];
		guard = guard.substring(1, guard.length()-1);
		return guard;
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