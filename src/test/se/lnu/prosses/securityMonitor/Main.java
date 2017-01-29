package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Main {
	public static void main(String[] args) throws Exception {
		String directoryPath = "/home/mohsen/git/runningexample//src";
		String[] classPath = new String[]{"/home/mohsen/servlet-api.jar", "/home/mohsen/git/stsextractor/src"};
		ArrayList<String> includingFilter = new ArrayList<String>();
		includingFilter.add("se\\.lnu.*");
//		includingFilter.add("java\\.sql.*");
//		includingFilter.add("javax\\.servlet.*");
		ArrayList<String> entryPoints = new ArrayList<>();
		ArrayList<String> excludingFilter = new ArrayList<>();
		Set<String> controllableMethodNames = new HashSet<>();

		entryPoints.add(".*\\.getStrangerInformation");
		controllableMethodNames.add("se.lnu.User.getStrangerInformation");
		controllableMethodNames.add("se.lnu.User.estimatLocation");

//		entryPoints.add(".*\\.f");
//		controllableMethodNames.add("se.lnu.Test.g");
		
		STSExtractor stsExtractor = new STSExtractor(includingFilter, excludingFilter , entryPoints, controllableMethodNames);
		stsExtractor.extract(directoryPath, classPath, controllableMethodNames);
		stsExtractor.sts.saveAsDot(directoryPath + File.separator + "model1.dot");
		stsExtractor.sts.propagateInitialValues();
		stsExtractor.sts.saveAsDot(directoryPath + File.separator + "model.dot");
		STS controlledSTS = stsExtractor.generateControlledSTS();
		controlledSTS.saveAsDot("/home/mohsen/aspects/modelc.dot");
		controlledSTS = controlledSTS.convertToUncontrollableFreeSTS();
		controlledSTS.saveAsDot("/home/mohsen/aspects/freemodelc.dot");
		controlledSTS.generateAspect(directoryPath, "/home/mohsen/aspects");
	}
}
