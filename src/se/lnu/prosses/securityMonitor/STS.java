package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.jgrapht.DirectedGraph;
import org.jgrapht.EdgeFactory;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.ext.EdgeNameProvider;
import org.jgrapht.ext.VertexNameProvider;
import org.jgrapht.graph.AbstractBaseGraph;
import org.project.automaton.Automaton;
import org.project.automaton.Edge;
import org.project.automaton.Event;
import org.project.automaton.Event.EventType;
import org.project.automaton.Expression;
import org.project.automaton.State;
import org.project.automaton.Variable;
import org.project.automaton.Variable.VariableType;

@SuppressWarnings("serial")
public class STS extends AbstractBaseGraph<Integer, Transition> implements DirectedGraph<Integer, Transition>{
	public Set<String> variables;
	public Set<String> controllableMethodNames;
	Set<String> controllableEvents;
	public Hashtable<Integer, String> securityLabelling;
	
	static EdgeFactory<Integer, Transition> ef = new EdgeFactory<Integer, Transition>() {
		@Override
		public Transition createEdge(Integer sourceVertex, Integer targetVertex) {
			return new Transition();
		}
	};
	
	public STS(Set<String> controllableMethodNames) {
		super(ef, true, true);
		this.variables = new HashSet<>();
		this.controllableMethodNames = controllableMethodNames;
		controllableEvents = new HashSet<>();
		for (String controllableMethodName : this.controllableMethodNames) {
			controllableMethodName = controllableMethodName.replaceAll("\\.", "_");
			controllableEvents.add(controllableMethodName);
		}
		controllableEvents.add(Transition.START);
		controllableEvents.add(Transition.RETURN);
		controllableEvents.add(Transition.PARAMETER);
		this.securityLabelling = new Hashtable<>();
	}
	
	@SuppressWarnings("unchecked")
	public void generateAspect(String sourcePath, String targetPath) throws IOException{
		Hashtable<String, TypeDeclaration> classes = getClasses(sourcePath);
		for (String controllableMethodName : controllableMethodNames) {
			MethodDeclaration methodDeclaration = getMethodDeclaration(classes, controllableMethodName);
			String parameters = methodDeclaration.parameters().toString().replace("[", "").replace("]", "");
			String parameterNames = "";
			String[] parts = parameters.replaceAll(" ,", ",").replaceAll("  ", " ").split(" ");
			for (int i = 1; i < parts.length; i+=2) {
				parameterNames += parts[i];
			}
			String className = controllableMethodName.substring(0, controllableMethodName.lastIndexOf("."));
			FileWriter fileWriter = new FileWriter(targetPath + File.separator + controllableMethodName.replaceAll("\\.", "") + "Monitor.aj");
			fileWriter.write("import " + className + ";\n");
			fileWriter.write("import se.lnu.MonitorHelper;\n");
			fileWriter.write("public aspect " + controllableMethodName.replaceAll("\\.", "") + "Monitor{\n");
			fileWriter.write("pointcut pc() : call(* " + controllableMethodName + "(..));\n");
			fileWriter.write(methodDeclaration.getReturnType2().toString() + " around(" + parameters + ", " + className + " target) : pc() && target(target) && args(" + parameterNames + ") {\n");
			fileWriter.write("MonitorHelper monitorHelper = null;\n");
			fileWriter.write("try {monitorHelper = (MonitorHelper)Class.forName(\"se.lnu.MonitorHelperImpl\").newInstance();} catch (Exception e) {e.printStackTrace();}\n");
			fileWriter.write("Integer monitorInstanceId = monitorHelper.getMonitorInstanceId(thisJoinPoint, thisJoinPointStaticPart, thisEnclosingJoinPointStaticPart);\n");
			fileWriter.write("Integer currentLocation = monitorHelper.getCurrentLocation(monitorInstanceId);\n");
			fileWriter.write("Object[] res = null;\n");
			boolean check = false;
			for (Transition transition : this.edgeSet()) {
				if(transition.getEvent().equals(controllableMethodName.replaceAll("\\.", "_"))){
					check = true;
					try{
					fileWriter.write("if(currentLocation==" + this.getEdgeSource(transition) + " && " + 
					convertToJavaSyntax(transition.getGuard(), methodDeclaration.parameters(), getArgumentParameterMap(transition.getUpadater())) + "){\n");
					}catch (Exception e) {
						e.printStackTrace();
					}
					fileWriter.write("currentLocation = " + this.getEdgeTarget(transition) + ";\n");
					fileWriter.write("}else ");
				}
			}
			if(check){
				fileWriter.write("{\n");
				fileWriter.write("res = monitorHelper.applyCountermeasure(\"" + controllableMethodName + "\", target, thisJoinPoint.getArgs());\n");
				fileWriter.write("currentLocation = (Integer)res[1];\n");
				fileWriter.write("}\n");
			}
			fileWriter.write("monitorHelper.setCurrentLocation(monitorInstanceId, currentLocation);\n");
			fileWriter.write("if(((Integer)res[0])!=0){\n");
			String args = "";
//			for (int i = 2; i < methodDeclaration.parameters().size()+2; i++) {
//				args += "res[" + i + "], ";
//			}
			parts = parameters.replaceAll("  ", " ").split(" ");
			int c = 2;
			for (int i = 0; i < parts.length; i+=2) {
				args += "(" + parts[i] + ")res[" + c++ + "], ";
			}
			fileWriter.write("return proceed(" + args + " target);\n");
			fileWriter.write("} else { return null;}\n");
			fileWriter.write("}\n");
			fileWriter.write("}");
			fileWriter.close();
		}
	}
	
	private Map<String, String> getArgumentParameterMap(String upadater) {
		String[] parts = upadater.split(";");
		HashMap<String, String> res = new HashMap<>();
		for (String part : parts) {
			String leftHandSide = part.replaceAll("\\s", "").split("=")[0];
			String rightHandSide = part.replaceAll("\\s", "").split("=")[1];
			if(!leftHandSide.matches("L(XC|IC|XI|II)_")){
				res.put(rightHandSide, leftHandSide);
			}
		}
		return res;
	}

	public static void main(String[] args) {
	}
	
	private String convertToJavaSyntax(String guard, List<SingleVariableDeclaration> parameters, Map<String, String> argumentParameterMap) {
		guard = guard.replaceAll("=", " == ");
		guard = guard.replaceAll("<>", " != ");
		guard = guard.replaceAll(" and ", " && ");
		guard = guard.replaceAll(" or ", " || " );
		guard = guard.replaceAll(" not ", " ! ");
		String[] guardParts = guard.replaceAll("\\W\\d+\\W|(\\s*true\\s*)|(\\s*false\\s*)|\\s", "").split("\\W+");
		sort(guardParts);		
		for (String guardPart : guardParts) {
			if(!guardPart/*.replaceAll(" ", "")*/.equals("")){
				String[] parts = null;
				if(argumentParameterMap.get(guardPart)!=null){
					parts = argumentParameterMap.get(guardPart).split("_");
				}else{
					parts = guardPart.split("_");
				}
				boolean check = true;
				for (int i = 0; i < parameters.size(); i++) {
					if(parts[parts.length-1].replaceAll(" ", "").equals(parameters.get(i).getName().toString().replaceAll(" ", ""))){
						check = false;
					}
				}
				if(check /*&& !parts[parts.length-1].matches("(\\s*[+-]?\\d*.?\\d+\\s*)|(\\s*true\\s*)|(\\s*false\\s*)")*/){
					guard = guard.replaceAll(guardPart, "target." + parts[parts.length-1]);
				}else{
					guard = guard.replaceAll(guardPart, parts[parts.length-1]);
				}
			}
		}
		return guard;
	}

	private void sort(String[] guardParts) {
		if(guardParts.length>1){
			boolean sorted = false;
			while (!sorted) {
				for (int i = 0; i < guardParts.length-1; i++) {
					sorted = true;
					if(guardParts[i].length()<guardParts[i+1].length()){
						String temp = guardParts[i];
						guardParts[i] = guardParts[i+1];
						guardParts[i+1] = temp;
						sorted = false;
					}
				}
			}
		}
	}

	private MethodDeclaration getMethodDeclaration(Hashtable<String, TypeDeclaration> classes, String methodFullName) {
		String className = methodFullName.substring(0, methodFullName.lastIndexOf("."));
		String methodName = methodFullName.substring(methodFullName.lastIndexOf(".") + 1, methodFullName.length());
		MethodDeclaration res = null;
		for (MethodDeclaration methodDeclaration : classes.get(className).getMethods()) {
			if(methodDeclaration.getName().toString().equals(methodName)){
				res = methodDeclaration;
				break;
			}
		}
		return res;
	}

	private Hashtable<String, TypeDeclaration> getClasses(String sourcePath) {
		String[] sourceDir = new String[]{sourcePath};
		ArrayList<String> javaFilePaths = STSExtractor.getAllJavaFilePaths(sourcePath);
		Hashtable<String, TypeDeclaration> classes = new Hashtable<>();
		for (String javaFilePath : javaFilePaths) {
			CompilationUnit compilationUnit = STSExtractor.getCompilationUnit(sourceDir, sourceDir, javaFilePath);
			if(compilationUnit.types().size()>0&&!((TypeDeclaration)compilationUnit.types().get(0)).isInterface()){
				classes.put(((TypeDeclaration)compilationUnit.types().get(0)).resolveBinding().getQualifiedName(), ((TypeDeclaration)compilationUnit.types().get(0)));
			}
		}
		return classes;
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
		removeSTARTTransitions(sts);
		return sts;
	}
	
	private void removeSTARTTransitions(STS sts) {
		Set<Transition> transitions = new HashSet<>();
		for (Transition transition : sts.edgeSet()) {
			if(transition.getEvent().equals(Transition.START)){
				transitions.add(transition);
			}
		}
		for (Transition transition : transitions) {
			Integer target = sts.getEdgeTarget(transition);
			Set<Transition> outgoingTransitions = new HashSet<>();
			outgoingTransitions.addAll(sts.outgoingEdgesOf(target));
			for (Transition outgoingTransition : outgoingTransitions) {
				sts.removeEdge(outgoingTransition);
				sts.addEdge(0, sts.getEdgeTarget(outgoingTransition), outgoingTransition);
			}
			Set<Transition> incomingTransitions = new HashSet<>();
			incomingTransitions.addAll(sts.incomingEdgesOf(target));
			for (Transition incomingTransition : incomingTransitions) {
				sts.removeEdge(incomingTransition);
				sts.addEdge(sts.getEdgeSource(incomingTransition), 0, incomingTransition);
			}
			sts.removeEdge(transition);
			sts.removeVertex(target);
		}
	}

	private void clearUpdaters(STS sts) {
		Set<Transition> transitions = new HashSet<>();
		transitions.addAll(sts.edgeSet());
		for (Transition transition : transitions) {
			String updater = "";
			if(transition.getEvent().equals(Transition.PARAMETER)){
				updater = transition.getUpadater().replaceAll("L(XC|IC|XI|II)_", "@@");
				updater = updater.substring(0, updater.indexOf("@@"));
				for (Transition transition2 : sts.outgoingEdgesOf(sts.getEdgeTarget(transition))) {
					transition2.setUpadater(updater);
					sts.addEdge(sts.getEdgeSource(transition), sts.getEdgeTarget(transition2), transition2);
				}
				sts.removeVertex(sts.getEdgeTarget(transition));
				sts.removeEdge(transition);
			}else{
				transition.setUpadater("?");
			}
		}
	}

	private void mergeTransitions(STS sts, Transition incomingTransition, Transition outgoingTransition) {
		if(controllableEvents.contains(incomingTransition.getEvent()) || sts.getEdgeSource(incomingTransition)!=sts.getEdgeTarget(outgoingTransition)){
			String guard1 = convertToSTSSyntax(incomingTransition.getGuard()).replaceAll("\\s+", " ");
			if(needParenthesis(guard1)){
				guard1 = "(" + guard1 + ")";
			}
			String guard2 = getWeakestPrecondition(convertToSTSSyntax(outgoingTransition.getGuard())
					, getControllableTransitionUpdater(incomingTransition)).replaceAll("\\s+", " ");
			if(!guard2.matches(" true | false ") && guard2.contains(" or ")){
				guard2 = "(" + guard2 + ")";
			}
			String guard = guard1 + " and " + guard2;
			guard = guard.replaceAll("\\s?true\\s?and\\s?", " ").replaceAll("\\s?and\\s?true\\s?", " ").replaceAll("\\s+", " ");
			String updater = incomingTransition.getUpadater() + outgoingTransition.getUpadater();
			Transition newTransition = new Transition(incomingTransition.getEvent(), guard, updater);
			sts.addEdge(sts.getEdgeSource(incomingTransition), sts.getEdgeTarget(outgoingTransition), newTransition);
		}
	}
	
	private boolean needParenthesis(String guard) {
		boolean res = !guard.matches(" true | false ") 
				&& guard.contains(" or ")
				&& !guard.matches(".* or [^\\(]*\\).*");
		guard = guard.replaceAll("\\s", "");
		if(guard.charAt(0)=='(' && guard.charAt(guard.length()-1)==')'){
			int c = 0;
			for (int i = 1; i < guard.length()-1; i++) {
				if(guard.charAt(i)=='('){
					c++;
				}else if(guard.charAt(i)==')'){
					c--;
				}
				if(c<0){
					break;
				}
			}
			if(c==0){
				res = false;
			}
		}
		return res;
	}

	private String getControllableTransitionUpdater(Transition transition) {
		String res = transition.getUpadater();
//		if(controllableEvents.contains(transition.getEvent())){
//			String[] parts = transition.getUpadater().split(";");
//			for (String part : parts) {
//				if(part.matches("\\s*L(XC|IC|XI|II)_.+")){
//					res += part + ";";
//				}
//			}
//		}else{
//			res = transition.getUpadater();
//		}
		return res;
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
		String[] assignments = upadater.split(";");
		for (int i=assignments.length-1; i>=0; i--) {
			guard = replaceWithAssignment(guard, assignments[i]);
		}
		return guard;
	}

	private String replaceWithAssignment(String guard, String assignment) {
		try{
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
		}catch(Exception e){
			e.printStackTrace();
		}
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
				List<Expression> updater = new ArrayList<>();
				if(!transition.getUpadater().replaceAll(" ", "").equals("")){
					String[] parts = transition.getUpadater().split(",");
					for (String part : parts) {
						String[] assignmentParts = part.split("=");
						String righthand = "";
						String separator = "";
						for (int i=1; i<assignmentParts.length; i++) {
							righthand += separator + assignmentParts[i];
							separator = "=";
						}
						Expression expression = new Expression();
						expression.setVariable(assignmentParts[0].replaceAll(" ", ""));
						expression.setValue(righthand);
						updater.add(expression);
					}
				}
				edges.add(new Edge(String.valueOf(this.getEdgeTarget(transition)), convertToSTSSyntax(transition.getGuard()), transition.getEvent(), updater));
				eventNames.add(transition.getEvent());
			}
			State state = new State(String.valueOf(vertex), edges);
			states.add(state);
		}
		for (String eventName : eventNames) {
			events.add(new Event(EventType.C, eventName));
		}
		for (String variable : variables) {
			String[] variableParts = variable.split(",");
			VariableType type;
			if(variableParts[0].equals("int")){
				type = VariableType.INT;
			} else if(variableParts[0].equals("real")){
				type = VariableType.FLOAT;
			} else if(variableParts[0].equals("bool")){
				type = VariableType.BOOLEAN;
			} else{
				type = VariableType.UNDEFINED;
			}
			vars.add(new Variable(type , variableParts[1]));
		}
		Automaton automaton = new Automaton("CaseStudy", events, vars, states);
		return automaton;
	}

	private String convertToSTSSyntax(String guard) {
		guard = guard.replaceAll("==", "=");
		guard = guard.replaceAll("!=", "<>");
		guard = guard.replaceAll("&&", " and ");
		guard = guard.replaceAll("\\|\\|", " or ");
		guard = guard.replaceAll("!", " not ");
		return guard;
	}
	
	public String convertToReax(){
		Hashtable<String, String> transitionFunctions = new Hashtable<>();
		HashSet<String> events = new HashSet<>();
		transitionFunctions.put("LOC", "LOC' = -");
		for (String variable : variables) {
			String[] variableParts = variable.split(",");
			transitionFunctions.put(variableParts[1], variableParts[1] + "' = -");
		}
		for (Transition transition : this.edgeSet()) {
			events.add(transition.getEvent());
			transitionFunctions.put("LOC", transitionFunctions.get("LOC") + "else if " + transition.getEvent() + " and LOC="	+ this.getEdgeSource(transition) 
			+ " and (" + convertToSTSSyntax(transition.getGuard()) + ")" + " then " + this.getEdgeTarget(transition) + " \n");
			if(!transition.getUpadater().equals("")){
				String[] updaterParts = transition.getUpadater().split(";");
				for (String updaterPart : updaterParts) {
					String leftHandSide = "";
					try{
						leftHandSide = updaterPart.substring(0, updaterPart.indexOf("=")).replaceAll(" ", "");
					}catch(Exception e){
						e.printStackTrace();
					}
					String rightHandSide = convertToSTSSyntax(updaterPart.substring(updaterPart.indexOf("=")+1, updaterPart.length()));
					transitionFunctions.put(leftHandSide, transitionFunctions.get(leftHandSide) + "else if " + transition.getEvent() + " and LOC="	+ this.getEdgeSource(transition) 
					+ " and (" + convertToSTSSyntax(transition.getGuard()) + ")" + " then " + rightHandSide + "\n");
				}
			}
		}
		Integer maxLocation = 0;
		for (Integer location : this.vertexSet()) {
			if(location>maxLocation){
				maxLocation = location;
			}
		}
		String stateSection = "!state\nLOC : uint[" + (Math.round(Math.log(maxLocation)/Math.log(2))+1) + "];\n";
		for (String variable : variables) {
			String[] variableParts = variable.split(",");
			if(stateSection.contains(" : " + variableParts[0] + ";")){
				stateSection = stateSection.replaceFirst(" : " + variableParts[0] + ";", ", " + variableParts[1] + " : " + variableParts[0] + ";");
			}else{
				stateSection += variableParts[1] + " : " + variableParts[0] + ";\n";
			}
		}
		String inputSection = "!input\n";
		String separator = "";
		for (String event : events) {
			if(!this.controllableEvents.contains(event)){
				inputSection += separator + event;
				separator = ", ";
			}
		}
		inputSection += " : bool;\n\n";
		String controllableSection = "!controllable\n";
		separator = "";
		for (String event : controllableEvents) {
			controllableSection += separator + event;
			separator = ", ";
		}
		controllableSection += " : bool;\n\n";
		String transitionSection = "!transition\n";
		for (String key : transitionFunctions.keySet()) {
			if(transitionFunctions.get(key).equals(key + "' = -")){
				transitionSection += key + "' = " + key + ";\n\n";
			}else{
				transitionSection += transitionFunctions.get(key).replaceFirst("-else ", "") + "else " + key + ";\n\n";
			}
		}
		Integer minLocation = Integer.MAX_VALUE;
		for (Integer location : this.vertexSet()) {
			if(location<minLocation){
				minLocation = location;
			}
		}
		String initialSection = "!initial\nLOC=" + minLocation + ";\n\n";
		String invariantSection = "!invariant\n";
		separator = "";
		String[] SLPrefixes = {"LXC_", "LXI_", "LIC_", "LII_"};
		for (Transition transition : this.edgeSet()) {
			if(!transition.getUpadater().equals("")){
				String[] upadaterParts = transition.getUpadater().split(";");
				for (String upadaterPart : upadaterParts) {
					if(!upadaterPart.split("=")[0].startsWith("LXC_")
							&& !upadaterPart.split("=")[0].startsWith("LXI_")
							&& !upadaterPart.split("=")[0].startsWith("LIC_")
							&& !upadaterPart.split("=")[0].startsWith("LII_")){
						for (String SLPrefix : SLPrefixes) {
							if(securityLabelling.get(this.getEdgeTarget(transition))!=null){
								String[] securityLabellingParts = securityLabelling.get(this.getEdgeTarget(transition)).split(";");
								for (String securityLabellingPart : securityLabellingParts) {
									if(securityLabellingPart.replaceAll(" ", "").startsWith(SLPrefix+upadaterPart.split("=")[0].replaceAll(" ", ""))){
										invariantSection += separator + "(LOC<>" + this.getEdgeTarget(transition) + " or " + securityLabellingPart + ")";
										separator = " and ";
									}
								}
							}
						}
					}
				}
			}
		}
		invariantSection += ";";
		return stateSection + inputSection + controllableSection + transitionSection + initialSection + invariantSection;
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