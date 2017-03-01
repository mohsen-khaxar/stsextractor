package se.lnu.prosses.securityMonitor;

import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.jgrapht.ext.DOTExporter;

public class STSHelper {
	public HashSet<String> actions;
	public HashSet<String> controllableActions;
	HashSet<String> variables;
	public ArrayList<Object[]> securityPolicies;
	public ArrayList<String> securityInits;
	Hashtable<String, String> actionMethodMap;
	STS sts;
	public STSHelper(STS sts) {
		this.sts = sts;
		this.variables = new HashSet<>();
		controllableActions = new HashSet<>();
		controllableActions.add(STS.START);
		controllableActions.add(STS.RETURN);
		controllableActions.add(STS.PARAMETER);
		actionMethodMap = new Hashtable<>();
		this.securityPolicies = new ArrayList<>();
		this.securityInits = new ArrayList<>();
	}
	
	public void saveAsDot(String path) throws Exception{
		DOTExporter<Integer, Transition> dotExporter = new DOTExporter<Integer, Transition>(new STSVertexNameProvider<Integer>(), null, new STSEdgeNameProvider<Transition>());
        Writer writer = new FileWriter(path);
		dotExporter.export(writer, sts);
		writer.close();
	}
	
	public String getQualifiedMethodName(String action){
		return actionMethodMap.get(action);
	}
	
	public String addAction(String qualifiedMethodName){
		String action = "";
		actionMethodMap.put(action, qualifiedMethodName);
		return action;
	}
	
	public void setCheckPoint(String qualifiedMethodName){
	}
	
	public void setObservationPoint(String qualifiedMethodName){
	}
	
	public void setMonitorablePoint(String qualifiedMethodName){
	}
	
	public void setEntryPoint(String qualifiedMethodName) {
		
	}
	
	public void addTransition(Integer source, Integer target, String action, String guard, String update) {
		if(!sts.vertexSet().contains(source)){
			sts.addVertex(source);
		}
		if(!sts.vertexSet().contains(target)){
			sts.addVertex(target);
		}
		Transition transition = new Transition(action, guard, update);
		sts.addEdge(source, source, transition);
		actions.add(action);
		variables.addAll(getVariables(guard));
		variables.addAll(getVariables(update));
	}

	private List<String> getVariables(String code) {
		ArrayList<String> variables = new ArrayList<>();
		code = code.replaceAll("\\\"", "").replaceAll("\\W\\d*\\.d+\\W|(\\s*true\\s*)|(\\s*false\\s*)|\".*\"|\\s", "");
		String[] parts = code.split("\\W+");
		for (String part : parts) {
			if(part.matches("[ibrL][\\w_$]+")){
				variables.add(part);
			}
		}
		return variables;
	}

	public void setSecurityPolicy(String securityPolicyExpression, Integer observationLocation) {
		securityPolicies.add(new Object[]{observationLocation, securityPolicyExpression});	
	}

	public void setSecurityInit(String securityPolicyExpression) {
		securityInits.add(securityPolicyExpression);
		
	}

	public List<Transition> getTransitions() {
		ArrayList<Transition> transitions = new ArrayList<>();
		transitions.addAll(sts.edgeSet());
		return transitions;
	}
	
	static public String convertToSTSSyntax(String guard) {
		guard = guard.replaceAll("==", "=");
		guard = guard.replaceAll("!=", "<>");
		guard = guard.replaceAll("&&", " and ");
		guard = guard.replaceAll("\\|\\|", " or ");
		guard = guard.replaceAll("!", " not ");
		return guard;
	}

	public List<Integer> getLocations() {
		ArrayList<Integer> locations = new ArrayList<>();
		locations.addAll(sts.vertexSet());
		return locations;
	}

	public List<Transition> getOutgoingTransitions(Integer location) {
		ArrayList<Transition> outgoingTransitions = new ArrayList<>();
		outgoingTransitions.addAll(sts.outgoingEdgesOf(location));
		return outgoingTransitions;
	}

	public List<Transition> getIncomingTransitions(Integer location) {
		ArrayList<Transition> incomingTransitions = new ArrayList<>();
		incomingTransitions.addAll(sts.incomingEdgesOf(location));
		return incomingTransitions;
	}
}
