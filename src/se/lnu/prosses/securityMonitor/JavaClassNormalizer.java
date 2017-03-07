package se.lnu.prosses.securityMonitor;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
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

	JavaFileHelper javaFileHelper;
	JavaExpressionNormalizer javaExpressionNormalizer;
	String auxVariablePrefix = "__X";
	
	public JavaClassNormalizer(String sourcePath, String[] classPath, String javaFilePath) throws Exception {
		CommentProcessor.process(javaFilePath);
		javaFileHelper = new JavaFileHelper(sourcePath, classPath, javaFilePath, null);
		javaFileHelper.load();
		javaExpressionNormalizer = new JavaExpressionNormalizer(javaFileHelper, auxVariablePrefix);
	}
	
	public JavaClassNormalizer(JavaFileHelper javaFileHelper) throws Exception {
		CommentProcessor.process(javaFileHelper.getJavaFilePath());
		this.javaFileHelper = javaFileHelper;
		javaFileHelper.load();
		javaExpressionNormalizer = new JavaExpressionNormalizer(javaFileHelper, auxVariablePrefix);
	}
	
	public void normalize() throws Exception {
		CompilationUnit compilationUnit = javaFileHelper.getCompilationUnit();
		MethodDeclaration[] methods = ((TypeDeclaration)compilationUnit.types().get(0)).getMethods();
		for (MethodDeclaration methodDeclaration : methods) {
			normalizeStatement(methodDeclaration.getBody());
		}
		javaFileHelper.saveModifiedJavaFile();
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
			normalizeBlock(block);
			break;
		default:
//			Nothing				
		}
	}

	private void normalizeExpressionStatement(ExpressionStatement expressionStatement) throws Exception {
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(expressionStatement.getExpression());	
		javaFileHelper.insertStatementsExceptFirstBefore(expressionStatement, normalizedExpression);
		javaFileHelper.insertExpressionInsteadOf(expressionStatement.getExpression(), normalizedExpression.get(0));
	}

	private void normalizeBlock(Block block) throws Exception {
		int oldAuxVariableIndex = javaExpressionNormalizer.auxVariableIndex;
		for (Object blockStatement : block.statements()) {
			normalizeStatement((Statement) blockStatement);
		}
		javaExpressionNormalizer.auxVariableIndex = oldAuxVariableIndex;
	}
	
	private void normalizeVariabledeclarationStatement(VariableDeclarationStatement variableDeclarationStatement) throws Exception {
		for (Object fragment : variableDeclarationStatement.fragments()) {
			VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment)fragment;
			List<ASTNode> normalizedInitializer = javaExpressionNormalizer.normalize(variableDeclarationFragment.getInitializer());
			javaFileHelper.insertStatementsExceptFirstBefore(variableDeclarationStatement, normalizedInitializer);
			String assignmentCode = variableDeclarationStatement.getType().toString() + " " 
					+ variableDeclarationFragment.getName() + " = " + normalizedInitializer.get(0).toString() + ";";
			javaFileHelper.insertStatementBefore(variableDeclarationStatement, javaFileHelper.parseStatement(assignmentCode));
		}
		javaFileHelper.removeStatement(variableDeclarationStatement);
	}
	
	private void normalizeEnhancedForStatement(EnhancedForStatement enhancedForStatement) throws Exception {	
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(enhancedForStatement.getExpression());
		String enhancedForExpression = normalizedExpression.get(0).toString();
		javaFileHelper.insertStatementsExceptFirstBefore(enhancedForStatement, normalizedExpression);
		int oldAuxVariableIndex = javaExpressionNormalizer.auxVariableIndex;
		normalizeStatement(enhancedForStatement.getBody());
		javaExpressionNormalizer.auxVariableIndex = oldAuxVariableIndex;
		String loopParameter = enhancedForStatement.getParameter().getName().toString();
		String loopParameterType = enhancedForStatement.getParameter().getType().toString();
		String normalizedWhileCode = "";
		String auxVariable = auxVariablePrefix + (++javaExpressionNormalizer.auxVariableIndex);
		javaFileHelper.insertStatementBefore(enhancedForStatement, javaFileHelper.parseStatement("int " + auxVariable + " = 0;"));
		if(javaFileHelper.getExpressionType(enhancedForStatement.getExpression()).isArray()){
			normalizedWhileCode = "while(" + auxVariable + "<(" + enhancedForExpression + ").length){" + loopParameterType + " " + loopParameter 
					+ " = (" + enhancedForExpression + ")[" + auxVariable + "];" + auxVariable + "++;" + javaFileHelper.getLatestVersionCode(enhancedForStatement.getBody()) 
					+ "se.lnu.DummyMethods.monitorablePoint();}"; 
		}else{
			normalizedWhileCode = "while(" + auxVariable + "<(" + enhancedForExpression + ").size()){" + loopParameterType + " " + loopParameter 
					+ " = (" + enhancedForExpression + ").get(" + auxVariable + ");" + auxVariable + "++;" + javaFileHelper.getLatestVersionCode(enhancedForStatement.getBody()) 
					+ "se.lnu.DummyMethods.monitorablePoint();}"; 
		}
		javaFileHelper.insertExpressionInsteadOf(enhancedForStatement, javaFileHelper.parseStatement(normalizedWhileCode));
	}

	private void normalizeSwitchStatement(SwitchStatement switchStatement) throws Exception {
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(switchStatement.getExpression());
		String switchVariable = normalizedExpression.get(0).toString();
		javaFileHelper.insertStatementsExceptFirstBefore(switchStatement, normalizedExpression);
		@SuppressWarnings("unchecked")
		List<Statement> switchStatements = switchStatement.statements();
		int oldAuxVariableIndex = javaExpressionNormalizer.auxVariableIndex;
		for (Statement statement : switchStatements) {
			normalizeStatement(statement);
		}
		javaExpressionNormalizer.auxVariableIndex = oldAuxVariableIndex;
		boolean firstSwitchCase = true;
		String normalizedIfCode = "";
		switchStatements = javaFileHelper.getLatestVersion(switchStatement);
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
				normalizedIfCode += javaFileHelper.parseStatement(blockCode).toString() + "}";
			}
		}
		javaFileHelper.insertExpressionInsteadOf(switchStatement, javaFileHelper.parseStatement(normalizedIfCode));
	}

	@SuppressWarnings("unchecked")
	private void normalizeForStatement(ForStatement forStatement) throws Exception {
//		normalize the initializers and paste its normalized statements before the statement for
		List<VariableDeclarationExpression> initializers = forStatement.initializers();
		for (VariableDeclarationExpression initializer : initializers) {
			List<ASTNode> normalizedInitializer = javaExpressionNormalizer.normalize(initializer);
			javaFileHelper.insertStatementsBefore(forStatement, normalizedInitializer);
		}
//		normalize the condition expression and paste its normalized statements before the statement for
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(forStatement.getExpression());
		javaFileHelper.insertStatementsExceptFirstBefore(forStatement, normalizedExpression);
//		normalize the body
		int oldAuxVariableIndex = javaExpressionNormalizer.auxVariableIndex;
		normalizeStatement(forStatement.getBody());
		javaExpressionNormalizer.auxVariableIndex = oldAuxVariableIndex;
//		generate the statement while and replace it with the statement for
		String whileCode = "while(" + normalizedExpression.get(0).toString() + "){"
				+ javaFileHelper.getLatestVersionCode(forStatement.getBody());
		List<Expression> updaters = forStatement.updaters();
		for (Expression updater : updaters) {
			List<ASTNode> normalizedUpdater = javaExpressionNormalizer.normalize(updater);
			for (ASTNode astNode : normalizedUpdater) {
				whileCode += astNode;
			}
			whileCode += ";";
		}
		whileCode += "se.lnu.DummyMethods.monitorablePoint();}";
		javaFileHelper.insertExpressionInsteadOf(forStatement, javaFileHelper.parseStatement(whileCode));
	}
	
	private void normalizeDoStatement(DoStatement doStatement) throws Exception {
//		normalize the condition expression
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(doStatement.getExpression());
		Block body = (Block) doStatement.getBody();
//		normalize the body
		int oldAuxVariableIndex = javaExpressionNormalizer.auxVariableIndex;
		normalizeStatement(body);
		javaExpressionNormalizer.auxVariableIndex = oldAuxVariableIndex;
//		paste one copy of the body and normalized statements that come from the condition expression before the extra while
		javaFileHelper.insertStatementBefore(doStatement, body);
		javaFileHelper.insertStatementsExceptFirstBefore(doStatement, normalizedExpression);
//		generate the extra while and replace it with the do statement
		String normalizedWhileCode = "while (" + normalizedExpression.get(0).toString() + "){" + javaFileHelper.getLatestVersionCode(body);
		for (int i=normalizedExpression.size()-1; i>0; i--) {
			VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement) normalizedExpression.get(i); 
			VariableDeclarationFragment variableDeclarationFragment = (VariableDeclarationFragment)variableDeclarationStatement.fragments().get(0);
			normalizedWhileCode += variableDeclarationFragment.getName() + " = " + variableDeclarationFragment.getInitializer() + ";";
		}
		normalizedWhileCode += "se.lnu.DummyMethods.monitorablePoint();}";
		WhileStatement whileStatement = (WhileStatement) javaFileHelper.parseStatement(normalizedWhileCode);
		javaFileHelper.insertExpressionInsteadOf(doStatement, whileStatement);
	}

	private void normalizeWhileStatement(WhileStatement whileStatement) throws Exception {
//		normalize the condition
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(whileStatement.getExpression());
		javaFileHelper.insertStatementsExceptFirstBefore(whileStatement, normalizedExpression);
		String whileCode = "while(" + normalizedExpression.get(0).toString() + ")";
//		normalize body
		int oldAuxVariableIndex = javaExpressionNormalizer.auxVariableIndex;
		normalizeStatement(whileStatement.getBody());
		javaExpressionNormalizer.auxVariableIndex = oldAuxVariableIndex;
//		inject a dummy monitorable point at after the last statement of the body
		@SuppressWarnings("rawtypes")
		List statements = ((Block)whileStatement.getBody()).statements();
		if(statements.size()>0){
			javaFileHelper.insertStatementAfter((Statement)statements.get(statements.size()-1), javaFileHelper.parseStatement("se.lnu.DummyMethods.monitorablePoint();"));
		}
		whileCode += javaFileHelper.getLatestVersionCode(whileStatement.getBody());
		javaFileHelper.insertStatementInsteadOf(whileStatement, javaFileHelper.parseStatement(whileCode));
	}

	private void normalizeIfStatement(IfStatement ifStatement) throws Exception {
//		normalize condition
		List<ASTNode> normalizedExpression = javaExpressionNormalizer.normalize(ifStatement.getExpression());
		javaFileHelper.insertStatementsExceptFirstBefore(ifStatement, normalizedExpression);
		String ifCode = "if(" + normalizedExpression.get(0).toString() + ")";
//		normalize then part
		int oldAuxVariableIndex = javaExpressionNormalizer.auxVariableIndex;
		normalizeStatement(ifStatement.getThenStatement());
		ifCode += javaFileHelper.getLatestVersionCode(ifStatement.getThenStatement());
		javaExpressionNormalizer.auxVariableIndex = oldAuxVariableIndex;
//		normalize else part
		if(ifStatement.getElseStatement()!=null){
			oldAuxVariableIndex = javaExpressionNormalizer.auxVariableIndex;
			normalizeStatement(ifStatement.getElseStatement());
			ifCode += "else" + javaFileHelper.getLatestVersionCode(ifStatement.getElseStatement());
			javaExpressionNormalizer.auxVariableIndex = oldAuxVariableIndex;
		}
		javaFileHelper.insertStatementInsteadOf(ifStatement, javaFileHelper.parseStatement(ifCode));
	}
}
