package se.lnu.prosses.securityMonitor;

import java.util.ArrayList;

public class Main {
	public static void main(String[] args) throws Exception {
		String sourcePath = "/home/mohsen/workspace/test/src/";
		String targetPath = "/home/mohsen/workspace/test/src/";
		String[] classPath = new String[]{"/home/mohsen/workspace/test/src", "/home/mohsen/git/stsextractor/src"};
		ArrayList<String> includingFilter = new ArrayList<String>();
		ArrayList<String> excludingFilter = new ArrayList<>();
		includingFilter.add("se\\.lnu.*");
		Utils.log(Main.class, "Synthesis starts.");
		Utils.log(Main.class, "Source path : " + sourcePath);
		Utils.log(Main.class, "Target path : " + targetPath);
		String message = "Class path(es) : ";
		for (String cp : classPath) {
			message += cp + ":";
		}
		Utils.log(Main.class, message);
		message = "Include filter(s) : ";
		for (String ifl : includingFilter) {
			message += ifl + ";";
		}
		Utils.log(Main.class, message);
		message = "Exclude filter(s) : ";
		for (String ef : excludingFilter) {
			message += ef + ";";
		}
		Utils.log(Main.class, message);
		JavaProjectHelper javaProjectHelper = new JavaProjectHelper(sourcePath, classPath);
		javaProjectHelper.load(true);
		JavaProjectSTSExtractor stsExtractor = new JavaProjectSTSExtractor(javaProjectHelper, includingFilter, excludingFilter, targetPath);
		STSHelper stsHelper = stsExtractor.extract();
		SecurityMonitorSynthesizer securityMonitorSynthesizer = new SecurityMonitorSynthesizer(stsHelper, targetPath);
		securityMonitorSynthesizer.synthesize();
//		AspectJGenerator aspectJGenerator = new AspectJGenerator(stsHelper, javaProjectHelper, targetPath);
//		aspectJGenerator.generate();
		javaProjectHelper = new JavaProjectHelper(sourcePath, classPath);
		javaProjectHelper.load(false);
		CodeTransformer codeTransformer = new CodeTransformer(stsHelper, javaProjectHelper);
		codeTransformer.transform();
	}
}
