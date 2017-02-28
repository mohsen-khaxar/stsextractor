package se.lnu.prosses.securityMonitor;

import java.util.Hashtable;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

public class JavaClassSTSExtractor {

	JavaFileHelper javaFileHelper;
	private JavaProjectSTSExtractor parent;
	
	public JavaClassSTSExtractor(JavaFileHelper javaFileHelper, JavaProjectSTSExtractor parent) {
		this.javaFileHelper = javaFileHelper;
		this.parent = parent;
	}

	public void extract() throws Exception {
		CompilationUnit compilationUnit = javaFileHelper.getCompilationUnit();
		MethodDeclaration[] methods = ((TypeDeclaration)compilationUnit.types().get(0)).getMethods();
		for (MethodDeclaration methodDeclaration : methods) {
			String qualifiedMethodName = javaFileHelper.getQualifiedName(methodDeclaration);
			String methodModifier = methodDeclaration.modifiers().toString().toLowerCase();
			if (parent.isEntryPoint(qualifiedMethodName) 
					&& methodDeclaration.getBody() != null 
					&& (methodModifier.contains("public") || methodModifier.contains("protected"))) {
				Integer finalLocation = processMethod(methodDeclaration);
				Transition transition = new Transition(STS.TAU, "true", "");
				parent.sts.addEdge(finalLocation, 2, transition);
			}
		}
	}

	private Integer processMethod(MethodDeclaration methodDeclaration) throws Exception {
		int oldScopeId = parent.scopeId;
		parent.scopeId++;
		Integer location = parent.newLocation();
		String qualifiedMethodName = javaFileHelper.getQualifiedName(methodDeclaration);
		String action = parent.sts.addAction(qualifiedMethodName);
		parent.sts.addTransition(1, location, action, "true", "");
		location = processStatement(methodDeclaration.getBody(), location);
		parent.scopeId = oldScopeId;
		return location;
	}
	
	private Integer processStatement(Statement statement, Integer initialLocation) throws Exception {
		Integer finalLocation = initialLocation; 
		switch(statement.getNodeType()){
		case ASTNode.EXPRESSION_STATEMENT:
			Expression expression = ((ExpressionStatement) statement).getExpression();
			if(expression.getNodeType()==ASTNode.METHOD_INVOCATION 
					&& javaFileHelper.isDummyMethod(expression)){
				MethodInvocation dummyMethodInvocation = (MethodInvocation) expression;
				finalLocation = processDummyMethodInvocation(dummyMethodInvocation, initialLocation);
			}else if((expression.getNodeType()==ASTNode.METHOD_INVOCATION 
					|| expression.getNodeType()==ASTNode.CLASS_INSTANCE_CREATION) 
					&& !isThirdParty(expression)){
				MethodInvocation methodInvocation = (MethodInvocation) expression;
				finalLocation = processMethodInvocation(methodInvocation, initialLocation);
			}else if(expression.getNodeType()==ASTNode.ASSIGNMENT){
				Assignment assignment = (Assignment)expression;
				finalLocation = processAssignment(assignment, initialLocation);
			}
			break;
		case ASTNode.IF_STATEMENT:
			IfStatement ifStatement = (IfStatement) statement;
			finalLocation = processIfStatement(ifStatement, initialLocation);
			break;
		case ASTNode.WHILE_STATEMENT:
			WhileStatement whileStatement = (WhileStatement) statement;
			finalLocation = processWhileStatement(whileStatement, initialLocation);
			break;
		case ASTNode.RETURN_STATEMENT:
			ReturnStatement returnStatement = (ReturnStatement) statement;
			finalLocation = processReturnStatement(returnStatement, initialLocation);
			break;
		case ASTNode.TRY_STATEMENT:
			TryStatement tryStatement = (TryStatement) statement;
			finalLocation = processBlock(tryStatement.getBody(), initialLocation);
			break;
		case ASTNode.BLOCK:
			Block block = (Block) statement;
			finalLocation = processBlock(block,initialLocation);
			break;
		case ASTNode.VARIABLE_DECLARATION_STATEMENT:
			VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement) statement;
			VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment)variableDeclarationStatement.fragments().get(0);
			finalLocation = processVariableDeclarationFragment(variableDeclarationFragment, initialLocation);
			break;
		default:
		}	
		return finalLocation;
	}
	
	private Integer processDummyMethodInvocation(MethodInvocation dummyMethodInvocation, Integer initialLocation) throws Exception {
		if(dummyMethodInvocation.getName().equals("checkPoint")){
			processCheckPoint(dummyMethodInvocation);
		}else if(dummyMethodInvocation.getName().equals("observe")){
			processObserve(dummyMethodInvocation, initialLocation);			
		}else if(dummyMethodInvocation.getName().equals("init")){
			processInit(dummyMethodInvocation);
		}else if(dummyMethodInvocation.getName().equals("entryPoint")){
			processEntryPoint(dummyMethodInvocation);
		}else if(dummyMethodInvocation.getName().equals("monitorablePoint")){
//			Nothing
		}else{
			throw new Exception(dummyMethodInvocation.getName() + " is not pre-defined.");
		}
		return initialLocation;
	}

	private void processEntryPoint(MethodInvocation dummyMethodInvocation) {
		ASTNode parentNode = dummyMethodInvocation.getParent();
		while(!(parentNode instanceof MethodDeclaration)){
			parentNode = dummyMethodInvocation.getParent();
		}
		parent.sts.setEntryPoint(javaFileHelper.getQualifiedName((MethodDeclaration)parentNode));
	}

	private void processCheckPoint(MethodInvocation dummyMethodInvocation) {
		ASTNode parentNode = dummyMethodInvocation.getParent();
		while(!(parentNode instanceof MethodDeclaration)){
			parentNode = dummyMethodInvocation.getParent();
		}
		parent.sts.setCheckPoint(javaFileHelper.getQualifiedName((MethodDeclaration)parentNode));
	}

	@SuppressWarnings("unchecked")
	private void processObserve(MethodInvocation dummyMethodInvocation, Integer observationLocation) throws Exception {
		List<StringLiteral> arguments = dummyMethodInvocation.arguments();
		String securityPolicyExpression = getSecurityExpression(parent.rename(arguments.get(0)), arguments.get(2).toString()) + "=";
		securityPolicyExpression += arguments.get(1).toString().equals("H") ? "true" : "false";
		parent.sts.setSecurityPolicy(securityPolicyExpression, observationLocation);
	}

	@SuppressWarnings("unchecked")
	private void processInit(MethodInvocation dummyMethodInvocation) {
		List<StringLiteral> arguments = dummyMethodInvocation.arguments();
		String securityPolicyExpression = getSecurityExpression(parent.rename(arguments.get(0)), arguments.get(2).toString()) + "=";
		securityPolicyExpression += arguments.get(1).toString().equals("H") ? "true" : "false";
		parent.sts.setSecurityInit(securityPolicyExpression);
	}

	private Integer processReturnStatement(ReturnStatement returnStatement, Integer initialLocation) {
		Integer finalLocation = initialLocation;
		Expression expression = returnStatement.getExpression();
		if(expression!=null){
			finalLocation = parent.newLocation();
			String target = parent.returnTarget.pop();
			String returnedExpression = parent.rename(expression);
			String update = target + "=" + returnedExpression + ";";
			update += "LX_" + target + "=" + getSecurityExpression(returnedExpression, "X") + ";";
			update += "LI_" + target + "=" + getSecurityExpression(returnedExpression, "I") + " or LPC;";
			parent.sts.addTransition(initialLocation, finalLocation, STS.TAU, "true", update);
		}
		return finalLocation;
	}
	
	private Integer processVariableDeclarationFragment(VariableDeclarationFragment variableDeclarationFragment, Integer initialLocation) throws Exception {
		return processAssignmentLike(variableDeclarationFragment.getName(), variableDeclarationFragment.getInitializer(), initialLocation);
	}
	
	private Integer processAssignment(Assignment assignment, Integer initialLocation) throws Exception {
		return processAssignmentLike(assignment.getLeftHandSide(), assignment.getRightHandSide(), initialLocation);
	}
	
	private Integer processAssignmentLike(Expression leftHandSide, Expression rightHandSide, Integer initialLocation) throws Exception{
		Integer finalLocation = initialLocation;
		if((rightHandSide instanceof MethodInvocation 
				|| rightHandSide instanceof ClassInstanceCreation) 
				&& !isThirdParty(rightHandSide)){
			String methodArgumentsUpdates = processMethodArguments(rightHandSide);
			finalLocation = parent.newLocation();
			parent.sts.addTransition(initialLocation, finalLocation, javaFileHelper.getQualifiedName(rightHandSide), "true", methodArgumentsUpdates);
			parent.returnTarget.push(parent.rename(leftHandSide));
			finalLocation = processBlock(javaFileHelper.getMethodBody(rightHandSide), finalLocation);
		}else{
			String rightHandExpression = parent.rename(rightHandSide);
			String leftHandExpression = parent.rename(leftHandSide);
			finalLocation = parent.newLocation();
			String update = leftHandExpression + "=" + rightHandExpression + ";";
			update += "LX_" + leftHandExpression + "=" + getSecurityExpression(rightHandExpression, "X") + ";";
			update += "LI_" + leftHandExpression + "=" + getSecurityExpression(rightHandExpression, "I") + " or LPC;";
			parent.sts.addTransition(initialLocation, finalLocation, STS.TAU, "true", update);
		}
		return finalLocation;
	}
	
	private Integer processMethodInvocation(Expression expression, Integer initialLocation) throws Exception {
		String methodArgumentsUpdates = processMethodArguments(expression);
		Integer finalLocation = parent.newLocation();
		parent.sts.addTransition(initialLocation, finalLocation, javaFileHelper.getQualifiedName(expression), "true", methodArgumentsUpdates);
		finalLocation = processBlock(javaFileHelper.getMethodBody(expression), finalLocation);
		return finalLocation;
	}
	
	@SuppressWarnings("unchecked")
	private Integer processBlock(Block block, Integer initialLocation) throws Exception {
		List<Statement> statements = block.statements();
		Integer finalLocation = initialLocation;
		for (Statement statement : statements) {
			finalLocation = processStatement(statement, finalLocation);
		}
		return finalLocation;
	}
	
	private Integer processWhileStatement(WhileStatement whileStatement, Integer initialLocation) throws Exception {
		String whileExpression = parent.rename(whileStatement.getExpression());
		int oldScopeId = parent.enterScope();
		Integer loopEntranceLocation = parent.newLocation();
		String lpcStackVariable = parent.getLPCUniqueName();
		String lpcUpdate = lpcStackVariable + "=LPC;" + "LPC=" + getSecurityExpression(whileExpression, "I") + " or LPC;";
		parent.sts.addTransition(initialLocation, loopEntranceLocation, STS.TAU, whileExpression, lpcUpdate);
		Integer finalLocationInLoop = processStatement(whileStatement.getBody(), loopEntranceLocation);
		parent.sts.addTransition(finalLocationInLoop, initialLocation, STS.TAU, "true", "LPC=" + lpcStackVariable + ";");
		Integer loopExitLocation = parent.newLocation();
		parent.sts.addTransition(initialLocation, loopExitLocation, STS.TAU, "  not (" + whileExpression + ")", 
				lpcUpdate + getSecurityExpressionForPossibleModifieds(whileStatement.getBody()));
		Integer finalLocation = parent.newLocation();
		parent.sts.addTransition(loopExitLocation, finalLocation, STS.TAU, "true", "LPC=" + lpcStackVariable + ";");
		parent.exitScope(oldScopeId);
		return loopExitLocation;
	}

	private Integer processIfStatement(IfStatement ifStatement, Integer initialLocation) throws Exception {
		String ifExpression = parent.rename(ifStatement.getExpression());
		int oldScopeId = parent.enterScope();
		Integer thenLocation = parent.newLocation();
		String lpcStackVariable = parent.getLPCUniqueName();
		String lpcUpdate = lpcStackVariable	+ "=LPC;" + "LPC=" + getSecurityExpression(ifExpression, "I") + " or LPC;";
		parent.sts.addTransition(initialLocation, thenLocation, STS.TAU, ifExpression, 
				lpcUpdate + getSecurityExpressionForPossibleModifieds(ifStatement.getElseStatement()));
		Integer elseLocation = parent.newLocation();
		parent.sts.addTransition(initialLocation, elseLocation, STS.TAU, "not (" + ifExpression + ")", 
				lpcUpdate + getSecurityExpressionForPossibleModifieds(ifStatement.getThenStatement()));
		Integer finalThenLocation = processStatement(ifStatement.getThenStatement(), thenLocation);
		Integer finalElseLocation = elseLocation;
		if(ifStatement.getElseStatement()!=null){
			finalElseLocation = processStatement(ifStatement.getElseStatement(), elseLocation);
		}
		Integer finalLocation = parent.newLocation();
		parent.sts.addTransition(finalThenLocation, finalLocation, STS.TAU, "true", "LPC=" + lpcStackVariable + ";");
		parent.sts.addTransition(finalElseLocation, finalLocation, STS.TAU, "true", "LPC=" + lpcStackVariable + ";");
		parent.exitScope(oldScopeId);
		return finalLocation;
	}
	
	String getSecurityExpression(String expression, String policyType){
		String securityExpression = "";
		String[] parts = expression.replaceAll(" ", "").split("[^\\w_$\"]+");
		String operator = "";
		for (String part : parts) {
			part = part.replaceAll(" ", "");
			if(!part.equals("")){
				if(part.matches("\".*\"") || part.equals("true") || part.equals("false") || part.matches("[-+]?\\d*\\.?\\d+")){
					securityExpression += operator + "false ";
				}else {
					securityExpression += operator + "L" + policyType + "_" + part;
				}
				operator = " or ";
			}
		}
		return securityExpression;		
	}
	
	String getSecurityExpressionForPossibleModifieds(Statement statement){
		String securityExpression = "";
		return securityExpression;
	}
	
	private boolean isThirdParty(Expression expression) {
		return !(parent.canProcess(javaFileHelper.getQualifiedName(expression)));
	}
	
	public String processMethodArguments(Expression expression) {
		List<SimpleName> parameters = javaFileHelper.getMethodParameters(expression);
		List<Expression> arguments = javaFileHelper.getMethodArguments(expression);
		Hashtable<SimpleName, String> renamingRuleSet = getRenamingRuleSet(parameters, arguments);
		String argumentAssignments = "";
		for (int i=0; i< parameters.size(); i++) {
			if(!renamingRuleSet.containsKey(parameters.get(i).toString())){
				String parameterUniqueName = parent.getUniqueName(parameters.get(i));
				String renamedArgument = parent.rename(arguments.get(i));
				argumentAssignments += parameterUniqueName + "=" + renamedArgument + ";";
				argumentAssignments += "LX_" + parameterUniqueName + "=" + getSecurityExpression(renamedArgument, "X") + ";";
				argumentAssignments += "LI_" + parameterUniqueName + "=" + getSecurityExpression(renamedArgument, "I") + " or LPC;";
			}
		}
		parent.renamingRuleSets.push(renamingRuleSet);
		return argumentAssignments;
	}
	
	private Hashtable<SimpleName, String> getRenamingRuleSet(List<SimpleName> parameters, List<Expression> arguments) {
		Hashtable<SimpleName, String> renamingRuleSet = new Hashtable<>();
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
					renamingRuleSet.put(parameters.get(i), parent.getUniqueName((SimpleName) arguments));
				}
			}
		}
		return renamingRuleSet;
	}
}
