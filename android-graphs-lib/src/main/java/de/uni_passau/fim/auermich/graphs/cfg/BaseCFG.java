package de.uni_passau.fim.auermich.graphs.cfg;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.model.mxICell;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import de.uni_passau.fim.auermich.graphs.BaseGraph;
import de.uni_passau.fim.auermich.graphs.Edge;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;
import de.uni_passau.fim.auermich.statement.EntryStatement;
import de.uni_passau.fim.auermich.statement.ExitStatement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.alg.shortestpath.BidirectionalDijkstraShortestPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class BaseCFG implements BaseGraph, Cloneable, Comparable<BaseCFG> {

    private static final Logger LOGGER = LogManager.getLogger(BaseCFG.class);

    protected Graph<Vertex, Edge> graph = GraphTypeBuilder
            .<Vertex, DefaultEdge>directed().allowingMultipleEdges(true).allowingSelfLoops(true)
            .edgeClass(Edge.class).buildGraph();

    // protected AbstractGraph graph = new DirectedMultigraph(DefaultEdge.class);
    private Vertex entry; // = new Vertex(-1, null, null);
    private Vertex exit; // = new Vertex(-2, null, null);

    // save vertices that include an invoke statement
    private Set<Vertex> invokeVertices = new HashSet<>();

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

    public void addInvokeVertices(Set<Vertex> vertices) {
        invokeVertices.addAll(vertices);
    }

    public void addInvokeVertex(Vertex vertex) {
        invokeVertices.add(vertex);
    }

    public Set<Vertex> getInvokeVertices() {
        return invokeVertices;
    }

    public int getShortestDistance(Vertex source, Vertex target) {
        /*
        FloydWarshallShortestPaths shortestPathsAlgorithm = new FloydWarshallShortestPaths(graph);
        return shortestPathsAlgorithm.getPath(source, target).getLength();
        */
        GraphPath<Vertex, Edge> path = BFSShortestPath.findPathBetween(graph, source, target);
        if (path != null) {
            return path.getLength();
        } else {
            return -1;
        }
    }

    public ShortestPathAlgorithm<Vertex, Edge> initBFSAlgorithm() {
        return new BFSShortestPath<>(graph);
    }

    public ShortestPathAlgorithm<Vertex, Edge> initDijkstraAlgorithm() {
        return new DijkstraShortestPath<>(graph);
    }

    public ShortestPathAlgorithm<Vertex, Edge> initBidirectionalDijkstraAlgorithm() {
        return new BidirectionalDijkstraShortestPath<>(graph);
    }

    public void addEdge(Vertex src, Vertex dest) {
        Edge e = graph.addEdge(src, dest);
        if (e == null) {
            LOGGER.debug("Edge already existing in graph!");
        }
    }

    public Set<Edge> getOutgoingEdges(Vertex vertex) {
        return graph.outgoingEdgesOf(vertex);
    }

    public Set<Edge> getIncomingEdges(Vertex vertex) {
        return graph.incomingEdgesOf(vertex);
    }

    public void addVertex(Vertex vertex) {

        boolean succeeded = graph.addVertex(vertex);

        if (!succeeded) {
            LOGGER.debug("Couldn't insert vertex: " + vertex);
        }
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

    @Override
    public int size() {
        return graph.vertexSet().size();
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
     * Draws the CFG where vertices are encoded as (instruction id, instruction opcode).
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

        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource("graph.png");

        // TODO: check whether this path still works within the jar executable
        Path resourceDirectory = Paths.get("android-graphs-lib","src", "test", "resources");
        File file = new File(resourceDirectory.toFile(), "graph.png");

        try {
            file.createNewFile();
            ImageIO.write(image, "PNG", file);
        } catch (IOException e) {
            LOGGER.warn(e.getMessage());
            e.printStackTrace();
        }
    }

    public void drawGraph(int ID) {
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
        File file = new File(resourceDirectory.toFile(), "graph" + ID + ".png");
        LOGGER.debug(file.getPath());

        try {
            file.createNewFile();
            ImageIO.write(image, "PNG", file);
        } catch (IOException e) {
            LOGGER.warn(e.getMessage());
        }
    }

    /**
     * Draws the graph and marks the visited vertices in a different color as well
     * as the selected target vertex.
     *
     * @param visitedVertices The set of visited vertices.
     * @param targetVertex The selected target vertex.
     * @param output The output path of graph.
     */
    public void drawGraph(Set<Vertex> visitedVertices, Vertex targetVertex, File output) {

        JGraphXAdapter<Vertex, Edge> graphXAdapter
                = new JGraphXAdapter<>(graph);
        graphXAdapter.getStylesheet().getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, "1");

        // retrieve for each vertex the corresponding cell
        List<Object> cells = new ArrayList<>();

        Map<Vertex, mxICell> vertexToCellMap = graphXAdapter.getVertexToCellMap();
        visitedVertices.forEach(v -> cells.add(vertexToCellMap.get(v)));

        // mark the cells with a certain color
        graphXAdapter.setCellStyles(mxConstants.STYLE_FILLCOLOR, "red", cells.toArray());

        // mark the target vertex as well
        mxICell targetCell = vertexToCellMap.get(targetVertex);
        graphXAdapter.setCellStyles(mxConstants.STYLE_FILLCOLOR, "green", new Object[]{targetCell});

        graphXAdapter.refresh();

        // this layout orders the vertices in a sequence from top to bottom (entry -> v1...vn -> exit)
        mxIGraphLayout layout = new mxHierarchicalLayout(graphXAdapter);

        // mxIGraphLayout layout = new mxCircleLayout(graphXAdapter);
        // ((mxCircleLayout) layout).setRadius(((mxCircleLayout) layout).getRadius()*2.5);

        layout.execute(graphXAdapter.getDefaultParent());

        BufferedImage image =
                mxCellRenderer.createBufferedImage(graphXAdapter, null, 1, Color.WHITE, true, null);

        try {
            output.createNewFile();
            ImageIO.write(image, "PNG", output);
        } catch (IOException e) {
            LOGGER.warn(e.getMessage());
        }
    }

    /**
     * Draws the graph where both the visited and target vertices are marked
     * in a different color.
     *
     * @param visitedVertices The set of visited vertices.
     * @param targetVertices The selected target vertices.
     * @param output The output path of graph.
     */
    public void drawGraph(Set<Vertex> visitedVertices, Set<Vertex> targetVertices, File output) {

        LOGGER.info("Number of visited vertices: " + visitedVertices.size());
        LOGGER.info("Number of target vertices: " + targetVertices.size());

        JGraphXAdapter<Vertex, Edge> graphXAdapter
                = new JGraphXAdapter<>(graph);
        graphXAdapter.getStylesheet().getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, "1");

        Map<Vertex, mxICell> vertexToCellMap = graphXAdapter.getVertexToCellMap();

        // we like to mark covered target vertices in a different color
        Set<Vertex> coveredTargets = new HashSet<>(targetVertices);
        coveredTargets.retainAll(visitedVertices);

        LOGGER.info("Number of covered target vertices: " + coveredTargets.size());

        // map covered target vertices to cells
        List<Object> coveredTargetCells = new ArrayList<>();
        coveredTargets.forEach(v -> coveredTargetCells.add(vertexToCellMap.get(v)));

        // mark covered target vertices orange
        graphXAdapter.setCellStyles(mxConstants.STYLE_FILLCOLOR, "orange", coveredTargetCells.toArray());

        // map visited vertices to cells
        List<Object> visitedCells = new ArrayList<>();
        visitedVertices.stream().filter(Predicate.not(coveredTargets::contains))
                .forEach(v -> visitedCells.add(vertexToCellMap.get(v)));

        // mark visited vertices red
        graphXAdapter.setCellStyles(mxConstants.STYLE_FILLCOLOR, "red", visitedCells.toArray());

        // map target vertices to cells
        List<Object> targetCells = new ArrayList<>();
        targetVertices.stream().filter(Predicate.not(coveredTargets::contains))
                .forEach(v -> targetCells.add(vertexToCellMap.get(v)));

        // mark target vertices green
        graphXAdapter.setCellStyles(mxConstants.STYLE_FILLCOLOR, "green", targetCells.toArray());

        graphXAdapter.refresh();

        // this layout orders the vertices in a sequence from top to bottom (entry -> v1...vn -> exit)
        mxIGraphLayout layout = new mxHierarchicalLayout(graphXAdapter);
        layout.execute(graphXAdapter.getDefaultParent());

        BufferedImage image =
                mxCellRenderer.createBufferedImage(graphXAdapter, null, 1, Color.WHITE, true, null);

        try {
            output.createNewFile();
            ImageIO.write(image, "PNG", output);
        } catch (IOException e) {
            LOGGER.warn(e.getMessage());
        }
    }

    /**
     * Draws the raw graph and saves it at the specified output path.
     *
     * @param outputPath The location where the graph should be saved.
     */
    public void drawGraph(File outputPath) {
        JGraphXAdapter<Vertex, Edge> graphXAdapter
                = new JGraphXAdapter<>(graph);
        graphXAdapter.getStylesheet().getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, "1");

        // this layout orders the vertices in a sequence from top to bottom (entry -> v1...vn -> exit)
        mxIGraphLayout layout = new mxHierarchicalLayout(graphXAdapter);
        layout.execute(graphXAdapter.getDefaultParent());

        BufferedImage image =
                mxCellRenderer.createBufferedImage(graphXAdapter, null, 1, Color.WHITE, true, null);

        try {
            outputPath.createNewFile();
            ImageIO.write(image, "PNG", outputPath);
        } catch (IOException e) {
            LOGGER.warn(e.getMessage());
        }
    }

    /**
     * Performs a deep copy of the graph.
     *
     * @return Returns a deep copy of the graph.
     */
    @SuppressWarnings("unused")
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

            cloneCFG.invokeVertices = new HashSet<>(this.invokeVertices);
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
