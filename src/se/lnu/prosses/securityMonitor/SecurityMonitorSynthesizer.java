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
	
	public void propagateInitialValues(){
		Queue<Integer> queue = new LinkedList<>();
		for (Transition transition : sts.outgoingEdgesOf(0)) {
			queue.add(sts.getEdgeTarget(transition));
		}
		ArrayList<Integer> visited = new ArrayList<>();
		while(!queue.isEmpty()){
			Integer location = queue.poll();
			if(location==5){
				System.out.println(location);
			}
			Hashtable<String, String> initialValues = getInitialValues(sts.incomingEdgesOf(location));
			for (Transition transition : sts.outgoingEdgesOf(location)) {
				String updater = transition.getUpdate().replaceAll("==", "#").replaceAll("!=", "<>");
				String newUpdater = updater.replaceAll("\\s*=", "=");
				for (String variable : initialValues.keySet()) {
					if(!newUpdater.contains(variable+"=")){
						newUpdater = variable + "=" + initialValues.get(variable) + ";" + newUpdater;  
					}
				}
				newUpdater = propagateInitialValuesAcrossUpdater(newUpdater);
//				if(/*variable.equals("LXI_se_lnu_User_getStrangerInformation___X0")*/newUpdater.matches(".*false\\s*=.*")){
//					System.out.println(newUpdater);
//				}
				transition.setUpdate(newUpdater.replaceAll("#", "==").replaceAll("<>", "!="));
				if(sts.getEdgeTarget(transition)!=0 && (!visited.contains(sts.getEdgeTarget(transition))
						|| !newUpdater.replaceAll("\\s", "").equals(updater.replaceAll("\\s", "")))){
					queue.add(sts.getEdgeTarget(transition));
				}
			}
			visited.add(location);
		}
	}
	
	private String propagateInitialValuesAcrossUpdater(String updater) {
		if(!updater.matches("\\s*")){
			String[] parts = updater.split(";");
			for (int i=0; i<parts.length; i++) {
				if(!parts[i].matches("\\s*")){
					String left = parts[i].split("=")[0].replaceAll("\\s", "");
					String right = parts[i].split("=")[1];
					if(right.matches("\\s*(true|false|[-+]?\\d*\\.?\\d+)\\s*")){
						for (int j = i+1; j < parts.length; j++) {
							String left2 = parts[j].split("=")[0].replaceAll("\\s", "");
							String right2 = parts[j].split("=")[1];
							if(!left.equals(left2)||(" "+right2+" ").matches(".*\\W"+left+"\\W.*")){
								parts[j] = parts[j].split("=")[0] + "=" + replace(parts[j].split("=")[1], left, right);
							}else{
								parts[i] = "";
								break;
							}
						}
					}
				}
			}
			updater = "";
			for (String part : parts) {
				if(!part.matches("\\s*")){
					updater += part + ";";
				}
			}
		}
		return updater;		
	}
	
	
	private Hashtable<String, String> getInitialValues(Set<Transition> transitions) {
		Hashtable<String, String> res = new Hashtable<>();
		boolean first = true;
		for (Transition transition : transitions) {
			String[] parts = transition.getUpdate().split(";");
			ArrayList<String> variables = new ArrayList<>();
			for (String part : parts) {
				part = part.replaceAll("\\s", "");
				variables.add(part.split("=")[0]);
				if(!part.equals("")){
					if(part.split("=")[1].matches("\\s*(true|false|[-+]?\\d*\\.?\\d+)\\s*")){
						if(res.get(part.split("=")[0])==null){
							if(first){
								res.put(part.split("=")[0], part.split("=")[1]);
							}else{
								res.put(part.split("=")[0], "V");
							}
						}else if(!res.get(part.split("=")[0]).equals(part.split("=")[1])){
							res.put(part.split("=")[0], "V");
						}
					}else{
						res.put(part.split("=")[0], "V");
					}
				}
			}
			for (String variable : res.keySet()) {
				if(!variables.contains(variable)){
					res.put(variable, "V");
				}
			}
			first = false;
		}
		ArrayList<String> keys = new ArrayList<>();
		keys.addAll(res.keySet());
		for (String key : keys) {
			if(res.get(key).equals("V")){
				res.remove(key);
			}
		}
		return res;
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
	
	private String replace(String string, String left, String right) {
		String[] parts = ("@"+string+"@").split(left);
		String res = "";
		for (int i=0;i<parts.length-1;i++) {
			if(!Character.isJavaIdentifierPart(parts[i].charAt(parts[i].length()-1))
					&& !Character.isJavaIdentifierPart(parts[i+1].charAt(0))){
				res += parts[i] + right;
			}else{
				res += parts[i] + left;
			}
		}
		res += parts[parts.length-1];
		res = res.replaceAll("@", "");
		return res;
	}
	
	public STS generateControlledSTS(){
		Hashtable<String, String> securityGuards = reaxHelper.run();
		return makeControlled(securityGuards);
	}
	STSHelper makeControlled(Hashtable<String, String> securityGuards){
		STS res = (STS) sts.clone();
		if(!securityGuards.isEmpty()){
			Set<Transition> transitions = new HashSet<>();
			transitions.addAll(res.edgeSet());
			for (Transition transition : transitions) {
				if(res.controllableEvents.contains(transition.getAction()) && !transition.getAction().equals(STS.START)
						&& !transition.getAction().equals(STS.RETURN) && !transition.getAction().equals(STS.PARAMETER)){
					String guard = "";
					String separator = "";
					if(securityGuards.get(transition.getAction().replaceAll("\\s", "")+",-1")!=null){
						guard = securityGuards.get(transition.getAction().replaceAll("\\s", "")+",-1");
						separator = " or ";
					}
					if(securityGuards.get(transition.getAction().replaceAll("\\s", "")+","+res.getEdgeSource(transition))!=null){
						guard += separator + securityGuards.get(transition.getAction().replaceAll("\\s", "")+","+res.getEdgeSource(transition));
					}
					if(guard.equals("")){
						guard = "false";
					}
					if(!guard.matches("(true|\\sand\\s|\\sor\\s|\\s)*")){
						transition.setGuard(guard);
						Transition insecureTransition = new Transition(transition.getAction(), " not (" + guard.replaceAll("\\s+", " ") + ")", transition.update);
						if(!res.vertexSet().contains(-res.getEdgeTarget(transition))){
							res.addVertex(-res.getEdgeTarget(transition));
						}
						res.addEdge(res.getEdgeSource(transition), -res.getEdgeTarget(transition), insecureTransition);
						Transition returnTransition = new Transition(Transition.RETURN, "true", "");
						res.addEdge(-res.getEdgeTarget(transition), res.getEdgeTarget(transition), returnTransition);
						
						((Transition)(res.incomingEdgesOf(res.getEdgeSource(transition)).toArray()[0])).setAction(Transition.PARAMETER);
					}
				}
			}
		}
		return res;
	}
	
	public STS convertToUncontrollableFreeSTS(){
		STS sts = (STS) sts.clone();
		Transition transition = nextUncontrollable(sts);
		while(transition!=null){
//			System.out.println(sts.getEdgeSource(transition) + ", " + sts.getEdgeTarget(transition));
//			try {
//				STS sts2 = (STS) sts.clone();
//				for (Transition transition2 : sts2.edgeSet()) {
//					transition2.setUpadater("");
//					transition2.setGuard("(true)");;
//				}
//				sts2.saveAsDot("/home/mohsen/git/runningexample/aspects/m.dot");
//			} catch (Exception e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			Set<Transition> incomingTransitions = new HashSet<>();
			incomingTransitions.addAll(sts.incomingEdgesOf(sts.getEdgeSource(transition)));
			for (Transition incomingTransition : incomingTransitions) {
				mergeTransitions(sts, incomingTransition, transition);
			}
			sts.removeEdge(transition);
			if(sts.outgoingEdgesOf(sts.getEdgeSource(transition)).size()==0){
				sts.removeVertex(sts.getEdgeSource(transition));
			}
			transition = nextUncontrollable(sts);
//			try {
//				STS sts2 = (STS) sts.clone();
//				for (Transition transition2 : sts2.edgeSet()) {
//					transition2.setUpadater("");
//					transition2.setGuard("(true)");;
//				}
//				sts2.saveAsDot("/home/mohsen/git/runningexample/aspects/m.dot");
//			} catch (Exception e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
		}
		clearUpdaters(sts);
		removeSTARTTransitions(sts);
		return sts;
	}
	
	private void removeSTARTTransitions(STS sts) {
		Set<Transition> transitions = new HashSet<>();
		for (Transition transition : sts.edgeSet()) {
			if(transition.getAction().equals(STS.START)){
				transitions.add(transition);
			}
		}
		for (Transition transition : transitions) {
			Integer target = sts.getEdgeTarget(transition);
			Set<Transition> outgoingTransitions = new HashSet<>();
			outgoingTransitions.addAll(sts.outgoingEdgesOf(target));
			for (Transition outgoingTransition : outgoingTransitions) {
				sts.removeEdge(outgoingTransition);
				sts.addEdge(0, sts.getEdgeTarget(outgoingTransition), outgoingTransition);
			}
			Set<Transition> incomingTransitions = new HashSet<>();
			incomingTransitions.addAll(sts.incomingEdgesOf(target));
			for (Transition incomingTransition : incomingTransitions) {
				sts.removeEdge(incomingTransition);
				sts.addEdge(sts.getEdgeSource(incomingTransition), 0, incomingTransition);
			}
			sts.removeEdge(transition);
			sts.removeVertex(target);
		}
	}

	private void clearUpdaters(STS sts) {
		Set<Transition> transitions = new HashSet<>();
		transitions.addAll(sts.edgeSet());
		for (Transition transition : transitions) {
			String updater = "";
			Integer source = sts.getEdgeSource(transition);
			Integer target = sts.getEdgeTarget(transition);
			if(transition.getAction().equals(STS.PARAMETER)){
				if(!transition.getUpdate().matches("\\s*")){
					updater = transition.getUpdate().replaceAll("L(XC|IC|XI|II)_", "@@");
					updater = updater.substring(0, updater.indexOf("@@"));
				}				
				Set<Transition> outgoingTransitions = new HashSet<>();
				outgoingTransitions.addAll(sts.outgoingEdgesOf(target));
				for (Transition transition2 : outgoingTransitions) {
					transition2.setUpdate(updater);
					sts.removeEdge(transition2);
					sts.addEdge(source, sts.getEdgeTarget(transition2), transition2);
				}
				if(sts.outDegreeOf(target)==0){
					sts.removeVertex(target);
				}
				sts.removeEdge(transition);
			}else if(transition.getAction().equals(STS.RETURN)){
				sts.removeAllEdges(source, target);
				transition.setUpdate("");
				transition.setAction(((Transition)(sts.incomingEdgesOf(source).toArray()[0])).getAction());
				transition.setGuard("true");
				sts.addEdge(source, target, transition);
			}
		}
	}

	private void mergeTransitions(STS sts, Transition incomingTransition, Transition outgoingTransition) {
		if(controllableEvents.contains(incomingTransition.getAction()) || 
				incomingTransition.getAction().equals("se_lnu_CaseStudy_dummy1") || 
				incomingTransition.getAction().equals("se_lnu_CaseStudy_dummy2") || 
				sts.getEdgeSource(incomingTransition)!=sts.getEdgeTarget(outgoingTransition)){
			String guard1 = convertToSTSSyntax(incomingTransition.getGuard()).replaceAll("\\s+", " ");
			if(needParenthesis(guard1)){
				guard1 = "(" + guard1 + ")";
			}
			String guard2 = getWeakestPrecondition(convertToSTSSyntax(outgoingTransition.getGuard())
					, getControllableTransitionUpdater(incomingTransition)).replaceAll("\\s+", " ");
			if(!guard2.matches(" true | false ") && guard2.contains(" or ")){
				guard2 = "(" + guard2 + ")";
			}
			String guard = guard1 + " and " + guard2;
			guard = guard.replaceAll("\\s?true\\s?and\\s?", " ").replaceAll("\\s?and\\s?true\\s?", " ").replaceAll("\\s+", " ");
			String updater = incomingTransition.getUpdate() + outgoingTransition.getUpdate();
			Transition newTransition = new Transition(incomingTransition.getAction(), guard, updater);
			sts.addEdge(sts.getEdgeSource(incomingTransition), sts.getEdgeTarget(outgoingTransition), newTransition);
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

	private String getControllableTransitionUpdater(Transition transition) {
		String res = transition.getUpdate();
//		if(controllableEvents.contains(transition.getEvent())){
//			String[] parts = transition.getUpadater().split(";");
//			for (String part : parts) {
//				if(part.matches("\\s*L(XC|IC|XI|II)_.+")){
//					res += part + ";";
//				}
//			}
//		}else{
//			res = transition.getUpadater();
//		}
		return res;
	}

	private Transition nextUncontrollable(STS sts) {
		Transition res = null;
		for (Transition transition : sts.edgeSet()) {
			if(!transition.getAction().equals("se_lnu_CaseStudy_dummy1") && !transition.getAction().equals("se_lnu_CaseStudy_dummy2") && !controllableEvents.contains(transition.getAction())){
				res = transition;
				break;
			}
		}
		return res;
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

	public void synthesize() {
		stsHelper.propagateInitialValues();
		stsHelper.saveAsDot(sourcePath + File.separator + "model.dot");
		STS controlledSTS = stsExtractor.generateControlledSTS();
		controlledSTS.saveAsDot("/home/mohsen/git/runningexample/aspects/modelc.dot");
		controlledSTS = controlledSTS.convertToUncontrollableFreeSTS();
		controlledSTS.saveAsDot("/home/mohsen/git/runningexample/aspects/freemodelc.dot");
		controlledSTS.generateAspect(sourcePath, "/home/mohsen/git/runningexample/aspects");
	}
}
