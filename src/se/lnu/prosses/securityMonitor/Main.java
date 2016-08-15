package se.lnu.prosses.securityMonitor;

import java.util.ArrayList;

public class Main {
	public static void main(String[] args) throws Exception {
		String directoryPath = "C:\\Users\\khmo222\\workspace\\RunningExample\\src";
		String[] classPath = new String[]{"C:\\tools\\apache-tomcat-6.0.4\\lib\\servlet-api.jar", "C:\\Users\\khmo222\\git\\stsextractor\\src"};
		ArrayList<String> includingFilter = new ArrayList<String>();
		includingFilter.add("se\\.lnu.*");
//		includingFilter.add("java\\.sql.*");
//		includingFilter.add("javax\\.servlet.*");
		ArrayList<String> entryPoints = new ArrayList<>();
		entryPoints.add(".*\\.doGet");
		entryPoints.add(".*\\.doPost");
		ArrayList<String> excludingFilter = new ArrayList<>();
		STSExtractor stsExtractor = new STSExtractor(includingFilter, excludingFilter , entryPoints);
		stsExtractor.extract(directoryPath, classPath);
	}
}
