package se.lnu.prosses.securityMonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.project.automaton.Automaton;

public class STSExtractor {
	int newLocation = 1;
	int pcLevel = 0;
	int maxPcLevel = 0;
	Hashtable<Integer, Integer> blocks;
	public ArrayList<String> includingFilter;
	public ArrayList<String> excludingFilter;
	public ArrayList<String> entryPoints;
	public STS sts;
	private Hashtable<String, TypeDeclaration> classes = new Hashtable<>();
	final static String DUMMY_METHOD = "se.lnu.SL.def";
	public STSExtractor(ArrayList<String> includingFilter, ArrayList<String> excludingFilter, ArrayList<String> entryPoints, Set<String> controllableMethodNames) {
		blocks = new Hashtable<>();
		this.includingFilter = includingFilter;
		this.excludingFilter = excludingFilter;
		this.entryPoints = entryPoints;
		this.sts = new STS(controllableMethodNames);
		Transition transition =  new Transition(Transition.START, "true", "");
		sts.addVertex(0);
		sts.addVertex(1);
		sts.addEdge(0, 1, transition);
		sts.variables.add("bool,LIC_PC");
		sts.variables.add("bool,LII_PC");
	}
	
	public Automaton convertToAutomaton(){
		return sts.convertToAutomaton();
	}
	
	public String convertToReax(){
		return sts.convertToReax();
	}
	
	String runReax(String reaxScript){
		String res = "";
		try {
			String reaxScriptFile = "_reax.ctrln";
			FileWriter fileWriter = new FileWriter(new File(reaxScriptFile), false);
			fileWriter.write(reaxScript);
			fileWriter.close();
			Process process = Runtime.getRuntime().exec("reax  " + reaxScriptFile + " -a sS:d={P},deads -t --debug D2");
			process.waitFor();
		    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
		    String line = "";
		    while ((line = bufferedReader.readLine())!= null) {
		    	res += line + "\n";
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(res);
		if(res.indexOf("Triangularized controller:\n")!=-1){
			res = res.substring(res.indexOf("Triangularized controller:")+27);
			res = res.substring(0, res.indexOf("["));
		}else{
			res = "";
		}
		return res;
	}
	
	Hashtable<String, String> getSecurityGuards(String reaxResult){
		Hashtable<String, String> securityGuards = new Hashtable<>();
		String[] parts = reaxResult.split(";");
		for (String part : parts) {
			if(!part.replaceAll("\\s", "").equals("")){
				String event = part.substring(0, part.indexOf("=")).replaceAll("['\\s]", "");
				String[] orParts = part.substring(part.indexOf("=")+1, part.length()).split("or");
				for (String orPart : orParts) {
					String[] andParts = orPart.split("and");
					String[] locations = null;
					for (String andPart : andParts) {
						if(andPart.matches("\\s*LOC\\s*in.*")){
							andPart = andPart.replaceAll("[^\\d,]", "");
							locations = andPart.split(",");
							break;
						}
					}
					orPart = orPart.replaceAll("LOC = \\d+", "true").replaceAll("LOC in \\{(\\s*\\d+\\s*,?)+\\}", "true");
					if(locations==null){
						String value = securityGuards.get(event+",-1")==null ? orPart : securityGuards.get(event+",-1")+" or "+orPart;
						securityGuards.put(event+",-1", value);
					}else{
						for (String location : locations) {
							String value = securityGuards.get(event+","+location)==null ? orPart : securityGuards.get(event+","+location)+" or "+orPart;
							securityGuards.put(event+","+location, value);
						}
					}
				}
			}
		}
		return securityGuards;
	}
	
	STS makeControlled(String reaxResult){
		STS res = (STS) sts.clone();
		if(!reaxResult.equals("")){
			Hashtable<String, String> securityGuards = getSecurityGuards(reaxResult);
			Set<Transition> transitions = new HashSet<>();
			transitions.addAll(res.edgeSet());
			for (Transition transition : transitions) {
				if(res.controllableEvents.contains(transition.getEvent()) && !transition.getEvent().equals(Transition.START)
						&& !transition.getEvent().equals(Transition.RETURN) && !transition.getEvent().equals(Transition.PARAMETER)){
					String guard = "";
					String separator = "";
					if(securityGuards.get(transition.getEvent().replaceAll("\\s", "")+",-1")!=null){
						guard = securityGuards.get(transition.getEvent().replaceAll("\\s", "")+",-1");
						separator = " or ";
					}
					if(securityGuards.get(transition.getEvent().replaceAll("\\s", "")+","+res.getEdgeSource(transition))!=null){
						guard += separator + securityGuards.get(transition.getEvent().replaceAll("\\s", "")+","+res.getEdgeSource(transition));
					}
					if(guard.equals("")){
						guard = "false";
					}
					transition.setGuard(guard);
					Transition insecureTransition = new Transition(transition.getEvent(), " not (" + guard.replaceAll("\\s+", " ") + ")", transition.upadater);
					if(!res.vertexSet().contains(-res.getEdgeTarget(transition))){
						res.addVertex(-res.getEdgeTarget(transition));
					}
					res.addEdge(res.getEdgeSource(transition), -res.getEdgeTarget(transition), insecureTransition);
					Transition returnTransition = new Transition(Transition.RETURN, "true", "");
					res.addEdge(-res.getEdgeTarget(transition), res.getEdgeTarget(transition), returnTransition);
					
					((Transition)(res.incomingEdgesOf(res.getEdgeSource(transition)).toArray()[0])).setEvent(Transition.PARAMETER);
				}
			}
		}
		return res;
	}
	
	public STS generateControlledSTS(){
		String reaxScript = convertToReax();
		String reaxResult = runReax(reaxScript);
		return makeControlled(reaxResult);
	}
	
	public static void main(String[] args) {
		STSExtractor se = new STSExtractor(null, null, null, null);
		
	}
	
	public void generateAspect(String sourcePath, String targetPath) throws IOException{
		sts.generateAspect(sourcePath, targetPath);
	}
	
	void getClasses(String directoryPath, String[] classPath) throws Exception{
		String[] sourceDir = new String[]{directoryPath};
		ArrayList<String> javaFilePaths = getAllJavaFilePaths(directoryPath);
		for (String javaFilePath : javaFilePaths) {
			JavaNormalizer javaNormalizer = new JavaNormalizer();
			javaNormalizer.normalize(sourceDir, classPath, javaFilePath);
			CompilationUnit compilationUnit = getCompilationUnit(sourceDir, classPath, javaFilePath);
			if(compilationUnit.types().size()>0&&!((TypeDeclaration)compilationUnit.types().get(0)).isInterface()){
				classes.put(((TypeDeclaration)compilationUnit.types().get(0)).resolveBinding().getQualifiedName(), ((TypeDeclaration)compilationUnit.types().get(0)));
			}
		}
	}
	
	void removeTemporaryFiles(String directoryPath){
		ArrayList<String> javaFilePaths = getAllJavaFilePaths(directoryPath);
		for (String javaFilePath : javaFilePaths) {
			new File(javaFilePath).delete();
			new File(javaFilePath + "_").renameTo(new File(javaFilePath));
		}
	}
	
	public void extract(String directoryPath, String[] classPath, Set<String> controllableMethodNames) throws Exception{
		sts.controllableMethodNames = controllableMethodNames;
		getClasses(directoryPath, classPath);
		for (TypeDeclaration cls : classes.values()) {
			extractForAClass(cls);
		}
		addImplicitIndirectSecurityAssignments();
		removeTemporaryFiles(directoryPath);
		System.out.println("DONE.");
//		sts.saveAsDot(directoryPath + File.separator + "model.dot");
//		Automaton automaton = sts.convertToAutomaton();
//		FileWriter fileWriter = new FileWriter(directoryPath + File.separator + "vars.txt");
//		List<Variable> vars = automaton.getVariables();
//		for (Variable variable : vars) {
//			fileWriter.write(variable.getName() + "\n");
//		}
//		fileWriter.close();
//		STS controlledSTS = generateControlledSTS();
//		controlledSTS.saveAsDot("/home/mohsen/aspects/modelc.dot");
//		controlledSTS = controlledSTS.convertToUncontrollableFreeSTS();
//		controlledSTS.saveAsDot("/home/mohsen/aspects/freemodelc.dot");
//		controlledSTS.generateAspect(directoryPath, "/home/mohsen/aspects");
//		String reax = sts.convertToReax();
//		System.out.println(reax);
//		STS uncontrollableFreeSTS = sts.convertToUncontrollableFreeSTS();
//		uncontrollableFreeSTS.saveAsDot(directoryPath + File.separator + "freemodel.dot");
//		uncontrollableFreeSTS.generateAspect(directoryPath, "P:\\aspects");
	}
	
	private void addImplicitIndirectSecurityAssignments() {
		for (Integer location = 1; location <= newLocation; location++) {
			if(sts.outDegreeOf(location)>1){
				Set<Transition> outgoingTransitions = sts.outgoingEdgesOf(location);
				ArrayList<Integer> exitLocations = new ArrayList<>();
				Hashtable<Transition, String> possibleModifieds = new Hashtable<>();
				if(!isLoopEntrance(location)){
					exitLocations.add(blocks.get(location));
					exitLocations.add(0);
					for (Transition transition : outgoingTransitions) {
						possibleModifieds.put(transition, getAllLeftHandSides(getAllTotalUpdater(sts.getEdgeTarget(transition), exitLocations)));
					}
					for (Transition transition : outgoingTransitions) {
						for (Transition transition2 : possibleModifieds.keySet()) {
							if(!transition.equals(transition2)){
								transition.setUpadater(getImplicitIndirectSecurityAssignment(possibleModifieds.get(transition2)) + transition.getUpdater());
//								for (Transition transition3 : sts.outgoingEdgesOf(sts.getEdgeTarget(transition))) {
//									transition3.setUpadater(getImplicitIndirectSecurityAssignment(possibleModifieds.get(transition2)) + transition3.getUpdater());
//								}
							}
						}
					}
				}else{
					exitLocations.add(0);
					exitLocations.add(location);
					exitLocations.add(blocks.get(-location));
					for (Transition transition : outgoingTransitions) {
						if(sts.getEdgeTarget(transition)!=blocks.get(-location)){
							possibleModifieds.put(transition, getAllLeftHandSides(getAllTotalUpdater(sts.getEdgeTarget(transition), exitLocations)));
						}
					}
					for (Transition transition : possibleModifieds.keySet()) {
						sts.getEdge(location, blocks.get(-location)).setUpadater(getImplicitIndirectSecurityAssignment(possibleModifieds.get(transition)) 
								+ sts.getEdge(location, blocks.get(-location)).getUpdater());
//						for (Transition transition2 : sts.outgoingEdgesOf(blocks.get(-location))) {
//							transition2.setUpadater(getImplicitIndirectSecurityAssignment(possibleModifieds.get(transition)) + transition2.getUpdater());
//						}
					}
				}
				ArrayList<String> variables = new ArrayList<>();
				for (String possibleModified : possibleModifieds.values()) {
					for (String part : possibleModified.split(",")) {
						part = part.replaceAll("\\s", "");
						if(!part.equals("")){
							variables.add(part);
						}
					}
				}
				clearWrongSecurityAssignments(location, variables);
			}
		}
	}
	
	private void clearWrongSecurityAssignments(Integer startLocation, ArrayList<String> variables) {
		Queue<Integer> queue = new LinkedList<>();
		ArrayList<Integer> visited = new ArrayList<>();
		visited.add(0);
		queue.add(startLocation);
		while (!queue.isEmpty()) {
			Integer location = queue.poll();
			for (Transition transition : sts.outgoingEdgesOf(location)) {
				boolean has = false;
				for (String variable : variables) {
					String updater = transition.getUpdater();
					if(updater.matches("LIC_"+variable + "\\s*=\\s*false")
							|| updater.matches("LII_"+variable + "\\s*=\\s*true")){
						transition.setUpadater(updater.replaceAll("(LIC_"+variable + "\\s*=\\s*false\\s*;)|"+"(LII_"+variable + "\\s*=\\s*true\\s*;)", ""));
						has = true;
					}
				}
				if(has){
					queue.add(sts.getEdgeTarget(transition));
				}
			}
		}
	}

	private void addImplicitIndirectSecurityAssignmentsOld() {
		Integer maxLocation = newLocation;
		for (Integer location = 1; location <= maxLocation; location++) {
			if(sts.outDegreeOf(location)>1){
				if(!isLoopEntrance(location)){
					Set<Transition> outgoingTransitions = sts.outgoingEdgesOf(location);
					ArrayList<Integer> exitLocations = getExitLocations(outgoingTransitions);
					Hashtable<Transition, ArrayList<String[]>> possibleModifieds = new Hashtable<>();
					for (Transition transition : outgoingTransitions) {
						possibleModifieds.put(transition, getPossibleModifiedVariables(transition, exitLocations));
					}
					for (Transition transition : outgoingTransitions) {
						Integer intermediateLocation = -1;
						for (Transition transition2 : possibleModifieds.keySet()) {
							if(!transition.equals(transition2)){
								if(intermediateLocation==-1){
									intermediateLocation = newLocation();
								}
								for (String[] possibleModified : possibleModifieds.get(transition2)) {
									String updater = getImplicitIndirectSecurityAssignment(possibleModified[1]);
									sts.addEdge(sts.getEdgeSource(transition), intermediateLocation, new Transition(Transition.TAU, 
											transition.getGuard() + " and " + possibleModified[0], updater));
								}
							}
						}
					}
				}else{
					
				}
			}
		}
	}

	private String getImplicitIndirectSecurityAssignment(String variables) {
		String updater = "";
		for (String part : variables.split(",")) {
			part = part.replaceAll("\\s", "");
			if(!part.equals("")){
				updater += part + "=" + part + ";";
				updater += "LIC_" + part + "=" + "LIC_" + part + " or LIC_PC;"; 
				updater += "LII_" + part + "=" + "LII_" + part + " or LII_PC;";
			}
		}
		return updater;
	}

	private ArrayList<Integer> getExitLocations(Set<Transition> transitions) {
	ArrayList<Integer> res = new ArrayList<>();
		res.add(0);
		ArrayList<Integer> locations = new ArrayList<>();
		Object[] ts = transitions.toArray();
		for (int i = 1; i < ts.length; i++) {
			locations.add(sts.getEdgeTarget((Transition)ts[i]));
		}
		Queue<Integer> queue = new LinkedList<>();
		queue.add(sts.getEdgeTarget((Transition)ts[0]));
		ArrayList<Integer> visited = new ArrayList<>();
		visited.add(sts.getEdgeSource((Transition)ts[0]));
		while(!queue.isEmpty()){
			Integer location = queue.poll();
			visited.add(location);
			boolean reachable = true;
			for (Integer temp : locations) {
				reachable = reachable && reachable(temp, location);
				if(reachable==false){
					break;
				}
			}
			if(reachable){
				res.add(location);
			}else{
				for (Transition transition : sts.outgoingEdgesOf(location)) {
					if(!visited.contains(sts.getEdgeTarget(transition)) && !queue.contains(sts.getEdgeTarget(transition))){
						queue.add(sts.getEdgeTarget(transition));
					}
				}
			}
		}
		return res;
	}

	private boolean reachable(Integer startLocation, Integer endLocation) {
		boolean res = false;
		Queue<Integer> queue = new LinkedList<>();
		queue.add(startLocation);
		ArrayList<Integer> visited = new ArrayList<>();
		visited.add(0);
		while(!queue.isEmpty()){
			Integer location = queue.poll();
			visited.add(location);
			for (Transition transition : sts.outgoingEdgesOf(location)) {
				if(sts.getEdgeTarget(transition)==endLocation){
					res = true;
					break;
				}else if(!visited.contains(sts.getEdgeTarget(transition))){
					queue.add(sts.getEdgeTarget(transition));
				}
			}
		}
		return res;
	}

	private ArrayList<String[]> getPossibleModifiedVariables(Transition transition, ArrayList<Integer> exitLocations) {
		Hashtable<Integer, String[]> executionPathes = new Hashtable<>();
//		String[] possibleModifiedVariables = new String[2];
//		possibleModifiedVariables[0] = transition.getGuard();
//		possibleModifiedVariables[1] = transition.getUpadater();
//		String[] upadaterParts = transition.getUpadater().replaceAll("\\s", "").split(";");
//		for (String upadaterPart : upadaterParts) {
//			if(!upadaterPart.equals("")){
//				possibleModifiedVariables.add(upadaterPart.split("=")[0]);
//			}
//		}
		executionPathes.put(sts.getEdgeTarget(transition), new String[]{transition.getGuard(), transition.getUpdater()});
		Queue<Integer> queue = new LinkedList<Integer>();
		if(!exitLocations.contains(sts.getEdgeTarget(transition))){
			queue.add(sts.getEdgeTarget(transition));
		}
		while (!queue.isEmpty()) {
			Integer location = queue.poll();
			if(!isLoopEntrance(location)){
				Set<Transition> outgoingTransitions = sts.outgoingEdgesOf(location);
				for (Transition outgoingTransition : outgoingTransitions) {
					String[] temp = new String[2];
					temp[0] = "(" + executionPathes.get(location)[0] + ") and (" + getWeakestPrecondition(executionPathes.get(location)[0], executionPathes.get(location)[1]) + ")";
					temp[1] = executionPathes.get(location)[1] + outgoingTransition.getUpdater();
					executionPathes.remove(location);
					executionPathes.put(sts.getEdgeTarget(outgoingTransition), temp);
					if(!exitLocations.contains(sts.getEdgeTarget(outgoingTransition))){
						queue.add(sts.getEdgeTarget(outgoingTransition));
					}
				}
			}else{
				executionPathes.get(location)[1] += getAllTotalUpdater(location, exitLocations);
			}
		}
		ArrayList<String[]> res = new ArrayList<>();
		res.addAll(executionPathes.values());
		for (String[] strings : res) {
			String variables = getAllLeftHandSides(strings[1]);
			strings[1] = variables;
		}
		return res;
	}

	private String getAllLeftHandSides(String statement) {
		String variables = "";
		String[] parts = statement.split(";");
		String separator = "";
		for (String part : parts) {
			part = part.replaceAll("\\s", "");
			if(!part.equals("") && !part.matches("(LIC|LXC|LII|LXI)_.*") && !(","+variables).contains(","+part.split("=")[0])){
				variables += separator + part.split("=")[0];
				separator = ",";
			}
		}
		return variables;
	}
	
	private String getAllTotalUpdater(Integer startLocation, ArrayList<Integer> exitLocations) {
		String res = "";
		ArrayList<Integer> visited = new ArrayList<>();
		visited.addAll(exitLocations);
		Queue<Integer> queue = new LinkedList<Integer>();
		if(!exitLocations.contains(startLocation)){
			queue.add(startLocation);
		}
		while(!queue.isEmpty()){
			Integer location = queue.poll();
			visited.add(location);
			Set<Transition> outgoingTransitions = sts.outgoingEdgesOf(location);
			for (Transition outgoingTransition : outgoingTransitions) {
				res += outgoingTransition.getUpdater();
				if(!visited.contains(sts.getEdgeTarget(outgoingTransition))){
					queue.add(sts.getEdgeTarget(outgoingTransition));
				}
			}
		}
		return res;
	}

	private String getWeakestPrecondition(String guard, String updater) {
		String[] assignments = updater.split(";");
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

	private boolean isLoopEntrance(Integer location) {
//		boolean res = false;
//		for (Transition transition : sts.outgoingEdgesOf(location)) {
//			if(reachable(sts.getEdgeTarget(transition), location)){
//				res = true;
//				break;
//			}
//		}
//		return res;
		return blocks.get(-location)!=null;
	}

	private void extractForAClass(TypeDeclaration cls) throws Exception {
		for (MethodDeclaration methodDeclaration : cls.getMethods()) {
			String qualifiedMethodName = methodDeclaration.resolveBinding().getDeclaringClass().getQualifiedName() + "." + methodDeclaration.getName();
			boolean matched = false;
			for (String filter : entryPoints) {
				if (qualifiedMethodName.matches(filter)) {
					matched = true;
					break;
				}
			}
			String methodModifier = methodDeclaration.modifiers().toString().toLowerCase();
			if (matched && methodDeclaration.getBody() != null	&& (methodModifier.contains("public") || methodModifier.contains("protected"))) {
				Integer finalLocation = processMethod(methodDeclaration);
				Transition transition = new Transition(Transition.TAU, "true", "");
				sts.addEdge(finalLocation, 0, transition);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private Integer processMethod(MethodDeclaration methodDeclaration) {
		List<Statement> statements = methodDeclaration.getBody().statements();
		Integer location = newLocation();
		String methodFullName = methodDeclaration.resolveBinding().getDeclaringClass().getQualifiedName().toString();
		methodFullName += "." + methodDeclaration.getName().toString();
		methodFullName = methodFullName.replaceAll("\\.", "_");
		Transition transition = new Transition(methodFullName, "true", "");
		sts.addVertex(location);
		sts.addEdge(1, location, transition);
 		String prefix = methodDeclaration.resolveBinding().getDeclaringClass().getQualifiedName().replaceAll("\\.", "_") + "_" + methodDeclaration.getName();
		Hashtable<String, String> SL = new Hashtable<String, String>();
		SL.put("PC,IC", "l");
		SL.put("PC,II", "h");
		sts.securityLabelling.put(1, "LIC_PC=false;LII_PC=true;");
		Hashtable<String, String> RS = new Hashtable<String, String>();
		for (Statement statement : statements) {
			location = processStatement(statement, "", RS, location, prefix, new Hashtable<Integer, Integer>(), SL);
		}
		return location;
	}
	
	private Integer processStatement(Statement statement, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		if(initialLocation==2){
			System.out.println(statement);
		}
		Integer finalLocation = initialLocation; 
		switch(statement.getNodeType()){
		case ASTNode.EXPRESSION_STATEMENT:
			Expression expression = ((ExpressionStatement) statement).getExpression();
			if(expression.getNodeType()==ASTNode.METHOD_INVOCATION && (((MethodInvocation) expression).resolveMethodBinding().getDeclaringClass().getQualifiedName() + "." + ((MethodInvocation) expression).getName()).equals(DUMMY_METHOD)){
				MethodInvocation dummyMethodInvocation = (MethodInvocation) expression;
				finalLocation = processDummyMethodInvocation(dummyMethodInvocation, XReturn, RS, initialLocation, prefix, breakContinueLocations, SL);
			}else if((expression.getNodeType()==ASTNode.METHOD_INVOCATION || expression.getNodeType()==ASTNode.CLASS_INSTANCE_CREATION) 
					&& canProcessMethod(expression)){
				MethodInvocation methodInvocation = (MethodInvocation) expression;
				finalLocation = processMethodInvocation(methodInvocation, XReturn, RS, initialLocation, prefix, breakContinueLocations, SL);
			}else if(expression.getNodeType()==ASTNode.ASSIGNMENT){
				Assignment assignment = (Assignment)expression;
				finalLocation = processAssignment(assignment, XReturn, RS, initialLocation, prefix, breakContinueLocations, SL);
			}
			break;
		case ASTNode.IF_STATEMENT:
			IfStatement ifStatement = (IfStatement) statement;
			finalLocation = processIfStatement(ifStatement, XReturn, RS, initialLocation, prefix, breakContinueLocations, SL);
			break;
		case ASTNode.WHILE_STATEMENT:
			WhileStatement whileStatement = (WhileStatement) statement;
			finalLocation = processWhileStatement(whileStatement, XReturn, RS, initialLocation, prefix, breakContinueLocations, SL);
			break;
		case ASTNode.RETURN_STATEMENT:
			ReturnStatement returnStatement = (ReturnStatement) statement;
			finalLocation = processReturnStatement(returnStatement, XReturn, RS, initialLocation, prefix, breakContinueLocations, SL);
			break;
		case ASTNode.TRY_STATEMENT:
			TryStatement tryStatement = (TryStatement) statement;
			finalLocation = processBlock(tryStatement.getBody(), XReturn, RS, initialLocation, prefix, breakContinueLocations, SL);
			break;
		case ASTNode.BLOCK:
			Block block = (Block) statement;
			finalLocation = processBlock(block, XReturn, RS, initialLocation, prefix, breakContinueLocations, SL);
			break;
		case ASTNode.BREAK_STATEMENT:
			breakContinueLocations.put(finalLocation, 1);
			break;
		case ASTNode.CONTINUE_STATEMENT:
			breakContinueLocations.put(finalLocation, -1);
			break;
		case ASTNode.VARIABLE_DECLARATION_STATEMENT:
			VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement) statement;
			finalLocation = processVariableDeclarationFragment((VariableDeclarationFragment)variableDeclarationStatement.fragments().get(0), XReturn, RS, initialLocation, prefix, breakContinueLocations, SL);
			break;
		default:
		}	
		return finalLocation;
	}

	private Integer processDummyMethodInvocation(MethodInvocation dummyMethodInvocation, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		 @SuppressWarnings("unchecked")
		List<StringLiteral> parameters = dummyMethodInvocation.arguments();
		 String variableName = "";
		if(RS.contains(parameters.get(0).getLiteralValue())){
			variableName = RS.get(parameters.get(0).getLiteralValue());
		}else {
			variableName = prefix + "_" + parameters.get(0).getLiteralValue().replaceAll("\\.", "_");
		}
		SL.put(variableName + "," + parameters.get(1).getLiteralValue(), parameters.get(2).getLiteralValue());
		sts.securityLabelling.put(initialLocation, (sts.securityLabelling.get(initialLocation)==null ? "" : sts.securityLabelling.get(initialLocation)) 
				+ "L" + parameters.get(1).getLiteralValue() + "_" + variableName + "=" + (parameters.get(2).getLiteralValue().equals("h") ? "true" : "false") + ";");
		return initialLocation;
	}

	private Integer processVariableDeclarationFragment(VariableDeclarationFragment variableDeclarationFragment,	String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		Integer finalLocation = initialLocation;
		if((variableDeclarationFragment.getInitializer() instanceof MethodInvocation || variableDeclarationFragment.getInitializer() instanceof ClassInstanceCreation) 
				&& canProcessMethod(variableDeclarationFragment.getInitializer())){
			Expression expression = ((Expression) variableDeclarationFragment.getInitializer());
			String methodArgumentsUpdater = getMethodArgumentsUpdater(expression, RS, prefix);
			for (String part : methodArgumentsUpdater.split(";")) {
				if(!part.matches("\\s*")){
					String leftHandSide = part.substring(0, part.indexOf("=")).replaceAll(" ", "");
					SL.remove(leftHandSide + "," + "XC");
					SL.remove(leftHandSide + "," + "IC");
					SL.remove(leftHandSide + "," + "XI");
					SL.remove(leftHandSide + "," + "II");
				}
			}
			Transition transition = new Transition(Transition.TAU, "true", methodArgumentsUpdater + getSecurityAssignments(SL) 
			+ getSecurityAssignment(methodArgumentsUpdater, "XC") + getSecurityAssignment(methodArgumentsUpdater, "XI") 
			+ getSecurityAssignment(methodArgumentsUpdater.replaceAll(";", " && PC;"), "IC") + getSecurityAssignment(methodArgumentsUpdater.replaceAll(";", " && PC;"), "II"));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
			updateStsSecurityLabelling(initialLocation, finalLocation);
			transition = new Transition(getMethodFullName(expression), "true", "");
			initialLocation = finalLocation;
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
			updateStsSecurityLabelling(initialLocation, finalLocation);
			Hashtable<String, String> newRS = getRenamingRules(expression, prefix);
			finalLocation = processBlock(getMethodBody(expression), rename(variableDeclarationFragment.getName(), newRS, prefix), newRS, finalLocation, getScopedName(expression, prefix), breakContinueLocations, SL);
		}else if (variableDeclarationFragment.getInitializer()!=null){
			String rename = rename(variableDeclarationFragment.getName(), RS, prefix) + "=" + rename(variableDeclarationFragment.getInitializer(), RS, prefix) + ";";
			Transition transition = new Transition(Transition.TAU, "true", rename + 
					getSecurityAssignments(SL) + getSecurityAssignment(rename, "XC") + getSecurityAssignment(rename, "XI") + 
					getSecurityAssignment(rename.replaceAll(";", " && PC;"), "IC") + getSecurityAssignment(rename.replaceAll(";", " && PC;"), "II"));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
			updateStsSecurityLabelling(initialLocation, finalLocation);
		};
		return finalLocation;
	}
	
	private String getScopedName(Expression expression, String prefix){
		String scopedName = "";
		if(expression instanceof MethodInvocation){
			scopedName = prefix + "_" +	(((MethodInvocation)expression).getExpression()==null ? "" : ((MethodInvocation)expression).getExpression().toString()).replaceAll("\\.", "_") + "_" + ((MethodInvocation)expression).getName();
		}else if(expression instanceof ClassInstanceCreation){
			scopedName = prefix + "_" +	((ClassInstanceCreation)expression).resolveConstructorBinding().getDeclaringClass().getName();
		}
		return scopedName;
	}

	private String getMethodFullName(Expression expression) {
		String declaringClass = ""; 
		String methodFullName = "";
		if(expression instanceof MethodInvocation){
			declaringClass = ((MethodInvocation)expression).resolveMethodBinding().getDeclaringClass().getQualifiedName();
			methodFullName = declaringClass.replaceAll("\\.", "_") + "_" + ((MethodInvocation)expression).getName().toString();
		} else if(expression instanceof ClassInstanceCreation){
			declaringClass = ((ClassInstanceCreation)expression).resolveConstructorBinding().getDeclaringClass().getQualifiedName();
			methodFullName = declaringClass.replaceAll("\\.", "_") + "_" + ((ClassInstanceCreation)expression).resolveConstructorBinding().getDeclaringClass().getName();
		}
		return methodFullName;
	}

	private Integer processReturnStatement(ReturnStatement returnStatement, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		Integer finalLocation = initialLocation;
		Expression expression = returnStatement.getExpression();
		if(expression!=null){
			XReturn = XReturn.replaceAll(" ", "");
			SL.remove(XReturn + "," + "XC");
			SL.remove(XReturn + "," + "IC");
			SL.remove(XReturn + "," + "XI");
			SL.remove(XReturn + "," + "II");
			String rename = XReturn + "=" + rename(expression, RS, prefix) + ";";
			Transition transition = new Transition(Transition.TAU, "true", rename + getSecurityAssignments(SL)
			+ getSecurityAssignment(rename, "XC") + getSecurityAssignment(rename, "XI") 
			+ getSecurityAssignment(rename.replaceAll(";", " && PC;"), "IC") 
			+ getSecurityAssignment(rename.replaceAll(";", " && PC;"), "II"));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
			updateStsSecurityLabelling(initialLocation, finalLocation);
		}
		return finalLocation;
	}

	private Integer processAssignment(Assignment assignment, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		Integer finalLocation = initialLocation;
		if((assignment.getRightHandSide() instanceof MethodInvocation || assignment.getRightHandSide() instanceof ClassInstanceCreation) 
				&& canProcessMethod(assignment.getRightHandSide())){
			Expression expression= assignment.getRightHandSide();
			String methodArgumentsUpdater = getMethodArgumentsUpdater(expression, RS, prefix);
			for (String part : methodArgumentsUpdater.split(";")) {
				String leftHandSide = part.substring(0, part.indexOf("=")).replaceAll(" ", "");
				SL.remove(leftHandSide + "," + "XC");
				SL.remove(leftHandSide + "," + "IC");
				SL.remove(leftHandSide + "," + "XI");
				SL.remove(leftHandSide + "," + "II");
			}
			Transition transition = new Transition(Transition.TAU, "true", methodArgumentsUpdater + getSecurityAssignments(SL) 
			+ getSecurityAssignment(methodArgumentsUpdater, "XC") + getSecurityAssignment(methodArgumentsUpdater, "XI") 
			+ getSecurityAssignment(methodArgumentsUpdater.replaceAll(";", " && PC;"), "IC") + getSecurityAssignment(methodArgumentsUpdater.replaceAll(";", " && PC;"), "II"));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
			updateStsSecurityLabelling(initialLocation, finalLocation);
			transition = new Transition(getMethodFullName(expression), "true", "");
			initialLocation = finalLocation;
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
			updateStsSecurityLabelling(initialLocation, finalLocation);
			Hashtable<String, String> newRS = getRenamingRules(expression, prefix);
			finalLocation = processBlock(getMethodBody(expression), rename(assignment.getLeftHandSide(), newRS, prefix), newRS, finalLocation, getScopedName(expression, prefix), breakContinueLocations, SL);
		}else{
			String rename = rename(assignment, RS, prefix) + ";";
			String leftHandSide = rename.substring(0, rename.indexOf("=")).replaceAll(" ", "");
			SL.remove(leftHandSide + "," + "XC");
			SL.remove(leftHandSide + "," + "IC");
			SL.remove(leftHandSide + "," + "XI");
			SL.remove(leftHandSide + "," + "II");
			Transition transition = new Transition(Transition.TAU, "true", rename + getSecurityAssignments(SL) 
			+ getSecurityAssignment(rename, "XC") + getSecurityAssignment(rename, "XI") +  getSecurityAssignment(rename.replaceAll(";", " && PC;"), "IC") 
			+ getSecurityAssignment(rename.replaceAll(";", " && PC;"), "II"));
//			sts.variables.add("bool,LIC_" + leftHandSide);
//			sts.variables.add("bool,LII_" + leftHandSide);
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
			updateStsSecurityLabelling(initialLocation, finalLocation);
		}
		return finalLocation;
	}

	private String getSecurityAssignment(String assignments, String policyType) {
		String[] assignmentsParts = assignments.split(";");
		String securityAssignment = "";
		for (String assignment : assignmentsParts) {
			if(!assignment.matches("\\s*")){
				String leftHandSide = assignment.substring(0, assignment.indexOf("=")).replaceAll(" ", "");
				String rightHandSide = assignment.substring(assignment.indexOf("=")+1, assignment.length());
				String op = "";
				String c = "";
				if(policyType.equals("XC") || policyType.equals("IC")){
					op = " or ";
					c = "false";
				}else{
					op = " and ";
					c = "true";
				}
//				sts.variables.add("bool,L" + policyType	+ "_" + leftHandSide);
				securityAssignment += "L" + policyType	+ "_" + leftHandSide + "=" ;
				String[] parts = rightHandSide.replaceAll(" ", "").split("[^\\w\"]+");
				String opt = "";
				for (String part : parts) {
					part = part.replaceAll(" ", "");
					if(!part.equals("")){
						if(part.matches("\".*\"") || part.equals("true") || part.equals("false") || part.matches("[-+]?\\d*\\.?\\d+")){
							securityAssignment += opt + c;
						}else {
							securityAssignment += opt + "L" + policyType + "_" + part;
						}
						opt = op;
					}
				}
				securityAssignment += ";";
			}
		}
		return securityAssignment;
	}

	private boolean canProcessMethod(Expression expression) {
		boolean res = false;
		String declaringClass = "";
		if(expression instanceof MethodInvocation){
			declaringClass = ((MethodInvocation)expression).resolveMethodBinding().getDeclaringClass().getQualifiedName();
		}else if(expression instanceof ClassInstanceCreation){
			declaringClass = ((ClassInstanceCreation)expression).resolveConstructorBinding().getDeclaringClass().getQualifiedName();
		}
		String methodFullName = declaringClass + "." + expression.toString().replaceAll("new\\s+", "");
		if(canProcess(methodFullName)){
			res = true;
		}
		return res;
	}

	private Integer processMethodInvocation(Expression expression, String XReturn,	Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		String methodArgumentsUpdater = getMethodArgumentsUpdater(expression, RS, prefix);
		for (String part : methodArgumentsUpdater.split(";")) {
			String leftHandSide = part.substring(0, part.indexOf("=")).replaceAll(" ", "");
			SL.remove(leftHandSide + "," + "XC");
			SL.remove(leftHandSide + "," + "IC");
			SL.remove(leftHandSide + "," + "XI");
			SL.remove(leftHandSide + "," + "II");
		}
		Transition transition = new Transition(Transition.TAU, "true", methodArgumentsUpdater + getSecurityAssignments(SL) 
		+ getSecurityAssignment(methodArgumentsUpdater, "XC") + getSecurityAssignment(methodArgumentsUpdater, "XI") 
		+ getSecurityAssignment(methodArgumentsUpdater.replaceAll(";", " && PC;"), "IC") + getSecurityAssignment(methodArgumentsUpdater.replaceAll(";", " && PC;"), "II"));
		Integer finalLocation = newLocation();
		sts.addVertex(initialLocation);
		sts.addVertex(finalLocation);
		sts.addEdge(initialLocation, finalLocation, transition);
		updateStsSecurityLabelling(initialLocation, finalLocation);
		transition = new Transition(getMethodFullName(expression), "true", "");
		initialLocation = finalLocation;
		finalLocation = newLocation();
		sts.addVertex(initialLocation);
		sts.addVertex(finalLocation);
		sts.addEdge(initialLocation, finalLocation, transition);
		updateStsSecurityLabelling(initialLocation, finalLocation);
		Hashtable<String, String> newRS = getRenamingRules(expression, prefix);
		finalLocation = processBlock(getMethodBody(expression), XReturn, newRS, finalLocation, getScopedName(expression, prefix), breakContinueLocations, SL);
		return finalLocation;
	}

	private void updateStsSecurityLabelling(Integer initialLocation, Integer finalLocation) {
		if(sts.securityLabelling.get(initialLocation)!=null){
			String[] securityLabellingParts = sts.securityLabelling.get(initialLocation).split(";");
			for (String securityLabellingPart : securityLabellingParts) {
				if(sts.securityLabelling.get(finalLocation)!=null){ 
					if (!sts.securityLabelling.get(finalLocation).contains(securityLabellingPart.split("=")[0])){
						sts.securityLabelling.put(finalLocation, sts.securityLabelling.get(finalLocation) + securityLabellingPart + ";");
					}
				}else{
					sts.securityLabelling.put(finalLocation, securityLabellingPart + ";");
				}
			}
		}
	}

	@SuppressWarnings("rawtypes")
	private String getMethodArgumentsUpdater(Expression expression, Hashtable<String, String> RS, String prefix) {
		ArrayList<String> methodParameters = getMethodParameters(expression, getScopedName(expression, prefix));
		List arguments = null;
		if(expression instanceof MethodInvocation){
			arguments = ((MethodInvocation) expression).arguments();
		}else if(expression instanceof ClassInstanceCreation){
			arguments = ((ClassInstanceCreation) expression).arguments();
		}
		Hashtable<String, String> newRS = getRenamingRules(expression, prefix);
		String res = "";
		for (int i=0; i< methodParameters.size(); i++) {
			String methodParameter = methodParameters.get(i);
			String fullParameterName = getScopedName(expression, prefix) + "_" + methodParameter;
//			if(!newRS.containsKey(methodParameter)){
			res += fullParameterName  + "=" + rename((Expression) arguments.get(i), RS, prefix) + ";";
//			}
		}
		return res;
	}

	@SuppressWarnings("unchecked")
	private ArrayList<String> getMethodParameters(Expression expression, String prefix) {
		String declaringClass = "";
		if(expression instanceof MethodInvocation){
			declaringClass = ((MethodInvocation) expression).resolveMethodBinding().getDeclaringClass().getQualifiedName();
		}else if(expression instanceof ClassInstanceCreation){
			declaringClass = ((ClassInstanceCreation) expression).resolveConstructorBinding().getDeclaringClass().getQualifiedName();
		}
		TypeDeclaration cls = classes.get(declaringClass);
		ArrayList<String> res = new ArrayList<>();
		for (MethodDeclaration methodDeclaration : cls.getMethods()) {
			String expressionResolveBinding = "";
			if(expression instanceof MethodInvocation){
				expressionResolveBinding  = ((MethodInvocation) expression).resolveMethodBinding().toString();
			}else if(expression instanceof ClassInstanceCreation){
				expressionResolveBinding = ((ClassInstanceCreation) expression).resolveConstructorBinding().toString();
			}
			if(methodDeclaration.resolveBinding().toString().equals(expressionResolveBinding)){
				List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
				for (SingleVariableDeclaration singleVariableDeclaration : parameters) {
					res.add(singleVariableDeclaration.getName().getFullyQualifiedName());
					addVariable(singleVariableDeclaration.getName(), prefix + "_" + singleVariableDeclaration.getName().toString());
				}
				break;
			}
		}
		return res;
	}

	private Block getMethodBody(Expression expression) {
		String declaringClass = "";
		if(expression instanceof MethodInvocation){
			declaringClass = ((MethodInvocation) expression).resolveMethodBinding().getDeclaringClass().getQualifiedName();
		}else if(expression instanceof ClassInstanceCreation){
			declaringClass = ((ClassInstanceCreation) expression).resolveConstructorBinding().getDeclaringClass().getQualifiedName();
		}
		TypeDeclaration cls = classes.get(declaringClass);
		Block res = expression.getAST().newBlock();
		for (MethodDeclaration methodDeclaration : cls.getMethods()) {
			String expressionResolveBinding = "";
			if(expression instanceof MethodInvocation){
				expressionResolveBinding  = ((MethodInvocation) expression).resolveMethodBinding().toString();
			}else if(expression instanceof ClassInstanceCreation){
				expressionResolveBinding = ((ClassInstanceCreation) expression).resolveConstructorBinding().toString();
			}
			if(methodDeclaration.resolveBinding().toString().equals(expressionResolveBinding)){
				res = methodDeclaration.getBody();
				break;
			}
		}
		return res;
	}

	@SuppressWarnings("rawtypes")
	private Hashtable<String, String> getRenamingRules(Expression expression, String prefix) {
		ArrayList<String> methodParameters = getMethodParameters(expression, getScopedName(expression, prefix));
		List arguments = null;
		if(expression instanceof MethodInvocation){
			arguments = ((MethodInvocation) expression).arguments();
		}else if(expression instanceof ClassInstanceCreation){
			arguments = ((ClassInstanceCreation) expression).arguments();
		}
		Hashtable<String, String> res = new Hashtable<>();
		for (int i=0; i< arguments.size(); i++) {
			Object methodArgument = arguments.get(i);
			if(methodArgument instanceof SimpleName){
				ITypeBinding iTypeBinding = ((SimpleName) methodArgument).resolveTypeBinding();
				if(!iTypeBinding.isPrimitive() 
						&& !iTypeBinding.getQualifiedName().equals("java.lang.Integer")
						&& !iTypeBinding.getQualifiedName().equals("java.lang.Long")
						&& !iTypeBinding.getQualifiedName().equals("java.lang.Byte")
						&& !iTypeBinding.getQualifiedName().equals("java.lang.Short")
						&& !iTypeBinding.getQualifiedName().equals("java.lang.Float")
						&& !iTypeBinding.getQualifiedName().equals("java.lang.Double")
						&& !iTypeBinding.getQualifiedName().equals("java.lang.BigDecimal")
						&& !iTypeBinding.getQualifiedName().equals("java.lang.BigInteger")
						&& !iTypeBinding.getQualifiedName().equals("java.lang.Boolean")){
					res.put(methodParameters.get(i), prefix + "_" + arguments.get(i).toString());
				}
			}
		}
		return res;
	}

	@SuppressWarnings("unchecked")
	private Integer processBlock(Block block, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		List<Statement> statements = block.statements();
		Integer finalLocation = initialLocation;
		for (Statement statement : statements) {
			finalLocation = processStatement(statement, XReturn, RS, finalLocation, prefix, breakContinueLocations, SL);
		}
		return finalLocation;
	}

	private Integer processWhileStatement(WhileStatement whileStatement, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		Integer blockEnterLocation = -initialLocation;
		String whileExpression = rename(whileStatement.getExpression(), RS, prefix);
		SL.remove("PC,IC");
		SL.remove("PC,II");
//		Hashtable <String, String> SLNotWhileBody = (Hashtable<String, String>) SL.clone();
//		ArrayList<Expression> modifiedInWhileBody = getPossibleModifiedVariables(whileStatement.getBody());
//		String implicitInderentWhileBody = "";
//		for (Expression expression : modifiedInWhileBody) {
//			implicitInderentWhileBody += rename(expression, RS, prefix) + "=" + rename(expression, RS, prefix) + "&&PC;";
//			SLNotWhileBody.remove(rename(expression, RS, prefix) + ",IC");
//			SLNotWhileBody.remove(rename(expression, RS, prefix) + ",II");
//		}
		pcLevel++;
		sts.variables.add("bool,LIC_PC"+pcLevel);
		sts.variables.add("bool,LII_PC"+pcLevel);
		Transition entranceTransition = new Transition(Transition.TAU, whileExpression, getSecurityAssignments(SL)
				+ getSecurityAssignment("PC=" + whileExpression + "&&PC", "IC") + getSecurityAssignment("PC=" + whileExpression + "&&PC", "II")
				+ getSecurityAssignment("PC" + pcLevel + "=PC", "IC") + getSecurityAssignment("PC" + pcLevel + "=PC", "II"));
//		Transition preExitTransition = new Transition(Transition.TAU, "  not (" + whileExpression + ")", "");
		Transition exitTransition = new Transition(Transition.TAU, "  not (" + whileExpression + ")", getSecurityAssignments(SL)
				+ getSecurityAssignment("PC=" + whileExpression + "&&PC", "IC") + getSecurityAssignment("PC=" + whileExpression + "&&PC", "II")
				/*+ getSecurityAssignment(implicitInderentWhileBody, "IC") + getSecurityAssignment(implicitInderentWhileBody, "II")*/);
		Integer entranceLocation = newLocation();
		Integer finalLocation = newLocation();
//		Integer preFinalLocation = newLocation();
		sts.addVertex(initialLocation);
		sts.addVertex(finalLocation);
		sts.addVertex(entranceLocation);
//		sts.addVertex(preFinalLocation);
		sts.addEdge(initialLocation, entranceLocation, entranceTransition);
		updateStsSecurityLabelling(initialLocation, entranceLocation);
//		sts.addEdge(initialLocation, preFinalLocation, preExitTransition);
//		updateStsSecurityLabelling(initialLocation, preFinalLocation);
		sts.addEdge(initialLocation, finalLocation, exitTransition);
		updateStsSecurityLabelling(initialLocation, finalLocation);
		Integer tempLocation = processStatement(whileStatement.getBody(), XReturn, RS, entranceLocation, prefix, breakContinueLocations, SL);
		pcLevel--;
		String slpc = "";
		if(pcLevel==0){
			slpc = "LIC_PC=false;LII_PC=true;";
		}else{
			slpc = getSecurityAssignment("PC" + "=PC" + pcLevel, "IC") + getSecurityAssignment("PC" + "=PC" + pcLevel, "II");
		}
		Transition tempTransition =  new Transition(Transition.TAU, "true", getSecurityAssignments(SL) + slpc);
		sts.addVertex(tempLocation);
		sts.addEdge(tempLocation, initialLocation, tempTransition);
		updateStsSecurityLabelling(tempLocation, initialLocation);
		for (Integer location : breakContinueLocations.keySet()) {
			sts.addVertex(location);
			Transition tempBCTransition =  new Transition(Transition.TAU, "true", getSecurityAssignments(SL));
			if(breakContinueLocations.get(location)==1){
				sts.addEdge(location, finalLocation, tempBCTransition);
				updateStsSecurityLabelling(location, finalLocation);
			}else{
				sts.addEdge(location, initialLocation, tempBCTransition);
				updateStsSecurityLabelling(location, initialLocation);
			}
		}
		blocks.put(blockEnterLocation, finalLocation);
		return finalLocation;
	}

	@SuppressWarnings("unchecked")
	private Integer processIfStatement(IfStatement ifStatement, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		Integer blockEnterLocation = initialLocation;
		String ifExpression = rename(ifStatement.getExpression(), RS, prefix);
		SL.remove("PC,IC");
		SL.remove("PC,II");
//		Hashtable <String, String> SLThen = (Hashtable<String, String>) SL.clone();
//		Hashtable <String, String> SLElse = (Hashtable<String, String>) SL.clone();
//		ArrayList<Expression> modifiedInElsePart = getPossibleModifiedVariables(ifStatement.getElseStatement());
//		String implicitInderentElsePart = "";
//		for (Expression expression : modifiedInElsePart) {
//			implicitInderentElsePart += rename(expression, RS, prefix) + "=" + rename(expression, RS, prefix) + "&&PC;";
//			SLThen.remove(rename(expression, RS, prefix) + ",IC");
//			SLThen.remove(rename(expression, RS, prefix) + ",II");
//		}
//		ArrayList<Expression> modifiedInThenPart = getPossibleModifiedVariables(ifStatement.getElseStatement());
//		String implicitInderentThenPart = "";
//		for (Expression expression : modifiedInThenPart) {
//			implicitInderentThenPart += rename(expression, RS, prefix) + "=" + rename(expression, RS, prefix) + "&&PC;";
//			SLElse.remove(rename(expression, RS, prefix) + ",IC");
//			SLElse.remove(rename(expression, RS, prefix) + ",II");
//		}
		pcLevel++;
		sts.variables.add("bool,LIC_PC"+pcLevel);
		sts.variables.add("bool,LII_PC"+pcLevel);
		Transition thenTransition = new Transition(Transition.TAU, ifExpression, getSecurityAssignments(SL)
				+ getSecurityAssignment("PC=" + ifExpression + "&&PC", "IC") + getSecurityAssignment("PC=" + ifExpression + "&&PC", "II")
				+ getSecurityAssignment("PC" + pcLevel + "=PC", "IC") + getSecurityAssignment("PC" + pcLevel + "=PC", "II")
				/*+ getSecurityAssignment(implicitInderentElsePart, "IC") + getSecurityAssignment(implicitInderentElsePart, "II")*/);
		Transition elseTransition = new Transition(Transition.TAU, "  not (" + ifExpression + ")", getSecurityAssignments(SL) 
				+ getSecurityAssignment("PC=" + ifExpression + "&&PC", "IC") + getSecurityAssignment("PC=" + ifExpression + "&&PC", "II")
				+ getSecurityAssignment("PC" + pcLevel + "=PC", "IC") + getSecurityAssignment("PC" + pcLevel + "=PC", "II")
				/*+ getSecurityAssignment(implicitInderentThenPart, "IC") + getSecurityAssignment(implicitInderentThenPart, "II")*/);
		Integer thenLocation = newLocation();
		Integer elseLocation = newLocation();
		sts.addVertex(initialLocation);
		sts.addVertex(thenLocation);
		sts.addVertex(elseLocation);
		sts.addEdge(initialLocation, thenLocation, thenTransition);
		updateStsSecurityLabelling(initialLocation, thenLocation);
		sts.addEdge(initialLocation, elseLocation, elseTransition);
		updateStsSecurityLabelling(initialLocation, elseLocation);
		Integer finalThenLocation = processStatement(ifStatement.getThenStatement(), XReturn, RS, thenLocation, prefix, breakContinueLocations, SL);
		Integer finalLocation = finalThenLocation;
		Integer finalElseLocation = elseLocation;
		if(ifStatement.getElseStatement()!=null){
			finalElseLocation = processStatement(ifStatement.getElseStatement(), XReturn, RS, elseLocation, prefix, breakContinueLocations, SL);
			
		}
		pcLevel--;
		String slpc = "";
		if(pcLevel==0){
			slpc = "LIC_PC=false;LII_PC=true;";
		}else{
			slpc = getSecurityAssignment("PC" + "=PC" + pcLevel, "IC") + getSecurityAssignment("PC" + "=PC" + pcLevel, "II");
		}
		Transition transition1 = new Transition(Transition.TAU, "true", getSecurityAssignments(SL) + slpc);
//		Transition transition2 = new Transition(Transition.TAU, "true", "");
		Transition transition2 = new Transition(Transition.TAU, "true", getSecurityAssignments(SL) + slpc);
//		Integer preFinalElseLocation = newLocation();
		finalLocation = newLocation();
		sts.addVertex(finalLocation);
		sts.addVertex(finalThenLocation);
//		sts.addVertex(preFinalElseLocation);
		sts.addVertex(finalElseLocation);
		sts.addEdge(finalThenLocation, finalLocation, transition1);
		updateStsSecurityLabelling(finalThenLocation, finalLocation);
		sts.addEdge(finalElseLocation, finalLocation, transition2);
		updateStsSecurityLabelling(finalElseLocation, finalLocation);
//		sts.addEdge(preFinalElseLocation, finalLocation, transition3);
//		updateStsSecurityLabelling(preFinalElseLocation, finalLocation);
		blocks.put(blockEnterLocation, finalLocation);
		return finalLocation;
	}
	
	static Hashtable<String, Expression> modified = new Hashtable<String, Expression>();
	private ArrayList<Expression> getPossibleModifiedVariables(Statement statement) {
		ArrayList<Expression> res = new ArrayList<>();
		if(statement!=null){
			modified.clear();
			statement.accept(new ASTVisitor() {
				@Override
				public boolean visit(Assignment assignment) {
					modified.put(assignment.getLeftHandSide().toString().replaceAll("\\s", ""), assignment.getLeftHandSide());
					if(assignment.getRightHandSide() instanceof MethodInvocation || assignment.getRightHandSide() instanceof ClassInstanceCreation){
						getPossibleModifiedVariables(getMethodBody(assignment.getRightHandSide()));
					}
					return false;
				}
				@Override
				public boolean visit(VariableDeclarationStatement variableDeclarationStatement) {
					for (Object obj : variableDeclarationStatement.fragments()) {
						VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment) obj;
						modified.put(variableDeclarationFragment.getName().toString().replaceAll("\\s", ""), variableDeclarationFragment.getName());
						if(variableDeclarationFragment.getInitializer() instanceof MethodInvocation 
								|| variableDeclarationFragment.getInitializer() instanceof ClassInstanceCreation){
							getPossibleModifiedVariables(getMethodBody(variableDeclarationFragment.getInitializer()));
						}
					}
					return false;
				}
			});
			res.addAll(modified.values());
		}
		return res;
	}

	private String getSecurityAssignments(Hashtable<String, String> SL){
		String res = "";
		for (String key : SL.keySet()) {
			String[] parts = key.split(",");
//			sts.variables.add("bool,L" + parts[1] + "_" + parts[0]);
			res += "L" + parts[1] + "_" + parts[0] + "=" + (SL.get(key).toLowerCase().equals("h") ? "true" : "false") + ";";
		}
//		if(!res.equals("") && withComa){
//			res = ";" + res;
//		}
		return res;
	}
	
	static String renamed = "";
	static String temp = "";
	private String rename(Expression expression, final Hashtable<String, String> RS, final String prefix){
		renamed = " " + expression.toString() + " ";
		renamed = renamed.replaceAll("\\s+\\(", "(");
//		if(expression.toString().replaceAll(" ", "").contains("(user.location.x-x)*(user.location.x-x)")){
//			System.out.println(expression);
//		}
		expression.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName simpleName) {
				if(simpleName.resolveBinding()!=null && simpleName.resolveBinding() instanceof IVariableBinding){
					IVariableBinding resolveBinding = (IVariableBinding)simpleName.resolveBinding();
					if(resolveBinding.isParameter() && RS.contains(simpleName.getIdentifier())){
						renamed = replace(renamed, simpleName.getIdentifier(), RS.get(simpleName.getIdentifier()));
						addVariable(simpleName, RS.get(simpleName.getIdentifier()));
					}else if(simpleName.getParent().getNodeType()!=ASTNode.QUALIFIED_NAME) {
						renamed = replace(renamed, simpleName.getIdentifier(), prefix + "_" + simpleName.getIdentifier());
						addVariable(simpleName, prefix + "_" + simpleName.getIdentifier());
					}else if(simpleName.getParent().toString().startsWith(simpleName.toString())){
//						renamed = replace(renamed, simpleName.getIdentifier(), prefix + "_" + simpleName.getIdentifier());
						temp = simpleName.getIdentifier();
					}else if(simpleName.getParent().getParent().getNodeType()!=ASTNode.QUALIFIED_NAME){
						renamed = replace(renamed, temp + "." + simpleName.getIdentifier(), prefix + "_" + temp.replaceAll("\\.", "_") + "_" + simpleName.getIdentifier());
						addVariable(simpleName, prefix + "_" + temp.replaceAll("\\.", "_") + "_" + simpleName.getIdentifier());
					} else{
//						renamed = replace(renamed, "." + simpleName.getIdentifier(), "_" + simpleName.getIdentifier());
						temp += "." + simpleName.getIdentifier();
					}
				}
				return false;
			}
		});
		renamed = renamed.replaceAll("\\.", "_");
		return renamed;
	}
	
	public void addVariable(SimpleName simpleName, String renamed) {
		if(renamed.contains("resx")){
			System.out.println(renamed);
		}
		IVariableBinding resolveBinding = (IVariableBinding)simpleName.resolveBinding();
		ITypeBinding iTypeBinding = resolveBinding.getVariableDeclaration().getType();
		if(iTypeBinding.getQualifiedName().equals("boolean")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Boolean")){
			sts.variables.add("bool,"+renamed);
		} else if(iTypeBinding.getQualifiedName().equals("int")
				|| iTypeBinding.getQualifiedName().equals("long")
				|| iTypeBinding.getQualifiedName().equals("byte")
				|| iTypeBinding.getQualifiedName().equals("short")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Integer")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Long")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Byte")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Short")
				|| iTypeBinding.getQualifiedName().equals("java.lang.BigInteger")){
			sts.variables.add("int,"+renamed);
		} else if(iTypeBinding.getQualifiedName().equals("float")
				|| iTypeBinding.getQualifiedName().equals("double")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Float")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Double")
				|| iTypeBinding.getQualifiedName().equals("java.lang.BigDecimal")){
			sts.variables.add("real,"+renamed);
		} else{
			sts.variables.add("undef,"+renamed);
		}
		sts.variables.add("bool,LIC_" + renamed);
		sts.variables.add("bool,LII_" + renamed);
		sts.variables.add("bool,LXC_" + renamed);
		sts.variables.add("bool,LXI_" + renamed);
	}
	
	private static String replace(String string, String find, String replace) {
		String[] res = string.split(find.replaceAll("\\.", "\\\\."));
		try{
			string = "";
			for (int i=0; i<res.length-1; i++) {
				if(res[i].charAt(res[i].length()-1)!='.' &&
						!Character.isLetter(res[i].charAt(res[i].length()-1)) && !Character.isLetter(res[i+1].charAt(0))
						&& res[i].charAt(res[i].length()-1)!='_' && res[i+1].charAt(0)!='_' 
						&& res[i+1].charAt(0)!='('){
					string += res[i] + replace;
				}else{
					string += res[i] + find;
				}
			}
			string += res[res.length-1];
		}catch(Exception e){
			System.out.println(string);
		}
		return string;
	}

	static CompilationUnit getCompilationUnit(String[] sourceDir, String[] classPath, String javaFilePath) {
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setResolveBindings(true);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setBindingsRecovery(true);
		@SuppressWarnings("rawtypes")
		Map options = JavaCore.getOptions();
		parser.setCompilerOptions(options);
		parser.setSource(Utils.readTextFile(javaFilePath));
		parser.setUnitName(new File(javaFilePath).getName());
		parser.setEnvironment(classPath, sourceDir, new String[] { "UTF-8"}, true);
		CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
		return compilationUnit;
	}
	
	Integer newLocation(){
		return ++newLocation;
	}
	
	private boolean canProcess(String methodFullName) {
		boolean res = false;
		for (String filter : includingFilter) {
			if(methodFullName.matches(filter)){
				res = true;
				break;
			}
		}
		for (String filter : excludingFilter) {
			if(methodFullName.matches(filter)){
				res = false;
				break;
			}
		}
		return res;
	}

	static ArrayList<String> getAllJavaFilePaths(String directoryPath) {
		ArrayList<String> allJavaFilePaths = new ArrayList<String>();
		getAllJavaFilePaths(directoryPath, allJavaFilePaths);
		return allJavaFilePaths;
	}
	
	private static void getAllJavaFilePaths(String directoryPath, ArrayList<String> allJavaFilePaths) {
		File directory = new File(directoryPath);
		File[] directoryContent = directory.listFiles();
		for (File file : directoryContent) {
			if(file.isFile()&&file.getName().toLowerCase().endsWith(".java")){
				allJavaFilePaths.add(file.getAbsolutePath());
			}else if(file.isDirectory()){
				getAllJavaFilePaths(file.getAbsolutePath(), allJavaFilePaths);
			}
		}
	}
}