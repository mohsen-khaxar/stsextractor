package se.lnu.prosses.securityMonitor;

import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

public class JavaClassNormalizer {

	ASTHelper astHelper;
	JavaExpressionNormalizer javaExpressionNormalizer;
	String javaFilePath;
	String auxVariablePrefix = "__X";
	int auxVariableIndex = 0;
	
	public static void main(String[] args) throws Exception {
		String[] sourceDir = new String[]{"/home/mohsen/git/runningexample/src"};
		String[] classPath = new String[]{"/home/mohsen/git/runningexample/src", "/home/mohsen/git/stsextractor/src"};
		String javaFilePath = "/home/mohsen/git/runningexample/src/se/lnu/A.java";
		JavaClassNormalizer classNormalizer = new JavaClassNormalizer(sourceDir, classPath, javaFilePath);
		classNormalizer.normalize();
	}
	
	public JavaClassNormalizer(String[] sourceDir, String[] classPath, String javaFilePath) throws Exception {
		CommentProcessor.process(javaFilePath);
		astHelper = new ASTHelper(sourceDir, classPath, javaFilePath);
		javaExpressionNormalizer = new JavaExpressionNormalizer(astHelper);
		this.javaFilePath = javaFilePath;
	}
	
	public void normalize() throws Exception {
		CompilationUnit compilationUnit = astHelper.getCompilationUnit();
		MethodDeclaration[] methods = ((TypeDeclaration)compilationUnit.types().get(0)).getMethods();
		for (MethodDeclaration methodDeclaration : methods) {
			auxVariableIndex = 0;
			normalizeStatement(methodDeclaration.getBody());
		}
		astHelper.saveModifiedJavaFile();
	}
	

	private void normalizeStatement(Statement statement) throws Exception {
		switch(statement.getNodeType()){
		case ASTNode.VARIABLE_DECLARATION_STATEMENT:
			VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement) statement;
			normalizeVariabledeclarationStatement(variableDeclarationStatement);
			break;
		case ASTNode.EXPRESSION_STATEMENT:
			ExpressionStatement expressionStatement = (ExpressionStatement) statement;
			normalizeExpressionStatement(expressionStatement);
			break;
		case ASTNode.IF_STATEMENT:
			IfStatement ifStatement = (IfStatement) statement;
			normalizeIfStatement(ifStatement);
			break;
		case ASTNode.WHILE_STATEMENT:
			WhileStatement whileStatement = (WhileStatement) statement;
			normalizeWhileStatement(whileStatement);
			break;
		case ASTNode.DO_STATEMENT:
			DoStatement doStatement = (DoStatement) statement;
			normalizeDoStatement(doStatement);
			break;
		case ASTNode.FOR_STATEMENT:
			ForStatement forStatement = (ForStatement) statement;
			normalizeForStatement(forStatement);
			break;
		case ASTNode.ENHANCED_FOR_STATEMENT:
			EnhancedForStatement enhancedForStatement = (EnhancedForStatement) statement;
			normalizeEnhancedForStatement(enhancedForStatement);
			break;
		case ASTNode.SWITCH_STATEMENT:
			SwitchStatement switchStatement = (SwitchStatement) statement;
			normalizeSwitchStatement(switchStatement);
			break;
		case ASTNode.BLOCK:
			Block block = (Block) statement;
			for (Object blockStatement : block.statements()) {
				normalizeStatement((Statement) blockStatement);
			}
			break;
		default:
//			Nothing				
		}
	}

	private void normalizeExpressionStatement(ExpressionStatement expressionStatement) throws Exception {
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(expressionStatement.getExpression());	
		for (int i = normalizedExpression.size()-1; i>0; i--) {
			astHelper.insertStatementBefore(expressionStatement, normalizedExpression.get(i));
		}
		ASTNode normalizedAssignment = null;
		if(expressionStatement.getExpression() instanceof Assignment){
			normalizedAssignment = normalizedExpression.get(0);
		}else{
			normalizedAssignment = astHelper.parse(normalizedExpression.get(0).toString() + ";", ASTParser.K_STATEMENTS);
		}
		astHelper.insertExpressionInsteadOf(expressionStatement, normalizedAssignment);
	}

	private void normalizeVariabledeclarationStatement(VariableDeclarationStatement variableDeclarationStatement) throws Exception {
		for (Object fragment : variableDeclarationStatement.fragments()) {
			VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment)fragment;
			List<ASTNode> normalizedInitializer = javaExpressionNormalizer.normalize(variableDeclarationFragment.getInitializer());
			for (int i = normalizedInitializer.size()-1; i>0; i--) {
				astHelper.insertStatementBefore(variableDeclarationStatement, normalizedInitializer.get(i));
			}
			String assignmentCode = variableDeclarationStatement.getType().toString() + " " 
					+ variableDeclarationFragment.getName() + " = " + normalizedInitializer.get(0).toString() + ";";
			astHelper.insertStatementBefore(variableDeclarationStatement, astHelper.parse(assignmentCode, ASTParser.K_STATEMENTS));
		}
		astHelper.removeStatement(variableDeclarationStatement);
	}
	
	private void normalizeEnhancedForStatement(EnhancedForStatement enhancedForStatement) throws Exception {	
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(enhancedForStatement.getExpression());
		String enhancedForExpression = normalizedExpression.get(0).toString();
		for (int i = normalizedExpression.size()-1; i>0; i--) {
			astHelper.insertStatementBefore(enhancedForStatement, normalizedExpression.get(0));
		}
		normalizeStatement(enhancedForStatement.getBody());
		String loopParameter = enhancedForStatement.getParameter().getName().toString();
		String loopParameterType = enhancedForStatement.getParameter().getType().toString();
		String normalizedWhileCode = "";
		String auxVariable = auxVariablePrefix + auxVariableIndex;
		astHelper.insertStatementBefore(enhancedForStatement, astHelper.parse("int " + auxVariable + " = 0;", ASTParser.K_STATEMENTS));
		if(astHelper.getExpressionType(enhancedForStatement.getExpression()).isArray()){
			normalizedWhileCode = "while(" + auxVariable + "<(" + enhancedForExpression + ").length){" + loopParameterType + " " + loopParameter 
					+ " = (" + enhancedForExpression + ")[" + auxVariable + "];" + auxVariable + "++;" + enhancedForStatement.getBody().toString() + "}"; 
		}else{
			normalizedWhileCode = "while(" + auxVariable + "<(" + enhancedForExpression + ").size()){" + loopParameterType + " " + loopParameter 
					+ " = (" + enhancedForExpression + ").get(" + auxVariable + ");" + auxVariable + "++;" + enhancedForStatement.getBody().toString() + "}"; 
		}
		astHelper.insertExpressionInsteadOf(enhancedForStatement, astHelper.parse(normalizedWhileCode, ASTParser.K_STATEMENTS));
	}

	private void normalizeSwitchStatement(SwitchStatement switchStatement) throws Exception {
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(switchStatement.getExpression());
		String switchVariable = normalizedExpression.get(0).toString();
		for (int i = normalizedExpression.size()-1; i>0; i--) {
			astHelper.insertStatementBefore(switchStatement, normalizedExpression.get(i));
		}
		@SuppressWarnings("unchecked")
		List<Statement> switchStatements = switchStatement.statements();
		boolean firstSwitchCase = true;
		String normalizedIfCode = "";
		for (int i = 0; i < switchStatements.size(); i++) {
			if(switchStatements.get(i).getNodeType()==ASTNode.SWITCH_CASE){
				if(firstSwitchCase){
					normalizedIfCode += "if (" + switchVariable + "==" + ((SwitchCase)switchStatements.get(i)).getExpression() + "){";
					firstSwitchCase = false;
				}else{
					if(((SwitchCase)switchStatements.get(i)).getExpression()==null){
						normalizedIfCode += " else {";
					}else{
						normalizedIfCode += " else if (" + switchVariable + "==" + ((SwitchCase)switchStatements.get(i)).getExpression() + "){";
					}
				}
				String blockCode = "{";
				for (int j = i+1; j < switchStatements.size(); j++) {
					if(switchStatements.get(j).getNodeType()!=ASTNode.BREAK_STATEMENT){
						blockCode += switchStatements.get(j).toString();
					}else{
						break;
					}
				}
				blockCode += "}";
				normalizedIfCode += astHelper.parse(blockCode, ASTParser.K_STATEMENTS).toString() + "}";
			}
		}
		astHelper.insertExpressionInsteadOf(switchStatement, astHelper.parse(normalizedIfCode, ASTParser.K_STATEMENTS));
	}

	@SuppressWarnings("unchecked")
	private void normalizeForStatement(ForStatement forStatement) throws Exception {
		List<VariableDeclarationExpression> initializers = forStatement.initializers();
		for (VariableDeclarationExpression initializer : initializers) {
			List<ASTNode> normalizedInitializer = javaExpressionNormalizer.normalize(initializer);
			for (int i = normalizedInitializer.size()-1; i>=0; i--) {
				astHelper.insertStatementBefore(forStatement, normalizedInitializer.get(i));
			}
		}
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(forStatement.getExpression());
		for (int i = normalizedExpression.size()-1; i>0; i--) {
			astHelper.insertStatementBefore(forStatement, normalizedExpression.get(i));
		}
		normalizeStatement(forStatement.getBody());
		String whileCode = "while(" + normalizedExpression.get(0).toString() + "){"
				+ forStatement.getBody().toString();
		List<Expression> updaters = forStatement.updaters();
		for (Expression updater : updaters) {
			List<ASTNode> normalizedUpdater = javaExpressionNormalizer.normalize(updater);
			for (ASTNode astNode : normalizedUpdater) {
				whileCode += astNode + ";";
			}
		}
		whileCode += "}";
		astHelper.insertExpressionInsteadOf(forStatement, astHelper.parse(whileCode, ASTParser.K_STATEMENTS));
	}
	
	private void normalizeDoStatement(DoStatement doStatement) throws Exception {
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(doStatement.getExpression());
		Block body = (Block) doStatement.getBody();
		normalizeStatement(body);
		astHelper.insertStatementBefore(doStatement, body);
		for (int i=normalizedExpression.size()-1; i>0; i--) {
			astHelper.insertStatementBefore(doStatement, normalizedExpression.get(i));
		}
		String normalizedWhileCode = "while (" + normalizedExpression.get(0).toString() + "){" + body.toString();
		for (int i=normalizedExpression.size()-1; i>0; i--) {
			VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement) normalizedExpression.get(i); 
			VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment)variableDeclarationStatement.fragments().get(0);
			normalizedWhileCode += variableDeclarationFragment.getName() + " = " + variableDeclarationFragment.getInitializer() + ";";
		}
		normalizedWhileCode += "}";
		WhileStatement whileStatement = (WhileStatement) astHelper.parse(normalizedWhileCode, ASTParser.K_STATEMENTS);
		astHelper.insertExpressionInsteadOf(doStatement, whileStatement);
	}

	private void normalizeWhileStatement(WhileStatement whileStatement) throws Exception {
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(whileStatement.getExpression());
		astHelper.insertExpressionInsteadOf(whileStatement.getExpression(), normalizedExpression.get(0));
		for (int i=normalizedExpression.size()-1; i>0; i--) {
			astHelper.insertStatementBefore(whileStatement, normalizedExpression.get(i));
		}
		normalizeStatement(whileStatement.getBody());
	}

	private void normalizeIfStatement(IfStatement ifStatement) throws Exception {
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(ifStatement.getExpression());
		astHelper.insertExpressionInsteadOf(ifStatement.getExpression(), normalizedExpression.get(0));
		for (int i=normalizedExpression.size()-1; i>0; i--) {
			astHelper.insertStatementBefore(ifStatement, normalizedExpression.get(i));
		}
		normalizeStatement(ifStatement.getThenStatement());
		if(ifStatement.getElseStatement()!=null){
			normalizeStatement(ifStatement.getThenStatement());
		}
	}
}
