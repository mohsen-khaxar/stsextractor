package se.lnu.prosses.securityMonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.Hashtable;

public class ReaxHelper {
	STSHelper stsHelper;
	String targetPath;
	
	public ReaxHelper(STSHelper stsHelper, String targetPath) {
		this.stsHelper = stsHelper;
		this.targetPath = targetPath;
	}
	
	Hashtable<String, String> run() throws Exception{
		String reaxScript = getReaxScript();
		String res = "";
		try {
			String reaxScriptFile = targetPath + File.separator + "sts.ctrln";
			FileWriter fileWriter = new FileWriter(new File(reaxScriptFile), false);
			fileWriter.write(reaxScript);
			fileWriter.close();
			Process process = Runtime.getRuntime().exec("reax  " + reaxScriptFile + " -a sS:d={P},deads -t --debug D2");
			process.waitFor();
		    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
		    String line = "";
		    while ((line = bufferedReader.readLine())!= null) {
		    	res += line + "\n";
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(res);
		if(res.indexOf("Triangularized controller:\n")!=-1){
			res = res.substring(res.indexOf("Triangularized controller:")+27);
			res = res.substring(0, res.indexOf("["));
		}else{
			res = "";
		}
		return getSecurityGuards(res);
	}
	
	Hashtable<String, String> getSecurityGuards(String reaxResult){
		Hashtable<String, String> securityGuards = new Hashtable<>();
		String[] parts = reaxResult.split(";");
		for (String part : parts) {
			if(!part.replaceAll("\\s", "").equals("")){
				String event = part.substring(0, part.indexOf("=")).replaceAll("['\\s]", "");
				String[] orParts = part.substring(part.indexOf("=")+1, part.length()).split("\\sor\\s");
				for (String orPart : orParts) {
					String[] andParts = orPart.split("\\sand\\s");
					String[] locations = null;
					for (String andPart : andParts) {
						if(andPart.contains(" LOC ")){
							andPart = andPart.replaceAll("[^\\d,]", "");
							locations = andPart.split(",");
							break;
						}
					}
					orPart = orPart.replaceAll("LOC = \\d+", "true").replaceAll("LOC in \\{(\\s*\\d+\\s*,?)+\\}", "true");
					if(locations==null){
						String value = securityGuards.get(event+",-1")==null ? orPart : securityGuards.get(event+",-1")+" or "+orPart;
						securityGuards.put(event+",-1", value);
					}else{
						for (String location : locations) {
							String value = securityGuards.get(event+","+location)==null ? orPart : securityGuards.get(event+","+location)+" or "+orPart;
							securityGuards.put(event+","+location, value);
						}
					}
				}
			}
		}
		return securityGuards;
	}
	
	public String getReaxScript() throws Exception{
		String stateSection = createStateSection();
		String inputSection = createInputSection();
		String controllableSection = createControllableSection();
		String transitionSection = createTransitionSection();
		String initialSection = createInitialSection();
		String invariantSection = createInvariantSection();
		return stateSection + inputSection + controllableSection + transitionSection + initialSection + invariantSection;
	}

	private String createInvariantSection() {
		String invariantSection = "!invariant\n";
		if(stsHelper.securityPolicies.size()==0){
			invariantSection += "true";
		}else{
			String separator = "";
			for (Object[] securityPolicy : stsHelper.securityPolicies) {
				Integer location = (Integer) securityPolicy[0];
				String securityExpression = (String) securityPolicy[1];
				invariantSection += separator + "(LOC<>" + location + " or " + securityExpression + ")";
				separator = " and ";
			}
		}
		invariantSection += ";";
		return invariantSection;
	}

	private String createInitialSection() {
		Integer minLocation = Integer.MAX_VALUE;
		for (Integer location : stsHelper.getLocations()) {
			if(location<minLocation){
				minLocation = location;
			}
		}
		String initialSection = "!initial\nLOC=" + minLocation + ";";
		for (String securityInit : stsHelper.securityInits) {
			initialSection += securityInit + ";\n";
		}
		initialSection += "\n";
		return initialSection;
	}

	private String createStateSection() throws Exception {
		Integer maxLocation = 0;
		for (Integer location : stsHelper.getLocations()) {
			if(location>maxLocation){
				maxLocation = location;
			}
		}
		String stateSection = "!state\nLOC : uint[" + (Math.round(Math.log(maxLocation)/Math.log(2))+1) + "];\n";
		for (String variable : stsHelper.variables) {
			String type = "";
			if(variable.startsWith("i")){
				type = "int";
			}else if(variable.startsWith("b")||variable.startsWith("L")){
				type = "bool";
			}else if(variable.startsWith("r")){
				type = "real";
			}else{
				throw new Exception(variable + " has undefined type.");
			}
			if(stateSection.contains(" : " + type + ";")){
				stateSection = stateSection.replaceFirst(" : " + type + ";", ", " + variable + " : " + type + ";");
			}else{
				stateSection += variable + " : " + type + ";\n";
			}
		}
		return stateSection;
	}

	private String createControllableSection() {
		String controllableSection = "!controllable\n";
		String separator = "";
		for (String event : stsHelper.controllableActions) {
			controllableSection += separator + event;
			separator = ", ";
		}
		controllableSection += " : bool;\n\n";
		return controllableSection;
	}

	private String createInputSection() {
		String inputSection = "!input\n";
		String separator = "";
		for (String action : stsHelper.actions) {
			if(!stsHelper.controllableActions.contains(action)){
				inputSection += separator + action;
				separator = ", ";
			}
		}
		inputSection += " : bool;\n\n";
		return inputSection;
	}

	private String createTransitionSection() {
		Hashtable<String, String> transitionFunctions = new Hashtable<>();
		transitionFunctions.put("LOC", "LOC' = -");
		for (String variable : stsHelper.variables) {
			String[] variableParts = variable.split(",");
			transitionFunctions.put(variableParts[1], variableParts[1] + "' = -");
		}
		for (Transition transition : stsHelper.getTransitions()) {
			transitionFunctions.put("LOC", transitionFunctions.get("LOC") + "else if " + transition.getAction() + " and LOC="	+ transition.getSource() 
			+ " and (" + STSHelper.convertToSTSSyntax(transition.getGuard()) + ")" + " then " + transition.getTarget() + " \n");
			if(!transition.getUpdate().equals("")){
				String[] updaterParts = transition.getUpdate().split(";");
				for (String updaterPart : updaterParts) {
					String leftHandSide = "";
					try{
						leftHandSide = updaterPart.substring(0, updaterPart.indexOf("=")).replaceAll(" ", "");
					}catch(Exception e){
						e.printStackTrace();
					}
					String rightHandSide = STSHelper.convertToSTSSyntax(updaterPart.substring(updaterPart.indexOf("=")+1, updaterPart.length()));
					transitionFunctions.put(leftHandSide, transitionFunctions.get(leftHandSide) + "else if " + transition.getAction() + " and LOC="	+ transition.getSource() 
					+ " and (" + STSHelper.convertToSTSSyntax(transition.getGuard()) + ")" + " then " + rightHandSide + "\n");
				}
			}
		}
		String transitionSection = "!transition\n";
		for (String key : transitionFunctions.keySet()) {
			if(transitionFunctions.get(key).equals(key + "' = -")){
				transitionSection += key + "' = " + key + ";\n\n";
			}else{
				transitionSection += transitionFunctions.get(key).replaceFirst("-else ", "") + "else " + key + ";\n\n";
			}
		}
		return transitionSection;
	}
}
