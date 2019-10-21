package de.uni_passau.fim.auermich.graphs.cfg;

import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.graphs.Edge;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.iface.DexFile;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.util.List;
import java.util.Set;

public class InterProceduralCFG extends BaseCFG implements Cloneable {

    private static final Logger LOGGER = LogManager.getLogger(InterProceduralCFG.class);

    private static final GraphType GRAPH_TYPE = GraphType.INTERCFG;

    public InterProceduralCFG(String methodName) {
        super(methodName);
    }

    public InterProceduralCFG(String methodName, List<DexFile> dexFiles, boolean useBasicBlocks) {
        super(methodName);
        constructCFG(dexFiles, useBasicBlocks);
    }


    private void constructCFG(List<DexFile> dexFiles, boolean useBasicBlocks) {
        if (useBasicBlocks) {
            constructCFGWithBasicBlocks(dexFiles);
        } else {
            constructCFG(dexFiles);
        }
    }

    private void constructCFGWithBasicBlocks(List<DexFile> dexFiles) {

    }

    private void constructCFG(List<DexFile> dexFiles) {

    }


    public InterProceduralCFG clone() {

        InterProceduralCFG cloneCFG = (InterProceduralCFG) super.clone();
        return cloneCFG;
    }

    public BaseCFG copy() {

        BaseCFG clone = new InterProceduralCFG(getMethodName());

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
