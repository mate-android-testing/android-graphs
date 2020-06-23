package de.uni_passau.fim.auermich.graphs.cfg;

import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.app.APK;
import de.uni_passau.fim.auermich.graphs.Edge;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.util.Set;

public class InterCFG extends BaseCFG {

    private static final Logger LOGGER = LogManager.getLogger(InterCFG.class);
    private static final GraphType GRAPH_TYPE = GraphType.INTERCFG;

    private boolean excludeARTClasses;

    public InterCFG(String graphName) {
        super(graphName);
    }

    public InterCFG(String graphName, APK apk, boolean useBasicBlocks, boolean excludeARTClasses) {
        super(graphName);
        this.excludeARTClasses = excludeARTClasses;
        constructCFG(apk, useBasicBlocks);
    }

    private void constructCFG(APK apk, boolean useBasicBlocks) {
        if (useBasicBlocks) {
            constructCFGWithBasicBlocks(apk);
        } else {
            constructCFG(apk);
        }
    }

    private void constructCFGWithBasicBlocks(APK apk) {



    }

    private void constructCFG(APK apk) {

    }

    @Override
    public GraphType getGraphType() {
        return GRAPH_TYPE;
    }

    @Override
    public BaseCFG copy() {
        BaseCFG clone = new InterCFG(getMethodName());

        Graph<Vertex, Edge> graphClone = GraphTypeBuilder
                .<Vertex, DefaultEdge>directed().allowingMultipleEdges(true).allowingSelfLoops(true)
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
