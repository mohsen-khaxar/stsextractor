package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;

import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class JavaProjectHelper {
	String sourcePath; 
	String[] classPath;
	Hashtable<String, JavaFileHelper> javaFileHelpers;
	
	public JavaProjectHelper(String sourcePath, String[] classPath) {
		this.sourcePath = sourcePath;
		this.classPath = classPath;
		this.javaFileHelpers = new Hashtable<>();
	}
	
	public void load(boolean withNormalization) throws Exception{
		ArrayList<String> javaFilePaths = getAllJavaFilePaths(sourcePath);
		for (String javaFilePath : javaFilePaths) {
			JavaFileHelper javaFileHelper = new JavaFileHelper(sourcePath, classPath, javaFilePath, this);
			if(withNormalization){
				Files.copy(Paths.get(javaFilePath), Paths.get(javaFilePath + "_"), StandardCopyOption.REPLACE_EXISTING);
				JavaClassNormalizer classNormalizer = new JavaClassNormalizer(javaFileHelper);
				classNormalizer.normalize();
			}
			javaFileHelper.load();
			String qualifiedClassName = javaFileHelper.getQualifiedClassName();
			if(qualifiedClassName!=null){
				javaFileHelpers.put(qualifiedClassName, javaFileHelper);
			}
		}
	}
	
	static ArrayList<String> getAllJavaFilePaths(String directoryPath) {
		ArrayList<String> allJavaFilePaths = new ArrayList<String>();
		getAllJavaFilePaths(directoryPath, allJavaFilePaths);
		return allJavaFilePaths;
	}
	
	private static void getAllJavaFilePaths(String directoryPath, ArrayList<String> allJavaFilePaths) {
		File directory = new File(directoryPath);
		File[] directoryContent = directory.listFiles();
		for (File file : directoryContent) {
			if(file.isFile()&&file.getName().toLowerCase().endsWith(".java")){
				allJavaFilePaths.add(file.getAbsolutePath());
			}else if(file.isDirectory()){
				getAllJavaFilePaths(file.getAbsolutePath(), allJavaFilePaths);
			}
		}
	}
	
	public void recoverOriginalJavaFiles(){
		ArrayList<String> javaFilePaths = getAllJavaFilePaths(sourcePath);
		for (String javaFilePath : javaFilePaths) {
			new File(javaFilePath).delete();
			new File(javaFilePath + "_").renameTo(new File(javaFilePath));
		}
	}

	public Collection<JavaFileHelper> getAllJavaFileHelpers() {
		return javaFileHelpers.values();
	}
	
	public TypeDeclaration getDeclaringClass(Expression expression) {
		String declaringClass = "";
		if(expression instanceof MethodInvocation){
			declaringClass = ((MethodInvocation) expression).resolveMethodBinding().getDeclaringClass().getQualifiedName();
		}else if(expression instanceof ClassInstanceCreation){
			declaringClass = ((ClassInstanceCreation) expression).resolveConstructorBinding().getDeclaringClass().getQualifiedName();
		}
		JavaFileHelper javaFileHelper = javaFileHelpers.get(declaringClass);
		return (TypeDeclaration)javaFileHelper.getCompilationUnit().types().get(0);
	}

	public JavaFileHelper getJavaFileHelper(String declaringClassName) {
		return javaFileHelpers.get(declaringClassName);
	}
}
