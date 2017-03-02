package se.lnu.prosses.securityMonitor;

import org.jgrapht.DirectedGraph;
import org.jgrapht.EdgeFactory;
import org.jgrapht.graph.AbstractBaseGraph;

@SuppressWarnings("serial")
public class STS extends AbstractBaseGraph<Integer, Transition> implements DirectedGraph<Integer, Transition>{
	static final public String TAU = "TAU";
	public static final String INSECURE = "@INSECURE";
	
	static EdgeFactory<Integer, Transition> ef = new EdgeFactory<Integer, Transition>() {
		@Override
		public Transition createEdge(Integer sourceVertex, Integer targetVertex) {
			return new Transition();
		}
	};
	
	public STS() {
		super(ef, true, true);
	}
}