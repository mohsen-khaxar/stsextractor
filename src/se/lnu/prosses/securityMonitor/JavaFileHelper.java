package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

public class JavaFileHelper {
	
	JavaProjectHelper parent;
	String[] sourcePath;
	String[] classPath;
	String javaFilePath;
	CompilationUnit compilationUnit;
	ASTRewrite astRewrite;
	String DUMMY_METHODS_CLASS = "se.lnu.DummyMethods.";
	
	public JavaFileHelper(String sourcePath, String[] classPath, String javaFilePath, JavaProjectHelper parent) {
		this.sourcePath = new String[]{sourcePath};
		this.classPath = classPath;
		this.javaFilePath = javaFilePath;
		this.parent = parent;
	}
	
	public CompilationUnit getCompilationUnit(){
		return compilationUnit;
	}
	
	public String getJavaFilePath(){
		return this.javaFilePath;
	}
	
	public String getQualifiedClassName(){
		String qualifiedClassName = null;
		if(compilationUnit.types().size()>0 && !((TypeDeclaration)compilationUnit.types().get(0)).isInterface()){
			qualifiedClassName = ((TypeDeclaration)compilationUnit.types().get(0)).resolveBinding().getQualifiedName();
		}
		return qualifiedClassName;
	}
	
	public void load() {
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setResolveBindings(true);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setBindingsRecovery(true);
		@SuppressWarnings("rawtypes")
		Map options = JavaCore.getOptions();
		parser.setCompilerOptions(options);
		parser.setSource(Utils.readTextFile(javaFilePath));
		parser.setUnitName(new File(javaFilePath).getName());
		parser.setEnvironment(classPath, sourcePath, new String[] { "UTF-8"}, true);
		CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
		this.compilationUnit = compilationUnit;
		AST ast = compilationUnit.getAST();
		this.astRewrite = ASTRewrite.create(ast);
	}
	
	public ASTNode parseStatement(String code) {
		ASTNode res = parse(code, ASTParser.K_STATEMENTS);
		return (ASTNode) ((Block)res).statements().get(0);
	}

	private ASTNode parse(String code, int type) {
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setSource(code.toCharArray());
		parser.setKind(type);
		return parser.createAST(null);
	}
	
	public ASTNode parseMethodDeclaration(String code) {
		return ((TypeDeclaration)parse(code, ASTParser.K_CLASS_BODY_DECLARATIONS)).getMethods()[0];
	}
	
	public Expression parseExpression(String code) {
		return (Expression) parse(code, ASTParser.K_EXPRESSION);
	}
	
	static Boolean has = false;
	public boolean hasMethodInvokation(ASTNode node) {
		has = false;
		node.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation methodInvocation) {
				has = true;
				return !has;
			}
		});
		return has;
	}
	
	public String getExpressionTypeName(Expression expression){
		return expression.resolveTypeBinding().getQualifiedName();
	}
	
	public ITypeBinding getExpressionType(Expression expression){
		return expression.resolveTypeBinding();
	}
	
	public void saveModifiedJavaFile() throws Exception{
		Document document = new Document(String.valueOf(Utils.readTextFile(javaFilePath)));
		TextEdit edits = astRewrite.rewriteAST(document, null);
		edits.apply(document);
		Utils.writeTextFile(javaFilePath, document.get());
	}
	
	public void insertStatementAfter(ASTNode insertionPoint, ASTNode insertingNode) throws Exception{
		ASTNode parent = insertionPoint.getParent();
		if(insertionPoint instanceof Statement && insertingNode instanceof Statement && parent.getNodeType()==ASTNode.BLOCK){
			ListRewrite listRewrite = astRewrite.getListRewrite(parent, Block.STATEMENTS_PROPERTY);
			listRewrite.insertAfter(insertingNode, insertionPoint, null);
		}else{
			throw new Exception("\"" + insertionPoint.toString() + "\" and \"" + insertingNode.toString() + "\" must be statement."
					+ "\"" + insertionPoint.toString() + "\" could not be an insertion point. Because its parent is not a block statement.");
		}
	}
	
	public void insertStatementBefore(ASTNode insertionPoint, ASTNode insertingNode) throws Exception{
		ASTNode parent = insertionPoint.getParent();
		if(insertionPoint instanceof Statement && insertingNode instanceof Statement){
			ListRewrite listRewrite = null;
			if(parent.getNodeType()==ASTNode.BLOCK){
				listRewrite = astRewrite.getListRewrite(parent, Block.STATEMENTS_PROPERTY);
			}else if(parent.getNodeType()==ASTNode.SWITCH_STATEMENT){
				listRewrite = astRewrite.getListRewrite(parent, SwitchStatement.STATEMENTS_PROPERTY);
			}else{
				throw new Exception("\"" + insertionPoint.toString() + "\" and \"" + insertingNode.toString() + "\" must be statement."
						+ "\"" + insertionPoint.toString() + "\" could not be an insertion point. Because its parent is not a block statement.");
			}
			listRewrite.insertBefore(insertingNode, insertionPoint, null);
		}else{
			throw new Exception("\"" + insertionPoint.toString() + "\" and \"" + insertingNode.toString() + "\" must be statement."
					+ "\"" + insertionPoint.toString() + "\" could not be an insertion point. Because its parent is not a block statement.");
		}
	}
	
	public void insertStatementInsteadOf(ASTNode insertionPoint, ASTNode insertingNode) throws Exception{
		if(insertionPoint instanceof Statement && insertingNode instanceof Statement){
			astRewrite.replace(insertionPoint, insertingNode, null);
		}else{
			throw new Exception("\"" + insertionPoint.toString() + "\" and \"" + insertingNode.toString() + "\" must be statement.");
		}
	}

	public void insertExpressionInsteadOf(ASTNode insertionPoint, ASTNode insertingNode) {
		astRewrite.replace(insertionPoint, insertingNode, null);
	}

	public void removeStatement(ASTNode astNode) {
		astRewrite.remove(astNode, null);
	}
	
	static boolean hasMethodInvocation = false;
	public boolean isNormalized(Expression expression){
		hasMethodInvocation = false;
		expression.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				hasMethodInvocation = true;
				return false;
			}
		});
		return hasMethodInvocation;
	}

	public void insertStatementsBefore(ASTNode insertionPoint, List<ASTNode> normalizedExpression) throws Exception {
		for (int i=normalizedExpression.size()-1; i>=0; i--) {
			insertStatementBefore(insertionPoint, normalizedExpression.get(i));
		}		
	}
	
	public void insertStatementsBefore(ASTNode insertionPoint, List<ASTNode> normalizedExpression, int start, int end) throws Exception {
		for (int i=end; i>=start; i--) {
			insertStatementBefore(insertionPoint, normalizedExpression.get(i));
		}		
	}

	public void insertStatementsExceptFirstBefore(ASTNode insertionPoint, List<ASTNode> insertingNodes) throws Exception {
		insertStatementsBefore(insertionPoint, insertingNodes, 1, insertingNodes.size()-1);
		
	}
	
	@SuppressWarnings("rawtypes")
	public void insertStatementsInsteadOf(Statement insertionPoint, List insertingStatements) throws Exception {
		for (Object insertingStatement : insertingStatements) {
			insertStatementBefore(insertionPoint, (ASTNode) insertingStatement);
		}
		removeStatement(insertionPoint);
	}

	public String getQualifiedName(ASTNode astNode) {
		String qualiofiedName = "";
		if(astNode instanceof MethodDeclaration){
			qualiofiedName = ((MethodDeclaration) astNode).resolveBinding().getDeclaringClass().getQualifiedName() 
					+ "." + ((MethodDeclaration) astNode).getName();
		}else if(astNode instanceof MethodInvocation){
			qualiofiedName = ((MethodInvocation) astNode).resolveMethodBinding().getDeclaringClass().getQualifiedName() 
					+ "." + ((MethodInvocation) astNode).getName();
		}else if(astNode instanceof ClassInstanceCreation){
			qualiofiedName = ((ClassInstanceCreation)astNode).resolveConstructorBinding().getDeclaringClass().getQualifiedName()
					+ "." + ((ClassInstanceCreation)astNode).resolveConstructorBinding().getDeclaringClass().getName();
		}
		return qualiofiedName;
	}
	
	@SuppressWarnings("unchecked")
	public List<Expression> getMethodArguments(Expression expression) {
		List<Expression> arguments = null;
		if(expression instanceof MethodInvocation){
			arguments = ((MethodInvocation) expression).arguments();
		}else if(expression instanceof ClassInstanceCreation){
			arguments = ((ClassInstanceCreation) expression).arguments();
		}
		return arguments;
	}
	
	public MethodDeclaration getMethodDeclaration(Expression expression) {
		String expressionResolveBinding = "";
		if(expression instanceof MethodInvocation){
			expressionResolveBinding  = ((MethodInvocation) expression).resolveMethodBinding().toString();
		}else if(expression instanceof ClassInstanceCreation){
			expressionResolveBinding = ((ClassInstanceCreation) expression).resolveConstructorBinding().toString();
		}
		TypeDeclaration clazz = parent.getDeclaringClass(expression);
		MethodDeclaration methodDeclaration = null;
		for (MethodDeclaration mDeclaration : clazz.getMethods()) {
			if(mDeclaration.resolveBinding().toString().equals(expressionResolveBinding)){
				methodDeclaration = mDeclaration;
				break;
			}
		}
		return methodDeclaration;
	}
	
	@SuppressWarnings("unchecked")
	public List<SimpleName> getMethodParameters(Expression expression) {
		MethodDeclaration methodDeclaration = getMethodDeclaration(expression);
		List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
		ArrayList<SimpleName> parameterNames = new ArrayList<>();
		for (SingleVariableDeclaration singleVariableDeclaration : parameters) {
			parameterNames.add(singleVariableDeclaration.getName());
		}
		return parameterNames;
	}
	
	public Block getMethodBody(Expression expression) {
		MethodDeclaration methodDeclaration = getMethodDeclaration(expression);
		return methodDeclaration.getBody();
	}

	public boolean isDummyMethod(Expression expression) {
		return getQualifiedName(expression).startsWith(DUMMY_METHODS_CLASS);
	}

	public List<Statement> getLatestVersion(Statement statement) {
		ListRewrite listRewrite = null;
		if(statement.getNodeType()==ASTNode.BLOCK){
			listRewrite = astRewrite.getListRewrite(statement, Block.STATEMENTS_PROPERTY);
		}else if(statement.getNodeType()==ASTNode.SWITCH_STATEMENT){
			listRewrite = astRewrite.getListRewrite(statement, SwitchStatement.STATEMENTS_PROPERTY);
		}
		ArrayList<Statement> latestVersion = new ArrayList<>();
		for (Object object : listRewrite.getRewrittenList()) {
			latestVersion.add((Statement) object);
		}
		return latestVersion;
	}
	
	public String getLatestVersionCode(Statement statement) {
		List<Statement> latestVersion = getLatestVersion(statement);
		String latestVersionCode = "";
		for (Statement statement2 : latestVersion) {
			latestVersionCode += statement2.toString();
		}
		if(statement instanceof Block){
			latestVersionCode = "{" + latestVersionCode + "}";
		}
		return latestVersionCode;
	}
}
