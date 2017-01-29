package se.lnu.prosses.generation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.ForeachStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntryStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.model.typesystem.Type;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import se.lnu.prosses.securityMonitor.Utils;

public class CodeNormalizer {
	int variableCounter = 0;
	
	private CombinedTypeSolver combinedTypeSolver;
	
	public CodeNormalizer(ArrayList<String> sourceDirs, ArrayList<String> classPathes) throws Exception {
		combinedTypeSolver = getTypeSolver(sourceDirs, classPathes);
	}
	
	BlockStmt normalizeExpression(Expression expression){
		BlockStmt block = new BlockStmt();
		Integer[] counter = {0};
		return normalizeExpression(expression, counter, block);
	}
	
	BlockStmt normalizeExpression(Expression expression, Integer[] counter, BlockStmt block){
		Object[] in = {counter , block}; 
		expression.accept(new VoidVisitorAdapter<Object[]>() {
			@Override
			public void visit(MethodCallExpr methodCallExpr, Object[] in) {
				Integer[] counter = ((Integer[])in[0]);
				BlockStmt block = ((BlockStmt)in[1]);
				String variable = "__X" + counter[0];
				Node parent = methodCallExpr.getParentNode().get();
				while(parent instanceof EnclosedExpr){
					parent = parent.getParentNode().get();
				}
				Expression variableExpr = JavaParser.parseExpression(variable);
				if(parent instanceof BinaryExpr){
					BinaryExpr binaryExpr = (BinaryExpr) parent;
					if(binaryExpr.getRight().equals(methodCallExpr)){
						binaryExpr.setRight(variableExpr);
					}else{
						binaryExpr.setLeft(variableExpr);
					}
				}else if (parent instanceof UnaryExpr){
					UnaryExpr unaryExpr = (UnaryExpr) parent;
					unaryExpr.setExpression(variableExpr);
				}else if(parent instanceof MethodCallExpr){
					MethodCallExpr methodCallExpr2 = (MethodCallExpr) parent;
					NodeList<Expression> argumants = methodCallExpr2.getArguments();
					int index = argumants.indexOf(methodCallExpr);
					argumants.remove(index);
					argumants.add(index, variableExpr);
				}else if(parent instanceof ArrayAccessExpr){
					ArrayAccessExpr arrayAccessExpr = (ArrayAccessExpr) parent;
					arrayAccessExpr.setIndex(variableExpr);
				}
				NodeList<Expression> argumants = methodCallExpr.getArguments();
				for (Expression argumant : argumants) {
					if(argumant instanceof MethodCallExpr){
						normalizeExpression(argumant, counter, block);
					}
				}
				String type = JavaParserFacade.get(combinedTypeSolver).getType(methodCallExpr).describe();
				block.addStatement(JavaParser.parseStatement(type + " " + variable + " = " + methodCallExpr.toString() + ";"));
				counter[0]++;
				super.visit(methodCallExpr, in);
			}
		}, in);
		block.addStatement(expression);
		return block;
	}
	
	BlockStmt normalizeExpression(ExpressionStmt expressionStmt){
		return normalizeExpression(expressionStmt.getExpression());
	}
	
	public void normalize(String javaFilePath) throws Exception {
		CompilationUnit compilationUnit = getCompilationUnit(javaFilePath);
		List<MethodDeclaration> methodDeclarations = compilationUnit.getTypes().get(0).getMethods();
		for (MethodDeclaration methodDeclaration : methodDeclarations) {
			if(methodDeclaration.getBody().isPresent()){
				BlockStmt normalizedBody = (BlockStmt) normalizeStatement(methodDeclaration.getBody().get());
				Statement iStatement = JavaParser.parseStatement("int __I = 0;");
				normalizedBody.addStatement(0, iStatement);
			}
		}
		writeNormalizedJavaFile(javaFilePath, compilationUnit);
	}

	private void writeNormalizedJavaFile(String javaFilePath, CompilationUnit compilationUnit) throws Exception {
		Utils.writeTextFile(javaFilePath + ".normalized", compilationUnit.toString());
		new File(javaFilePath).renameTo(new File(javaFilePath + "_"));
		new File(javaFilePath + ".normalized").renameTo(new File(javaFilePath));
	}
	
	private CompilationUnit getCompilationUnit(String javaFilePath) throws Exception {
		CompilationUnit compilationUnit = JavaParser.parse(new File(javaFilePath));
		return compilationUnit;
	}

	private CombinedTypeSolver getTypeSolver(ArrayList<String> sourceDirs, ArrayList<String> classPathes) throws Exception {
		CombinedTypeSolver typeSolver = new CombinedTypeSolver();
		typeSolver.add(new ReflectionTypeSolver());
		for (String classPath : classPathes) {
			typeSolver.add(new JarTypeSolver(classPath));
		}
		for (String sourceDir : sourceDirs) {
			typeSolver.add(new JavaParserTypeSolver(new File(sourceDir)));
		}
		return typeSolver;
	}

	private Statement normalizeStatement(Statement statement) {
		Statement normalizedStatement = null;
		if(statement instanceof ExpressionStmt){
			Expression expression = ((ExpressionStmt) statement).getExpression();
			if(expression instanceof VariableDeclarationExpr){
				normalizeVariableDeclaration(expression);
			}else{
				normalizeExpression(expression);
			}
		}else if(statement instanceof IfStmt){
			IfStmt ifStmt = (IfStmt) statement;
			normalizedStatement = normalizeIfStatement(ifStmt);
		}else if(statement instanceof WhileStmt){
			WhileStmt whileStmt = (WhileStmt) statement;
			normalizedStatement = normalizeWhileStatement(whileStmt);
		}else if(statement instanceof DoStmt){
			DoStmt doStmt = (DoStmt) statement;
			normalizedStatement = normalizeDoStatement(doStmt);
		}else if(statement instanceof ForStmt){
			ForStmt forStmt = (ForStmt) statement;
			normalizedStatement = normalizeForStatement(forStmt);
		}else if(statement instanceof ForeachStmt){
			ForeachStmt foreachStmt = (ForeachStmt) statement;
			normalizedStatement = normalizeForeachStatement(foreachStmt);
		}else if(statement instanceof SwitchStmt){
			SwitchStmt switchStmt = (SwitchStmt) statement;
			normalizedStatement = normalizeSwitchStatement(switchStmt);
		}else if(statement instanceof BlockStmt){
			BlockStmt blockStmt = (BlockStmt) statement;
			for (Statement statement2 : blockStmt.getStatements()) {
				normalizeStatement(statement2);
			}
			normalizedStatement = blockStmt;
		}else{
			normalizedStatement = statement;
		}
		return normalizedStatement;
	}

	private Statement normalizeSwitchStatement(SwitchStmt switchStmt) {
		String selector = switchStmt.getSelector().toString();
		String ifCode = "";
		boolean hasDefault = false;
		for (SwitchEntryStmt entry : switchStmt.getEntries()) {
			if(entry.getLabel().isPresent()){
				ifCode += "if("
						+ selector + "==" + entry.getLabel().get().toString()
						+ "){";
				hasDefault = true;
			}else{
				ifCode += "{";
			}
			for (Statement statement : entry.getStatements()) {
				ifCode += statement.toString();
			}
			ifCode	+= "}else ";
		}
		if(hasDefault==false){
			ifCode +="{}";
		}
		Statement ifStmt = JavaParser.parseStatement(ifCode);
		Statement normalizedIfStmt = normalizeStatement(ifStmt);
		addToParent(switchStmt, ifStmt);
		switchStmt.remove();
		return normalizedIfStmt;
	}

	private Statement normalizeForeachStatement(ForeachStmt foreachStmt) {
		Expression iterable = foreachStmt.getIterable();
		Type iterableType = JavaParserFacade.get(combinedTypeSolver).getType(iterable);
		String condition = "";
		String variable = foreachStmt.getVariable().getVariable(0).getType().toString() 
				+ " " + foreachStmt.getVariable().getVariable(0).getNameAsString() + " = ";
		if(iterableType.isArray()){
			condition = "__I<(" + iterable.toString() + ").length";
			variable += iterable.toString() + "[__I];";
		}else{
			condition = "__I<(" + iterable.toString() + ").size()";
			variable += iterable.toString() + ".get(__I);";
		}
		foreachStmt.getBody();
		String whileBlockCode = "{__I = 0;";
		whileBlockCode += "while("
				+ variable
				+ condition
				+ "){"
				+ foreachStmt.getBody().toString()
				+ "__I++;}";
		BlockStmt whileBlock = JavaParser.parseBlock(whileBlockCode);
		addToParent(foreachStmt, whileBlock);
		foreachStmt.remove();
		Statement normalizedBlock = normalizeStatement(whileBlock);
		return normalizedBlock;
	}

	private Statement normalizeForStatement(ForStmt forStmt) {
		String whileBlock = "{";
		for (Expression initializer : forStmt.getInitialization()) {
			whileBlock += initializer.toString() + ";";
		}
		String condition = "true";
		if(forStmt.getCompare().isPresent()){
			condition = forStmt.getCompare().get().toString();
		}
		String updates = "";
		for (Expression update : forStmt.getUpdate()) {
			updates += update.toString();
		}
		whileBlock += "while("
				+ condition 
				+ "){"
				+ forStmt.getBody()
				+ updates 
				+ "}}";
		BlockStmt bolck = (BlockStmt) JavaParser.parseStatement(whileBlock);
		addToParent(forStmt, bolck);
		forStmt.remove();
		Statement normalizedBlock = normalizeStatement(bolck);
		return normalizedBlock;
	}

	private Statement normalizeDoStatement(DoStmt doStmt) {
		BlockStmt normalizedBlock = normalizeExpression(doStmt.getCondition());
		Expression normalizedCondition = getAndRemoveLastExpression(normalizedBlock);
		BlockStmt normalizedBody = (BlockStmt) normalizeStatement(doStmt.getBody());
		BlockStmt newNormalizedBlock = (BlockStmt) JavaParser.parseStatement(
				normalizedBody.toString() +
				normalizedBlock.toString() + 
				"while (" + normalizedCondition.toString() + ")" +
				" {" +
				normalizedBody.toString() +
				normalizedBlock.toString() +
				"}");
		addToParent(doStmt, newNormalizedBlock);
		doStmt.remove();
		return newNormalizedBlock;
	}

	private Statement normalizeWhileStatement(WhileStmt whileStmt) {
		BlockStmt normalizedBlock = normalizeExpression(whileStmt.getCondition());
		Expression normalizedCondition = getAndRemoveLastExpression(normalizedBlock);
		whileStmt.setCondition(normalizedCondition);
		addToParent(whileStmt, normalizedBlock);
		normalizeStatement(whileStmt.getBody());
		return whileStmt;
	}

	private Statement normalizeIfStatement(IfStmt ifStmt) {
		BlockStmt normalizedBlock = normalizeExpression(ifStmt.getCondition());
		Expression normalizedCondition = getAndRemoveLastExpression(normalizedBlock);
		ifStmt.setCondition(normalizedCondition);
		addToParent(ifStmt, normalizedBlock);
		normalizeStatement(ifStmt.getThenStmt());
		if(ifStmt.getElseStmt().isPresent()){
			normalizeStatement(ifStmt.getElseStmt().get());
		}
		return ifStmt;
	}

	private void addToParent(Statement statement, Statement addingStatement) {
		BlockStmt parent = (BlockStmt) statement.getParentNode().get();
		int index = parent.getStatements().indexOf(statement);
		parent.addStatement(index, addingStatement);		
	}

	private Expression getAndRemoveLastExpression(BlockStmt block) {
		int lastIndex = block.getStatements().size()-1;
		Statement lastStatement = block.getStatement(lastIndex);
		Expression expression = JavaParser.parseExpression(
				lastStatement.toString().substring(0, lastStatement.toString().length()-2));
		lastStatement.remove();
		return expression;
	}

	private void normalizeVariableDeclaration(Expression expression) {
		VariableDeclarationExpr variableDeclarationExpr = (VariableDeclarationExpr) expression;
		NodeList<VariableDeclarator> variables = variableDeclarationExpr.getVariables();
		BlockStmt parent = (BlockStmt) variableDeclarationExpr.getParentNode().get();
		NodeList<Statement> statements = parent.getStatements();
		for (VariableDeclarator variable : variables) {
			BlockStmt blockStmt = normalizeExpression(variable.getInitializer().get());
			int index = blockStmt.getStatements().size() - 1;
			Statement normalizedInitializer = blockStmt.getStatement(index);
			blockStmt.addStatement(index, JavaParser.parseStatement(
					variable.getType() + " " + variable.getNameAsString() + " = " + normalizedInitializer.toString()));
			index = statements.indexOf(variableDeclarationExpr);
			parent.addStatement(index, blockStmt);
		}
		variableDeclarationExpr.remove();
	}
}
