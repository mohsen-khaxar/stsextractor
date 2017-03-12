package se.lnu.prosses.securityMonitor;

import java.util.Hashtable;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
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
			String methodModifier = methodDeclaration.modifiers().toString().toLowerCase();
			if (parent.isEntryPoint(methodDeclaration) 
					&& methodDeclaration.getBody() != null 
					&& (methodModifier.contains("public") || methodModifier.contains("protected"))) {
				Integer finalLocation = processMethod(methodDeclaration);
				parent.stsHelper.addTransition(finalLocation, 0, STS.MONITORABLE_ACTION, "true", "");
			}
		}
	}

	private Integer processMethod(MethodDeclaration methodDeclaration) throws Exception {
		Integer location = parent.newLocation();
		String qualifiedMethodName = javaFileHelper.getQualifiedName(methodDeclaration);
		String action = parent.stsHelper.addAction(qualifiedMethodName);
		parent.stsHelper.addTransition(1, location, action, "true", "LPC=false;");
		for (Object parameter : methodDeclaration.parameters()) {
			parent.enterScope();
			parent.saveLocalVariableDeclarationScope(((SingleVariableDeclaration) parameter).getName());
			parent.revertToLastScope();
		}
		location = processStatement(methodDeclaration.getBody(), location);
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
			}else if(expression.getNodeType()==ASTNode.METHOD_INVOCATION 
					|| expression.getNodeType()==ASTNode.CLASS_INSTANCE_CREATION){
				finalLocation = processMethodInvocation(expression, initialLocation);
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
		Integer finalLocation = initialLocation;
		if(dummyMethodInvocation.getName().toString().equals("checkPoint")){
			finalLocation = processCheckPoint(dummyMethodInvocation, initialLocation);
		}else if(dummyMethodInvocation.getName().toString().equals("observe")){
			finalLocation = processObserve(dummyMethodInvocation, initialLocation);			
		}else if(dummyMethodInvocation.getName().toString().equals("init")){
			finalLocation = processInit(dummyMethodInvocation, initialLocation);
		}else if(dummyMethodInvocation.getName().toString().equals("entryPoint")){
			finalLocation = processEntryPoint(dummyMethodInvocation, initialLocation);
		}else if(dummyMethodInvocation.getName().toString().equals("monitorablePoint")){
			finalLocation = processMonitorablePoint(dummyMethodInvocation, initialLocation);
		}else{
			throw new Exception(dummyMethodInvocation.getName() + " is not pre-defined.");
		}
		return finalLocation;
	}

	private Integer processMonitorablePoint(MethodInvocation dummyMethodInvocation, Integer initialLocation) {
		Integer finalLocation = parent.newLocation();
		String action = parent.stsHelper.addAction(javaFileHelper.getQualifiedName(dummyMethodInvocation));
		parent.stsHelper.addTransition(initialLocation, finalLocation, action, "true", "");
		return finalLocation;
	}

	private Integer processEntryPoint(MethodInvocation dummyMethodInvocation, Integer initialLocation) {
		ASTNode parentNode = dummyMethodInvocation.getParent();
		while(!(parentNode instanceof MethodDeclaration)){
			parentNode = parentNode.getParent();
		}
		String qualifiedMethodName = javaFileHelper.getQualifiedName((MethodDeclaration)parentNode);
		parent.stsHelper.setMonitorablePoint(qualifiedMethodName);
		return initialLocation;
	}

	private Integer processCheckPoint(MethodInvocation dummyMethodInvocation, Integer initialLocation) {
		ASTNode parentNode = dummyMethodInvocation.getParent();
		while(!(parentNode instanceof MethodDeclaration)){
			parentNode = parentNode.getParent();
		}
		String qualifiedMethodName = javaFileHelper.getQualifiedName((MethodDeclaration)parentNode);
		parent.stsHelper.setCheckPoint(qualifiedMethodName);
		parent.stsHelper.setMonitorablePoint(qualifiedMethodName);
		return initialLocation;
	}

	@SuppressWarnings("unchecked")
	private Integer processObserve(MethodInvocation dummyMethodInvocation, Integer observationLocation) throws Exception {
		List<Expression> arguments = dummyMethodInvocation.arguments();
		String variable = parent.rename(((CastExpression)arguments.get(0)).getExpression());
		String policyTypes = ((StringLiteral)arguments.get(2)).getLiteralValue();
		String securityLevels = ((StringLiteral)arguments.get(1)).getLiteralValue();
		String securityPolicyExpression = getSecurityExpression(variable , policyTypes.substring(0, 1)) + "=";
		securityPolicyExpression += securityLevels.substring(0, 1).equals("H") ? "true" : "false";
		parent.stsHelper.setSecurityPolicy(securityPolicyExpression, observationLocation);
		String update = securityPolicyExpression + ";";
		if(policyTypes.length()==2){
			securityPolicyExpression = getSecurityExpression(variable , policyTypes.substring(1, 2)) + "=";
			securityPolicyExpression += securityLevels.substring(1, 2).equals("H") ? "true" : "false";
		}
		parent.stsHelper.setSecurityPolicy(securityPolicyExpression, observationLocation);
		update +=  securityPolicyExpression + ";";
		List<Transition> outgoingTransitions = parent.stsHelper.getOutgoingTransitions(1);
		for (Transition transition : outgoingTransitions) {
			transition.setUpdate(transition.getUpdate() + update);
		}
		return observationLocation;
	}

	@SuppressWarnings("unchecked")
	private Integer processInit(MethodInvocation dummyMethodInvocation, Integer initialLocation) {
		List<Expression> arguments = dummyMethodInvocation.arguments();
		String variable = parent.rename(((CastExpression)arguments.get(0)).getExpression());
		String policyTypes = ((StringLiteral)arguments.get(2)).getLiteralValue();
		String securityLevels = ((StringLiteral)arguments.get(1)).getLiteralValue();
		String securityInitExpression = getSecurityExpression(variable , policyTypes.substring(0, 1)) + "=";
		securityInitExpression += securityLevels.substring(0, 1).equals("H") ? "true" : "false";
		parent.stsHelper.setSecurityInit(securityInitExpression);
		String update = securityInitExpression + ";";
		if(policyTypes.length()==2){
			securityInitExpression = getSecurityExpression(variable , policyTypes.substring(1, 2)) + "=";
			securityInitExpression += securityLevels.substring(1, 2).equals("H") ? "true" : "false";
		}
		parent.stsHelper.setSecurityInit(securityInitExpression);
		update += securityInitExpression + ";";
		List<Transition> outgoingTransitions = parent.stsHelper.getOutgoingTransitions(1);
		for (Transition transition : outgoingTransitions) {
			transition.setUpdate(transition.getUpdate() + update);
		}
		return initialLocation;
	}

	private Integer processReturnStatement(ReturnStatement returnStatement, Integer initialLocation) {
		Integer finalLocation = initialLocation;
		Expression expression = returnStatement.getExpression();
		if(expression!=null){
			finalLocation = parent.newLocation();
			String target = parent.returnTarget.peek().replaceAll("\\s", "");
			String returnedExpression = parent.rename(expression);
			String update = target + "=" + returnedExpression + ";";
			update += "LX" + target + "=" + getSecurityExpression(returnedExpression, "X") + ";";
			update += "LI" + target + "=" + getSecurityExpression(returnedExpression, "I") + " or LPC;";
			parent.stsHelper.addTransition(initialLocation, finalLocation, STS.TAU, "true", update);
		}
		return finalLocation;
	}
	
	private Integer processVariableDeclarationFragment(VariableDeclarationFragment variableDeclarationFragment, Integer initialLocation) throws Exception {
		parent.saveLocalVariableDeclarationScope(variableDeclarationFragment.getName());
		return processAssignmentLike(variableDeclarationFragment.getName(), variableDeclarationFragment.getInitializer(), initialLocation);
	}
	
	private Integer processAssignment(Assignment assignment, Integer initialLocation) throws Exception {
		return processAssignmentLike(assignment.getLeftHandSide(), assignment.getRightHandSide(), initialLocation);
	}
	
	private Integer processAssignmentLike(Expression leftHandSide, Expression rightHandSide, Integer initialLocation) throws Exception{
		Integer finalLocation = initialLocation;
		if(rightHandSide instanceof MethodInvocation || rightHandSide instanceof ClassInstanceCreation){
			parent.returnTarget.push(parent.rename(leftHandSide));
			finalLocation = processMethodInvocation(rightHandSide, initialLocation);
			parent.returnTarget.pop();
		}else{
			String rightHandExpression = parent.rename(rightHandSide);
			String leftHandExpression = parent.rename(leftHandSide).replaceAll("\\s", "");
			finalLocation = parent.newLocation();
			String update = leftHandExpression + "=" + rightHandExpression + ";";
			update += "LX" + leftHandExpression + "=" + getSecurityExpression(rightHandExpression, "X") + ";";
			update += "LI" + leftHandExpression + "=" + getSecurityExpression(rightHandExpression, "I") + " or LPC;";
			parent.stsHelper.addTransition(initialLocation, finalLocation, STS.TAU, "true", update);
		}
		return finalLocation;
	}
	
	private Integer processMethodInvocation(Expression expression, Integer initialLocation) throws Exception {
		Integer finalLocation = initialLocation;
		if(!isThirdParty(expression)){
			finalLocation = nonThirdPartyMethodInvocation(expression, initialLocation);
		}else{
			finalLocation = thirdPartyMethodInvocation(expression, initialLocation);
		}
		return finalLocation;
	}

	private Integer thirdPartyMethodInvocation(Expression expression, Integer initialLocation) {
		Integer finalLocation = parent.newLocation();
		String action = parent.stsHelper.addAction(javaFileHelper.getQualifiedName(expression));
		parent.stsHelper.addTransition(initialLocation, finalLocation, action, "true", "");
		return finalLocation;
	}

	private Integer nonThirdPartyMethodInvocation(Expression expression, Integer initialLocation) throws Exception {
		boolean newContext = false;
		if(expression instanceof MethodInvocation){
			Expression methodExpression = ((MethodInvocation)expression).getExpression();
			if(methodExpression!=null){
				parent.thisContext.push(parent.getUniqueName((SimpleName) methodExpression));
				newContext = true;
			}
		}else if(expression instanceof ClassInstanceCreation){
			if(expression.getParent() instanceof VariableDeclarationFragment){
				parent.thisContext.push(parent.getUniqueName(((VariableDeclarationFragment)expression.getParent()).getName()));
				newContext = true;
			}else{
				throw new Exception("The code is not in the normal form.");
			}
		}
		List<SimpleName> parameters = javaFileHelper.getMethodParameters(expression);
		for (SimpleName parameter : parameters) {
			parent.enterScope();
			parent.saveLocalVariableDeclarationScope(parameter);
			parent.revertToLastScope();
		}
		String methodArgumentsUpdates = processMethodArguments(expression);
		Integer intermediateLocation = parent.newLocation();
		parent.stsHelper.addTransition(initialLocation, intermediateLocation, STS.TAU, "true", methodArgumentsUpdates);
		Integer finalLocation = parent.newLocation();
		String action = parent.stsHelper.addAction(javaFileHelper.getQualifiedName(expression));
		Hashtable<SimpleName, String> renamingRuleSet = parent.renamingRuleSets.peek();
		Hashtable<String, Object> argumentParameterMap = new Hashtable<>();
		for (SimpleName parameter : parameters) {
			if(renamingRuleSet.contains(parameter)){
				argumentParameterMap.put(parent.getUniqueName(parameter), renamingRuleSet.get(parameter));
			}else{
				parent.enterScope();
				argumentParameterMap.put(parent.getUniqueName(parameter), parent.getUniqueName(parameter));
				parent.revertToLastScope();
			}
		}
		argumentParameterMap.put("@status", "");
		parent.stsHelper.addTransition(intermediateLocation, finalLocation, action, "true", "", argumentParameterMap);
		finalLocation = processBlock(javaFileHelper.getMethodBody(expression), finalLocation);
		if(newContext){
			parent.thisContext.pop();
		}
		return finalLocation;
	}
	
	@SuppressWarnings("unchecked")
	private Integer processBlock(Block block, Integer initialLocation) throws Exception {
		parent.enterScope();
		List<Statement> statements = block.statements();
		Integer finalLocation = initialLocation;
		for (Statement statement : statements) {
			finalLocation = processStatement(statement, finalLocation);
		}
		parent.exitScope();
		return finalLocation;
	}
	
	private Integer processWhileStatement(WhileStatement whileStatement, Integer initialLocation) throws Exception {
		String whileExpression = parent.rename(whileStatement.getExpression());
		Integer loopEntranceLocation = parent.newLocation();
		String lpcStackVariable = parent.getLPCUniqueName();
		String lpcUpdate = lpcStackVariable + "=LPC;" + "LPC=" + getSecurityExpression(whileExpression, "I") + " or LPC;";
		parent.stsHelper.addTransition(initialLocation, loopEntranceLocation, STS.TAU, whileExpression, lpcUpdate);
		Integer finalLocationInLoop = processStatement(whileStatement.getBody(), loopEntranceLocation);
		parent.stsHelper.addTransition(finalLocationInLoop, initialLocation, STS.TAU, "true", "LPC=" + lpcStackVariable + ";");
		Integer loopExitLocation = parent.newLocation();
		parent.stsHelper.addTransition(initialLocation, loopExitLocation, STS.TAU, "  not (" + whileExpression + ")", 
				lpcUpdate + getSecurityExpressionForPossibleModifieds(whileStatement.getBody()));
		Integer finalLocation = parent.newLocation();
		parent.stsHelper.addTransition(loopExitLocation, finalLocation, STS.TAU, "true", "LPC=" + lpcStackVariable + ";");
		return finalLocation;
	}

	private Integer processIfStatement(IfStatement ifStatement, Integer initialLocation) throws Exception {
		String ifExpression = parent.rename(ifStatement.getExpression());
		Integer thenLocation = parent.newLocation();
		String lpcStackVariable = parent.getLPCUniqueName();
		String lpcUpdate = lpcStackVariable	+ "=LPC;" + "LPC=" + getSecurityExpression(ifExpression, "I") + " or LPC;";
		parent.stsHelper.addTransition(initialLocation, thenLocation, STS.TAU, ifExpression, 
				lpcUpdate + getSecurityExpressionForPossibleModifieds(ifStatement.getElseStatement()));
		Integer elseLocation = parent.newLocation();
		parent.stsHelper.addTransition(initialLocation, elseLocation, STS.TAU, "not (" + ifExpression + ")", 
				lpcUpdate + getSecurityExpressionForPossibleModifieds(ifStatement.getThenStatement()));
		Integer finalThenLocation = processStatement(ifStatement.getThenStatement(), thenLocation);
		Integer finalElseLocation = elseLocation;
		if(ifStatement.getElseStatement()!=null){
			finalElseLocation = processStatement(ifStatement.getElseStatement(), elseLocation);
		}
		Integer finalLocation = parent.newLocation();
		parent.stsHelper.addTransition(finalThenLocation, finalLocation, STS.TAU, "true", "LPC=" + lpcStackVariable + ";");
		parent.stsHelper.addTransition(finalElseLocation, finalLocation, STS.TAU, "true", "LPC=" + lpcStackVariable + ";");
		return finalLocation;
	}
	
	String getSecurityExpression(String expression, String policyType){
		String securityExpression = "";
		String[] parts = expression.replaceAll(" ", "").split("[^\\w_$\"]+");
		String operator = "";
		for (String part : parts) {
			part = part.replaceAll("\\s", "");
			if(!part.equals("")){
				if(part.matches("\".*\"") || part.equals("true") || part.equals("false") || part.matches("[-+]?\\d*\\.?\\d+")){
					securityExpression += operator + "false ";
				}else {
					securityExpression += operator + "L" + policyType + part;
				}
				operator = " or ";
			}
		}
		return securityExpression;		
	}
	
	static String possibleModifiedsSecurityAssignments;
	String getSecurityExpressionForPossibleModifieds(Statement statement){
		possibleModifiedsSecurityAssignments = "";
		if(statement!=null){
			statement.accept(new ASTVisitor() {
				@Override
				public boolean visit(Assignment assignment) {
					possibleModifiedsSecurityAssignments += "LI" + parent.getUniqueName((Name) assignment.getLeftHandSide())
							+ "=" + "LI" + parent.getUniqueName((Name) assignment.getLeftHandSide()) + " or LPC;";
					return true;
				}
			});
		}
		return possibleModifiedsSecurityAssignments;
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
			parent.enterScope();
			String parameterUniqueName = parent.getUniqueName(parameters.get(i)).replaceAll("\\s", "");
			parent.revertToLastScope();
			String renamedArgument = parent.rename(arguments.get(i));
			if(!renamingRuleSet.containsKey(parameters.get(i).toString())){
				argumentAssignments += parameterUniqueName + "=" + renamedArgument + ";";
			}
			argumentAssignments += "LX" + parameterUniqueName + "=" + getSecurityExpression(renamedArgument, "X") + ";";
			argumentAssignments += "LI" + parameterUniqueName + "=" + getSecurityExpression(renamedArgument, "I") + " or LPC;";
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
