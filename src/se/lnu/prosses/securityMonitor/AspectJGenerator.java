package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class AspectJGenerator {
	STSHelper stsHelper; 
	JavaProjectHelper javaProjectHelper;
	String targetPath;
	
	public AspectJGenerator(STSHelper stsHelper, JavaProjectHelper javaProjectHelper, String targetPath) {
		this.stsHelper= stsHelper; 
		this.javaProjectHelper = javaProjectHelper;
		this.targetPath = targetPath;
	}
	
	public void generate() throws IOException{
		Utils.remakeDirectory(targetPath);
		for (String controllableAction : stsHelper.controllableActions) {
			String qualifiedControllableMethodName = stsHelper.getQualifiedMethodName(controllableAction);
			MethodDeclaration methodDeclaration = getMethodDeclaration(controllableAction);
			String parameters = methodDeclaration.parameters().toString().replace("[", "").replace("]", "");
			String parameterNames = "";
			String[] parts = parameters.replaceAll(" ,", ",").replaceAll("  ", " ").split(" ");
			for (int i = 1; i < parts.length; i+=2) {
				parameterNames += parts[i];
			}
			String className = qualifiedControllableMethodName.substring(0, qualifiedControllableMethodName.lastIndexOf("."));
			FileWriter fileWriter = new FileWriter(targetPath + File.separator + qualifiedControllableMethodName.replaceAll("\\.", "") + "Monitor.aj");
			generateHeader(qualifiedControllableMethodName, methodDeclaration, parameters, parameterNames, className, fileWriter);
			generatePreMainPart(fileWriter);
			generateMainPart(qualifiedControllableMethodName, methodDeclaration, fileWriter);
			generatePostMainPart(qualifiedControllableMethodName, parameters, fileWriter);
			fileWriter.close();
		}
	}

	private void generatePostMainPart(String qualifiedControllableMethodName, String parameters, FileWriter fileWriter)
			throws IOException {
		String[] parts;
		fileWriter.write("MonitorHelper.setCurrentLocation(monitorInstanceId, currentLocation);\n");
		fileWriter.write("if(violation){\n");
		fileWriter.write("\tres = MonitorHelper.applyCountermeasure(\"" + qualifiedControllableMethodName + "\", target, thisJoinPoint.getArgs());\n");
		fileWriter.write("\tif(((Integer)res[0])==0){\n");
		String args = "";
		parts = parameters.replaceAll("  ", " ").split(" ");
		int c = 2;
		for (int i = 0; i < parts.length; i+=2) {
			args += "(" + parts[i] + ")res[" + c++ + "], ";
		}
		fileWriter.write("\treturn proceed(" + args + " target);\n");
		fileWriter.write("\t} else {\n");
		fileWriter.write("\tMonitorHelper.removeMonitorInstanceId(monitorInstanceId);\n");
		fileWriter.write("\tthrow new Exception(\"Security Violation\");\n\t}\n");
		fileWriter.write("}\n");
		fileWriter.write("}\n");
		fileWriter.write("}");
	}

	private void generatePreMainPart(FileWriter fileWriter) throws IOException {
		fileWriter.write("Integer MonitorInstanceId = MonitorHelper.getMonitorInstanceId(thisJoinPoint, thisJoinPointStaticPart, thisEnclosingJoinPointStaticPart);\n");
		fileWriter.write("Integer currentLocation = MonitorHelper.getCurrentLocation(monitorInstanceId);\n");
		fileWriter.write("Object[] res = null;\n");
		fileWriter.write("\tboolean violation=false;\n");
	}

	@SuppressWarnings("unchecked")
	private void generateMainPart(String controllableAction, MethodDeclaration methodDeclaration, FileWriter fileWriter) throws IOException {
		boolean check = false;
		for (Transition transition : stsHelper.getTransitions()) {
			if(transition.getAction().equals(controllableAction)){
				check = true;
				fileWriter.write("if(currentLocation==" + transition.getSource() + " && " + 
				convertToJavaSyntax(transition.getGuard(), (Hashtable<String, String>) transition.getExtraData()) + "){\n");
				if(transition.getTarget()==0){
					fileWriter.write("\tMonitorHelper.removeMonitorInstanceId(monitorInstanceId);\n");
				}else if(transition.getExtraData().equals(STS.INSECURE)){
					fileWriter.write("\tcurrentLocation = " + transition.getTarget() + ";\n");
					fileWriter.write("\tviolation=true;\n");
				}else {
					fileWriter.write("\tcurrentLocation = " + transition.getTarget() + ";\n");
				}
				fileWriter.write("}else ");
			}
		}
		if(check){
			fileWriter.write("{ throw new Exception(\"Safety Violation\");}\n");
		}
		fileWriter.write("\n");
	}

	private void generateHeader(String qualifiedControllableMethodName, MethodDeclaration methodDeclaration,
			String parameters, String parameterNames, String className, FileWriter fileWriter) throws IOException {
		fileWriter.write("import " + className + ";\n");
		fileWriter.write("import se.lnu.MonitorHelper;\n");
		fileWriter.write("public aspect " + qualifiedControllableMethodName.replaceAll("\\.", "") + "Monitor{\n");
		fileWriter.write("pointcut pc() : call(* " + qualifiedControllableMethodName + "(..));\n");
		fileWriter.write(methodDeclaration.getReturnType2().toString() + " around(" + parameters + ", " + className + " target) : pc() && target(target) && args(" + parameterNames + ") {\n");
	}

	private String convertToJavaSyntax(String guard, Hashtable<String, String> argumentParameterMap) {
		guard = guard.replaceAll("=", " == ");
		guard = guard.replaceAll("<>", " != ");
		guard = guard.replaceAll(" and ", " && ");
		guard = guard.replaceAll(" or ", " || " );
		guard = guard.replaceAll(" not ", " ! ");
		String[] guardParts = guard.replaceAll("\\W\\d+\\W|(\\s*true\\s*)|(\\s*false\\s*)|\\s", "").split("\\W+");
		sort(guardParts);		
		for (String guardPart : guardParts) {
			if(!guardPart/*.replaceAll(" ", "")*/.equals("")){
				if(argumentParameterMap.get(guardPart)!=null){
					guard = guard.replaceAll(guardPart, stsHelper.getJavaName(argumentParameterMap.get(guardPart)));
				}else{
					guard = guard.replaceAll(guardPart, "target." + stsHelper.getJavaName(guardPart));
				}
			}
		}
		return guard;
	}

	private void sort(String[] guardParts) {
		if(guardParts.length>1){
			boolean sorted = false;
			while (!sorted) {
				for (int i = 0; i < guardParts.length-1; i++) {
					sorted = true;
					if(guardParts[i].length()<guardParts[i+1].length()){
						String temp = guardParts[i];
						guardParts[i] = guardParts[i+1];
						guardParts[i+1] = temp;
						sorted = false;
					}
				}
			}
		}
	}

	private MethodDeclaration getMethodDeclaration(String qualifiedMethodName) {
		JavaFileHelper javaFileHelper = javaProjectHelper.getJavaFileHelper(qualifiedMethodName.substring(0, qualifiedMethodName.lastIndexOf(".")));
		String methodName = qualifiedMethodName.substring(qualifiedMethodName.lastIndexOf(".") + 1, qualifiedMethodName.length());
		MethodDeclaration res = null;
		for (MethodDeclaration methodDeclaration : ((TypeDeclaration)javaFileHelper.getCompilationUnit().types().get(0)).getMethods()) {
			if(methodDeclaration.getName().toString().equals(methodName)){
				res = methodDeclaration;
				break;
			}
		}
		return res;
	}
}
