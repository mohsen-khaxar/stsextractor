package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;

public class JavaProjectHelper {
	String sourcePath; 
	String[] classPath;
	Hashtable<String, JavaFileHelper> javaFileHelpers;
	
	public JavaProjectHelper(String sourcePath, String[] classPath) {
		this.sourcePath = sourcePath;
		this.classPath = classPath;
		this.javaFileHelpers = new Hashtable<>();
	}
	
	public void load() throws Exception{
		ArrayList<String> javaFilePaths = getAllJavaFilePaths(sourcePath);
		for (String javaFilePath : javaFilePaths) {
			JavaFileHelper javaFileHelper = new JavaFileHelper(sourcePath, classPath, javaFilePath, this);
			JavaClassNormalizer classNormalizer = new JavaClassNormalizer(javaFileHelper);
			classNormalizer.normalize();
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
}
