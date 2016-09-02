package se.lnu;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.JoinPoint.StaticPart;

public interface MonitorHelper {
	public Integer getMonitorInstanceId(JoinPoint thisJoinPoint, StaticPart thisJoinPointStaticPart, StaticPart thisEnclosingJoinPointStaticPart);

	public Integer getCurrentLocation(Integer monitorInstanceId);

	public void setCurrentLocation(Integer monitorInstanceId, Integer currentLocation);

	public Object[] applyCountermeasure(Object thisObject, Object[] parameters);
}
