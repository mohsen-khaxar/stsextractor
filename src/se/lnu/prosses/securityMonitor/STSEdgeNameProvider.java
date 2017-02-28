package se.lnu.prosses.securityMonitor;

import org.jgrapht.ext.EdgeNameProvider;

public class STSEdgeNameProvider<E> implements EdgeNameProvider<E>{
	@Override
	public String getEdgeName(E edge) {
		return ((Transition)edge).toString();
	}
}