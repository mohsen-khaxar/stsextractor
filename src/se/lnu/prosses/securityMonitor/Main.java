package se.lnu.prosses.securityMonitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Main {
	public static void main(String[] args) throws Exception {
		String sourcePath = "/home/mohsen/git/runningexample/src";
		String targetPath = "/home/mohsen/git/runningexample/";
		String[] classPath = new String[]{"/home/mohsen/git/runningexample/src", "/home/mohsen/git/stsextractor/src"};
		ArrayList<String> includingFilter = new ArrayList<String>();
		ArrayList<String> entryPoints = new ArrayList<>();
		ArrayList<String> excludingFilter = new ArrayList<>();
		Set<String> controllableMethodNames = new HashSet<>();

		includingFilter.add("se\\.lnu.*");
		entryPoints.add(".*\\.main");
		controllableMethodNames.add("se.lnu.CaseStudy.estimatLocation");

		JavaProjectHelper javaProjectHelper = new JavaProjectHelper(sourcePath, classPath);
		javaProjectHelper.load();
		JavaProjectSTSExtractor stsExtractor = new JavaProjectSTSExtractor(javaProjectHelper, includingFilter, excludingFilter, targetPath);
		STSHelper stsHelper = stsExtractor.extract();
		SecurityMonitorSynthesizer securityMonitorSynthesizer = new SecurityMonitorSynthesizer(stsHelper, targetPath);
		securityMonitorSynthesizer.synthesize();
		AspectJGenerator aspectJGenerator = new AspectJGenerator(stsHelper, javaProjectHelper, targetPath);
		aspectJGenerator.generate();
	}
}
