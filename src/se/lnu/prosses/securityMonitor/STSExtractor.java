package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
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
import org.project.automaton.Variable;

public class STSExtractor {
	int newLocation = 1;
	public ArrayList<String> includingFilter;
	public ArrayList<String> excludingFilter;
	public ArrayList<String> entryPoints;
	private STS sts;
	private Hashtable<String, TypeDeclaration> classes = new Hashtable<>();
	final static String DUMMY_METHOD = "se.lnu.SL.def";
	public STSExtractor(ArrayList<String> includingFilter, ArrayList<String> excludingFilter, ArrayList<String> entryPoints) {
		this.includingFilter = includingFilter;
		this.excludingFilter = excludingFilter;
		this.entryPoints = entryPoints;
		this.sts = new STS();
		Transition transition =  new Transition(Transition.START, "true", "");
		sts.addVertex(0);
		sts.addVertex(1);
		sts.addEdge(0, 1, transition);
	}
	
	public Automaton convertToAutomaton(){
		return sts.convertToAutomaton();
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
		removeTemporaryFiles(directoryPath);
		System.out.println("DONE.");
		sts.saveAsDot(directoryPath + File.separator + "model.dot");
		Automaton automaton = sts.convertToAutomaton();
		FileWriter fileWriter = new FileWriter(directoryPath + File.separator + "vars.txt");
		List<Variable> vars = automaton.getVariables();
		for (Variable variable : vars) {
			fileWriter.write(variable.getName() + "\n");
		}
		fileWriter.close();
		STS uncontrollableFreeSTS = sts.convertToUncontrollableFreeSTS();
		uncontrollableFreeSTS.saveAsDot(directoryPath + File.separator + "freemodel.dot");
//		uncontrollableFreeSTS.generateAspect(directoryPath, "P:\\aspects");
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
		Integer location = 1;
 		String prefix = methodDeclaration.resolveBinding().getDeclaringClass().getQualifiedName().replaceAll("\\.", "_") + "_" + methodDeclaration.getName();
		Hashtable<String, String> SL = new Hashtable<String, String>();
		for (Statement statement : statements) {
			location = processStatement(statement, "", SL, location, prefix, new Hashtable<Integer, Integer>(), SL);
		}
		return location;
	}
	
	private Integer processStatement(Statement statement, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		if(statement.toString().replaceAll(" ", "").contains("new")){
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
		if(RS.contains(parameters.get(0).getLiteralValue())){
			SL.put(RS.get(parameters.get(0).getLiteralValue()) + "," + parameters.get(1).getLiteralValue(), parameters.get(2).getLiteralValue());
		}else {
			SL.put(prefix + "_" + parameters.get(0).getLiteralValue() + "," + parameters.get(1).getLiteralValue(), parameters.get(2).getLiteralValue());
		}
		return initialLocation;
	}

	private Integer processVariableDeclarationFragment(VariableDeclarationFragment variableDeclarationFragment,	String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		Integer finalLocation = initialLocation;
		if((variableDeclarationFragment.getInitializer() instanceof MethodInvocation || variableDeclarationFragment.getInitializer() instanceof ClassInstanceCreation) 
				&& canProcessMethod(variableDeclarationFragment.getInitializer())){
			Expression expression = ((Expression) variableDeclarationFragment.getInitializer());
			String methodFullName = getMethodFullName(expression);
			Transition transition = new Transition(methodFullName, "true", getPassByValueMethodArgumentsUpdater(expression, RS, prefix) + getSecurityAssignments(SL, true));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
			Hashtable<String, String> newRS = getRenamingRules(expression, prefix);
			finalLocation = processBlock(getMethodBody(expression), rename(variableDeclarationFragment.getName(), newRS, prefix), newRS, finalLocation, getScopedName(expression, prefix), breakContinueLocations, SL);
		}else if (variableDeclarationFragment.getInitializer()!=null){
			Transition transition = new Transition(Transition.TAU, "true", rename(variableDeclarationFragment.getName(), RS, prefix) + "=" + rename(variableDeclarationFragment.getInitializer(), RS, prefix) + getSecurityAssignments(SL, true));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
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
			Transition transition = new Transition(Transition.TAU, "true", XReturn + "=" + rename(expression, RS, prefix) + getSecurityAssignments(SL, true));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
		}
		return finalLocation;
	}

	private Integer processAssignment(Assignment assignment, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		Integer finalLocation = initialLocation;
		if((assignment.getRightHandSide() instanceof MethodInvocation || assignment.getRightHandSide() instanceof ClassInstanceCreation) 
				&& canProcessMethod(assignment.getRightHandSide())){
			Expression expression= assignment.getRightHandSide();
			Transition transition = new Transition(getMethodFullName(expression), "true", getPassByValueMethodArgumentsUpdater(expression, RS, prefix) + getSecurityAssignments(SL, true));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
			Hashtable<String, String> newRS = getRenamingRules(expression, prefix);
			finalLocation = processBlock(getMethodBody(expression), rename(assignment.getLeftHandSide(), newRS, prefix), newRS, finalLocation, getScopedName(expression, prefix), breakContinueLocations, SL);
		}else{
			Transition transition = new Transition(Transition.TAU, "true", rename(assignment, RS, prefix) + getSecurityAssignments(SL, true));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
		};
		return finalLocation;
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
		Transition transition = new Transition(getMethodFullName(expression), "true", getPassByValueMethodArgumentsUpdater(expression, RS, prefix) + getSecurityAssignments(SL, true));
		Integer finalLocation = newLocation();
		sts.addVertex(initialLocation);
		sts.addVertex(finalLocation);
		sts.addEdge(initialLocation, finalLocation, transition);
		Hashtable<String, String> newRS = getRenamingRules(expression, prefix);
		finalLocation = processBlock(getMethodBody(expression), XReturn, newRS, finalLocation, getScopedName(expression, prefix), breakContinueLocations, SL);
		return finalLocation;
	}

	@SuppressWarnings("rawtypes")
	private String getPassByValueMethodArgumentsUpdater(Expression expression, Hashtable<String, String> RS, String prefix) {
		ArrayList<String> methodParameters = getMethodParameters(expression, getScopedName(expression, prefix));
		List arguments = null;
		if(expression instanceof MethodInvocation){
			arguments = ((MethodInvocation) expression).arguments();
		}else if(expression instanceof ClassInstanceCreation){
			arguments = ((ClassInstanceCreation) expression).arguments();
		}
		Hashtable<String, String> newRS = getRenamingRules(expression, prefix);
		String res = "";
		String separator = "";
		for (int i=0; i< methodParameters.size(); i++) {
			String methodParameter = methodParameters.get(i);
			if(!newRS.containsKey(methodParameter)){
					String fullParameterName = getScopedName(expression, prefix) + "_" + methodParameter;
					res += separator + fullParameterName  + "=" + rename((Expression) arguments.get(i), RS, prefix);
				separator = ", ";
			}
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
		String whileExpression = rename(whileStatement.getExpression(), RS, prefix);
		Transition entranceTransition = new Transition(Transition.TAU, whileExpression, getSecurityAssignments(SL, false));
		Transition exitTransition = new Transition(Transition.TAU, "  not (" + whileExpression + ")", getSecurityAssignments(SL, false));
		Integer entranceLocation = newLocation();
		Integer finalLocation = newLocation();
		sts.addVertex(initialLocation);
		sts.addVertex(finalLocation);
		sts.addVertex(entranceLocation);
		sts.addEdge(initialLocation, entranceLocation, entranceTransition);
		sts.addEdge(initialLocation, finalLocation, exitTransition);
		Integer tempLocation = processStatement(whileStatement.getBody(), XReturn, RS, entranceLocation, prefix, breakContinueLocations, SL);
		Transition tempTransition =  new Transition(Transition.TAU, "true", getSecurityAssignments(SL, false));
		sts.addVertex(tempLocation);
		sts.addEdge(tempLocation, initialLocation, tempTransition);
		for (Integer location : breakContinueLocations.keySet()) {
			sts.addVertex(location);
			Transition tempBCTransition =  new Transition(Transition.TAU, "true", getSecurityAssignments(SL, false));
			if(breakContinueLocations.get(location)==1){
				sts.addEdge(location, finalLocation, tempBCTransition);
			}else{
				sts.addEdge(location, initialLocation, tempBCTransition);
			}
		}
		return finalLocation;
	}

	private Integer processIfStatement(IfStatement ifStatement, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations, Hashtable<String, String> SL) {
		String ifExpression = rename(ifStatement.getExpression(), RS, prefix);
		Transition thenTransition = new Transition(Transition.TAU, ifExpression, getSecurityAssignments(SL, false));
		Transition elseTransition = new Transition(Transition.TAU, "  not (" + ifExpression + ")", getSecurityAssignments(SL, false));
		Integer thenLocation = newLocation();
		Integer elseLocation = newLocation();
		sts.addVertex(initialLocation);
		sts.addVertex(thenLocation);
		sts.addVertex(elseLocation);
		sts.addEdge(initialLocation, thenLocation, thenTransition);
		sts.addEdge(initialLocation, elseLocation, elseTransition);
		Integer finalThenLocation = processStatement(ifStatement.getThenStatement(), XReturn, RS, thenLocation, prefix, breakContinueLocations, SL);
		Integer finalLocation = finalThenLocation;
		if(ifStatement.getElseStatement()!=null){
			Integer finalElseLocation = processStatement(ifStatement.getElseStatement(), XReturn, RS, elseLocation, prefix, breakContinueLocations, SL);
			Transition transition1 = new Transition(Transition.TAU, "true", getSecurityAssignments(SL, false));
			Transition transition2 = new Transition(Transition.TAU, "true", getSecurityAssignments(SL, false));
			finalLocation = newLocation();
			sts.addVertex(finalLocation);
			sts.addVertex(finalThenLocation);
			sts.addVertex(finalElseLocation);
			sts.addEdge(finalThenLocation, finalLocation, transition1);
			sts.addEdge(finalElseLocation, finalLocation, transition2);
		}else{
			Transition transition = new Transition(Transition.TAU, "true", getSecurityAssignments(SL, false));
			sts.addEdge(elseLocation, finalLocation, transition);
		}
		return finalLocation;
	}
	
	private String getSecurityAssignments(Hashtable<String, String> SL, boolean withComa){
		String res = "";
		String separator = "";
		for (String key : SL.keySet()) {
			String[] parts = key.split(",");
			res += separator + "L" + parts[1] + "_" + parts[0] + "=" + (SL.get(key).toLowerCase().equals("h") ? "true" : "false");
			separator = ",";
		}
		if(!res.equals("") && withComa){
			res = "," + res;
		}
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
