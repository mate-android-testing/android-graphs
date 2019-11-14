package de.uni_passau.fim.auermich.graphs.cfg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mxgraph.layout.*;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.orthogonal.mxOrthogonalLayout;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.graphs.BaseGraph;
import de.uni_passau.fim.auermich.graphs.Edge;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;
import de.uni_passau.fim.auermich.statement.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.iface.DexFile;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.AbstractGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class BaseCFG implements BaseGraph, Cloneable, Comparable<BaseCFG> {

    private static final Logger LOGGER = LogManager.getLogger(BaseCFG.class);

    protected Graph<Vertex, Edge> graph = GraphTypeBuilder
            .<Vertex, DefaultEdge>directed().allowingMultipleEdges(true).allowingSelfLoops(true)
            .edgeClass(Edge.class).buildGraph();

    // protected AbstractGraph graph = new DirectedMultigraph(DefaultEdge.class);
    private Vertex entry; // = new Vertex(-1, null, null);
    private Vertex exit; // = new Vertex(-2, null, null);

    /*
     * Contains the full-qualified name of the method,
     * e.g. className->methodName(p0...pN)ReturnType.
     * For the inter-procedural CFG, we can use some
     * arbitrary name. It's solely purpose is to
     * distinguish vertices among different intra CFGs.
     */
    private final String methodName;

    /**
     * Used to initialize an inter-procedural CFG.
     */
    public BaseCFG(String methodName) {
        this.methodName = methodName;
        entry = new Vertex(new EntryStatement(methodName));
        exit = new Vertex(new ExitStatement(methodName));
        graph.addVertex(entry);
        graph.addVertex(exit);
    }

    public int getShortestDistance(Vertex source, Vertex target) {
        /*
        FloydWarshallShortestPaths shortestPathsAlgorithm = new FloydWarshallShortestPaths(graph);
        return shortestPathsAlgorithm.getPath(source, target).getLength();
        */
        GraphPath<Vertex, Edge> path = DijkstraShortestPath.findPathBetween(graph, source, target);
        if (path != null) {
            return path.getLength();
        } else {
            return -1;
        }
    }

    public ShortestPathAlgorithm initDijkstraAlgorithm() {
        return new DijkstraShortestPath(graph);
    }

    public void addEdge(Vertex src, Vertex dest) {
        graph.addEdge(src, dest);
    }

    public Set<Edge> getOutgoingEdges(Vertex vertex) {
        return graph.outgoingEdgesOf(vertex);
    }

    public Set<Edge> getIncomingEdges(Vertex vertex) {
        return graph.incomingEdgesOf(vertex);
    }

    public void addVertex(Vertex vertex) {
        graph.addVertex(vertex);
    }

    public void removeEdge(Edge edge) {
        graph.removeEdge(edge);
    }

    public void removeEdges(Collection<Edge> edges) {
        graph.removeAllEdges(edges);
    }

    public Vertex getEntry() {
        return entry;
    }

    public Vertex getExit() {
        return exit;
    }

    public Set<Edge> getEdges() {
        return graph.edgeSet();
    }

    public Set<Vertex> getVertices() {
        return graph.vertexSet();
    }

    public boolean containsVertex(Vertex vertex) {
        return graph.containsVertex(vertex);
    }

    public boolean containsEdge(Edge edge) {
        return graph.containsEdge(edge);
    }

    public String getMethodName() {
        return methodName;
    }

    public void removeVertex(Vertex vertex) {
        graph.removeVertex(vertex);
    }

    public void addSubGraph(BaseCFG subGraph) {

        // add all vertices
        for (Vertex vertex : subGraph.getVertices()) {
            addVertex(vertex);
        }

        // add all edges
        for (Edge edge : subGraph.getEdges()) {
            addEdge(edge.getSource(), edge.getTarget());
        }
    }

    /**
     * Returns the list of branches contained in the graph. That are
     * all vertices, which are successors of if-statements.
     *
     * @return Returns the list of branches.
     */
    public List<Vertex> getBranches() {
        return getVertices().stream()
                .filter(v -> v.isBranchVertex()).collect(Collectors.toList());
    }

    public abstract GraphType getGraphType();

    @Override
    public String toString() {
        return graph.toString();
    }

    /**
     * Draws the cFG where vertices are encoded as (instruction id, instruction opcode).
     */
    @Override
    public void drawGraph() {

        JGraphXAdapter<Vertex, Edge> graphXAdapter
                = new JGraphXAdapter<>(graph);
        graphXAdapter.getStylesheet().getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, "1");

        // this layout orders the vertices in a sequence from top to bottom (entry -> v1...vn -> exit)
        mxIGraphLayout layout = new mxHierarchicalLayout(graphXAdapter);

        // mxIGraphLayout layout = new mxCircleLayout(graphXAdapter);
        // ((mxCircleLayout) layout).setRadius(((mxCircleLayout) layout).getRadius()*2.5);

        layout.execute(graphXAdapter.getDefaultParent());

        BufferedImage image =
                mxCellRenderer.createBufferedImage(graphXAdapter, null, 1, Color.WHITE, true, null);

        Path resourceDirectory = Paths.get("src", "test", "resources");
        File file = new File(resourceDirectory.toFile(), "graph.png");
        LOGGER.debug(file.getPath());

        try {
            file.createNewFile();
            ImageIO.write(image, "PNG", file);
        } catch (IOException e) {
            LOGGER.warn(e.getMessage());
        }
    }

    /**
     * Performs a deep copy of the graph.
     *
     * @return Returns a deep copy of the graph.
     */
    public abstract BaseCFG copy();

    public BaseCFG clone() {

        LOGGER.debug("Cloning CFG");

        /*
        Gson gson = new Gson();
        BaseCFG clone = gson.fromJson(gson.toJson(this), BaseCFG.class);
        return clone;
        */

        try {
            BaseCFG cloneCFG = (BaseCFG) super.clone();

            Graph<Vertex, Edge> graphClone = GraphTypeBuilder
                    .<Vertex, DefaultEdge>directed().allowingMultipleEdges(true).allowingSelfLoops(true)
                    .edgeClass(Edge.class).buildGraph();

            Set<Vertex> vertices = graph.vertexSet();
            Set<Edge> edges = graph.edgeSet();

            for (Vertex vertex : vertices) {
                graphClone.addVertex(vertex.clone());
            }

            for (Edge edge : edges) {
                graphClone.addEdge(edge.getSource().clone(), edge.getTarget().clone());
            }

            cloneCFG.entry = this.entry.clone();
            cloneCFG.exit = this.exit.clone();
            cloneCFG.graph = graphClone;
            return cloneCFG;
        } catch (CloneNotSupportedException e) {
            LOGGER.warn("Cloning of CFG failed" + e.getMessage());
            return null;
        }
    }

    @Override
    public int compareTo(BaseCFG o) {
        return this.methodName.compareTo(o.methodName);
    }
}
