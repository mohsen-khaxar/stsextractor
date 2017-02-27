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
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
//import org.project.automaton.Automaton;

public class JavaProjectSTSExtractor {
	JavaProjectHelper javaProjectHelper;
	Stack<String> returnExpression;
	Stack<Hashtable<SimpleName, String>> renamingRuleSets;
	int newLocation = 2;
	int pcLevel = 0;
	int maxPcLevel = 0;
	Hashtable<Integer, Integer> blocks;
	public ArrayList<String> includingFilter;
	public ArrayList<String> excludingFilter;
	public ArrayList<String> entryPoints;
	public STS sts;
	public int scopeId = 0;
	private Hashtable<String, TypeDeclaration> classes = new Hashtable<>();
	final static String DUMMY_METHOD = "se.lnu.SL.def";
	public JavaProjectSTSExtractor(ArrayList<String> includingFilter, ArrayList<String> excludingFilter, ArrayList<String> entryPoints, Set<String> controllableMethodNames) {
		blocks = new Hashtable<>();
		this.includingFilter = includingFilter;
		this.excludingFilter = excludingFilter;
		this.entryPoints = entryPoints;
		this.sts = new STS(controllableMethodNames);
		Transition transition =  new Transition(Transition.START, "true", "");
		sts.addVertex(0);
		sts.addVertex(1);
		sts.addEdge(0, 1, transition);
		sts.addVertex(2);
		transition =  new Transition(Transition.TAU, "true", "");
		sts.addEdge(2, 2, transition);
		sts.variables.add("bool,LIC_PC");
		sts.variables.add("bool,LII_PC");
	}
	
//	public Automaton convertToAutomaton(){
//		return sts.convertToAutomaton();
//	}
	
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
				String[] orParts = part.substring(part.indexOf("=")+1, part.length()).split("\\sor\\s");
				for (String orPart : orParts) {
					String[] andParts = orPart.split("\\sand\\s");
					String[] locations = null;
					for (String andPart : andParts) {
						if(andPart.contains(" LOC ")){
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
				if(res.controllableEvents.contains(transition.getAction()) && !transition.getAction().equals(Transition.START)
						&& !transition.getAction().equals(Transition.RETURN) && !transition.getAction().equals(Transition.PARAMETER)){
					String guard = "";
					String separator = "";
					if(securityGuards.get(transition.getAction().replaceAll("\\s", "")+",-1")!=null){
						guard = securityGuards.get(transition.getAction().replaceAll("\\s", "")+",-1");
						separator = " or ";
					}
					if(securityGuards.get(transition.getAction().replaceAll("\\s", "")+","+res.getEdgeSource(transition))!=null){
						guard += separator + securityGuards.get(transition.getAction().replaceAll("\\s", "")+","+res.getEdgeSource(transition));
					}
					if(guard.equals("")){
						guard = "false";
					}
					if(!guard.matches("(true|\\sand\\s|\\sor\\s|\\s)*")){
						transition.setGuard(guard);
						Transition insecureTransition = new Transition(transition.getAction(), " not (" + guard.replaceAll("\\s+", " ") + ")", transition.update);
						if(!res.vertexSet().contains(-res.getEdgeTarget(transition))){
							res.addVertex(-res.getEdgeTarget(transition));
						}
						res.addEdge(res.getEdgeSource(transition), -res.getEdgeTarget(transition), insecureTransition);
						Transition returnTransition = new Transition(Transition.RETURN, "true", "");
						res.addEdge(-res.getEdgeTarget(transition), res.getEdgeTarget(transition), returnTransition);
						
						((Transition)(res.incomingEdgesOf(res.getEdgeSource(transition)).toArray()[0])).setAction(Transition.PARAMETER);
					}
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
		JavaProjectSTSExtractor se = new JavaProjectSTSExtractor(null, null, null, null);
		
	}
	
	public void generateAspect(String sourcePath, String targetPath) throws IOException{
		sts.generateAspect(sourcePath, targetPath);
	}
	
	public void extract(String sourcePath, String[] classPath) throws Exception{
		javaProjectHelper = new JavaProjectHelper(sourcePath, classPath);
		javaProjectHelper.load();
		for (JavaFileHelper javaFileHelper : javaProjectHelper.getAllJavaFileHelpers()) {
			JavaClassSTSExtractor javaClassSTSExtractor = new JavaClassSTSExtractor(javaFileHelper, this);
			javaClassSTSExtractor.extract();
			extractForAClass(javaFileHelper);
		}
		addImplicitIndirectSecurityAssignments();
		javaProjectHelper.recoverOriginalJavaFiles();
		System.out.println("DONE.");
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
								transition.setUpdate(getImplicitIndirectSecurityAssignment(possibleModifieds.get(transition2)) + transition.getUpdate());
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
						sts.getEdge(location, blocks.get(-location)).setUpdate(getImplicitIndirectSecurityAssignment(possibleModifieds.get(transition)) 
								+ sts.getEdge(location, blocks.get(-location)).getUpdate());
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
					String updater = transition.getUpdate();
					if(updater.matches("LIC_"+variable + "\\s*=\\s*false")
							|| updater.matches("LII_"+variable + "\\s*=\\s*true")){
						transition.setUpdate(updater.replaceAll("(LIC_"+variable + "\\s*=\\s*false\\s*;)|"+"(LII_"+variable + "\\s*=\\s*true\\s*;)", ""));
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
		executionPathes.put(sts.getEdgeTarget(transition), new String[]{transition.getGuard(), transition.getUpdate()});
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
					temp[1] = executionPathes.get(location)[1] + outgoingTransition.getUpdate();
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
				res += outgoingTransition.getUpdate();
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

	private void extractForAClass(TypeDeclaration clazz) throws Exception {
		for (MethodDeclaration methodDeclaration : clazz.getMethods()) {
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
				sts.addEdge(finalLocation, 2, transition);
			}
		}
	}
	
	

    public String getUniqueName(SimpleName simpleName){
    	return "";
    }
    
    public int enterScope(){
    	int oldScopeId = this.scopeId;
    	this.scopeId++;
    	return oldScopeId;
    }
	
    public void exitScope(int oldScopeId){
    	this.scopeId = oldScopeId;
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
	
	public String rename(Expression expression){
		String renamed = expression.toString();
		renamed = " " + renamed.replaceAll("\\.\\s*", ".").replaceFirst("[\\w_\\.\\$]+\\s*\\(", "(") + " ";
		Hashtable<SimpleName, String> renamingRuleSet = renamingRuleSets.peek();
		String regex = "[^\\.\\w_\\$][\\w_\\$]+";
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(renamed);
        StringBuffer stringBuffer = new StringBuffer();
        while (matcher.find()) {
        	String find = matcher.group();
        	SimpleName simpleName = getSimpleNameByName(expression, find.substring(1));
        	String replacement = find.substring(0, 0);
        	if(renamingRuleSet.containsKey(simpleName)){
        		replacement += renamingRuleSet.get(simpleName);
        	}else{
        		replacement += getUniqueName(simpleName);
        	}
			matcher.appendReplacement(stringBuffer, replacement);
        }
        matcher.appendTail(stringBuffer);
        renamed = stringBuffer.toString();
        
        regex = "[^\\.\\w_\\$][\\.\\w_\\$]+";
		pattern = Pattern.compile(regex);
        matcher = pattern.matcher(renamed);
        stringBuffer = new StringBuffer();
        while (matcher.find()) {
        	String find = matcher.group();
        	String replacement = find.replaceAll("\\.", "_");
			matcher.appendReplacement(stringBuffer, replacement);
        }
        matcher.appendTail(stringBuffer);
        renamed = stringBuffer.toString();
		return renamed;
	}
	
	static SimpleName simpleName = null;
	public SimpleName getSimpleNameByName(Expression expression, String name){
		expression.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
//				TODO must be sure that it is the root.
				if(node.getIdentifier().equals(name)){
					simpleName = node;
				}
				return false;
			}
		});
		return simpleName;
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

	public Integer newLocation(){
		return ++newLocation;
	}
	
	public boolean canProcess(String methodFullName) {
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

	public boolean isEntryPoint(String qualifiedMethodName) {
		boolean isIt = false;
		for (String filter : entryPoints) {
			if (qualifiedMethodName.matches(filter)) {
				isIt = true;
				break;
			}
		}
		return isIt;
	}

	public String getLPCUniqueName() {
		return "LPC" + scopeId;
	}

}