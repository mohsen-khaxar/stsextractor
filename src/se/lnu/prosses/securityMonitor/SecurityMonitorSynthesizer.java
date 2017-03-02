package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class SecurityMonitorSynthesizer {
	STSHelper stsHelper;
	String targetPath;
	private ReaxHelper reaxHelper;
	
	public SecurityMonitorSynthesizer(STSHelper stsHelper, String targetPath) {
		this.stsHelper = stsHelper;
		this.targetPath = targetPath;
		this.reaxHelper = new ReaxHelper(stsHelper, targetPath);
	}
	
	private void propagateInitialValues(){
		propagateInitialValues(true);
		propagateInitialValues(false);
	}
	
	private void propagateInitialValues(boolean first){
		Queue<Integer> queue = new LinkedList<>();
		for (Transition transition : stsHelper.getOutgoingTransitions(0)) {
			queue.add(transition.getTarget());
		}
		ArrayList<Integer> visited = new ArrayList<>();
		while(!queue.isEmpty()){
			Integer location = queue.poll();
			Hashtable<String, String> initialValues = getIncomingInitialValues(location);
			for (Transition transition : stsHelper.getOutgoingTransitions(location)) {
				String update = transition.getUpdate().replaceAll("==", "#").replaceAll("!=", "<>");
				String newUpdate = update.replaceAll("\\s*=", "=");
				for (String variable : initialValues.keySet()) {
					if(!newUpdate.contains(variable+"=")){
						String initialValue = initialValues.get(variable);
						if(!first && initialValue.contains("{")){
							initialValue = initialValue.substring(initialValue.indexOf("{"), initialValue.indexOf("}")).split(",")[1];
						}
						newUpdate = variable + "=" + initialValue + ";" + newUpdate;  
					}
				}
				if(!first){
					String regex = "\\{[\\w_$]+\\,\\s*(true|false|[-+]?\\d*\\.?\\d+)\\s*\\}";
					Pattern pattern = Pattern.compile(regex);
			        Matcher matcher = pattern.matcher(newUpdate);
			        StringBuffer stringBuffer = new StringBuffer();
			        while (matcher.find()) {
			        	String variable = matcher.group();
			        	variable = variable.substring(variable.indexOf("{"), variable.indexOf("}")).split(",")[0];
			        	matcher.appendReplacement(stringBuffer, variable);
			        }
			        matcher.appendTail(stringBuffer);
			        newUpdate = stringBuffer.toString();
				}
				newUpdate = propagateInitialValuesAcrossUpdate(newUpdate);
				transition.setUpdate(newUpdate.replaceAll("#", "==").replaceAll("<>", "!="));
				if(!visited.contains(transition.getTarget()) /*|| !newUpdate.replaceAll("\\s", "").equals(update.replaceAll("\\s", ""))*/){
					queue.add(transition.getTarget());
				}
			}
			visited.add(location);
		}
	}
	
	private String propagateInitialValuesAcrossUpdate(String update) {
		if(!update.matches("\\s*")){
			String[] parts = update.split(";");
			for (int i=0; i<parts.length; i++) {
				if(!parts[i].matches("\\s*")){
					String leftHandSide = parts[i].split("=")[0].replaceAll("\\s", "");
					String rightHandSide = parts[i].split("=")[1];
					String regex = "\\s*(true|false|[-+]?\\d*\\.?\\d+)\\s*";
					regex = "(" + regex + ")|(\\{[\\w_$]+\\," + regex + "\\})";
					if(rightHandSide.matches(regex)){
						for (int j = i+1; j < parts.length; j++) {
							String leftHandSide2 = parts[j].split("=")[0].replaceAll("\\s", "");
							String rightHandSide2 = parts[j].split("=")[1];
							if(!leftHandSide.equals(leftHandSide2)||(" "+rightHandSide2+" ").matches(".*\\W"+leftHandSide+"\\W.*")){
								parts[j] = parts[j].split("=")[0] + "=" + replace(parts[j].split("=")[1], leftHandSide, rightHandSide);
							}else{
								parts[i] = "";
								break;
							}
						}
					}
				}
			}
			update = "";
			for (String part : parts) {
				if(!part.matches("\\s*")){
					update += part + ";";
				}
			}
		}
		return update;		
	}
	
	
	private Hashtable<String, String> getIncomingInitialValues(Integer location) {
		List<Transition> incomingTransitions = stsHelper.getIncomingTransitions(location);
		Hashtable<String, String> incomingInitialValues = new Hashtable<>();
		boolean first = true;
		for (Transition transition : incomingTransitions) {
			String[] parts = transition.getUpdate().split(";");
			ArrayList<String> leftHandSideVariables = new ArrayList<>();
			for (String part : parts) {
				part = part.replaceAll("\\s", "");
				if(!part.equals("")){
					String leftHandSide = part.split("=")[0];
					leftHandSideVariables.add(leftHandSide);
					String rightHandSide = part.split("=")[1];
					String regex = "\\s*(true|false|[-+]?\\d*\\.?\\d+)\\s*";
					regex = "(" + regex + ")|(\\{[\\w_$]+\\," + regex + "\\})";
					if(rightHandSide.matches(regex)){
						if(incomingInitialValues.get(leftHandSide)==null){
							if(first){
								incomingInitialValues.put(leftHandSide, rightHandSide);
							}else{
								incomingInitialValues.put(leftHandSide, "V");
							}
						}else {
							String value1 = incomingInitialValues.get(leftHandSide);
							if(value1.contains("{")){
								value1 = value1.substring(value1.indexOf("{"), value1.indexOf("}")).split(",")[1];
							}
							String value2 = rightHandSide;
							if(rightHandSide.contains("{")){
								value2 = value2.substring(value2.indexOf("{"), value2.indexOf("}")).split(",")[1];
							}
							if(!value1.equals(value2)){	
								incomingInitialValues.put(leftHandSide, "V");
							}
						}
					}else{
						incomingInitialValues.put(leftHandSide, "V");
					}
				}
			}
//			for (String variable : incomingInitialValues.keySet()) {
//				if(!leftHandSideVariables.contains(variable)){
//					incomingInitialValues.put(variable, "V");
//				}
//			}
			first = false;
		}
		ArrayList<String> keys = new ArrayList<>();
		keys.addAll(incomingInitialValues.keySet());
		for (String key : keys) {
			if(incomingInitialValues.get(key).equals("V")){
				incomingInitialValues.remove(key);
			}
		}
		return incomingInitialValues;
	}
	
	private String replace(String string, String find, String replace) {
		String regex = "\\W" + find + "\\W";
		Pattern pattern = Pattern.compile(regex);
		string = " " + string.replaceAll("\\{\\s*", "") + " ";
        Matcher matcher = pattern.matcher(string);
        StringBuffer stringBuffer = new StringBuffer();
        while (matcher.find()) {
        	String temp = matcher.group();
        	if(!temp.startsWith("{")){
        		temp = temp.charAt(0) + replace + temp.charAt(temp.length()-1);
        	}
        	matcher.appendReplacement(stringBuffer, temp);
        }
        matcher.appendTail(stringBuffer);
        return stringBuffer.toString();
	}
	
	@SuppressWarnings("unchecked")
	public void generateAspect(String sourcePath, String targetPath) throws IOException{
		Hashtable<String, TypeDeclaration> classes = getClasses(sourcePath);
		HashSet<String> methodNames = new HashSet<>();
		methodNames.addAll(controllableMethodNames);
		methodNames.add("se.lnu.CaseStudy.dummy1");
		methodNames.add("se.lnu.CaseStudy.dummy2");
		for (String controllableMethodName : methodNames) {
			MethodDeclaration methodDeclaration = getMethodDeclaration(classes, controllableMethodName);
			String parameters = methodDeclaration.parameters().toString().replace("[", "").replace("]", "");
			String parameterNames = "";
			String[] parts = parameters.replaceAll(" ,", ",").replaceAll("  ", " ").split(" ");
			for (int i = 1; i < parts.length; i+=2) {
				parameterNames += parts[i];
			}
			String className = controllableMethodName.substring(0, controllableMethodName.lastIndexOf("."));
			FileWriter fileWriter = new FileWriter(targetPath + File.separator + controllableMethodName.replaceAll("\\.", "") + "Monitor.aj");
			fileWriter.write("import " + className + ";\n");
			fileWriter.write("import se.lnu.MonitorHelper;\n");
			fileWriter.write("public aspect " + controllableMethodName.replaceAll("\\.", "") + "Monitor{\n");
			fileWriter.write("pointcut pc() : call(* " + controllableMethodName + "(..));\n");
			fileWriter.write(methodDeclaration.getReturnType2().toString() + " around(" + parameters + ", " + className + " target) : pc() && target(target) && args(" + parameterNames + ") {\n");
//			fileWriter.write("MonitorHelperImpl monitorHelper = new MonitorHelperImpl();\n");
//			fileWriter.write("try {monitorHelper = (MonitorHelper)Class.forName(\"se.lnu.MonitorHelperImpl\").newInstance();} catch (Exception e) {e.printStackTrace();}\n");
			fileWriter.write("Integer MonitorInstanceId = MonitorHelper.getMonitorInstanceId(thisJoinPoint, thisJoinPointStaticPart, thisEnclosingJoinPointStaticPart);\n");
			fileWriter.write("Integer currentLocation = MonitorHelper.getCurrentLocation(monitorInstanceId);\n");
			fileWriter.write("Object[] res = null;\n");
			fileWriter.write("\tboolean violation=false;\n");
			boolean check = false;
			for (Transition transition : sts.edgeSet()) {
				if(transition.getAction().equals(controllableMethodName.replaceAll("\\.", "_")) && sts.getEdgeSource(transition)>=0){
					check = true;
					fileWriter.write("if(currentLocation==" + sts.getEdgeSource(transition) + " && " + 
					convertToJavaSyntax(transition.getGuard(), methodDeclaration.parameters(), getArgumentParameterMap(transition.getUpdate())) + "){\n");
					if(sts.getEdgeTarget(transition)==0){
						fileWriter.write("\tMonitorHelper.removeMonitorInstanceId(monitorInstanceId);\n");
					}else if(sts.getEdgeTarget(transition)<0){
						fileWriter.write("\tcurrentLocation = " + sts.getEdgeTarget(transition) + ";\n");
						fileWriter.write("\tviolation=true;\n");
					}else {
						fileWriter.write("\tcurrentLocation = " + sts.getEdgeTarget(transition) + ";\n");
					}
					fileWriter.write("}else ");
				}
			}
			if(check){
				fileWriter.write("{ throw new Exception(\"Safty Violation\");}\n");
			}
			fileWriter.write("\n");
			for (Transition transition : sts.edgeSet()) {
				if(transition.getAction().equals(controllableMethodName.replaceAll("\\.", "_")) && sts.getEdgeSource(transition)<0){
					check = true;
					fileWriter.write("if(currentLocation==" + sts.getEdgeSource(transition) + " && " + 
					convertToJavaSyntax(transition.getGuard(), methodDeclaration.parameters(), getArgumentParameterMap(transition.getUpdate())) + "){\n");
					if(sts.getEdgeTarget(transition)==0){
						fileWriter.write("\tMonitorHelper.removeMonitorInstanceId(monitorInstanceId);\n");
					}else {
						fileWriter.write("\tcurrentLocation = " + sts.getEdgeTarget(transition) + ";\n");
					}
					fileWriter.write("}else ");
				}
			}
			if(check){
				fileWriter.write("{ throw new Exception(\"Safty Violation\");}\n");
			}
			fileWriter.write("MonitorHelper.setCurrentLocation(monitorInstanceId, currentLocation);\n");
			fileWriter.write("if(violation){\n");
			fileWriter.write("\tres = MonitorHelper.applyCountermeasure(\"" + controllableMethodName + "\", target, thisJoinPoint.getArgs());\n");
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
			fileWriter.close();
		}
	}
	
	private Map<String, String> getArgumentParameterMap(String upadater) {
		HashMap<String, String> res = new HashMap<>();
		if(!upadater.replaceAll("\\s", "").equals("")){
			String[] parts = upadater.split(";");
			for (String part : parts) {
				String leftHandSide = part.replaceAll("\\s", "").split("=")[0];
				String rightHandSide = part.replaceAll("\\s", "").split("=")[1];
				if(!leftHandSide.matches("L(XC|IC|XI|II)_")){
					res.put(rightHandSide, leftHandSide);
				}
			}
		}
		return res;
	}

	private String convertToJavaSyntax(String guard, List<SingleVariableDeclaration> parameters, Map<String, String> argumentParameterMap) {
		guard = guard.replaceAll("=", " == ");
		guard = guard.replaceAll("<>", " != ");
		guard = guard.replaceAll(" and ", " && ");
		guard = guard.replaceAll(" or ", " || " );
		guard = guard.replaceAll(" not ", " ! ");
		String[] guardParts = guard.replaceAll("\\W\\d+\\W|(\\s*true\\s*)|(\\s*false\\s*)|\\s", "").split("\\W+");
		sort(guardParts);		
		for (String guardPart : guardParts) {
			if(!guardPart/*.replaceAll(" ", "")*/.equals("")){
				String[] parts = null;
				if(argumentParameterMap.get(guardPart)!=null){
					parts = argumentParameterMap.get(guardPart).split("_");
				}else{
					parts = guardPart.split("_");
				}
				boolean check = true;
				for (int i = 0; i < parameters.size(); i++) {
					if(parts[parts.length-1].replaceAll(" ", "").equals(parameters.get(i).getName().toString().replaceAll(" ", ""))){
						check = false;
					}
				}
				if(check /*&& !parts[parts.length-1].matches("(\\s*[+-]?\\d*.?\\d+\\s*)|(\\s*true\\s*)|(\\s*false\\s*)")*/){
					guard = guard.replaceAll(guardPart, "target." + parts[parts.length-1]);
				}else{
					guard = guard.replaceAll(guardPart, parts[parts.length-1]);
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

	private MethodDeclaration getMethodDeclaration(Hashtable<String, TypeDeclaration> classes, String methodFullName) {
		String className = methodFullName.substring(0, methodFullName.lastIndexOf("."));
		String methodName = methodFullName.substring(methodFullName.lastIndexOf(".") + 1, methodFullName.length());
		MethodDeclaration res = null;
		for (MethodDeclaration methodDeclaration : classes.get(className).getMethods()) {
			if(methodDeclaration.getName().toString().equals(methodName)){
				res = methodDeclaration;
				break;
			}
		}
		return res;
	}
	
	STSHelper synthesizeControlledSTS() throws Exception{
		Hashtable<String, String> securityGuards = reaxHelper.run();
//		STSHelper stsHelper = (STSHelper) stsHelper.clone();
		if(!securityGuards.isEmpty()){
			Set<Transition> transitions = new HashSet<>();
			transitions.addAll(stsHelper.getTransitions());
			for (Transition transition : transitions) {
				if(stsHelper.controllableActions.contains(transition.getAction())){
					String guard = "";
					String separator = "";
					if(securityGuards.get(transition.getAction().replaceAll("\\s", "")+",-1")!=null){
						guard = securityGuards.get(transition.getAction().replaceAll("\\s", "")+",-1");
						separator = " or ";
					}
					if(securityGuards.get(transition.getAction().replaceAll("\\s", "")+","+transition.getSource())!=null){
						guard += separator + securityGuards.get(transition.getAction().replaceAll("\\s", "")+","+transition.getSource());
					}
					if(guard.equals("")){
						guard = "false";
					}
					if(!guard.matches("(true|\\sand\\s|\\sor\\s|\\s)*")){
						guard = guard.replaceAll("\\s+", " ");
						transition.setGuard(guard);
						stsHelper.addTransition(transition.getSource(), transition.getTarget(), transition.getAction(), " not (" + guard + ")", "", STS.INSECURE);
					}
				}
			}
		}
		return stsHelper;
	}
	
	private STSHelper makeUnmonitorableFree(){
//		STSHelper stsHelper = (STS) stsHelper.clone();
		Transition unmonitorableTransition = nextUnmonitorable(stsHelper);
		while(unmonitorableTransition!=null){
			Set<Transition> incomingTransitions = new HashSet<>();
			incomingTransitions.addAll(stsHelper.getIncomingTransitions(unmonitorableTransition.getSource()));
			for (Transition incomingTransition : incomingTransitions) {
				mergeTransitions(stsHelper, incomingTransition, unmonitorableTransition);
			}
			stsHelper.removeTransition(unmonitorableTransition);
			if(stsHelper.getOutgoingTransitions(unmonitorableTransition.getSource()).size()==0){
				stsHelper.removeLocation(unmonitorableTransition.getSource());
			}
			unmonitorableTransition = nextUnmonitorable(stsHelper);
		}
		clearUpdates(stsHelper);
		removeSTARTTransitions(stsHelper);
		return stsHelper;
	}
	
	private void removeSTARTTransitions(STSHelper stsHelper) {
		Set<Transition> transitions = new HashSet<>();
		for (Transition transition : stsHelper.getTransitions()) {
			if(transition.getAction().equals(STS.START)){
				transitions.add(transition);
			}
		}
		for (Transition transition : transitions) {
			Integer target = transition.getTarget();
			Set<Transition> outgoingTransitions = new HashSet<>();
			outgoingTransitions.addAll(stsHelper.getOutgoingTransitions(target));
			for (Transition outgoingTransition : outgoingTransitions) {
				stsHelper.removeTransition(outgoingTransition);
				stsHelper.addTransition(0, outgoingTransition.getTarget(), outgoingTransition.getAction(), outgoingTransition.getGuard(), outgoingTransition.getUpdate());
			}
			Set<Transition> incomingTransitions = new HashSet<>();
			incomingTransitions.addAll(stsHelper.getIncomingTransitions(target));
			for (Transition incomingTransition : incomingTransitions) {
				stsHelper.removeTransition(incomingTransition);
				stsHelper.addTransition(incomingTransition.getSource(), 0, incomingTransition.getAction(), incomingTransition.getGuard(), incomingTransition.getUpdate());
			}
			stsHelper.removeTransition(transition);
			stsHelper.removeLocation(target);
		}
	}

	private void clearUpdates(STSHelper stsHelper) {
		Set<Transition> transitions = new HashSet<>();
		transitions.addAll(stsHelper.getTransitions());
		for (Transition transition : transitions) {
			String update = "";
			Integer source = transition.getSource();
			Integer target = transition.getTarget();
			if(transition.getAction().equals(STS.PARAMETER)){
				if(!transition.getUpdate().matches("\\s*")){
					update = transition.getUpdate().replaceAll("L(XC|IC|XI|II)_", "@@");
					update = update.substring(0, update.indexOf("@@"));
				}				
				Set<Transition> outgoingTransitions = new HashSet<>();
				outgoingTransitions.addAll(stsHelper.getOutgoingTransitions(target));
				for (Transition transition2 : outgoingTransitions) {
					transition2.setUpdate(update);
					stsHelper.removeTransition(transition2);
					stsHelper.addTransition(source, transition2.getTarget(), transition2.getAction(), transition2.getGuard(), transition2.getUpdate());
				}
				if(stsHelper.getOutDegree(target)==0){
					stsHelper.removeLocation(target);
				}
				stsHelper.removeTransition(transition);
			}else if(transition.getAction().equals(STS.RETURN)){
				stsHelper.removeAllTransitions(source, target);
				stsHelper.addTransition(source, target, ((Transition)(stsHelper.getIncomingTransitions(source).toArray()[0])).getAction(), 
						"true", "");
			}
		}
	}

	private void mergeTransitions(STSHelper stsHelper, Transition incomingTransition, Transition outgoingTransition) {
		if(stsHelper.monitorableActions.contains(incomingTransition.getAction()) || 
				incomingTransition.getSource()!=outgoingTransition.getTarget()){
			String guard1 = STSHelper.convertToSTSSyntax(incomingTransition.getGuard()).replaceAll("\\s+", " ");
			if(needParenthesis(guard1)){
				guard1 = "(" + guard1 + ")";
			}
			String guard2 = getWeakestPrecondition(STSHelper.convertToSTSSyntax(outgoingTransition.getGuard())
					, incomingTransition.getUpdate()).replaceAll("\\s+", " ");
			if(!guard2.matches(" true | false ") && guard2.contains(" or ")){
				guard2 = "(" + guard2 + ")";
			}
			String guard = guard1 + " and " + guard2;
			guard = guard.replaceAll("\\s?true\\s?and\\s?", " ").replaceAll("\\s?and\\s?true\\s?", " ").replaceAll("\\s+", " ");
			String update = incomingTransition.getUpdate() + outgoingTransition.getUpdate();
			stsHelper.addTransition(incomingTransition.getSource(), outgoingTransition.getTarget(), incomingTransition.getAction(), guard, update);
		}
	}
	
	private boolean needParenthesis(String guard) {
		boolean res = !guard.matches(" true | false ") 
				&& guard.contains(" or ")
				&& !guard.matches(".* or [^\\(]*\\).*");
		guard = guard.replaceAll("\\s", "");
		if(guard.charAt(0)=='(' && guard.charAt(guard.length()-1)==')'){
			int c = 0;
			for (int i = 1; i < guard.length()-1; i++) {
				if(guard.charAt(i)=='('){
					c++;
				}else if(guard.charAt(i)==')'){
					c--;
				}
				if(c<0){
					break;
				}
			}
			if(c==0){
				res = false;
			}
		}
		return res;
	}

	private Transition nextUnmonitorable(STSHelper stsHelper) {
		Transition unmonitorableTransition = null;
		for (Transition transition : stsHelper.getTransitions()) {
			if(!stsHelper.monitorableActions.contains(transition.getAction())){
				unmonitorableTransition = transition;
				break;
			}
		}
		return unmonitorableTransition;
	}
	
	private String getWeakestPrecondition(String guard, String upadater) {
		String[] assignments = upadater.split(";");
		for (int i=assignments.length-1; i>=0; i--) {
			guard = replaceWithAssignment(guard, assignments[i]);
		}
		return guard;
	}
	
	private String replaceWithAssignment(String guard, String assignment) {
		try{
		String[] assignmentParts = assignment.split("=");
		String[] guardParts = ("@" + guard + "@").split(assignmentParts[0].replaceAll(" ", ""));
		String replace = "";
		String separator = "";
		for (int i=1; i<assignmentParts.length; i++) {
			replace += separator + assignmentParts[i];
			separator = "=";
		}
		guard = "";
		for (int i=0; i<guardParts.length-1; i++) {
			guard += guardParts[i];
			if(!Character.isJavaIdentifierPart(guardParts[i].charAt(guardParts[i].length()-1))
					&& !Character.isJavaIdentifierPart(guardParts[i+1].charAt(0))){
				guard += replace;
			}else{
				guard += assignmentParts[0].replaceAll(" ", "");
			}
		}
		guard += guardParts[guardParts.length-1];
		guard = guard.substring(1, guard.length()-1);
		}catch(Exception e){
			e.printStackTrace();
		}
		return guard;
	}
	
	public void generateAspect() throws IOException{
		stsHelper.generateAspect(sourcePath, targetPath);
	}

	public void synthesize() throws Exception{
		propagateInitialValues();
		stsHelper.saveAsDot(targetPath + File.separator + "initValuesPropagatedSts.dot");
		STSHelper controlledSTSHelper = synthesizeControlledSTS();
		controlledSTSHelper.saveAsDot(targetPath + File.separator +"controlledSts.dot");
		controlledSTSHelper = makeUnmonitorableFree();
		controlledSTSHelper.saveAsDot("/home/mohsen/git/runningexample/aspects/freemodelc.dot");
		controlledSTSHelper.generateAspect(sourcePath, "/home/mohsen/git/runningexample/aspects");
	}
}
