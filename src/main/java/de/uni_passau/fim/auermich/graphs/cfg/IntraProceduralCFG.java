package de.uni_passau.fim.auermich.graphs.cfg;

import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.graphs.Edge;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.nio.file.LinkOption;
import java.util.Set;

public class IntraProceduralCFG extends BaseCFG implements Cloneable {

    private static final GraphType GRAPH_TYPE = GraphType.INTRACFG;

    public IntraProceduralCFG(String methodName) {
        super(methodName);
    }

    public IntraProceduralCFG clone() {
        IntraProceduralCFG cloneCFG = (IntraProceduralCFG) super.clone();
        return cloneCFG;
    }

    public BaseCFG copy() {

        BaseCFG clone = new IntraProceduralCFG(getMethodName());

        Graph<Vertex, Edge> graphClone = GraphTypeBuilder
                .<Vertex, DefaultEdge> directed().allowingMultipleEdges(true).allowingSelfLoops(true)
                .edgeClass(Edge.class).buildGraph();

        Set<Vertex> vertices = graph.vertexSet();
        Set<Edge> edges = graph.edgeSet();

        Cloner cloner = new Cloner();

        for (Vertex vertex : vertices) {
            graphClone.addVertex(cloner.deepClone(vertex));
        }

        for (Edge edge : edges) {
            Vertex src = cloner.deepClone(edge.getSource());
            Vertex dest = cloner.deepClone(edge.getTarget());
            graphClone.addEdge(src, dest);
        }

        clone.graph = graphClone;
        return clone;
    }
}
