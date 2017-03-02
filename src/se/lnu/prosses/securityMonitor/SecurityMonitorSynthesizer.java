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
	
	private void synthesizeControlledSTS() throws Exception{
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
	}
	
	private void makeUnmonitorableFree(){
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
	}
	
	private void clearUpdates(STSHelper stsHelper) {
		Set<Transition> transitions = new HashSet<>();
		transitions.addAll(stsHelper.getTransitions());
		for (Transition transition : transitions) {
			String newUpdate = "";
			String[] parts = transition.getUpdate().split(";");
			for (String part : parts) {
				if(part.matches("\\s*L[IX][^=]+\\=.+")){
					newUpdate += part + ";";
				}
			}
			transition.setUpdate(newUpdate);
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
	
	public void synthesize() throws Exception{
		propagateInitialValues();
		stsHelper.saveAsDot(targetPath + File.separator + "initValuesPropagatedSts.dot");
		synthesizeControlledSTS();
		stsHelper.saveAsDot(targetPath + File.separator +"controlledSts.dot");
		makeUnmonitorableFree();
		stsHelper.saveAsDot(targetPath + File.separator + "unmonitorableFreeSts.dot");
	}
}
