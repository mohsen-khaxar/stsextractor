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
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
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

	public void extract() {
		CompilationUnit compilationUnit = javaFileHelper.getCompilationUnit();
		MethodDeclaration[] methods = ((TypeDeclaration)compilationUnit.types().get(0)).getMethods();
		for (MethodDeclaration methodDeclaration : methods) {
			String qualifiedMethodName = javaFileHelper.getQualifiedName(methodDeclaration);
			String methodModifier = methodDeclaration.modifiers().toString().toLowerCase();
			if (parent.isEntryPoint(qualifiedMethodName) 
					&& methodDeclaration.getBody() != null 
					&& (methodModifier.contains("public") || methodModifier.contains("protected"))) {
				Integer finalLocation = processMethod(methodDeclaration);
				Transition transition = new Transition(Transition.TAU, "true", "");
				parent.sts.addEdge(finalLocation, 2, transition);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Integer processMethod(MethodDeclaration methodDeclaration) {
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
	
	private Integer processStatement(Statement statement, Integer initialLocation) {
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
			finalLocation = processIfStatement(ifStatement, initialLocation);
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
	private Integer processIfStatement(IfStatement ifStatement, Integer initialLocation) {
		Integer blockEnterLocation = initialLocation;
		String ifExpression = rename(ifStatement.getExpression(), RS, prefix);
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
//		Transition transition2 = new Transition(Transition.TAU, "true", "");
//		Integer preFinalElseLocation = newLocation();
		finalLocation = newLocation();
		sts.addVertex(finalLocation);
//		sts.addVertex(finalThenLocation);
//		sts.addVertex(preFinalElseLocation);
		sts.addVertex(finalElseLocation);
		if(!breakContinueLocations.containsKey(finalThenLocation)){
			Transition transition1 = new Transition(Transition.TAU, "true", getSecurityAssignments(SL) + slpc);
			sts.addEdge(finalThenLocation, finalLocation, transition1);
			updateStsSecurityLabelling(finalThenLocation, finalLocation);
		}
		if(!breakContinueLocations.containsKey(finalElseLocation)){
			Transition transition2 = new Transition(Transition.TAU, "true", getSecurityAssignments(SL) + slpc);
			sts.addEdge(finalElseLocation, finalLocation, transition2);
			updateStsSecurityLabelling(finalElseLocation, finalLocation);
		}
//		sts.addEdge(preFinalElseLocation, finalLocation, transition3);
//		updateStsSecurityLabelling(preFinalElseLocation, finalLocation);
		blocks.put(blockEnterLocation, finalLocation);
		return finalLocation;
	}
}
