package se.lnu.prosses.securityMonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;

public class JavaExpressionNormalizer {
	ASTHelper astHelper;
	
	public JavaExpressionNormalizer(ASTHelper astHelper) {
		this.astHelper = astHelper;
	}
	
	public List<ASTNode> normalize(Expression expression){
		Stack<Object[]> stack = new Stack<>();
		int index = 0;
		stack.push(new Object[]{index, expression});
		ArrayList<ASTNode> normalizedStatements = new ArrayList<>();
		while(!stack.isEmpty()){
			Object[] indexAndExpression = stack.pop();
			if(indexAndExpression[1] instanceof ParenthesizedExpression){
				ParenthesizedExpression parenthesizedExpression = (ParenthesizedExpression) indexAndExpression[1];
				stack.push(new Object[]{indexAndExpression[0], parenthesizedExpression.getExpression()});
			}else if(indexAndExpression[1] instanceof InfixExpression){
				Object[] res = normalizeInfixExpresion(indexAndExpression, stack, index);
				index = (int) res[0];
				normalizedStatements.add((ASTNode) res[1]);
			}else if(indexAndExpression[1] instanceof PrefixExpression){
				Object[] res = normalizePrefixExpression(indexAndExpression, stack, index);
				index = (int) res[0];
				normalizedStatements.add((ASTNode) res[1]);
			}else if(indexAndExpression[1] instanceof PostfixExpression){
				Object[] res = normalizePostfixExpression(indexAndExpression, stack, index);
				index = (int) res[0];
				normalizedStatements.add((ASTNode) res[1]);
			}else if(indexAndExpression[1] instanceof MethodInvocation){
				Object[] res = normalizeMethodInvocation(indexAndExpression, stack, index);
				index = (int) res[0];
				normalizedStatements.add((ASTNode) res[1]);
			}
		}
		Assignment assignment = (Assignment) normalizedStatements.get(0);
		normalizedStatements.set(0, assignment.getRightHandSide());
		return normalizedStatements;
	}
	
	private Object[] normalizeMethodInvocation(Object[] indexAndExpression, Stack<Object[]> stack, Integer index){
		MethodInvocation methodInvocation = (MethodInvocation) indexAndExpression[1];
		String generatedAssignment = astHelper.getExpressionType(methodInvocation) 
				+ " __X" + indexAndExpression[0] + " = ";
		generatedAssignment += ((methodInvocation.getExpression()==null)?"":methodInvocation.getExpression().toString() + ".") + methodInvocation.getName() + "("; 
		String separator = "";
		for (Object argument : methodInvocation.arguments()) {
			if(isCompound((Expression) argument)){
				stack.push(new Object[]{++index, argument});
				generatedAssignment += separator + "__X" + index;
			}else{
				generatedAssignment += separator + argument.toString();
			}
			separator = ", ";
		}
		generatedAssignment += ")";
		generatedAssignment += ";";
		return new Object[]{index, astHelper.parse(generatedAssignment, ASTParser.K_STATEMENTS)};
	}

	private Object[] normalizePostfixExpression(Object[] indexAndExpression, Stack<Object[]> stack, Integer index){
		PostfixExpression postfixExpression = (PostfixExpression) indexAndExpression[1];
		String generatedAssignment = astHelper.getExpressionType(postfixExpression) 
				+ " __X" + indexAndExpression[0] + " = ";
		Expression operand = postfixExpression.getOperand();
		if(isCompound(operand)){
			stack.push(new Object[]{++index, operand});
			generatedAssignment += "__X" + index;
		}else{
			generatedAssignment += operand.toString();
		}
		generatedAssignment += " " + postfixExpression.getOperator().toString();
		generatedAssignment += ";";
		return new Object[]{index, astHelper.parse(generatedAssignment, ASTParser.K_STATEMENTS)};
	}
	
	private Object[] normalizePrefixExpression(Object[] indexAndExpression, Stack<Object[]> stack, Integer index){
		PrefixExpression prefixExpression = (PrefixExpression) indexAndExpression[1];
		String generatedAssignment = astHelper.getExpressionType(prefixExpression) 
				+ " __X" + indexAndExpression[0] + " = ";
		Expression operand = prefixExpression.getOperand();
		generatedAssignment += prefixExpression.getOperator().toString() + " ";
		if(isCompound(operand)){
			stack.push(new Object[]{++index, operand});
			generatedAssignment += "__X" + index;
		}else{
			generatedAssignment += operand.toString();
		}
		generatedAssignment += ";";
		return new Object[]{index, astHelper.parse(generatedAssignment, ASTParser.K_STATEMENTS)};
	}
	
	private Object[] normalizeInfixExpresion(Object[] indexAndExpression, Stack<Object[]> stack, Integer index){
		InfixExpression infixExpression = (InfixExpression) indexAndExpression[1];
		String generatedAssignment = astHelper.getExpressionType(infixExpression) 
				+ " __X" + indexAndExpression[0] + " = ";
		Expression leftOperand = infixExpression.getLeftOperand();
		if(isCompound(leftOperand)){
			stack.push(new Object[]{++index, leftOperand});
			generatedAssignment += "__X" + index;
		}else{
			generatedAssignment += leftOperand.toString();
		}
		generatedAssignment += " " + infixExpression.getOperator().toString() + " ";
		Expression rightOperand = infixExpression.getRightOperand();
		if(isCompound(rightOperand)){
			stack.push(new Object[]{++index, rightOperand});
			generatedAssignment += "__X" + index;
		}else{
			generatedAssignment += rightOperand.toString();
		}
		generatedAssignment += ";";
		return new Object[]{index, astHelper.parse(generatedAssignment, ASTParser.K_STATEMENTS)};
	}
	
	private boolean isCompound(Expression operand) {
		return operand instanceof InfixExpression || operand instanceof PrefixExpression ||
				operand instanceof PostfixExpression || operand instanceof MethodInvocation ||
				operand instanceof ParenthesizedExpression;
	}
}
