package se.lnu;

import java.util.HashMap;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.JoinPoint.StaticPart;

public class MonitorHelper {
	static HashMap<Long, Integer> locations = new HashMap<>();

	static public Long getMonitorInstanceId(JoinPoint thisJoinPoint, StaticPart thisJoinPointStaticPart, StaticPart thisEnclosingJoinPointStaticPart) {
		Long monitorInstanceId = Thread.currentThread().getId();
		if(!locations.containsKey(monitorInstanceId)) {
			locations.put(monitorInstanceId, 0);
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

	static public Object[] applyCountermeasure(String fullQualifiedMethodName, Object thisObject, Object[] parameters) {
		// TODO Auto-generated method stub
		return null;
	}
}
