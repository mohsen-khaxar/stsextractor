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
		this.targetPath = targetPath + File.separator + "aspects";
	}
	
	public void generate() throws IOException{
		Utils.remakeDirectory(targetPath);
		for (String monitorableAction : stsHelper.monitorableActions) {
			String qualifiedMonitorableMethodName = stsHelper.getQualifiedMethodName(monitorableAction);
			MethodDeclaration methodDeclaration = getMethodDeclaration(qualifiedMonitorableMethodName);
			String parameters = methodDeclaration.parameters().toString().replace("[", "").replace("]", "");
			String parameterNames = "";
			String[] parts = parameters.replaceAll(" ,", ",").replaceAll("  ", " ").split(" ");
			for (int i = 1; i < parts.length; i+=2) {
				parameterNames += parts[i];
			}
			String className = qualifiedMonitorableMethodName.substring(0, qualifiedMonitorableMethodName.lastIndexOf("."));
			FileWriter fileWriter = new FileWriter(targetPath + File.separator + qualifiedMonitorableMethodName.replaceAll("\\.", "") + "Monitor.aj");
			String code = "";
			code += generateHeader(qualifiedMonitorableMethodName, methodDeclaration, parameters, parameterNames, className);
			code += generatePreMainPart();
			code += generateMainPart(monitorableAction, methodDeclaration);
			code += generatePostMainPart(qualifiedMonitorableMethodName, parameters);
			fileWriter.write(code);
			fileWriter.close();
		}
	}

	private String generatePostMainPart(String qualifiedControllableMethodName, String parameters)
			throws IOException {
		String[] parts;
		String code = "";
		code += "MonitorHelper.setCurrentLocation(monitorInstanceId, currentLocation);\n";
		code += "if(violation){\n";
		code += "\tres = MonitorHelper.applyCountermeasure(\"" + qualifiedControllableMethodName + "\", target, thisJoinPoint.getArgs());\n";
		code += "\tif(((Integer)res[0])==0){\n";
		String args = "";
		if(!parameters.matches("\\s*")){
			parts = parameters.replaceAll("  ", " ").split(" ");
			int c = 2;
			for (int i = 0; i < parts.length; i+=2) {
				args += "(" + parts[i] + ")res[" + c++ + "], ";
			}
		}
		code += "\treturn proceed(" + args + " target);\n";
		code += "\t} else {\n";
		code += "\tMonitorHelper.removeMonitorInstanceId(monitorInstanceId);\n";
		code += "\tthrow new Exception(\"Security Violation\");\n\t}\n";
		code += "}\n";
		code += "}\n";
		code += "}";
		return code;
	}

	private String generatePreMainPart() throws IOException {
		String code = "";
		code += "Integer MonitorInstanceId = MonitorHelper.getMonitorInstanceId(thisJoinPoint);\n";
		code += "Integer currentLocation = MonitorHelper.getCurrentLocation(monitorInstanceId);\n";
		code += "Object[] res = null;\n";
		code += "\tboolean violation=false;\n";
		return code;
	}

	private String generateMainPart(String controllableAction, MethodDeclaration methodDeclaration) throws IOException {
		boolean check = false;
		String code = "";
		for (Transition transition : stsHelper.getTransitions()) {
			if(transition.getAction().equals(controllableAction)){
				check = true;
				code += "if(currentLocation==" + transition.getSource() + " && " + 
				convertToJavaSyntax(transition.getGuard(), transition.getExtraData()) + "){\n";
				if(stsHelper.getOutDegree(transition.getTarget())==0){
					code += "\tMonitorHelper.removeMonitorInstanceId(monitorInstanceId);\n";
				}else if(transition.getExtraData()!=null&&transition.getExtraData().get("@status").equals(STS.INSECURE)){
					code += "\tcurrentLocation = " + transition.getTarget() + ";\n";
					code += "\tviolation=true;\n";
				}else {
					code += "\tcurrentLocation = " + transition.getTarget() + ";\n";
				}
				code += "}else ";
			}
		}
		if(check){
			code += "{ throw new Exception(\"Safety Violation\");}\n";
		}
		code += "\n";
		return code;
	}

	private String generateHeader(String qualifiedControllableMethodName, MethodDeclaration methodDeclaration,
			String parameters, String parameterNames, String className) throws IOException {
		String code = "";
		code += "import " + className + ";\n";
		code += "import se.lnu.MonitorHelper;\n";
		code += "public aspect " + qualifiedControllableMethodName.replaceAll("\\.", "") + "Monitor{\n";
		code += "pointcut pc() : call(* " + qualifiedControllableMethodName + "(..));\n";
		code += methodDeclaration.getReturnType2().toString() + " around(" + parameters + ", " + className + " target) : pc() && target(target) && args(" + parameterNames + ") {\n";
		return code;
	}

	private String convertToJavaSyntax(String guard, Hashtable<String, Object> argumentParameterMap) {
		guard = guard.replaceAll("=", " == ");
		guard = guard.replaceAll("> == ", " >= ");
		guard = guard.replaceAll("< == ", " <= ");
		guard = guard.replaceAll("<>", " != ");
		guard = guard.replaceAll(" and ", " && ");
		guard = guard.replaceAll(" or ", " || " );
		guard = guard.replaceAll(" not ", " ! ");
		String[] guardParts = guard.replaceAll("\\W\\d+\\W|(\\s*true\\s*)|(\\s*false\\s*)|\\s", "").split("\\W+");
		sort(guardParts);		
		for (String guardPart : guardParts) {
			if(!guardPart.equals("")){
				if(argumentParameterMap!=null&&argumentParameterMap.get(guardPart)!=null){
					guard = guard.replaceAll(guardPart, stsHelper.getJavaName(argumentParameterMap.get(guardPart).toString()));
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
		MethodDeclaration res = null;
		if(qualifiedMethodName.equals("se.lnu.DummyMethods.monitorablePoint")){
			JavaFileHelper javaFileHelper = new JavaFileHelper("", null, "", null);
			res = (MethodDeclaration) javaFileHelper.parseMethodDeclaration("static public void monitorablePoint(){}");
		}else{
			JavaFileHelper javaFileHelper = javaProjectHelper.getJavaFileHelper(qualifiedMethodName.substring(0, qualifiedMethodName.lastIndexOf(".")));
			String methodName = qualifiedMethodName.substring(qualifiedMethodName.lastIndexOf(".") + 1, qualifiedMethodName.length());
			for (MethodDeclaration methodDeclaration : ((TypeDeclaration)javaFileHelper.getCompilationUnit().types().get(0)).getMethods()) {
				if(methodDeclaration.getName().toString().equals(methodName)){
					res = methodDeclaration;
					break;
				}
			}
		}
		return res;
	}
}
