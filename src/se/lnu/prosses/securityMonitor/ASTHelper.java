package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

public class ASTHelper {
	
	String[] sourceDir;
	String[] classPath;
	String javaFilePath;
	CompilationUnit compilationUnit;
	ASTRewrite astRewrite;
	
	public CompilationUnit getCompilationUnit(){
		return compilationUnit;
	}
	
	public ASTHelper(String[] sourceDir, String[] classPath, String javaFilePath) {
		this.sourceDir = sourceDir;
		this.classPath = classPath;
		this.javaFilePath = javaFilePath;
		this.compilationUnit = buildCompilationUnit();
		AST ast = compilationUnit.getAST();
		this.astRewrite = ASTRewrite.create(ast);
	}
	
	private CompilationUnit buildCompilationUnit() {
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setResolveBindings(true);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setBindingsRecovery(true);
		@SuppressWarnings("rawtypes")
		Map options = JavaCore.getOptions();
		parser.setCompilerOptions(options);
		parser.setSource(Utils.readTextFile(javaFilePath));
		parser.setUnitName(new File(javaFilePath).getName());
		parser.setEnvironment(classPath, sourceDir, new String[] { "UTF-8"}, true);
		CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
		return compilationUnit;
	}
	
	public ASTNode parse(String code, int type) {
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setSource(code.toCharArray());
		parser.setKind(type);
		ASTNode res = parser.createAST(null);
		if(type==ASTParser.K_STATEMENTS){
			res = (ASTNode) ((Block)res).statements().get(0);
		}
		return res;
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
	
	static String type = "";
	public String getType(final String nodeString, ASTNode origin){
		origin.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				if(node.toString().equals(nodeString)){
					type = ((Expression)node).resolveTypeBinding().getQualifiedName();
				}
			}
		});
		return type;
	}
	
	public String getExpressionType(Expression expression){
		return expression.resolveTypeBinding().getQualifiedName();
	}
	
	public void saveModifiedJavaFile() throws Exception{
		Document document = new Document(String.valueOf(Utils.readTextFile(javaFilePath)));
		TextEdit edits = astRewrite.rewriteAST(document, null);
		edits.apply(document);
		Utils.writeTextFile(javaFilePath + ".normalized", document.get());
		new File(javaFilePath).renameTo(new File(javaFilePath + "_"));
		new File(javaFilePath + ".normalized").renameTo(new File(javaFilePath));
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
		if(insertionPoint instanceof Statement && insertingNode instanceof Statement && parent.getNodeType()==ASTNode.BLOCK){
			ListRewrite listRewrite = astRewrite.getListRewrite(parent, Block.STATEMENTS_PROPERTY);
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
}
