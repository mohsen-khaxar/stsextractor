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
//		entryPoints.add(".*\\.mohsen");
//		entryPoints.add(".*\\.doGet");
//		entryPoints.add(".*\\.doPost");
		entryPoints.add(".*\\.f");
		ArrayList<String> excludingFilter = new ArrayList<>();
		Set<String> controllableMethodNames = new HashSet<>();
//		controllableMethodNames .add("se.lnu.Users.removeUser");
//		controllableMethodNames.add("se.lnu.User.getFriendAt");
//		controllableMethodNames.add("se.lnu.EstimateLocation.getDistance");
//		controllableMethodNames.add("se.lnu.EstimateLocation.estimatLocation");
//		controllableMethodNames.add("se.lnu.Users.findUserById");
//		controllableMethodNames.add("se.lnu.Users.addUser");
//		controllableMethodNames.add("se.lnu.Users.addFriend");
//		controllableMethodNames.add("se.lnu.Users.auth");
		controllableMethodNames.add("se.lnu.Test.g");
//		controllableMethodNames.add("se.lnu.Test.f");
		STSExtractor stsExtractor = new STSExtractor(includingFilter, excludingFilter , entryPoints, controllableMethodNames);
		stsExtractor.extract(directoryPath, classPath, controllableMethodNames);
		stsExtractor.sts.saveAsDot(directoryPath + File.separator + "model.dot");
		STS controlledSTS = stsExtractor.generateControlledSTS();
		controlledSTS.saveAsDot("/home/mohsen/aspects/modelc.dot");
		controlledSTS = controlledSTS.convertToUncontrollableFreeSTS();
		controlledSTS.saveAsDot("/home/mohsen/aspects/freemodelc.dot");
		controlledSTS.generateAspect(directoryPath, "/home/mohsen/aspects");
	}
}
