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
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

public class JavaExpressionNormalizer {
	ASTHelper astHelper;
	int index;
	
	public JavaExpressionNormalizer(ASTHelper astHelper) {
		this.astHelper = astHelper;
	}
	
	public List<ASTNode> normalize(Expression expression){
		index = 0;
		Stack<Object[]> stack = new Stack<>();
		if(expression instanceof VariableDeclarationExpression){
			for (Object fragment : ((VariableDeclarationExpression)expression).fragments()) {
				stack.push(new Object[]{((VariableDeclarationFragment) fragment).getName(), ((VariableDeclarationFragment) fragment).getInitializer()});
			}
		}else if(expression instanceof Assignment){
			stack.push(new Object[]{((Assignment)expression).getRightHandSide().toString(), ((Assignment)expression).getRightHandSide()});
		}else{
			stack.push(new Object[]{"__X0", expression});
		}
		ArrayList<ASTNode> normalizedStatements = new ArrayList<>();
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
			}
		}
		if(!(expression instanceof VariableDeclarationExpression) && !(expression instanceof Assignment)){
			Assignment assignment = (Assignment) normalizedStatements.get(0);
			normalizedStatements.set(0, assignment.getRightHandSide());
		}
		return normalizedStatements;
	}
	
	private ASTNode normalizeMethodInvocation(Object[] indexAndExpression, Stack<Object[]> stack){
		MethodInvocation methodInvocation = (MethodInvocation) indexAndExpression[1];
		String generatedAssignment = astHelper.getExpressionTypeName(methodInvocation) 
				+ indexAndExpression[0] + " = ";
		generatedAssignment += ((methodInvocation.getExpression()==null)?"":methodInvocation.getExpression().toString() + ".") + methodInvocation.getName() + "("; 
		String separator = "";
		for (Object argument : methodInvocation.arguments()) {
			if(isCompound((Expression) argument)){
				index++;
				stack.push(new Object[]{"__X"+index, argument});
				generatedAssignment += separator + "__X" + index;
			}else{
				generatedAssignment += separator + argument.toString();
			}
			separator = ", ";
		}
		generatedAssignment += ")";
		generatedAssignment += ";";
		return astHelper.parse(generatedAssignment, ASTParser.K_STATEMENTS);
	}

	private ASTNode normalizePostfixExpression(Object[] indexAndExpression, Stack<Object[]> stack){
		PostfixExpression postfixExpression = (PostfixExpression) indexAndExpression[1];
		String generatedAssignment = astHelper.getExpressionTypeName(postfixExpression) 
				+ indexAndExpression[0] + " = ";
		Expression operand = postfixExpression.getOperand();
		if(isCompound(operand)){
			index++;
			stack.push(new Object[]{"__X"+index, operand});
			generatedAssignment += "__X" + index;
		}else{
			generatedAssignment += operand.toString();
		}
		generatedAssignment += " " + postfixExpression.getOperator().toString();
		generatedAssignment += ";";
		return astHelper.parse(generatedAssignment, ASTParser.K_STATEMENTS);
	}
	
	private ASTNode normalizePrefixExpression(Object[] indexAndExpression, Stack<Object[]> stack){
		PrefixExpression prefixExpression = (PrefixExpression) indexAndExpression[1];
		String generatedAssignment = astHelper.getExpressionTypeName(prefixExpression) 
				+ indexAndExpression[0] + " = ";
		Expression operand = prefixExpression.getOperand();
		generatedAssignment += prefixExpression.getOperator().toString() + " ";
		if(isCompound(operand)){
			index++;
			stack.push(new Object[]{"__X"+index, operand});
			generatedAssignment += "__X" + index;
		}else{
			generatedAssignment += operand.toString();
		}
		generatedAssignment += ";";
		return astHelper.parse(generatedAssignment, ASTParser.K_STATEMENTS);
	}
	
	private ASTNode normalizeInfixExpresion(Object[] indexAndExpression, Stack<Object[]> stack){
		InfixExpression infixExpression = (InfixExpression) indexAndExpression[1];
		String generatedAssignment = astHelper.getExpressionTypeName(infixExpression) 
				+ indexAndExpression[0] + " = ";
		Expression leftOperand = infixExpression.getLeftOperand();
		if(isCompound(leftOperand)){
			index++;
			stack.push(new Object[]{"__X"+index, leftOperand});
			generatedAssignment += "__X" + index;
		}else{
			generatedAssignment += leftOperand.toString();
		}
		generatedAssignment += " " + infixExpression.getOperator().toString() + " ";
		Expression rightOperand = infixExpression.getRightOperand();
		if(isCompound(rightOperand)){
			index++;
			stack.push(new Object[]{"__X"+index, rightOperand});
			generatedAssignment += "__X" + index;
		}else{
			generatedAssignment += rightOperand.toString();
		}
		generatedAssignment += ";";
		return astHelper.parse(generatedAssignment, ASTParser.K_STATEMENTS);
	}
	
	private boolean isCompound(Expression operand) {
		return operand instanceof InfixExpression || operand instanceof PrefixExpression ||
				operand instanceof PostfixExpression || operand instanceof MethodInvocation ||
				operand instanceof ParenthesizedExpression;
	}
}
