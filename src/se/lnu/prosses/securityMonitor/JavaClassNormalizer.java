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
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

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
		ASTNode insertionPoint = null;
		if(expressionStatement.getExpression() instanceof Assignment){
			insertionPoint = normalizedExpression.get(0);
		}else{
			insertionPoint = astHelper.parse(normalizedExpression.get(0).toString() + ";", ASTParser.K_STATEMENTS);
		}
		astHelper.insertExpressionInsteadOf(expressionStatement, insertionPoint);
		normalizedExpression.remove(0);
		for (ASTNode astNode : normalizedExpression) {
			astHelper.insertStatementBefore(insertionPoint, astNode);
			insertionPoint = astNode;
		}
	}

	private void normalizeVariabledeclarationStatement(VariableDeclarationStatement variableDeclarationStatement) throws Exception {
		VariableDeclarationExpression variableDeclarationExpression = 
				(VariableDeclarationExpression) astHelper.parse(variableDeclarationStatement.toString().replace(";", ""), ASTParser.K_EXPRESSION);
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(variableDeclarationExpression);
		ASTNode insertionPoint = variableDeclarationStatement;
		for (ASTNode astNode : normalizedExpression) {
			astHelper.insertStatementBefore(insertionPoint, astNode);
			insertionPoint = astNode;
		}
		astHelper.removeStatement(variableDeclarationStatement);
	}
	
	private void normalizeEnhancedForStatement(EnhancedForStatement enhancedForStatement) throws Exception {	
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(enhancedForStatement.getExpression());
		String enhancedForExpression = normalizedExpression.get(0).toString();
		normalizedExpression.remove(0);
		ASTNode insertionPoint = enhancedForStatement;
		for (ASTNode astNode : normalizedExpression) {
			astHelper.insertStatementBefore(insertionPoint, astNode);
			insertionPoint = astNode;
		}
		normalizeStatement(enhancedForStatement.getBody());
		String loopParameter = enhancedForStatement.getParameter().getName().toString();
		String loopParameterType = enhancedForStatement.getParameter().getType().toString();
		String normalizedWhileCode = "";
		String x = "__X0";
		if(astHelper.getExpressionType(enhancedForStatement.getExpression()).isArray()){
			normalizedWhileCode = "while(" + x + "<(" + enhancedForExpression + ").length){" + loopParameterType + " " + loopParameter 
					+ " = (" + enhancedForExpression + ")[" + x + "];" + x + "++;" + enhancedForStatement.getBody().toString() + "}"; 
		}else{
			normalizedWhileCode = "while(" + x + "<(" + enhancedForExpression + ").size()){" + loopParameterType + " " + loopParameter 
					+ " = (" + enhancedForExpression + ").get(" + x + ");" + x + "++;" + enhancedForStatement.getBody().toString() + "}"; 
		}
		astHelper.insertExpressionInsteadOf(enhancedForStatement, astHelper.parse(normalizedWhileCode, ASTParser.K_STATEMENTS));
	}

	private void normalizeSwitchStatement(SwitchStatement switchStatement) throws Exception {
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(switchStatement.getExpression());
		String switchVariable = normalizedExpression.get(0).toString();
		normalizedExpression.remove(0);
		ASTNode insertionPoint = switchStatement;
		for (ASTNode astNode : normalizedExpression) {
			astHelper.insertStatementBefore(insertionPoint, astNode);
			insertionPoint = astNode;
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
			ASTNode insertionPoint = forStatement;
			for (ASTNode astNode : normalizedInitializer) {
				astHelper.insertStatementBefore(insertionPoint, astNode);
				insertionPoint = astNode;
			}
		}
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(forStatement.getExpression());
		ASTNode insertionPoint = forStatement;
		for (ASTNode astNode : normalizedExpression) {
			astHelper.insertStatementBefore(insertionPoint, astNode);
			insertionPoint = astNode;
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
		whileCode = "}";
		astHelper.insertExpressionInsteadOf(forStatement, astHelper.parse(whileCode, ASTParser.K_STATEMENTS));
	}
	
	private void normalizeDoStatement(DoStatement doStatement) throws Exception {
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(doStatement.getExpression());
		Block body = (Block) doStatement.getBody();
		normalizeStatement(body);
		astHelper.insertStatementBefore(doStatement, body);
		ASTNode insertionPoint = body;
		for (int i=normalizedExpression.size()-1; i>0; i--) {
			astHelper.insertStatementAfter(insertionPoint, normalizedExpression.get(i));
			insertionPoint = normalizedExpression.get(i);
		}
		String normalizedWhileCode = "while (" + normalizedExpression.get(0).toString() + ")" + body.toString();
		WhileStatement whileStatement = (WhileStatement) astHelper.parse(normalizedWhileCode, ASTParser.K_STATEMENTS);
		List whileStatements = ((Block)whileStatement.getBody()).statements();
		insertionPoint = (ASTNode) whileStatements.get(whileStatements.size()-1);
		for (int i=normalizedExpression.size()-1; i>0; i--) {
			astHelper.insertStatementAfter(insertionPoint, normalizedExpression.get(i));
			insertionPoint = normalizedExpression.get(i);
		}
		astHelper.insertExpressionInsteadOf(doStatement, whileStatement);
	}

	private void normalizeWhileStatement(WhileStatement whileStatement) throws Exception {
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(whileStatement.getExpression());
		astHelper.insertExpressionInsteadOf(whileStatement.getExpression(), normalizedExpression.get(0));
		normalizedExpression.remove(0);
		ASTNode insertionPoint = whileStatement;
		for (ASTNode astNode : normalizedExpression) {
			astHelper.insertStatementBefore(insertionPoint, astNode);
			insertionPoint = astNode;
		}
		normalizeStatement(whileStatement.getBody());
	}

	private void normalizeIfStatement(IfStatement ifStatement) throws Exception {
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(ifStatement.getExpression());
		astHelper.insertExpressionInsteadOf(ifStatement.getExpression(), normalizedExpression.get(0));
		normalizedExpression.remove(0);
		ASTNode insertionPoint = ifStatement;
		for (ASTNode astNode : normalizedExpression) {
			astHelper.insertStatementBefore(insertionPoint, astNode);
			insertionPoint = astNode;
		}
		normalizeStatement(ifStatement.getThenStatement());
		if(ifStatement.getElseStatement()!=null){
			normalizeStatement(ifStatement.getThenStatement());
		}
	}
}
