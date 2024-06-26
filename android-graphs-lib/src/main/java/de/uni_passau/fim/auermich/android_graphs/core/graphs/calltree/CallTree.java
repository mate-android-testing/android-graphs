package de.uni_passau.fim.auermich.android_graphs.core.graphs.calltree;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.model.mxICell;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.BaseGraph;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.CFGEdge;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.CFGVertex;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.InterCFG;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.ManyToManyShortestPathsAlgorithm;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.alg.shortestpath.BidirectionalDijkstraShortestPath;
import org.jgrapht.alg.shortestpath.CHManyToManyShortestPaths;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.GraphWalk;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.nio.AttributeType;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;
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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A call tree consists of vertices that represent methods and edges describe method invocations. The call tree can
 * be derived from the {@link InterCFG}.
 */
public class CallTree implements BaseGraph {

    private static final Logger LOGGER = LogManager.getLogger(CallTree.class);

    protected final Graph<CallTreeVertex, CallTreeEdge> graph = GraphTypeBuilder
            .<CallTreeVertex, CallTreeEdge>directed()
            .allowingSelfLoops(true)
            .edgeClass(CallTreeEdge.class)
            .edgeSupplier(CallTreeEdge::new)
            .buildGraph();

    /**
     * The inter-procedural CFG from which the call tree is derived.
     */
    private final InterCFG interCFG;

    /**
     * A virtual root entry.
     */
    private final CallTreeVertex root;

    /**
     * Constructs the call tree based on the supplied {@link InterCFG}.
     *
     * @param interCFG The inter CFG from which the call tree is derived.
     */
    public CallTree(final InterCFG interCFG) {

        this.interCFG = interCFG;
        root = new CallTreeVertex(interCFG.getEntry().getMethod());

        final Set<CFGVertex> entryVertices = interCFG.getVertices().stream()
                .filter(CFGVertex::isEntryVertex)
                .collect(Collectors.toSet());

        // TODO: Should we eliminate cycles, i.e., primarily recursive calls?
        // TODO: Flatten out virtual 'callbacks' graph.

        for (final CFGVertex entry : entryVertices) {
            // construct for each method in the CFG a vertex for the call tree
            final CallTreeVertex callerMethod = new CallTreeVertex(entry.getMethod());
            graph.addVertex(callerMethod);

            // add from each callee an edge to the caller (this method)
            for (final CFGEdge edge : interCFG.getIncomingEdges(entry)) {
                final CFGVertex callee = edge.getSource();
                final CallTreeVertex calleeMethod = new CallTreeVertex(callee.getMethod());
                graph.addVertex(calleeMethod);
                graph.addEdge(calleeMethod, callerMethod);
            }
        }
    }

    /**
     * Returns the virtual root vertex.
     *
     * @return Returns the root vertex.
     */
    public CallTreeVertex getRoot() {
        return root;
    }

    /**
     * The inter-procedural CFG from which the call tree was derived.
     *
     * @return Returns the inter-procedural CFG.
     */
    public InterCFG getInterCFG() {
        return interCFG;
    }

    /**
     * Retrieves the shortest path between the root vertex and the given target vertex.
     *
     * @param target Describes a target vertex that must exist in the graph.
     * @return Returns the shortest path between the root and the given target vertex if such path exists.
     */
    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPath(String target) {
        return getShortestPath(new CallTreeVertex(target));
    }

    /**
     * Retrieves the shortest path between the root vertex and the given target vertex.
     *
     * @param target The target vertex that must exist in the graph.
     * @return Returns the shortest path between the root and the given target vertex if such path exists.
     */
    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPath(CallTreeVertex target) {
        return getShortestPath(root, target);
    }

    /**
     * Retrieves the shortest path between the given source and target vertex.
     *
     * @param source The source vertex that must exist in the graph.
     * @param target The target vertex that must exist in the graph.
     * @return Returns the shortest path between the given source and target vertex if such path exists.
     */
    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPath(CallTreeVertex source, CallTreeVertex target) {
        return Optional.ofNullable(BidirectionalDijkstraShortestPath.findPathBetween(graph, source, target));
    }

    /**
     * Retrieves the shortest path from the root vertex through the list of given stop vertices.
     *
     * @param stops A list of vertices through which the shortest path must flow.
     * @return Returns the shortest path from the root vertex through the given intermediate vertices if such path exists.
     */
    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPathWithStops(List<CallTreeVertex> stops) {
        return getShortestPathWithStops(root, stops);
    }

    /**
     * Retrieves the shortest path from the given start vertex through the list of given stop vertices.
     *
     * @param startVertex The first vertex in the shortest path.
     * @param stops A list of existent vertices through which the shortest path must flow.
     * @return Returns the shortest path from the given start vertex through the given intermediate vertices.
     */
    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPathWithStops(CallTreeVertex startVertex,
                                                                                      List<CallTreeVertex> stops) {

        CallTreeVertex start = startVertex;
        List<GraphPath<CallTreeVertex, CallTreeEdge>> paths = new LinkedList<>();

        // assemble the path through the stop vertices
        for (CallTreeVertex end : stops) {
            var path = getShortestPath(start, end);

            if (path.isEmpty()) {
                LOGGER.warn("No path between " + start + " and " + end + "!");
                return Optional.empty();
            } else {
                paths.add(path.get());
            }

            start = end;
        }

        // derive the edges describing the shortest path
        List<CallTreeEdge> edges = paths.stream().map(GraphPath::getEdgeList)
                .flatMap(Collection::stream).collect(Collectors.toList());
        double weight = paths.stream().mapToDouble(GraphPath::getWeight).sum();

        return Optional.of(new GraphWalk<>(graph, startVertex, start, edges, weight));
    }

    /**
     * Returns the set of outgoing edges from the given vertex.
     *
     * @param vertex The vertex from which the outgoing vertices should be determined.
     * @return Returns the set of outgoing edges from the given vertex.
     */
    public Set<CallTreeEdge> getOutgoingEdges(CallTreeVertex vertex) {
        return this.graph.outgoingEdgesOf(vertex);
    }

    /**
     * Returns the set of incoming edges from the given vertex.
     *
     * @param vertex The vertex from which the incoming vertices should be determined.
     * @return Returns the set of incoming edges from the given vertex.
     */
    public Set<CallTreeEdge> getIncomingEdges(CallTreeVertex vertex) {
        return this.graph.incomingEdgesOf(vertex);
    }

    /**
     * Returns the vertices of the call tree.
     *
     * @return Returns the vertices of the call tree.
     */
    public Set<CallTreeVertex> getVertices() {
        return graph.vertexSet();
    }

    /**
     * Returns the set of unreachable vertices.
     *
     * @return Returns the set of unreachable vertices.
     */
    @SuppressWarnings("unused")
    public Set<CallTreeVertex> getUnreachableVertices() {
        return graph.vertexSet().stream().filter(v -> getShortestPath(v).isEmpty()).collect(Collectors.toSet());
    }

    /**
     * Retrieves the set of method callees for the given caller method, i.e. the set of vertices that call the specified
     * method.
     *
     * @param method The method caller.
     * @param validCallee A predicate that determines whether a callee is valid, e.g., is not identical to the caller.
     * @return Returns the set of vertices (methods) that call the given method.
     */
    @SuppressWarnings("unused")
    public Set<CallTreeVertex> getMethodCallees(final CallTreeVertex method, final Predicate<CallTreeVertex> validCallee) {
        return goUntilSatisfied(method, m -> graph.incomingEdgesOf(m).stream().map(CallTreeEdge::getSource), validCallee);
    }

    /**
     * Performs an operation from the given start until the predicate is satisfied.
     *
     * @param start The start point.
     * @param childGetter A function that jumps to the next point(s).
     * @param predicate The predicate that should be satisfied.
     * @param <T> The type parameter.
     * @return Returns a set of {@link <T>} instances that satisfy the given predicate.
     */
    private <T> Set<T> goUntilSatisfied(final T start, final Function<T, Stream<T>> childGetter, final Predicate<T> predicate) {

        final Queue<T> workQueue = new LinkedList<>();
        workQueue.add(start);
        final Set<T> satisfied = new HashSet<>();
        final Set<T> seen = new HashSet<>();

        while (!workQueue.isEmpty()) {
            T current = workQueue.poll();
            seen.add(current);

            if (predicate.test(current)) {
                satisfied.add(current);
            } else {
                childGetter.apply(current)
                        .filter(Predicate.not(seen::contains))
                        .forEach(workQueue::add);
            }
        }

        return satisfied;
    }

    private static Graph<CallTreeVertex, CallTreeEdge> onlyKeepVertices(Graph<CallTreeVertex, CallTreeEdge> graph,
                                                                       Set<CallTreeVertex> vertices) {
        var newGraph = GraphTypeBuilder
                .<CallTreeVertex, CallTreeEdge>directed()
                .allowingSelfLoops(true)
                .edgeClass(CallTreeEdge.class)
                .edgeSupplier(CallTreeEdge::new)
                .buildGraph();
        graph.vertexSet().forEach(newGraph::addVertex);
        graph.edgeSet().forEach(e -> newGraph.addEdge(e.getSource(), e.getTarget()));

        Set<CallTreeVertex> verticesToUnlink = graph.vertexSet().stream()
                .filter(v -> !vertices.contains(v)).collect(Collectors.toSet());

        for (CallTreeVertex vertexToUnlink : verticesToUnlink) {
            Set<CallTreeVertex> children = newGraph.outgoingEdgesOf(vertexToUnlink).stream()
                    .map(CallTreeEdge::getTarget).collect(Collectors.toSet());
            Set<CallTreeVertex> parents = newGraph.incomingEdgesOf(vertexToUnlink).stream()
                    .map(CallTreeEdge::getSource).collect(Collectors.toSet());

            for (CallTreeVertex parent : parents) {
                for (CallTreeVertex child : children) {
                    newGraph.addEdge(parent, child);
                }
            }

            newGraph.removeVertex(vertexToUnlink);
        }

        return newGraph;
    }

    public void toDot(File output, Map<String, String> methodsToHighlight) {

        Pattern pattern = Pattern.compile("^.*/(\\S+);->(\\S+)\\(.*\\).*$");

        DOTExporter<CallTreeVertex, CallTreeEdge> exporter = new DOTExporter<>(v -> {
            Matcher matcher = pattern.matcher(v.toString());
            int hash = v.hashCode() % 100;

            return '"' +
                    (matcher.matches()
                            ? matcher.group(1) + "::" + matcher.group(2) + " (" + hash + ")"
                            : v.toString())
                    + '"';
        });

        exporter.setVertexAttributeProvider(v -> {
            String color = methodsToHighlight.get(v.getMethod());

            if (color == null) {
                return Map.of();
            } else {
                return Map.of(
                        "style", new DefaultAttribute<>("filled", AttributeType.STRING),
                        "fillcolor", new DefaultAttribute<>(color, AttributeType.STRING)
                );
            }
        });

        exporter.exportGraph(graph, output);
    }

    public void convertGraphToDOT(File output) {

        Pattern pattern = Pattern.compile("^.*/(\\S+);->\\S+\\(.*\\).*$|^callbacks L.+/(\\S+);$");

        Function<CallTreeVertex, String> toString = v -> {
            Matcher matcher = pattern.matcher(v.toString());
            return '"' + (matcher.matches()
                    ? (matcher.group(1) == null ? matcher.group(2) : matcher.group(1)).split("\\$")[0]
                    : v.toString()) + '"';
        };

        String verticesPreamble = graph.vertexSet().stream()
                .map(toString).distinct().collect(Collectors.joining("\n"));
        String graphString = graph.edgeSet().stream()
                .map(e -> String.format("%s -> %s", toString.apply(e.getSource()), toString.apply(e.getTarget())))
                .distinct()
                .collect(Collectors.joining("\n"));

        try {
            Files.writeString(output.toPath(), "digraph D {\n" + verticesPreamble + "\n" + graphString + "\n}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void toMergedDot(File output, Set<String> classesToKeep) {

        var activityGraph = onlyKeepVertices(graph, graph.vertexSet().stream()
                .filter(v -> v.equals(root) || (v.toString().contains("-><init>")
                        && classesToKeep.contains(v.getClassName()))).collect(Collectors.toSet()));

        DOTExporter<CallTreeVertex, CallTreeEdge> exporter = new DOTExporter<>(v -> '"' + v.getClassName() + '"');
        exporter.setVertexAttributeProvider(v -> {
            if (BFSShortestPath.findPathBetween(activityGraph, root, v) != null) {
                return Map.of(
                        "fillcolor", new DefaultAttribute<>("red", AttributeType.STRING),
                        "style", new DefaultAttribute<>("filled", AttributeType.STRING)
                );
            } else {
                return Map.of();
            }
        });

        exporter.exportGraph(activityGraph, output);
    }

    /**
     * Retrieves the shortest distance between the given source and target vertex.
     *
     * @param source The given source vertex.
     * @param target The given target vertex.
     * @return Returns the shortest distance between the given source and target vertex, or {@code -1} if no path exists.
     */
    public int getShortestDistance(CallTreeVertex source, CallTreeVertex target) {
        GraphPath<CallTreeVertex, CallTreeEdge> path = BidirectionalDijkstraShortestPath.findPathBetween(graph, source, target);
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
    public ShortestPathAlgorithm<CallTreeVertex, CallTreeEdge> initBFSAlgorithm() {
        return new BFSShortestPath<>(graph);
    }

    /**
     * Initialises the bidirectional dijkstra algorithm. This seems to be fastest algorithm for computing the distance
     * between two vertices.
     *
     * @return Returns the bi-directional dijkstra shortest path algorithm.
     */
    public ShortestPathAlgorithm<CallTreeVertex, CallTreeEdge> initBidirectionalDijkstraAlgorithm() {
        return new BidirectionalDijkstraShortestPath<>(graph);
    }

    /**
     * Initialises the CH many-to-many shortest paths algorithm on the underlying graph. This seems to be the fastest
     * option on large graphs.
     *
     * @return Returns the CH many-to-many shortest paths algorithm.
     */
    public ManyToManyShortestPathsAlgorithm<CallTreeVertex, CallTreeEdge> initCHManyToManyShortestPathAlgorithm() {

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
     * Converts the graph into a SVG file.
     * This conversion works for medium-sized graphs (<5000 vertices) best, while
     * for larger graphs it takes too long.
     *
     * @param outputFile The file path of the resulting SVG file.
     * @param visitedVertices The set of visited vertices.
     * @param targetVertices The set of target vertices.
     */
    private void convertGraphToSVG(File outputFile, Set<CallTreeVertex> visitedVertices, Set<CallTreeVertex> targetVertices) {

        JGraphXAdapter<CallTreeVertex, CallTreeEdge> graphXAdapter
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
     * Draws the graph where both the visited and target vertices are marked in a different color.
     *
     * @param outputDir The output directory of the graph.
     * @param visitedVertices The set of visited vertices.
     * @param targetVertices The selected target vertices.
     */
    public void drawGraph(File outputDir, Set<CallTreeVertex> visitedVertices, Set<CallTreeVertex> targetVertices) {

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
            // too 'large' graphs can't be handled by the JGraphXAdapter class, export to DOT
            File output = new File(outputDir, "graph.dot");
            convertGraphToDOT(output);
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

    @Override
    public void drawGraph() {
        // FIXME: drawing only works within IDE, no valid file path when being executed via command line
        final Path resourceDirectory = Paths.get("android-graphs-lib","src", "main", "resources");

        if (size() <= 1000) {
            File output = new File(resourceDirectory.toFile(), "graph.png");
            convertGraphToPNG(output, Collections.emptySet(), Collections.emptySet());
        } else {
            final File output = new File(resourceDirectory.toFile(), "graph.dot");
            convertGraphToDOT(output);
        }
    }

    @Override
    public int size() {
        return graph.vertexSet().size();
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.CALLTREE;
    }

    @Override
    public CallTreeVertex lookUpVertex(String trace) { // trace needs to refer to a method!
        return lookUpVertexBFS(trace);
    }

    @Override
    public String toString() {
        return graph.toString();
    }

    /**
     * Performs a breadth first search for looking up the vertex.
     *
     * @param method           The method describing the vertex.
     * @return Returns the vertex described by the given method.
     */
    private CallTreeVertex lookUpVertexBFS(final String method) {

        BreadthFirstIterator<CallTreeVertex, CallTreeEdge> bfs = new BreadthFirstIterator<>(graph, root);

        while (bfs.hasNext()) {
            CallTreeVertex vertex = bfs.next();
            if (vertex.getMethod().equals(method)) {
                return vertex;
            }
        }

        throw new IllegalArgumentException("Given trace refers to no vertex in graph!");
    }

    /**
     * Performs a depth first search for looking up the vertex.
     *
     * @param method           The method describing the vertex.
     * @return Returns the vertex described by the given method.
     */
    @SuppressWarnings("unused")
    private CallTreeVertex lookUpVertexDFS(final String method) {

        DepthFirstIterator<CallTreeVertex, CallTreeEdge> dfs = new DepthFirstIterator<>(graph, root);

        while (dfs.hasNext()) {
            CallTreeVertex vertex = dfs.next();
            if (vertex.getMethod().equals(method)) {
                return vertex;
            }
        }

        throw new IllegalArgumentException("Given trace refers to no vertex in graph!");
    }

    /**
     * Converts the graph into a PNG file using a hierarchical layout.
     * This method should be only used for small graphs, i.e. not more than 1000 (max 2000) vertices.
     *
     * @param output The file path of the resulting PNG file.
     * @param visitedVertices The set of visited vertices.
     * @param targetVertices The set of target vertices.
     */
    private void convertGraphToPNG(File output, Set<CallTreeVertex> visitedVertices, Set<CallTreeVertex> targetVertices) {

        JGraphXAdapter<CallTreeVertex, CallTreeEdge> graphXAdapter
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
    private void colorVertices(JGraphXAdapter<CallTreeVertex, CallTreeEdge> graphXAdapter, Set<CallTreeVertex> visitedVertices,
                               Set<CallTreeVertex> targetVertices) {

        Map<CallTreeVertex, mxICell> vertexToCellMap = graphXAdapter.getVertexToCellMap();

        /*
         * We mark the covered target vertices orange in the graph.
         */
        Set<CallTreeVertex> coveredTargets = new HashSet<>(targetVertices);
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
}
