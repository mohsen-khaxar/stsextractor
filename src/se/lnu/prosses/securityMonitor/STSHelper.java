package se.lnu.prosses.securityMonitor;

import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import org.jgrapht.ext.DOTExporter;

public class STSHelper implements Cloneable{
	public HashSet<String> actions;
	public HashSet<String> controllableActions;
	public HashSet<String> monitorableActions;
	HashSet<String> variables;
	public Hashtable<String, String[]> uniqueNameMetadatas;
	public ArrayList<Object[]> securityPolicies;
	public ArrayList<String> securityInits;
	Hashtable<String, String> actionMethodMap;
	Hashtable<Integer, String> defaultActions;
	STS sts;
	STS savedVersionSts;
	static public String LOCAL = "L";
	static public String PARAMETER = "P";
	static public String STATICFIELD = "S";
	static public String INSTANCEFIELD = "I";
	public STSHelper() {
		this.sts = new STS();
		this.variables = new HashSet<>();
		this.actions = new HashSet<>();
		this.uniqueNameMetadatas = new Hashtable<>();
		this.actionMethodMap = new Hashtable<>();
		this.controllableActions = new HashSet<>();
		this.monitorableActions = new HashSet<>();
		this.monitorableActions.add(addAction("se.lnu.DummyMethods.monitorablePoint"));
		this.securityPolicies = new ArrayList<>();
		this.securityInits = new ArrayList<>();
		this.defaultActions = new Hashtable<>();
	}
	
	@Override
	protected Object clone() throws CloneNotSupportedException {
		STSHelper stsHelper = (STSHelper) super.clone();
		stsHelper.sts = (STS) this.sts.clone();
		return stsHelper;
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
		String action = qualifiedMethodName.replaceAll("\\.", "_");
		actionMethodMap.put(action, qualifiedMethodName);
		return action;
	}
	
	public void setCheckPoint(String qualifiedMethodName){
		String action = addAction(qualifiedMethodName);
		controllableActions.add(action);
		monitorableActions.add(action);
	}
	
	public void setMonitorablePoint(String qualifiedMethodName){
		String action = addAction(qualifiedMethodName);
		monitorableActions.add(action);
	}
	
	public void addTransition(Integer source, Integer target, String action, String guard, String update, Hashtable<String, Object> extraData) {
		addTransition(source, target, new Transition(action, guard, update, extraData));
	}
	
	public void addTransition(Integer source, Integer target, String action, String guard, String update) {
		addTransition(source, target, new Transition(action, guard, update));
	}
	
	private void addTransition(Integer source, Integer target, Transition transition) {
		if(!sts.vertexSet().contains(source)){
			sts.addVertex(source);
		}
		if(!sts.vertexSet().contains(target)){
			sts.addVertex(target);
		}
		sts.addEdge(source, target, transition);
		actions.add(transition.getAction());
		variables.addAll(getVariables(transition.getGuard()));
		variables.addAll(getVariables(transition.getUpdate()));
	}

	private List<String> getVariables(String code) {
		ArrayList<String> variables = new ArrayList<>();
		code = code.replaceAll("\\\"", "").replaceAll("[^\\w_$](or|and|not)[^\\w_$]", "&").replaceAll("\\W\\d*\\.d+\\W|(\\s*true\\s*)|(\\s*false\\s*)|\".*\"|\\s", "");
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
	
	public int getSecurityPolicyIndex(){
		return securityPolicies.size()-1;
	}
	
	public void readdSecurityPolicies(int startIndex, int endIndex, Integer observationLocation){
		for (int i = startIndex; i <= endIndex; i++) {
			securityPolicies.add(new Object[]{observationLocation, securityPolicies.get(i)[1]});
		}
	}

	public void setSecurityInit(String securityInitExpression) {
		securityInits.add(securityInitExpression);
		variables.addAll(getVariables(securityInitExpression));
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

	public void removeTransition(Transition transition) {
		sts.removeEdge(transition);		
	}

	public void removeLocation(Integer location) {
		sts.removeVertex(location);		
	}

	public int getOutDegree(Integer location) {
		return sts.outDegreeOf(location);
	}

	public void removeAllTransitions(Integer source, Integer target) {
		sts.removeAllEdges(source, target);
	}
	
	public String getJavaName(String uniqueName){
		String res = null;
		if(uniqueNameMetadatas.get(uniqueName)!=null){
			res = uniqueNameMetadatas.get(uniqueName)[0];
		}
		return res;
	}
	
	public String getJavaType(String uniqueName){
		String res = null;
		if(uniqueNameMetadatas.get(uniqueName)!=null){
			res = uniqueNameMetadatas.get(uniqueName)[1];
		}
		return res;
	}
	
	public String getDeclaringMethodName(String uniqueName){
		String res = null;
		if(uniqueNameMetadatas.get(uniqueName)!=null){
			res = uniqueNameMetadatas.get(uniqueName)[2];
		}
		return res;
	}
	
	public String getJavaScope(String uniqueName){
		String res = null;
		if(uniqueNameMetadatas.get(uniqueName)!=null){
			res = uniqueNameMetadatas.get(uniqueName)[3];
		}
		return res;
	}
	
	public void setJavaName(String uniqueName, String javaName){
		String[] metadata = null; 
		if(uniqueNameMetadatas.get(uniqueName)==null){
			metadata = new String[4];
		}else{
			metadata = uniqueNameMetadatas.get(uniqueName);
		}
		metadata[0] = javaName;
		uniqueNameMetadatas.put(uniqueName, metadata);
	}
	
	public void setJavaType(String uniqueName, String javaType){
		String[] metadata = null; 
		if(uniqueNameMetadatas.get(uniqueName)==null){
			metadata = new String[4];
		}else{
			metadata = uniqueNameMetadatas.get(uniqueName);
		}
		metadata[1] = javaType;
		uniqueNameMetadatas.put(uniqueName, metadata);
	}
	
	public void setDeclaringMethodName(String uniqueName, String declaringMethodName){
		String[] metadata = null; 
		if(uniqueNameMetadatas.get(uniqueName)==null){
			metadata = new String[4];
		}else{
			metadata = uniqueNameMetadatas.get(uniqueName);
		}
		metadata[2] = declaringMethodName;
		uniqueNameMetadatas.put(uniqueName, metadata);
	}
	
	public void setJavaScope(String uniqueName, String javaScope){
		String[] metadata = null; 
		if(uniqueNameMetadatas.get(uniqueName)==null){
			metadata = new String[4];
		}else{
			metadata = uniqueNameMetadatas.get(uniqueName);
		}
		metadata[3] = javaScope;
		uniqueNameMetadatas.put(uniqueName, metadata);
	}

	public void saveVersion() {
		savedVersionSts = (STS) sts.clone();		
	}

	public void restoreLastVersion() {
		sts = savedVersionSts;		
	}

	public void setDefaultAction(Integer observationPointStartPosition, String defaultAction) {
		defaultActions.put(observationPointStartPosition, defaultAction);		
	}

	public void appendUpdate(Integer sourcelLocation, Integer targetLocation, String appending) {
		String update = sts.getEdge(sourcelLocation, targetLocation).getUpdate();
		sts.getEdge(sourcelLocation, targetLocation).setUpdate(update + appending);
	}
}
