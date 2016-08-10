package se.lnu.prosses.securityMonitor;

import java.util.ArrayList;

public class Main {
	public static void main(String[] args) throws Exception {
		String directoryPath = "C:\\Users\\khmo222\\workspace\\test\\src";//"C:\\temp\\BroadleafCommerce-BroadleafCommerce-4.0.x\\core\\broadleaf-framework\\src\\main\\java";"C:\\Users\\khmo222\\workspace\\test\\src";
		String[] classPath = new String[]{"C:\\tools\\apache-tomcat-6.0.4\\lib\\servlet-api.jar"};
		ArrayList<String> includingFilter = new ArrayList<String>();
		includingFilter.add("com\\.mohsen.*");
//		includingFilter.add("java\\.sql.*");
//		includingFilter.add("javax\\.servlet.*");
		ArrayList<String> entryPoints = new ArrayList<>();
		entryPoints.add(".*\\.doGet");
		ArrayList<String> excludingFilter = new ArrayList<>();
		STSExtractor stsExtractor = new STSExtractor(includingFilter, excludingFilter , entryPoints);
		stsExtractor.extract(directoryPath, classPath);
	}
}
