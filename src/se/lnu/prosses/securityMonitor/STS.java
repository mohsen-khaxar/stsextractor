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
import org.jgrapht.graph.SimpleDirectedGraph;
import org.main.parser.automaton.Automaton;
import org.main.parser.automaton.Edge;
import org.main.parser.automaton.Event;
import org.main.parser.automaton.Event.EventType;
import org.main.parser.automaton.State;
import org.main.parser.automaton.Variable;
import org.main.parser.automaton.Variable.VariableType;

@SuppressWarnings("serial")
public class STS extends SimpleDirectedGraph<Integer, Transition>{
	public Set<String[]> variables;
	public STS(Class<? extends Transition> edgeClass) {
		super(edgeClass);
	}
	public STS() {
		super(Transition.class);
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