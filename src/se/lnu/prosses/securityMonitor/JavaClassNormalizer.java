package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import se.lnu.prosses.securityMonitor.Utils;

public class JavaClassNormalizer {

	ASTHelper astHelper;
	JavaExpressionNormalizer javaExpressionNormalizer;
	String javaFilePath;
	int variableCounter = 0;
	
	public static void main(String[] args) throws Exception {
	}
	
	public JavaClassNormalizer(String[] sourceDir, String[] classPath, String javaFilePath) {
		astHelper = new ASTHelper(sourceDir, classPath, javaFilePath);
		javaExpressionNormalizer = new JavaExpressionNormalizer(astHelper);
		this.javaFilePath = javaFilePath;
	}
	
	public void normalize() throws Exception {
		CommentProcessor.process(javaFilePath);
		CompilationUnit compilationUnit = astHelper.getCompilationUnit();
		MethodDeclaration[] methods = ((TypeDeclaration)compilationUnit.types().get(0)).getMethods();
		for (MethodDeclaration methodDeclaration : methods) {
			List<Statement> statements = normalizeStatement(methodDeclaration.getBody());
			String blockCode = "{";
			blockCode += "boolean __C = false; int __XL0 = 0; int __XL1 = 0;";
			for (Statement statement : statements) {
				blockCode += statement.toString();
			}
			blockCode += "}";
			Block block = (Block) ASTHelper.parse(blockCode , ASTParser.K_STATEMENTS);
			astRewrite.replace(methodDeclaration.getBody(), block, null);
		}
		astHelper.saveModifiedJavaFile();
	}
	

	@SuppressWarnings("unchecked")
	private List<Statement> normalizeStatement(Statement statement) {
		List<Statement> statements = new ArrayList<Statement>();
		String statementString = statement.toString();
		String normalizedCode = "";
		switch(statement.getNodeType()){
		case ASTNode.VARIABLE_DECLARATION_STATEMENT:
			VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement) statement;
			normalizedCode = variableDeclarationStatement.toString();
			List<VariableDeclarationFragment> fragments = variableDeclarationStatement.fragments();
			String normalizedFragments = "";
			for (VariableDeclarationFragment variableDeclarationFragment : fragments) {
				Expression variableDeclarationFragmentInitializer = variableDeclarationFragment.getInitializer();
				if(variableDeclarationFragmentInitializer!=null && !isNormalized(variableDeclarationFragmentInitializer)){
					String tempX = "__X" + variableCounter++;
					normalizedFragments += normalize(ASTParser.K_STATEMENTS, ASTHelper.getType(variableDeclarationFragmentInitializer.toString(), variableDeclarationFragmentInitializer) + " " + tempX + "=" + variableDeclarationFragmentInitializer.toString() + ";", variableDeclarationFragmentInitializer);
					normalizedCode = normalizedCode.replace(variableDeclarationFragmentInitializer.toString(), tempX);
				}
			} 
			normalizedCode = normalizedFragments + normalizedCode;
			break;
		case ASTNode.EXPRESSION_STATEMENT:
			normalizedCode = normalize(ASTParser.K_STATEMENTS, statementString, statement);
			break;
		case ASTNode.IF_STATEMENT:
			IfStatement ifStatement = (IfStatement) statement;
			normalizedCode = normalizeIfStatement(ifStatement);
			break;
		case ASTNode.WHILE_STATEMENT:
			WhileStatement whileStatement = (WhileStatement) statement;
			normalizedCode = normalizeWhileStatement(whileStatement);
			break;
		case ASTNode.DO_STATEMENT:
			DoStatement doStatement = (DoStatement) statement;
			normalizedCode = normalizeDoStatement(doStatement);
			break;
		case ASTNode.FOR_STATEMENT:
			ForStatement forStatement = (ForStatement) statement;
			normalizedCode = normalizeForStatement(forStatement);
			break;
		case ASTNode.ENHANCED_FOR_STATEMENT:
			EnhancedForStatement enhancedForStatement = (EnhancedForStatement) statement;
			normalizedCode = normalizeEnhancedForStatement(enhancedForStatement);
			break;
		case ASTNode.SWITCH_STATEMENT:
			SwitchStatement switchStatement = (SwitchStatement) statement;
			normalizedCode = normalizeSwitchStatement(switchStatement);
			break;
		case ASTNode.BLOCK:
			Block block = (Block) statement;
			for (Object blockStatement : block.statements()) {
				statements.addAll(normalizeStatement((Statement) blockStatement));
			}
			break;
		default:
			normalizedCode = statement.toString();				
		}
		if(statement.getNodeType()!=ASTNode.BLOCK){
			ASTParser parser = ASTParser.newParser(AST.JLS4);
			parser.setSource(normalizedCode.toCharArray());
			parser.setKind(ASTParser.K_STATEMENTS);
			Block block = (Block)parser.createAST(null);
			statements = block.statements();
		}
		return  statements;
	}
	
	private String normalizeEnhancedForStatement(EnhancedForStatement enhancedForStatement) {	
		String x = " __X" + (variableCounter++);
		String statementString = enhancedForStatement.getExpression().resolveTypeBinding().getQualifiedName() + x + " = " + enhancedForStatement.getExpression().toString() + ";";
		String normalizedEnhancedForExpression = normalize(ASTParser.K_STATEMENTS, statementString, enhancedForStatement);
		List<Statement> statements = normalizeStatement(enhancedForStatement.getBody());
		String normalizedEnhancedForBody = "";
		for ( Statement bodyStatement : statements) {
			normalizedEnhancedForBody += bodyStatement.toString();
		}
		String loopParameter = enhancedForStatement.getParameter().getName().toString();
		String loopParameterType = enhancedForStatement.getParameter().getType().toString();
		String normalizedCode = normalizedEnhancedForExpression 
				+ "if(" + x + ".getClass().isArray()) {__XL0 = " + x + ".length;} else {__XL0 = " + x + ".size();} __XL1 = 0;"
				+ " while(__XL1<__XL0) {if(" + x + ".getClass().isArray()) {" + loopParameterType + " " + loopParameter + " = " + x + "[__XL1];} else {" + loopParameter + " = " + x + ".iterator().next();}"
				+ normalizedEnhancedForBody
				+ " __XL1++;}";
		return normalizedCode;
	}

	private String normalizeSwitchStatement(SwitchStatement switchStatement) {
		String statementString = "__C = " + switchStatement.getExpression().toString() + ";";
		String normalizedCode = normalize(ASTParser.K_STATEMENTS, statementString, switchStatement);
		@SuppressWarnings("unchecked")
		List<Statement> switchStatements = switchStatement.statements();
		boolean firstSwitchCase = true;
		for (int i = 0; i < switchStatements.size(); i++) {
			if(switchStatements.get(i).getNodeType()==ASTNode.SWITCH_CASE){
				if(firstSwitchCase){
					normalizedCode += "if (__C==" + ((SwitchCase)switchStatements.get(i)).getExpression() + "){";
					firstSwitchCase = false;
				}else{
					if(((SwitchCase)switchStatements.get(i)).getExpression()==null){
						normalizedCode += " else {";
					}else{
						normalizedCode += " else if (__C==" + ((SwitchCase)switchStatements.get(i)).getExpression() + "){";
					}
				}
				for (int j = i+1; j < switchStatements.size(); j++) {
					if(switchStatements.get(j).getNodeType()!=ASTNode.BREAK_STATEMENT){
						List<Statement> statements = normalizeStatement(switchStatements.get(j));
						for (Statement statement : statements) {
							normalizedCode += statement.toString();
						}
					}else{
						break;
					}
				}
				normalizedCode += "}";
			}
		}
		return normalizedCode;
	}

	@SuppressWarnings("unchecked")
	private String normalizeForStatement(ForStatement forStatement) {
		List<Expression> initializerExpressions = forStatement.initializers();
		String normalizedForInitializers = "";
		String statementString = "";
		for (Expression expression : initializerExpressions) {
			statementString  = expression.toString() + ";";
			normalizedForInitializers += normalize(ASTParser.K_STATEMENTS, statementString, expression);
		}
		statementString = "__C = " + forStatement.getExpression().toString() + ";";
		String normalizedForExpression = normalize(ASTParser.K_STATEMENTS, statementString, forStatement);
		List<Statement> statements = normalizeStatement(forStatement.getBody());
		String normalizedForBody = "";
		for ( Statement bodyStatement : statements) {
			normalizedForBody += bodyStatement.toString();
		}
		List<Expression> updaterExpressions = forStatement.updaters();
		String normalizedForUpdaters = "";
		for (Expression expression : updaterExpressions) {
			statementString = expression.toString() + ";";
			normalizedForUpdaters += normalize(ASTParser.K_STATEMENTS, statementString, expression);
		}
		String normalizedCode = normalizedForInitializers + normalizedForExpression + "while(__C){" + normalizedForBody + normalizedForUpdaters + normalizedForExpression + "}";
		return normalizedCode;
	}
	
	private String normalizeDoStatement(DoStatement doStatement) {
		String statementString = "__C = " + doStatement.getExpression().toString() + ";";
		String normalizedDoExpression = normalize(ASTParser.K_STATEMENTS, statementString, doStatement);
		List<Statement> statements = normalizeStatement(doStatement.getBody());
		String normalizedDoBody = "";
		for ( Statement bodyStatement : statements) {
			normalizedDoBody += bodyStatement.toString();
		}
		String normalizedCode = normalizedDoBody + normalizedDoExpression + " while (__C) {" + normalizedDoBody + normalizedDoExpression + "}"; 
		return normalizedCode;
	}

	private String normalizeWhileStatement(WhileStatement whileStatement) {
		String statementString = "__C = " + whileStatement.getExpression().toString() + ";";
		String normalizedWhileExpression = normalize(ASTParser.K_STATEMENTS, statementString, whileStatement);
		List<Statement> statements = normalizeStatement(whileStatement.getBody());
		String normalizedWhileBody = "";
		for ( Statement bodyStatement : statements) {
			normalizedWhileBody += bodyStatement.toString();
		}
		String normalizedCode = normalizedWhileExpression + "while(__C){" + normalizedWhileBody + normalizedWhileExpression + "}";
		return normalizedCode;
	}

	private String normalizeIfStatement(IfStatement ifStatement) {
		String statementString = "__C = " + ifStatement.getExpression().toString() + ";";
		String normalizedIfExpression = normalize(ASTParser.K_STATEMENTS, statementString, ifStatement);
		statementString = ifStatement.getThenStatement().toString();
		List<Statement> statements = normalizeStatement(ifStatement.getThenStatement());
		String normalizedThenStatement = "";
		for ( Statement thenStatement : statements) {
			normalizedThenStatement += thenStatement.toString();
		}
		String normalizedCode = "";
		if(ifStatement.getElseStatement()!=null){
			statements = normalizeStatement(ifStatement.getElseStatement());
			String normalizedElseStatement = "";
			for ( Statement elseStatement : statements) {
				normalizedElseStatement += elseStatement.toString();
			}
			normalizedCode  = normalizedIfExpression + " if(__C){" + normalizedThenStatement + "}else{" + normalizedElseStatement + "}";
		}else{
			normalizedCode = normalizedIfExpression + " if(__C){" + normalizedThenStatement + "}";
		}
		return normalizedCode;
	}

	private boolean isNormalized(ASTNode node) {
		boolean normalized = false;
		if(node.getNodeType()==ASTNode.EXPRESSION_STATEMENT){
			node = ((ExpressionStatement)node).getExpression();
		}
		if (node.getNodeType() == ASTNode.ASSIGNMENT && isNormalized(((Assignment) node).getRightHandSide())
				&& isNormalized(((Assignment) node).getLeftHandSide())) {
			normalized = true;
		} else if (node.getNodeType() == ASTNode.METHOD_INVOCATION) {
			boolean allArgsNormalized = true;
			for (Object argument : ((MethodInvocation) node).arguments()) {
				if (((Expression) argument).getNodeType() != ASTNode.SIMPLE_NAME
						&& ((Expression) argument).getNodeType() != ASTNode.QUALIFIED_NAME) {
					allArgsNormalized = false;
					break;
				}
			}
			normalized = allArgsNormalized;
		} else if (!ASTHelper.hasMethodInvokation(node)) {
			normalized = true;
		}
		if(node.getNodeType()==ASTNode.VARIABLE_DECLARATION_STATEMENT&&((VariableDeclarationStatement)node).fragments().size()==1
				&&isNormalized(((VariableDeclarationFragment)((VariableDeclarationStatement)node).fragments().get(0)).getInitializer())){
			normalized = true;
		}
		return normalized;
	}
}
