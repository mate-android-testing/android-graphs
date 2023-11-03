package de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.model.mxICell;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraph;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.statements.EntryStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.ExitStatement;
import de.uni_passau.fim.auermich.android_graphs.core.utility.DotConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.ManyToManyShortestPathsAlgorithm;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.alg.shortestpath.BidirectionalDijkstraShortestPath;
import org.jgrapht.alg.shortestpath.CHManyToManyShortestPaths;
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.json.JSONExporter;
import org.jgrapht.util.ConcurrencyUtil;
import org.w3c.dom.Document;

import javax.imageio.ImageIO;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class BaseCFG implements BaseGraph, Cloneable, Comparable<BaseCFG> {

    private static final Logger LOGGER = LogManager.getLogger(BaseCFG.class);

    protected Graph<CFGVertex, CFGEdge> graph = GraphTypeBuilder
            .<CFGVertex, DefaultEdge>directed().allowingMultipleEdges(true).allowingSelfLoops(true)
            .edgeClass(CFGEdge.class).buildGraph();

    private CFGVertex entry;
    private CFGVertex exit;

    // save vertices that include an invoke statement
    private Set<CFGVertex> invokeVertices = new HashSet<>();

    /*
     * Contains the full-qualified name of the method,
     * e.g. className->methodName(p0...pN)ReturnType.
     * For the inter-procedural CFG, we can use some
     * arbitrary name. It's solely purpose is to
     * distinguish vertices among different intra CFGs.
     */
    private final String methodName;

    /**
     * Used to initialize a CFG.
     *
     * @param methodName The name of the graph.
     */
    public BaseCFG(String methodName) {
        this.methodName = methodName;
        entry = new CFGVertex(new EntryStatement(methodName));
        exit = new CFGVertex(new ExitStatement(methodName));
        graph.addVertex(entry);
        graph.addVertex(exit);
    }

    /**
     * Used to initialize a CFG with the given entry and exit vertex.
     *
     * @param methodName The name of the graph.
     * @param entry The entry vertex.
     * @param exit The exit vertex.
     */
    public BaseCFG(String methodName, CFGVertex entry, CFGVertex exit) {
        this.methodName = methodName;
        this.entry = entry;
        this.exit = exit;
        graph.addVertex(entry);
        graph.addVertex(exit);
    }

    public abstract CFGVertex lookUpVertex(String trace);

    public void addInvokeVertices(Set<CFGVertex> vertices) {
        invokeVertices.addAll(vertices);
    }

    public void addInvokeVertex(CFGVertex vertex) {
        invokeVertices.add(vertex);
    }

    public Set<CFGVertex> getInvokeVertices() {
        return invokeVertices;
    }

    /**
     * Retrieves the shortest distance between the given source and target vertex.
     *
     * @param source The given source vertex.
     * @param target The given target vertex.
     * @return Returns the shortest distance between the given source and target vertex, or {@code -1} if no path exists.
     */
    public int getShortestDistance(CFGVertex source, CFGVertex target) {
        GraphPath<CFGVertex, CFGEdge> path = BidirectionalDijkstraShortestPath.findPathBetween(graph, source, target);
        if (path != null) {
            return path.getLength();
        } else {
            return -1;
        }
    }

    /**
     * Initialises the BFS shortest path algorithm. Seems to be a bit slower than bi-directional dijkstra.
     *
     * @return Returns the BFS shortest path algorithm.
     */
    @SuppressWarnings("unused")
    public ShortestPathAlgorithm<CFGVertex, CFGEdge> initBFSAlgorithm() {
        return new BFSShortestPath<>(graph);
    }

    /**
     * Initialises the bidirectional dijkstra algorithm. This seems to be fastest algorithm for computing the distance
     * between two vertices.
     *
     * @return Returns the bi-directional dijkstra shortest path algorithm.
     */
    public ShortestPathAlgorithm<CFGVertex, CFGEdge> initBidirectionalDijkstraAlgorithm() {
        return new BidirectionalDijkstraShortestPath<>(graph);
    }

    /**
     * Initialises the CH many-to-many shortest paths algorithm on the underlying graph. This seems to be the fastest
     * option on large graphs.
     *
     * @return Returns the CH many-to-many shortest paths algorithm.
     */
    public ManyToManyShortestPathsAlgorithm<CFGVertex, CFGEdge> initCHManyToManyShortestPathAlgorithm() {

        // enable the maximal parallelism
        final int parallelism = Runtime.getRuntime().availableProcessors();
        final var executor = ConcurrencyUtil.createThreadPoolExecutor(parallelism);
        final var algorithm = new CHManyToManyShortestPaths<>(graph, executor);

        try {
            ConcurrencyUtil.shutdownExecutionService(executor);
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }

        return algorithm;
    }

    /**
     * Initialises the dijkstra many-to-many shortest paths algorithm on the underlying graph. This seems to be slow
     * and memory consuming on a large graph!
     *
     * @return Returns the dijkstra many-to-many shortest paths algorithm.
     */
    @SuppressWarnings("unused")
    public ManyToManyShortestPathsAlgorithm<CFGVertex, CFGEdge> initDijkstraManyToManyShortestPathAlgorithm() {
        return new DijkstraManyToManyShortestPaths<>(graph);
    }

    public void addEdge(CFGVertex src, CFGVertex dest) {
        CFGEdge e = graph.addEdge(src, dest);
        if (e == null) {
            LOGGER.debug("Edge already existing in graph!");
        }
    }

    public Set<CFGEdge> getOutgoingEdges(CFGVertex vertex) {
        return graph.outgoingEdgesOf(vertex);
    }

    public Set<CFGEdge> getIncomingEdges(CFGVertex vertex) {
        return graph.incomingEdgesOf(vertex);
    }

    /**
     * Adds a vertex to the graph. It is not possible to add duplicate vertices to the graph. If the given vertex is
     * already present in the graph, the graph is left unchanged.
     *
     * @param vertex The vertex to be added to the graph.
     */
    public void addVertex(CFGVertex vertex) {

        boolean succeeded = graph.addVertex(vertex);

        if (!succeeded) {
            LOGGER.debug("Vertex is already present in graph: " + vertex);
        }
    }

    @SuppressWarnings("unused")
    public void removeEdge(CFGEdge edge) {
        graph.removeEdge(edge);
    }

    @SuppressWarnings("unused")
    public CFGEdge getEdge(CFGVertex source, CFGVertex target) {
        return graph.getEdge(source, target);
    }

    public void removeEdges(Collection<CFGEdge> edges) {
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

    public CFGVertex getEntry() {
        return entry;
    }

    public CFGVertex getExit() {
        return exit;
    }

    public Set<CFGEdge> getEdges() {
        return graph.edgeSet();
    }

    public Set<CFGVertex> getVertices() {
        return graph.vertexSet();
    }

    public boolean containsVertex(CFGVertex vertex) {
        return graph.containsVertex(vertex);
    }

    @SuppressWarnings("unused")
    public boolean containsEdge(CFGEdge edge) {
        return graph.containsEdge(edge);
    }

    public String getMethodName() {
        return methodName;
    }

    public void removeVertex(CFGVertex vertex) {
        graph.removeVertex(vertex);
    }

    public void addSubGraph(BaseCFG subGraph) {

        // add all vertices
        for (CFGVertex vertex : subGraph.getVertices()) {
            addVertex(vertex);
        }

        // add all edges
        for (CFGEdge edge : subGraph.getEdges()) {
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
    public List<CFGVertex> getBranches() {
        return getVertices().stream()
                .filter(CFGVertex::isBranchVertex).collect(Collectors.toList());
    }

    public abstract GraphType getGraphType();

    @Override
    public String toString() {
        return graph.toString();
    }

    /**
     * Reverses the given BaseCFG graph by reversing the direction of all edges.
     * NOTE: Calling getEntry() or getExit() on the resulting graph returns the original entry or exit, respectively.
     *
     * @return Returns the reversed BaseCFG.
     */
    public BaseCFG reverseGraph() {

        // TODO: Be careful, although the graph is reversed, getEntry() and getExit() still refer to the entry and exit
        //  of the original graph. May use reflection to overwrite those two fields.
        BaseCFG reversed = this.clone();
        reversed.removeEdges(reversed.getEdges());

        for (CFGVertex source : this.getVertices()) {
            for (CFGVertex target : this.getSuccessors(source)) {
                reversed.addEdge(target, source);
            }
        }

        return reversed;
    }

    /**
     * Retrieves the direct successor vertices of a given source vertex.
     *
     * @param source The source vertex whose successors should be retrieved.
     * @return Returns all direct successors of given source vertex.
     */
    public Set<CFGVertex> getSuccessors(final CFGVertex source) {
        return this.getOutgoingEdges(source).stream().map(CFGEdge::getTarget).collect(Collectors.toSet());
    }

    /**
     * Retrieves the direct predecessor vertices of a given source vertex.
     *
     * @param source The source vertex whose predecessors should be retrieved.
     * @return Returns all direct predecessors of given source vertex.
     */
    public Set<CFGVertex> getPredecessors(final CFGVertex source) {
        return this.getIncomingEdges(source).stream().map(CFGEdge::getSource).collect(Collectors.toSet());
    }

    /**
     * Retrieves all transitive successors of the supplied vertex, i.e. any vertex that could be eventually reached
     * from the supplied vertex.
     *
     * @param vertex The vertex whose transitive successors should be retrieved.
     * @return Returns a collection of vertices that represent transitive successors of the supplied vertex.
     */
    public Collection<CFGVertex> getTransitiveSuccessors(final CFGVertex vertex) {
        return transitiveSuccessors(vertex, new HashSet<>());
    }

    /**
     * Retrieves all transitive successors of the supplied vertex.
     *
     * @param vertex The vertex whose transitive successors should be retrieved.
     * @param visitedVertices The set of vertices that have been already visited.
     * @return Returns a collection of vertices that are transitive successors of the supplied vertex.
     */
    private Collection<CFGVertex> transitiveSuccessors(final CFGVertex vertex, final Set<CFGVertex> visitedVertices) {
        final Collection<CFGVertex> successors = new HashSet<>();
        for (CFGVertex successor : getSuccessors(vertex)) {
            if (!visitedVertices.contains(successor)) {
                successors.add(successor);
                visitedVertices.add(successor);
                successors.addAll(transitiveSuccessors(successor, visitedVertices));
            }
        }
        return successors;
    }

    /**
     * Retrieves the least common ancestor for the given pair of vertices.
     *
     * NOTE: This operation presumes that the graph contains no cycles.
     *
     * @param firstVertex The first vertex.
     * @param secondVertex The second vertex.
     * @return The vertex that is the least common ancestor of the two given vertices.
     */
    public CFGVertex getLeastCommonAncestor(final CFGVertex firstVertex, final CFGVertex secondVertex) {
        CFGVertex current = firstVertex;
        while (!isCommonAncestor(current, firstVertex, secondVertex)) {
            current = getPredecessors(current).iterator().next();
        }
        return current;
    }

    /**
     * Checks whether the given start vertex represents a common ancestor of the given pair of vertices.
     *
     * @param startVertex The given start vertex.
     * @param firstVertex The first vertex.
     * @param secondVertex The second vertex.
     * @return Returns {@code true} if the start vertex represents a common ancestor of the given two vertices,
     *         otherwise {@code false} is returned.
     */
    private boolean isCommonAncestor(final CFGVertex startVertex, final CFGVertex firstVertex,
                                     final CFGVertex secondVertex) {
        Collection<CFGVertex> transitiveSuccessors = getTransitiveSuccessors(startVertex);
        transitiveSuccessors.add(startVertex);
        return transitiveSuccessors.contains(firstVertex) && transitiveSuccessors.contains(secondVertex);
    }

    /**
     * Converts a graph into a JSON file.
     *
     * @param output The file path of the JSON file.
     */
    private void convertGraphToJSON(File output) {
        JSONExporter<CFGVertex, CFGEdge> exporter = new JSONExporter<>(CFGVertex::toString);
        exporter.exportGraph(graph, output);
    }

    /**
     * Converts a graph into a DOT file.
     *
     * @param output The file path of the DOT file.
     */
    private void convertGraphToDOT(File output) {

        DOTExporter<CFGVertex, CFGEdge> exporter = new DOTExporter<>(DotConverter::convertVertexToDOTNode);
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
    private void convertGraphToSVG(File outputFile, Set<CFGVertex> visitedVertices, Set<CFGVertex> targetVertices) {

        JGraphXAdapter<CFGVertex, CFGEdge> graphXAdapter
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
    private void convertGraphToPNG(File output, Set<CFGVertex> visitedVertices, Set<CFGVertex> targetVertices) {

        JGraphXAdapter<CFGVertex, CFGEdge> graphXAdapter
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
    private void colorVertices(JGraphXAdapter<CFGVertex, CFGEdge> graphXAdapter, Set<CFGVertex> visitedVertices,
                               Set<CFGVertex> targetVertices) {

        Map<CFGVertex, mxICell> vertexToCellMap = graphXAdapter.getVertexToCellMap();

        /*
         * We mark the covered target vertices orange in the graph.
         */
        Set<CFGVertex> coveredTargets = new HashSet<>(targetVertices);
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

        // FIXME: drawing only works within IDE, no valid file path when being executed via command line
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
    public void drawGraph(File outputDir, Set<CFGVertex> visitedVertices, CFGVertex targetVertex) {
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
    public void drawGraph(File outputDir, Set<CFGVertex> visitedVertices, Set<CFGVertex> targetVertices) {

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
     * Draws the graph where vertices matching the criterion are marked.
     *
     * @param outputDir The output directory of the graph.
     * @param criterion Either a class or a method that should be marked.
     */
    public void drawGraph(File outputDir, String criterion) {

        Set<CFGVertex> toBeMarked = getVertices()
                .stream()
                .filter(vertex -> vertex.getMethod().startsWith(criterion))
                .collect(Collectors.toSet());

        drawGraph(outputDir, Collections.emptySet(), toBeMarked);
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

            Graph<CFGVertex, CFGEdge> graphClone = GraphTypeBuilder
                    .<CFGVertex, DefaultEdge>directed().allowingMultipleEdges(true).allowingSelfLoops(true)
                    .edgeClass(CFGEdge.class).buildGraph();

            Set<CFGVertex> vertices = graph.vertexSet();
            Set<CFGEdge> edges = graph.edgeSet();

            for (CFGVertex vertex : vertices) {
                graphClone.addVertex(vertex.clone());
            }

            for (CFGEdge edge : edges) {
                graphClone.addEdge(edge.getSource().clone(), edge.getTarget().clone());
            }

            cloneCFG.invokeVertices = new HashSet<>(this.invokeVertices);
            cloneCFG.entry = this.entry.clone();
            cloneCFG.exit = this.exit.clone();
            cloneCFG.graph = graphClone;
            return cloneCFG;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Failed to clone CFG!", e);
        }
    }

    @Override
    public int compareTo(BaseCFG o) {
        return this.methodName.compareTo(o.methodName);
    }
}
