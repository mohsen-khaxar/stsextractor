package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Main {
	public static void main(String[] args) throws Exception {
		String directoryPath = "/home/mohsen/git/runningexample/src";
		String[] classPath = new String[]{"/home/mohsen/git/runningexample/src", "/home/mohsen/git/stsextractor/src"};
		ArrayList<String> includingFilter = new ArrayList<String>();
		ArrayList<String> entryPoints = new ArrayList<>();
		ArrayList<String> excludingFilter = new ArrayList<>();
		Set<String> controllableMethodNames = new HashSet<>();

		includingFilter.add("se\\.lnu.*");
		entryPoints.add(".*\\.main");
		controllableMethodNames.add("se.lnu.CaseStudy.estimatLocation");

		STSExtractor stsExtractor = new STSExtractor(includingFilter, excludingFilter , entryPoints, controllableMethodNames);
		stsExtractor.extract(directoryPath, classPath, controllableMethodNames);
		stsExtractor.sts.propagateInitialValues();
		stsExtractor.sts.saveAsDot(directoryPath + File.separator + "model.dot");
		STS controlledSTS = stsExtractor.generateControlledSTS();
		controlledSTS.saveAsDot("/home/mohsen/git/runningexample/aspects/modelc.dot");
		controlledSTS = controlledSTS.convertToUncontrollableFreeSTS();
		controlledSTS.saveAsDot("/home/mohsen/git/runningexample/aspects/freemodelc.dot");
		controlledSTS.generateAspect(directoryPath, "/home/mohsen/git/runningexample/aspects");
	}
}
