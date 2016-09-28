package se.lnu.prosses.securityMonitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Main {
	public static void main(String[] args) throws Exception {
		String directoryPath = "C:\\Users\\khmo222\\git\\runningexample\\src";
		String[] classPath = new String[]{"C:\\tools\\apache-tomcat-6.0.4\\lib\\servlet-api.jar", "C:\\Users\\khmo222\\git\\stsextractor\\src"};
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
		STSExtractor stsExtractor = new STSExtractor(includingFilter, excludingFilter , entryPoints);
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
		stsExtractor.extract(directoryPath, classPath, controllableMethodNames);
	}
}
