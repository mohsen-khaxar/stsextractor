package se.lnu.prosses.securityMonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

public class JavaExpressionNormalizer {
	JavaFileHelper javaFileHelper;
	private String auxVariablePrefix;
	public int auxVariableIndex = 0;
	
	public JavaExpressionNormalizer(JavaFileHelper javaFileHelper, String auxVariablePrefix) {
		this.javaFileHelper = javaFileHelper;
		this.auxVariablePrefix = auxVariablePrefix;
	}
	
	public List<ASTNode> normalize(Expression expression){
		ArrayList<ASTNode> normalizedStatements = new ArrayList<>();
		Stack<Object[]> stack = new Stack<>();
		if(expression instanceof VariableDeclarationExpression){
			for (Object fragment : ((VariableDeclarationExpression)expression).fragments()) {
				stack.push(new Object[]{((VariableDeclarationFragment) fragment).getName(), ((VariableDeclarationFragment) fragment).getInitializer()});
			}
		}else if(expression instanceof Assignment){
			stack.push(new Object[]{((Assignment)expression).getLeftHandSide().toString(), ((Assignment)expression).getRightHandSide()});
		}else{
			stack.push(new Object[]{auxVariablePrefix+auxVariableIndex, expression});
		}
		while(!stack.isEmpty()){
			Object[] indexAndExpression = stack.pop();
			if(indexAndExpression[1] instanceof ParenthesizedExpression){
				ParenthesizedExpression parenthesizedExpression = (ParenthesizedExpression) indexAndExpression[1];
				stack.push(new Object[]{indexAndExpression[0], parenthesizedExpression.getExpression()});
			}else if(indexAndExpression[1] instanceof InfixExpression){
				ASTNode astNode = normalizeInfixExpresion(indexAndExpression, stack);
				normalizedStatements.add(astNode);
			}else if(indexAndExpression[1] instanceof PrefixExpression){
				ASTNode astNode = normalizePrefixExpression(indexAndExpression, stack);
				normalizedStatements.add(astNode);
			}else if(indexAndExpression[1] instanceof PostfixExpression){
				ASTNode astNode = normalizePostfixExpression(indexAndExpression, stack);
				normalizedStatements.add(astNode);
			}else if(indexAndExpression[1] instanceof MethodInvocation){
				ASTNode astNode = normalizeMethodInvocation(indexAndExpression, stack);
				normalizedStatements.add(astNode);
			}else {
				String assignmentCode = "";
				if(indexAndExpression[1] instanceof NullLiteral){
					assignmentCode = "Object " + indexAndExpression[0] + " = " + indexAndExpression[1].toString() + ";";
				}else{
					assignmentCode = javaFileHelper.getExpressionTypeName((Expression) indexAndExpression[1]) 
							+ " " + indexAndExpression[0] + " = " + indexAndExpression[1].toString() + ";";
				}
				normalizedStatements.add(javaFileHelper.parseStatement(assignmentCode));
			}
		}
		VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement) normalizedStatements.get(0);
		if(expression instanceof Assignment){
			ExpressionStatement expressionStatement = (ExpressionStatement) javaFileHelper.parseStatement(
					((Assignment)expression).getLeftHandSide().toString() + "=" 
					+ ((VariableDeclarationFragment)variableDeclarationStatement.fragments().get(0)).getInitializer().toString() + ";");
			normalizedStatements.set(0, expressionStatement.getExpression());
		}else if(!(expression instanceof Assignment)&&!(expression instanceof VariableDeclarationExpression)){
			normalizedStatements.set(0, ((VariableDeclarationFragment)variableDeclarationStatement.fragments().get(0)).getInitializer());
		}
		return normalizedStatements;
	}
	
	private ASTNode normalizeMethodInvocation(Object[] indexAndExpression, Stack<Object[]> stack){
		MethodInvocation methodInvocation = (MethodInvocation) indexAndExpression[1];
		String generatedAssignment = javaFileHelper.getExpressionTypeName(methodInvocation) 
				+ " " + indexAndExpression[0] + " = ";
		generatedAssignment += ((methodInvocation.getExpression()==null)?"":methodInvocation.getExpression().toString() + ".") + methodInvocation.getName() + "("; 
		String separator = "";
		for (Object argument : methodInvocation.arguments()) {
			if(!isNormalized((Expression) argument)){
				auxVariableIndex++;
				stack.push(new Object[]{auxVariablePrefix+auxVariableIndex, argument});
				generatedAssignment += separator + auxVariablePrefix+auxVariableIndex;
			}else{
				generatedAssignment += separator + argument.toString();
			}
			separator = ", ";
		}
		generatedAssignment += ")";
		generatedAssignment += ";";
		return javaFileHelper.parseStatement(generatedAssignment);
	}

	private ASTNode normalizePostfixExpression(Object[] indexAndExpression, Stack<Object[]> stack){
		PostfixExpression postfixExpression = (PostfixExpression) indexAndExpression[1];
		String generatedAssignment = javaFileHelper.getExpressionTypeName(postfixExpression) 
				+ " " + indexAndExpression[0] + " = ";
		Expression operand = postfixExpression.getOperand();
		if(!isNormalized(operand)){
			auxVariableIndex++;
			stack.push(new Object[]{auxVariablePrefix+auxVariableIndex, operand});
			generatedAssignment += auxVariablePrefix+auxVariableIndex;
		}else{
			generatedAssignment += operand.toString();
		}
		generatedAssignment += " " + postfixExpression.getOperator().toString();
		generatedAssignment += ";";
		return javaFileHelper.parseStatement(generatedAssignment);
	}
	
	private ASTNode normalizePrefixExpression(Object[] indexAndExpression, Stack<Object[]> stack){
		PrefixExpression prefixExpression = (PrefixExpression) indexAndExpression[1];
		String generatedAssignment = javaFileHelper.getExpressionTypeName(prefixExpression) 
				+ " " + indexAndExpression[0] + " = ";
		Expression operand = prefixExpression.getOperand();
		generatedAssignment += prefixExpression.getOperator().toString() + " ";
		if(!isNormalized(operand)){
			auxVariableIndex++;
			stack.push(new Object[]{auxVariablePrefix+auxVariableIndex, operand});
			generatedAssignment += auxVariablePrefix+auxVariableIndex;
		}else{
			generatedAssignment += operand.toString();
		}
		generatedAssignment += ";";
		return javaFileHelper.parseStatement(generatedAssignment);
	}
	
	private ASTNode normalizeInfixExpresion(Object[] indexAndExpression, Stack<Object[]> stack){
		InfixExpression infixExpression = (InfixExpression) indexAndExpression[1];
		String generatedAssignment = javaFileHelper.getExpressionTypeName(infixExpression) 
				+ " " + indexAndExpression[0] + " = ";
		Expression leftOperand = infixExpression.getLeftOperand();
		if(!isNormalized(leftOperand)){
			auxVariableIndex++;
			stack.push(new Object[]{auxVariablePrefix+auxVariableIndex, leftOperand});
			generatedAssignment += auxVariablePrefix+auxVariableIndex;
		}else{
			generatedAssignment += leftOperand.toString();
		}
		generatedAssignment += " " + infixExpression.getOperator().toString() + " ";
		Expression rightOperand = infixExpression.getRightOperand();
		if(!isNormalized(rightOperand)){
			auxVariableIndex++;
			stack.push(new Object[]{auxVariablePrefix+auxVariableIndex, rightOperand});
			generatedAssignment += auxVariablePrefix+auxVariableIndex;
		}else{
			generatedAssignment += rightOperand.toString();
		}
		generatedAssignment += ";";
		return javaFileHelper.parseStatement(generatedAssignment);
	}
	
//	private boolean isCompound(Expression operand) {
//		return operand instanceof InfixExpression || operand instanceof PrefixExpression ||
//				operand instanceof PostfixExpression || operand instanceof MethodInvocation ||
//				operand instanceof ParenthesizedExpression;
//	}
//	
	private boolean isNormalized(Expression expression){
		return !javaFileHelper.hasMethodInvokation(expression);
	}
}
