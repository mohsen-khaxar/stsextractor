package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
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
	
	static EdgeFactory<Integer, Transition> ef = new EdgeFactory<Integer, Transition>() {
		@Override
		public Transition createEdge(Integer sourceVertex, Integer targetVertex) {
			return new Transition();
		}
	};
	
	public STS() {
		super(ef, true, true);
		this.variables = new HashSet<>();
		this.controllableMethodNames = new HashSet<>();
		this.controllableEvents = new HashSet<>(); 
	}
	
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
			fileWriter.write("pointcut pointcup() : call(* " + controllableMethodName + "(..));\n");
			fileWriter.write(methodDeclaration.getReturnType2().toString() + " around(" + parameters + ", " + className + " target) : pointcup() && target(target) && args(" + parameterNames + ") {\n");
			fileWriter.write("MonitorHelper monitorHelper = null;\n");
			fileWriter.write("try {monitorHelper = (MonitorHelper)Class.forName(\"se.lnu.MonitorHelperImpl\").newInstance();} catch (Exception e) {e.printStackTrace();}\n");
			fileWriter.write("Integer monitorInstanceId = monitorHelper.getMonitorInstanceId(thisJoinPoint, thisJoinPointStaticPart, thisEnclosingJoinPointStaticPart);\n");
			fileWriter.write("Integer currentLocation = monitorHelper.getCurrentLocation(monitorInstanceId);\n");
			fileWriter.write("Object[] res = null;\n");
			boolean check = false;
			for (Transition transition : this.edgeSet()) {
				if(transition.getEvent().equals(controllableMethodName.replaceAll("\\.", "_"))){
					check = true;
					fileWriter.write("if(currentLocation==" + this.getEdgeSource(transition) + " && " + convertToJavaSyntax(transition.getGuard(), methodDeclaration.parameters()) + "){\n");
					fileWriter.write("currentLocation = " + this.getEdgeTarget(transition) + ";\n");
					fileWriter.write("}else ");
				}
			}
			if(check){
				fileWriter.write("{\n");
				fileWriter.write("res = monitorHelper.applyCountermeasure(target, thisJoinPoint.getArgs());\n");
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
	
	public static void main(String[] args) {
	}
	
	@SuppressWarnings("rawtypes")
	private String convertToJavaSyntax(String guard, List parameters) {
		guard = guard.replaceAll("=", "==");
		guard = guard.replaceAll("<>", "!=");
		guard = guard.replaceAll(" and ", "&&");
		guard = guard.replaceAll(" or ", "||" );
		guard = guard.replaceAll(" not ", "!");
		String[] guardParts = guard.replaceAll("\\W\\d+\\W", "").split("\\W+");
		sort(guardParts);		
		for (String guardPart : guardParts) {
			if(!guardPart.replaceAll(" ", "").equals("")){
				String[] parts = guardPart.split("_");
				boolean check = true;
				for (int i = 0; i < parameters.size(); i++) {
					if(parts[parts.length-1].replaceAll(" ", "").equals(parameters.get(i).toString().replaceAll(" ", ""))){
						check = false;
					}
				}
				if(check){
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
		controllableEvents = new HashSet<>();
		for (String controllableMethodName : controllableMethodNames) {
			controllableMethodName = controllableMethodName.replaceAll("\\.", "_");
			controllableEvents.add(controllableMethodName);
		}
		controllableEvents.add(Transition.START);
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
			String guard = convertToSTSSyntax(incomingTransition.getGuard()) + " and " + getWeakestPrecondition(convertToSTSSyntax(outgoingTransition.getGuard()), incomingTransition.getUpadater());
			guard = guard.replaceAll("\\s?true\\s?and\\s?", "").replaceAll("\\s?and\\s?true\\s?", "");
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