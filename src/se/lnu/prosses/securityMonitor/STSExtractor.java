package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
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
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

public class STSExtractor {

	int newLocation = 0;
	public ArrayList<String> includingFilter;
	public ArrayList<String> excludingFilter;
	public ArrayList<String> entryPoints;
	private STS sts;
	private Hashtable<String, TypeDeclaration> classes = new Hashtable<>();
	
	public STSExtractor(ArrayList<String> includingFilter, ArrayList<String> excludingFilter, ArrayList<String> entryPoints) {
		this.includingFilter = includingFilter;
		this.excludingFilter = excludingFilter;
		this.entryPoints = entryPoints;
		this.sts = new STS();
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
	
	public void extract(String directoryPath, String[] classPath) throws Exception{
		getClasses(directoryPath, classPath);
		for (TypeDeclaration cls : classes.values()) {
			extractForAClass(cls);
		}
		System.out.println("DONE.");
		sts.saveAsDot(directoryPath + File.separator + "model.dot");
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
				Transition transition =  new Transition(Transition.TAU, "true", "");
				sts.addEdge(finalLocation, 0, transition);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private Integer processMethod(MethodDeclaration methodDeclaration) {
		List<Statement> statements = methodDeclaration.getBody().statements();
		Integer location = 0;
		String prefix = methodDeclaration.resolveBinding().getDeclaringClass().getQualifiedName().replaceAll("\\.", "_") + "_" + methodDeclaration.getName();
		for (Statement statement : statements) {
			location = processStatement(statement, "", new Hashtable<String, String>(), location, prefix, new Hashtable<Integer, Integer>());
		}
		return location;
	}
	
	private Integer processStatement(Statement statement, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations) {
		Integer finalLocation = initialLocation; 
		switch(statement.getNodeType()){
		case ASTNode.EXPRESSION_STATEMENT:
			Expression expression = ((ExpressionStatement) statement).getExpression();
			if(expression.getNodeType()==ASTNode.METHOD_INVOCATION && canProcessMethod((MethodInvocation) expression)){
				MethodInvocation methodInvocation = (MethodInvocation) expression;
				finalLocation = processMethodInvocation(methodInvocation, XReturn, RS, initialLocation, prefix, breakContinueLocations);
			}else if(expression.getNodeType()==ASTNode.ASSIGNMENT){
				Assignment assignment = (Assignment)expression;
				finalLocation = processAssignment(assignment, XReturn, RS, initialLocation, prefix, breakContinueLocations);
			}
			break;
		case ASTNode.IF_STATEMENT:
			IfStatement ifStatement = (IfStatement) statement;
			finalLocation = processIfStatement(ifStatement, XReturn, RS, initialLocation, prefix, breakContinueLocations);
			break;
		case ASTNode.WHILE_STATEMENT:
			WhileStatement whileStatement = (WhileStatement) statement;
			finalLocation = processWhileStatement(whileStatement, XReturn, RS, initialLocation, prefix, breakContinueLocations);
			break;
		case ASTNode.RETURN_STATEMENT:
			ReturnStatement returnStatement = (ReturnStatement) statement;
			finalLocation = processReturnStatement(returnStatement, XReturn, RS, initialLocation, prefix, breakContinueLocations);
			break;
		case ASTNode.TRY_STATEMENT:
			@SuppressWarnings("unused")
			TryStatement tryStatement = (TryStatement) statement;
			finalLocation = processBlock(tryStatement.getBody(), XReturn, RS, initialLocation, prefix, breakContinueLocations);
			break;
		case ASTNode.BLOCK:
			Block block = (Block) statement;
			finalLocation = processBlock(block, XReturn, RS, initialLocation, prefix, breakContinueLocations);
			break;
		case ASTNode.BREAK_STATEMENT:
			breakContinueLocations.put(finalLocation, 1);
			break;
		case ASTNode.CONTINUE_STATEMENT:
			breakContinueLocations.put(finalLocation, -1);
			break;
		case ASTNode.VARIABLE_DECLARATION_STATEMENT:
			VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement) statement;
			finalLocation = processVariableDeclarationFragment((VariableDeclarationFragment)variableDeclarationStatement.fragments().get(0), XReturn, RS, initialLocation, prefix, breakContinueLocations);
			break;
		default:
		}	
		return finalLocation;
	}

	private Integer processVariableDeclarationFragment(VariableDeclarationFragment variableDeclarationFragment,	String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations) {
		Integer finalLocation = initialLocation;
		if(variableDeclarationFragment.getInitializer() instanceof MethodInvocation && canProcessMethod((MethodInvocation) variableDeclarationFragment.getInitializer())){
			MethodInvocation methodInvocation = (MethodInvocation) variableDeclarationFragment.getInitializer();
			String declaringClass = methodInvocation.resolveMethodBinding().getDeclaringClass().getQualifiedName();
			String methodFullName = declaringClass.replaceAll("\\.", "_") + "_" + methodInvocation.getName();
			Transition transition = new Transition(methodFullName, "true", getPassByValueMethodArgumentsUpdater(methodInvocation, RS, prefix));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
			Hashtable<String, String> newRS = getRenamingRules(methodInvocation, prefix);
			finalLocation = processBlock(getMethodBody(methodInvocation, newRS), variableDeclarationFragment.getName().toString(), newRS, finalLocation, prefix + "_" + methodInvocation.getExpression().toString().replaceAll("\\.", "_") + "_" + methodInvocation.getName(), breakContinueLocations);
		}else{
			Transition transition = new Transition(Transition.TAU, "true", rename(variableDeclarationFragment.getName(), RS, prefix) + "=" + rename(variableDeclarationFragment.getInitializer(), RS, prefix));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
		};
		return finalLocation;
	}

	private Integer processReturnStatement(ReturnStatement returnStatement, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations) {
		Integer finalLocation = initialLocation;
		Expression expression = returnStatement.getExpression();
		if(expression!=null){
			Transition transition = new Transition(Transition.TAU, "true", XReturn + "=" + rename(expression, RS, prefix));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
		}
		return finalLocation;
	}

	private Integer processAssignment(Assignment assignment, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations) {
		Integer finalLocation = initialLocation;
		if(assignment.getRightHandSide() instanceof MethodInvocation && canProcessMethod((MethodInvocation) assignment.getRightHandSide())){
			MethodInvocation methodInvocation = (MethodInvocation) assignment.getRightHandSide();
			String declaringClass = methodInvocation.resolveMethodBinding().getDeclaringClass().getQualifiedName();
			String methodFullName = declaringClass.replaceAll("\\.", "_") + "_" + methodInvocation.getName();
			Transition transition = new Transition(methodFullName, "true", getPassByValueMethodArgumentsUpdater(methodInvocation, RS, prefix));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
			Hashtable<String, String> newRS = getRenamingRules(methodInvocation, prefix);
			finalLocation = processBlock(getMethodBody(methodInvocation, newRS), assignment.getLeftHandSide().toString(), newRS, finalLocation, prefix + "_" + methodInvocation.getExpression().toString().replaceAll("\\.", "_") + "_" + methodInvocation.getName(), breakContinueLocations);
		}else{
			Transition transition = new Transition(Transition.TAU, "true", rename(assignment, RS, prefix));
			finalLocation = newLocation();
			sts.addVertex(initialLocation);
			sts.addVertex(finalLocation);
			sts.addEdge(initialLocation, finalLocation, transition);
		};
		return finalLocation;
	}

	private boolean canProcessMethod(MethodInvocation methodInvocation) {
		boolean res = false;
		String declaringClass = methodInvocation.resolveMethodBinding().getDeclaringClass().getQualifiedName();
		String methodFullName = declaringClass + "." + methodInvocation.getName();
		if(canProcess(methodFullName)){
			res = true;
		}
		return res;
	}

	private Integer processMethodInvocation(MethodInvocation methodInvocation, String XReturn,	Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations) {
		String declaringClass = methodInvocation.resolveMethodBinding().getDeclaringClass().getQualifiedName();
		String methodFullName = declaringClass.replaceAll("\\.", "_") + "_" + methodInvocation.getName();
		Transition transition = new Transition(methodFullName, "true", getPassByValueMethodArgumentsUpdater(methodInvocation, RS, prefix));
		Integer finalLocation = newLocation();
		sts.addVertex(initialLocation);
		sts.addVertex(finalLocation);
		sts.addEdge(initialLocation, finalLocation, transition);
		Hashtable<String, String> newRS = getRenamingRules(methodInvocation, prefix);
		finalLocation = processBlock(getMethodBody(methodInvocation, newRS), XReturn, newRS, finalLocation, prefix + "_" + methodInvocation.getExpression().toString().replaceAll("\\.", "_") + "_" + methodInvocation.getName(), breakContinueLocations);
		return finalLocation;
	}

	@SuppressWarnings("rawtypes")
	private String getPassByValueMethodArgumentsUpdater(MethodInvocation methodInvocation, Hashtable<String, String> RS, String prefix) {
		ArrayList<String> methodParameters = getMethodParameters(methodInvocation);
		List methodArguments = methodInvocation.arguments();
		Hashtable<String, String> newRS = getRenamingRules(methodInvocation, prefix);
		String res = "";
		String separator = "";
		for (int i=0; i< methodParameters.size(); i++) {
			String methodParameter = methodParameters.get(i);
			if(!newRS.containsKey(methodParameter)){
				res += separator + prefix + "_" + methodInvocation.getExpression().toString().replaceAll("\\.", "_") + "_" + methodInvocation.getName() + methodParameter + "=" + rename((Expression) methodArguments.get(i), RS, prefix);
				separator = ", ";
			}
		}
		return res;
	}

	@SuppressWarnings("unchecked")
	private ArrayList<String> getMethodParameters(MethodInvocation methodInvocation) {
		String declaringClass = methodInvocation.resolveMethodBinding().getDeclaringClass().getQualifiedName();
		TypeDeclaration cls = classes.get(declaringClass);
		ArrayList<String> res = new ArrayList<>();
		for (MethodDeclaration methodDeclaration : cls.getMethods()) {
			if(methodDeclaration.getName().toString().equals(methodInvocation.getName().toString())){
				List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
				for (SingleVariableDeclaration singleVariableDeclaration : parameters) {
					res.add(singleVariableDeclaration.getName().getFullyQualifiedName());
				}
				break;
			}
		}
		return res;
	}

	private Block getMethodBody(MethodInvocation methodInvocation, Hashtable<String, String> RS) {
		String declaringClass = methodInvocation.resolveMethodBinding().getDeclaringClass().getQualifiedName();
		TypeDeclaration cls = classes.get(declaringClass);
		Block res = null;
		for (MethodDeclaration methodDeclaration : cls.getMethods()) {
			if(methodDeclaration.getName().toString().equals(methodInvocation.getName().toString())){
				res = methodDeclaration.getBody();
				break;
			}
		}
		return res;
	}

	@SuppressWarnings("rawtypes")
	private Hashtable<String, String> getRenamingRules(MethodInvocation methodInvocation, String prefix) {
		ArrayList<String> methodParameters = getMethodParameters(methodInvocation);
		List methodArguments = methodInvocation.arguments();
		Hashtable<String, String> res = new Hashtable<>();
		for (int i=0; i< methodArguments.size(); i++) {
			Object methodArgument = methodArguments.get(i);
			if(methodArgument instanceof SimpleName){
				ITypeBinding iTypeBinding = ((SimpleName) methodArgument).resolveTypeBinding();
				if(!iTypeBinding.isPrimitive() 
						&& iTypeBinding.getQualifiedName()!="java.lang.Integer"
						&& iTypeBinding.getQualifiedName()!="java.lang.Long"
						&& iTypeBinding.getQualifiedName()!="java.lang.Byte"
						&& iTypeBinding.getQualifiedName()!="java.lang.Short"
						&& iTypeBinding.getQualifiedName()!="java.lang.Float"
						&& iTypeBinding.getQualifiedName()!="java.lang.Double"
						&& iTypeBinding.getQualifiedName()!="java.lang.BigDecimal"
						&& iTypeBinding.getQualifiedName()!="java.lang.BigInteger"){
					res.put(methodParameters.get(i), prefix + methodArguments.get(i).toString());
				}
			}
		}
		return res;
	}

	@SuppressWarnings("unchecked")
	private Integer processBlock(Block block, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations) {
		List<Statement> statements = block.statements();
		Integer finalLocation = initialLocation;
		for (Statement statement : statements) {
			finalLocation = processStatement(statement, XReturn, RS, finalLocation, prefix, breakContinueLocations);
		}
		return finalLocation;
	}

	private Integer processWhileStatement(WhileStatement whileStatement, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations) {
		String whileExpression = rename(whileStatement.getExpression(), RS, prefix);
		Transition entranceTransition = new Transition(Transition.TAU, whileExpression, "");
		Transition exitTransition = new Transition(Transition.TAU, "not (" + whileExpression + ")", "");
		Integer entranceLocation = newLocation();
		Integer finalLocation = newLocation();
		sts.addVertex(initialLocation);
		sts.addVertex(finalLocation);
		sts.addVertex(entranceLocation);
		sts.addEdge(initialLocation, entranceLocation, entranceTransition);
		sts.addEdge(initialLocation, finalLocation, exitTransition);
		Integer tempLocation = processStatement(whileStatement.getBody(), XReturn, RS, entranceLocation, prefix, breakContinueLocations);
		Transition tempTransition =  new Transition(Transition.TAU, "true", "");
		sts.addVertex(tempLocation);
		sts.addEdge(tempLocation, initialLocation, tempTransition);
		for (Integer location : breakContinueLocations.keySet()) {
			sts.addVertex(location);
			Transition tempBCTransition =  new Transition(Transition.TAU, "true", "");
			if(breakContinueLocations.get(location)==1){
				sts.addEdge(location, finalLocation, tempBCTransition);
			}else{
				sts.addEdge(location, initialLocation, tempBCTransition);
			}
		}
		return finalLocation;
	}

	private Integer processIfStatement(IfStatement ifStatement, String XReturn, Hashtable<String, String> RS, Integer initialLocation, String prefix, Hashtable<Integer, Integer> breakContinueLocations) {
		String ifExpression = rename(ifStatement.getExpression(), RS, prefix);
		Transition thenTransition = new Transition(Transition.TAU, ifExpression, "");
		Transition elseTransition = new Transition(Transition.TAU, "not (" + ifExpression + ")", "");
		Integer thenLocation = newLocation();
		Integer elseLocation = newLocation();
		sts.addVertex(initialLocation);
		sts.addVertex(thenLocation);
		sts.addVertex(elseLocation);
		sts.addEdge(initialLocation, thenLocation, thenTransition);
		sts.addEdge(initialLocation, elseLocation, elseTransition);
		Integer finalThenLocation = processStatement(ifStatement.getThenStatement(), XReturn, RS, thenLocation, prefix, breakContinueLocations);
		Integer finalLocation = finalThenLocation;
		if(ifStatement.getElseStatement()!=null){
			Integer finalElseLocation = processStatement(ifStatement.getElseStatement(), XReturn, RS, elseLocation, prefix, breakContinueLocations);
			Transition transition1 = new Transition(Transition.TAU, "true", "");
			Transition transition2 = new Transition(Transition.TAU, "true", "");
			finalLocation = newLocation();
			sts.addVertex(finalLocation);
			sts.addVertex(finalThenLocation);
			sts.addVertex(finalElseLocation);
			sts.addEdge(finalThenLocation, finalLocation, transition1);
			sts.addEdge(finalElseLocation, finalLocation, transition2);
		}else{
			Transition transition = new Transition(Transition.TAU, "true", "");
			sts.addEdge(elseLocation, finalLocation, transition);
		}
		return finalLocation;
	}
	
	static String renamed = "";
	private String rename(Expression expression, final Hashtable<String, String> RS, final String prefix){
		renamed = " " + expression.toString() + " ";
		renamed = renamed.replaceAll("\\s+\\(", "(");
		expression.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName simpleName) {
				if(simpleName.resolveBinding()!=null && simpleName.resolveBinding() instanceof IVariableBinding){
					IVariableBinding resolveBinding = (IVariableBinding)simpleName.resolveBinding();
					if(resolveBinding.isParameter() && RS.contains(simpleName.getIdentifier())){
						renamed = replace(renamed, simpleName.getIdentifier(), RS.get(simpleName.getIdentifier()));
					}else {
						renamed = replace(renamed, simpleName.getIdentifier(), prefix + "_" + simpleName.getIdentifier());
					}
				}
				return false;
			}
		});
		return renamed;
	}
	
	private static String replace(String string, String find, String replace) {
		String[] res = string.split(find);
		string = "";
		for (int i=0; i<res.length-1; i++) {
			if((!Character.isLetter(res[i].charAt(res[i].length()-1)) && res[i].charAt(res[i].length()-1)!='_' && res[i+1].charAt(0)!='(')  
					|| (!Character.isLetter(res[i+1].charAt(0))&&res[i+1].charAt(0)!='_'&&res[i+1].charAt(0)!='(')){
				string += res[i] + replace;
			}else{
				string += res[i] + find;
			}
		}
		string += res[res.length-1];
		return string;
	}

	private CompilationUnit getCompilationUnit(String[] sourceDir, String[] classPath, String javaFilePath) {
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
	
	private CompilationUnit getCompilationUnit(ICompilationUnit iCompilationUnit) {
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(iCompilationUnit);
		parser.setResolveBindings(true);
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

	private static ArrayList<String> getAllJavaFilePaths(String directoryPath) {
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
