package de.uni_passau.fim.auermich.android_graphs.core.graphs.calltree;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.Edge;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.Vertex;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.InterCFG;
import de.uni_passau.fim.auermich.android_graphs.core.statements.ExitStatement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.graph.GraphWalk;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.nio.AttributeType;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
public class CallTree {

    private static final Logger LOGGER = LogManager.getLogger(CallTree.class);

    protected final Graph<CallTreeVertex, CallTreeEdge> graph = GraphTypeBuilder
            .<CallTreeVertex, CallTreeEdge>directed()
            .allowingSelfLoops(true)
            .edgeClass(CallTreeEdge.class)
            .edgeSupplier(CallTreeEdge::new)
            .buildGraph();

    /**
     * A virtual root entry.
     */
    protected final CallTreeVertex root;

    /**
     * Caches all computed graph paths.
     */
    private final Map<String, Optional<GraphPath<CallTreeVertex, CallTreeEdge>>> cache = new HashMap<>();

    /**
     * Constructs the call tree based on the supplied {@link InterCFG}.
     *
     * @param interCFG The inter CFG from which the call tree is derived.
     */
    public CallTree(final InterCFG interCFG) {

        root = new CallTreeVertex(interCFG.getEntry().getMethod());
        String mainActivityConstructor = interCFG.getMainActivity().getDefaultConstructor();
        Set<String> methods = interCFG.getVertices().stream().map(Vertex::getMethod).collect(Collectors.toSet());

        for (String method : methods) {

            // construct for each method in the CFG a vertex for the call tree
            CallTreeVertex methodVertex = new CallTreeVertex(method);
            graph.addVertex(methodVertex);

            // derive which methods are invoked by the current vertex
            Set<String> calledMethods = interCFG.getVertices().stream()
                    .filter(v -> v.getMethod().equals(method)) // only consider vertices belonging to the current method
                    .flatMap(v -> interCFG.getOutgoingEdges(v).stream())
                    .filter(e -> !ignoreEdge(e))
                    .map(Edge::getTarget)
                    .filter(v -> !v.containsReturnStatement()) // ignore virtual return statements
                    .map(Vertex::getMethod)
                    .filter(m -> !m.equals(method)) // ignore recursive calls (only a single vertex per method)
                    .filter(m -> !m.startsWith("callbacks ") // ignore the callbacks sub graph
                            || method.contains("onResume"))
                    .filter(m -> !m.startsWith("static initializer") // ignore the static initializers
                            || method.equals(root.toString()))
                    .filter(m -> !method.equals(root.toString()) || m.startsWith("static initializers")
                            || m.startsWith(mainActivityConstructor))
                    .collect(Collectors.toSet());

            // for each invoked method define an edge
            calledMethods.forEach(calledMethod -> {
                CallTreeVertex vertex = new CallTreeVertex(calledMethod);
                graph.addVertex(vertex);
                graph.addEdge(methodVertex, vertex);
            });
        }
    }

    /**
     * Ignores certain edges, e.g. edges between virtual exit vertices.
     *
     * @param edge The edge that should be checked.
     * @return Returns {@code true} if the edge should be ignored, otherwise {@code false} is returned.
     */
    private boolean ignoreEdge(Edge edge) {
        return edge.getSource().getStatement() instanceof ExitStatement
                && edge.getTarget().getStatement() instanceof ExitStatement;
    }

    /**
     * Retrieves the shortest path between the root vertex and the given target vertex.
     *
     * @param target Describes a target vertex.
     * @return Returns the shortest path between the root and the given target vertex if such path exists.
     */
    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPath(String target) {
        return getShortestPath(new CallTreeVertex(target));
    }

    /**
     * Retrieves the shortest path between the root vertex and the given target vertex.
     *
     * @param target The target vertex.
     * @return Returns the shortest path between the root and the given target vertex if such path exists.
     */
    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPath(CallTreeVertex target) {
        return getShortestPath(root, target);
    }

    /**
     * Retrieves the shortest path between the given source and target vertex.
     *
     * @param source The source vertex.
     * @param target The target vertex.
     * @return Returns the shortest path between the given source and target vertex if such path exists.
     */
    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPath(CallTreeVertex source, CallTreeVertex target) {
        Stream.of(source, target).forEach(vertex -> {
            if (graph.addVertex(vertex)) {
                LOGGER.warn("Graph did not contain vertex '" + vertex.toString() + "'");
            }
        });
        return cache.computeIfAbsent(source.getMethod() + "-->" + target.getMethod(),
                t -> Optional.ofNullable(BFSShortestPath.findPathBetween(graph, source, target)));
    }

    /**
     * Retrieves the shortest path from the root vertex through the list of given stop vertices.
     *
     * @param stops A list of vertices through which the shortest path must flow.
     * @return Returns the shortest path from the root vertex through the given intermediate vertices.
     */
    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPathWithStops(List<CallTreeVertex> stops) {
        return getShortestPathWithStops(root, stops);
    }

    /**
     * Retrieves the shortest path from the given start vertex through the list of given stop vertices.
     *
     * @param startVertex The first vertex in the shortest path.
     * @param stops A list of vertices through which the shortest path must flow.
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
    public Set<CallTreeVertex> getUnreachableVertices() {
        return graph.vertexSet().stream().filter(v -> getShortestPath(v).isEmpty()).collect(Collectors.toSet());
    }

    public Set<CallTreeVertex> getMethodCallers(CallTreeVertex method, Predicate<CallTreeVertex> validCaller) {
        return goUntilSatisfied(method, m -> graph.incomingEdgesOf(m).stream().map(CallTreeEdge::getSource), validCaller);
    }

    private <T> Set<T> goUntilSatisfied(T start, Function<T, Stream<T>> childGetter, Predicate<T> predicate) {

        Queue<T> workQueue = new LinkedList<>();
        workQueue.add(start);
        Set<T> satisfied = new HashSet<>();
        Set<T> seen = new HashSet<>();

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

    public static Graph<CallTreeVertex, CallTreeEdge> onlyKeepVertices(Graph<CallTreeVertex, CallTreeEdge> graph,
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

    public void toClassTreeDot(File output) {
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
}
