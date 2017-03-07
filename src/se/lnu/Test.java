package se.lnu;

import java.io.IOException;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import se.lnu.prosses.securityMonitor.JavaFileHelper;

public class Test {
	
	public static void main(String[] args) throws IOException {
		String javaFilePath = "/home/mohsen/git/runningexample/src/se/lnu/A.java";
		String sourceDir = "/home/mohsen/git/runningexample/src";
		String[] classPath = new String[]{"/home/mohsen/git/runningexample/src", "/home/mohsen/git/stsextractor/src"};
		JavaFileHelper astHelper = new JavaFileHelper(sourceDir, classPath, javaFilePath, null);
		astHelper.load();
		CompilationUnit cu = astHelper.getCompilationUnit();
		cu.accept(new ASTVisitor() {
			
			@Override
			public boolean visit(MethodInvocation node) {
				System.out.println(node.getExpression());
				return super.visit(node);
			}
			
			@Override
			public boolean visit(SimpleName simpleName) {
				if(simpleName.resolveBinding() instanceof IVariableBinding){
//					System.out.println(((IVariableBinding)simpleName.resolveBinding()).getDeclaringClass().getModifiers());
				}
				return false;
			}
		});
	}
}