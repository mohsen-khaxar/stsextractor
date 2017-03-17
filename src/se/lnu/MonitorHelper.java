package se.lnu;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class MonitorHelper {
	static HashMap<Long, Integer> locations = new HashMap<>();
	static Hashtable<String, Object> localVariableValues = new Hashtable<>();
	static Hashtable<String, Boolean> securityLevels = new Hashtable<>();
	
	static{
		localVariableValues.put("@isCheckPoint", false);
	}

	static public Long getMonitorInstanceId(Object thisObject) {
		Long monitorInstanceId = Thread.currentThread().getId();
		if(!locations.containsKey(monitorInstanceId)) {
			locations.put(monitorInstanceId, 1);
		}
		return monitorInstanceId;
	}
	
	static public void removeMonitorInstanceId(Long monitorInstanceId) {
		locations.remove(monitorInstanceId);
	}

	static public Integer getCurrentLocation(Long monitorInstanceId) {
		return locations.get(monitorInstanceId);
	}

	static public void setCurrentLocation(Long monitorInstanceId, Integer currentLocation) {
		locations.put(monitorInstanceId, currentLocation);
	}

	static public Object[] applyCountermeasure(String fullQualifiedMethodName/*, Object thisObject, Object[] parameters*/) {
		System.err.println("Countermeasures are applied for " + fullQualifiedMethodName);
		return new Object[]{1};
	}
	
	static public void throwException(Object thisObject, String message) {
		System.err.println(message);
	}

	public static void setLocalVariableValue(String localVariableUniqueName, Object value) {
		localVariableValues.put(localVariableUniqueName, value);
	}

	public static Object getLocalVariableValue(String localVariableUniqueName) {
		return localVariableValues.get(localVariableUniqueName);
	}

	public static boolean getSecurityLevel(String variableName) {
		return securityLevels.get(variableName)==null ? false : securityLevels.get(variableName);
	}

	public static void setSecurityLevel(String variableName, boolean securityLevel) {
		securityLevels.put(variableName, securityLevel);		
	}
	
	public static void setSecurityLevel(String securityAssignments){
		if(!securityAssignments.matches("\\s*")){
			String[] parts = securityAssignments.split(";");
			ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("JavaScript");
			for (String part : parts) {
				if(part.matches("\\s*L.+")){
					part = part.replaceAll("\\sor\\s", "||").replaceAll("\\snot\\s", "!");
					String leftHandSide = part.split("=")[0];
					String rightHandSide = part.split("=")[1];
					String regex = "[a-zA-Z_$][\\w_$]*"; 
					Pattern pattern = Pattern.compile(regex);
			        Matcher matcher = pattern.matcher(rightHandSide);
			        StringBuffer processedCode = new StringBuffer();
			        while (matcher.find()) {
			        	String find = matcher.group();
			        	String replace = ""; 
			        	if(find.equals("true")||find.equals("false")){
							replace = find;
						}else{
							replace = String.valueOf(getSecurityLevel(find));
						}
						matcher.appendReplacement(processedCode, replace);
			        }
			        matcher.appendTail(processedCode);
			        try {
						setSecurityLevel(leftHandSide, (boolean) scriptEngine.eval(processedCode.toString()));
					} catch (ScriptException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
}
