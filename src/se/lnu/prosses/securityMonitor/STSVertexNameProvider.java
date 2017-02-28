package se.lnu.prosses.securityMonitor;

import org.jgrapht.ext.VertexNameProvider;

public class STSVertexNameProvider<V> implements VertexNameProvider<V>{
	@Override
	public String getVertexName(V vertex) {
		return String.valueOf(((Integer)vertex));
	}
}