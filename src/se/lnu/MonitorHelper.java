package se.lnu;

import java.util.HashMap;
import java.util.Hashtable;

public class MonitorHelper {
	static HashMap<Long, Integer> locations = new HashMap<>();
	static Hashtable<String, Object> localVariableValues = new Hashtable<>();
	static Hashtable<String, Boolean> securityLevels = new Hashtable<>();

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
}
