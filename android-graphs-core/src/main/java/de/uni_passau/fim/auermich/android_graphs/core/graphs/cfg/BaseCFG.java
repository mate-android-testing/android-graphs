package de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.model.mxICell;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraph;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.Edge;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.Vertex;
import de.uni_passau.fim.auermich.android_graphs.core.statements.*;
import de.uni_passau.fim.auermich.android_graphs.core.utility.DotConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.alg.shortestpath.BidirectionalDijkstraShortestPath;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.*;
import org.jgrapht.nio.json.JSONExporter;
import org.w3c.dom.Document;


import javax.imageio.ImageIO;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
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

    private Vertex entry;
    private Vertex exit;

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

    public abstract Vertex lookUpVertex(String trace);

    public void addInvokeVertices(Set<Vertex> vertices) {
        invokeVertices.addAll(vertices);
    }

    public void addInvokeVertex(Vertex vertex) {
        invokeVertices.add(vertex);
    }

    public Set<Vertex> getInvokeVertices() {
        return invokeVertices;
    }

    // TODO: replace with bidirectional dijkstra
    public int getShortestDistance(Vertex source, Vertex target) {
        GraphPath<Vertex, Edge> path = BFSShortestPath.findPathBetween(graph, source, target);
        if (path != null) {
            return path.getLength();
        } else {
            return -1;
        }
    }

    @SuppressWarnings("unused")
    public ShortestPathAlgorithm<Vertex, Edge> initBFSAlgorithm() {
        return new BFSShortestPath<>(graph);
    }

    /**
     * Initialises the bidirectional dijkstra algorithm. This seems to be fastest algorithm
     * next to the BFS search algorithm.
     *
     * @return Returns a shortest path algorithm.
     */
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

    @SuppressWarnings("unused")
    public void removeEdge(Edge edge) {
        graph.removeEdge(edge);
    }

    @SuppressWarnings("unused")
    public Edge getEdge(Vertex source, Vertex target) {
        return graph.getEdge(source, target);
    }

    public void removeEdges(Collection<Edge> edges) {
        /*
        * JGraphT ends up in a ConcurrentModificationException if you don't supply
        * a copy of edges to this method. For instance:
        *
        *       removeEdges(getOutgoingEdges(invokeVertex));
        *
        * will fail with the above mentioned exception while:
        *
        *       removeEdges(new ArrayList<>((getOutgoingEdges(invokeVertex))));
        *
        * will work.
        *
        * See https://github.com/jgrapht/jgrapht/issues/767 for more details.
         */
        graph.removeAllEdges(new ArrayList<>(edges));
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

    @SuppressWarnings("unused")
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
     * Converts a graph into a JSON file.
     *
     * @param output The file path of the JSON file.
     */
    private void convertGraphToJSON(File output) {
        JSONExporter<Vertex, Edge> exporter = new JSONExporter<>(Vertex::toString);
        exporter.exportGraph(graph, output);
    }

    /**
     * Converts a graph into a DOT file.
     *
     * @param output The file path of the DOT file.
     */
    private void convertGraphToDOT(File output) {

        DOTExporter<Vertex, Edge> exporter = new DOTExporter<>(DotConverter::convertVertexToDOTNode);
        exporter.setVertexAttributeProvider((vertex) -> {
            // the label is what we see when we render the graph
            Map<String, Attribute> map = new LinkedHashMap<>();
            map.put("label", DefaultAttribute.createAttribute(DotConverter.convertVertexToDOTLabel(vertex)));
            return map;
        });

        exporter.exportGraph(graph, output);
    }

    /**
     * Converts the graph into a SVG file.
     * This conversion works for medium-sized graphs (<5000 vertices) best, while
     * for larger graphs it takes too long.
     *
     * @param outputFile The file path of the resulting SVG file.
     * @param visitedVertices The set of visited vertices.
     * @param targetVertices The set of target vertices.
     */
    private void convertGraphToSVG(File outputFile, Set<Vertex> visitedVertices, Set<Vertex> targetVertices) {

        JGraphXAdapter<Vertex, Edge> graphXAdapter
                = new JGraphXAdapter<>(graph);
        graphXAdapter.getStylesheet().getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, "1");

        if (!visitedVertices.isEmpty() || !targetVertices.isEmpty()) {
            colorVertices(graphXAdapter, visitedVertices, targetVertices);
        }

        // this layout orders the vertices in a sequence from top to bottom (entry -> v1...vn -> exit)
        mxIGraphLayout layout = new mxHierarchicalLayout(graphXAdapter);

        // mxIGraphLayout layout = new mxCircleLayout(graphXAdapter);
        // ((mxCircleLayout) layout).setRadius(((mxCircleLayout) layout).getRadius()*2.5);

        layout.execute(graphXAdapter.getDefaultParent());

        Document svg =
                mxCellRenderer.createSvgDocument(graphXAdapter, null, 1, Color.WHITE, null);

        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            Source input = new DOMSource(svg);
            Result output = new StreamResult(outputFile);
            transformer.transform(input, output);
        } catch (TransformerException e) {
            LOGGER.warn(e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Converts the graph into a PNG file using a hierarchical layout.
     * This method should be only used for small graphs, i.e. not more than 1000 (max 2000) vertices.
     *
     * @param output The file path of the resulting PNG file.
     * @param visitedVertices The set of visited vertices.
     * @param targetVertices The set of target vertices.
     */
    private void convertGraphToPNG(File output, Set<Vertex> visitedVertices, Set<Vertex> targetVertices) {

        JGraphXAdapter<Vertex, Edge> graphXAdapter
                = new JGraphXAdapter<>(graph);
        graphXAdapter.getStylesheet().getDefaultEdgeStyle().put(mxConstants.STYLE_NOLABEL, "1");

        if (!visitedVertices.isEmpty() || !targetVertices.isEmpty()) {
            colorVertices(graphXAdapter, visitedVertices, targetVertices);
        }

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
            e.printStackTrace();
        }
    }

    /**
     * Marks the given set of visited and target vertices in different colors.
     *
     * @param graphXAdapter The graph adapter.
     * @param visitedVertices The set of visited vertices.
     * @param targetVertices The set of target vertices.
     */
    private void colorVertices(JGraphXAdapter<Vertex, Edge> graphXAdapter, Set<Vertex> visitedVertices,
                               Set<Vertex> targetVertices) {

        Map<Vertex, mxICell> vertexToCellMap = graphXAdapter.getVertexToCellMap();

        /*
         * We mark the covered target vertices orange in the graph.
         */
        Set<Vertex> coveredTargets = new HashSet<>(targetVertices);
        coveredTargets.retainAll(visitedVertices);
        List<Object> coveredTargetCells = new ArrayList<>();
        coveredTargets.forEach(v -> coveredTargetCells.add(vertexToCellMap.get(v)));
        graphXAdapter.setCellStyles(mxConstants.STYLE_FILLCOLOR, "orange", coveredTargetCells.toArray());

        /*
         * We mark the visited vertices red in the graph.
         */
        List<Object> visitedCells = new ArrayList<>();
        visitedVertices.stream().filter(Predicate.not(coveredTargets::contains))
                .forEach(v -> visitedCells.add(vertexToCellMap.get(v)));
        graphXAdapter.setCellStyles(mxConstants.STYLE_FILLCOLOR, "green", visitedCells.toArray());

        /*
         * We mark the yet uncovered target vertices green in the graph.
         */
        List<Object> targetCells = new ArrayList<>();
        targetVertices.stream().filter(Predicate.not(coveredTargets::contains))
                .forEach(v -> targetCells.add(vertexToCellMap.get(v)));
        graphXAdapter.setCellStyles(mxConstants.STYLE_FILLCOLOR, "red", targetCells.toArray());

        // update layout
        graphXAdapter.refresh();
    }

    /**
     * Draws the CFG where vertices are encoded as (instruction id, instruction opcode).
     */
    @Override
    public void drawGraph() {

        // FIXME: drawing only works within IDE, no valid file paths when being executed as via command line
        Path resourceDirectory = Paths.get("android-graphs-core","src", "main", "resources");

        if (size() <= 1000) {
            File output = new File(resourceDirectory.toFile(), "graph.png");
            convertGraphToPNG(output, Collections.emptySet(), Collections.emptySet());
        } else if (size() <= 5000) {
            // can theoretically render large graphs to SVG, but this takes quite some time
            File output = new File(resourceDirectory.toFile(), "graph.svg");
            convertGraphToSVG(output, Collections.emptySet(), Collections.emptySet());
        } else {
            // too 'large' graphs can't be handled by the JGraphXAdapter class, export to DOT and JSON
            File output = new File(resourceDirectory.toFile(), "graph.dot");
            convertGraphToDOT(output);
            output = new File(resourceDirectory.toFile(), "graph.json");
            convertGraphToJSON(output);
        }
    }

    /**
     * Draws the graph and marks the visited vertices in a different color as well
     * as the selected target vertex.
     *
     * @param outputDir The output directory of the graph.
     * @param visitedVertices The set of visited vertices.
     * @param targetVertex The selected target vertices.
     */
    public void drawGraph(File outputDir, Set<Vertex> visitedVertices, Vertex targetVertex) {
        drawGraph(outputDir, visitedVertices, Collections.singleton(targetVertex));
    }

    /**
     * Draws the graph where both the visited and target vertices are marked
     * in a different color.
     *
     * @param outputDir The output directory of the graph.
     * @param visitedVertices The set of visited vertices.
     * @param targetVertices The selected target vertices.
     */
    public void drawGraph(File outputDir, Set<Vertex> visitedVertices, Set<Vertex> targetVertices) {

        LOGGER.info("Number of visited vertices: " + visitedVertices.size());
        LOGGER.info("Number of target vertices: " + targetVertices.size());

        if (size() <= 1000) {
            File output = new File(outputDir, "graph.png");
            convertGraphToPNG(output, visitedVertices, targetVertices);
        } else if (size() <= 5000) {
            // can theoretically render large graphs to SVG, but this takes quite some time
            File output = new File(outputDir, "graph.svg");
            convertGraphToSVG(output, visitedVertices, targetVertices);
        } else {
            // too 'large' graphs can't be handled by the JGraphXAdapter class, export to DOT and JSON
            File output = new File(outputDir, "graph.dot");
            convertGraphToDOT(output);
            output = new File(outputDir, "graph.json");
            convertGraphToJSON(output);
        }
    }

    /**
     * Draws the raw graph and saves it at the specified output path.
     *
     * @param outputDir The output directory of the graph.
     */
    public void drawGraph(File outputDir) {
        drawGraph(outputDir, Collections.emptySet(), Collections.emptySet());
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
