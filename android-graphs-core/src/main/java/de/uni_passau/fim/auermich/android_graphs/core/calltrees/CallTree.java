package de.uni_passau.fim.auermich.android_graphs.core.calltrees;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.Edge;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.Vertex;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.InterCFG;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.ReferenceType;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.BFSShortestPath;
import org.jgrapht.graph.GraphWalk;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.nio.dot.DOTExporter;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CallTree {
    private static final Logger LOGGER = LogManager.getLogger(CallTree.class);

    protected final Graph<CallTreeVertex, CallTreeEdge> graph = GraphTypeBuilder
            .<CallTreeVertex, CallTreeEdge>directed()
            .allowingSelfLoops(true)
            .edgeClass(CallTreeEdge.class)
            .edgeSupplier(CallTreeEdge::new)
            .buildGraph();
    protected final CallTreeVertex root;

    public CallTree(InterCFG interCFG) {
        root = new CallTreeVertex(interCFG.getEntry().getMethod());
        String mainActivityConstructor = interCFG.getMainActivity().getDefaultConstructor();
        Set<String> methods = interCFG.getVertices().stream().map(Vertex::getMethod).collect(Collectors.toSet());

        for (String method : methods) {
            CallTreeVertex methodVertex = new CallTreeVertex(method);
            graph.addVertex(methodVertex);

            Set<String> calledMethods = interCFG.getVertices().stream()
                    .filter(v -> v.getMethod().equals(method))
                    .flatMap(v -> interCFG.getOutgoingEdges(v).stream())
                    .map(Edge::getTarget)
                    .filter(v -> !v.containsReturnStatement())
                    .map(Vertex::getMethod)
                    .filter(m -> !m.equals(method)) // No self calling
                    .filter(m -> !m.startsWith("callbacks ") || method.contains("onResume")) // No return to callback graph
                    .filter(m -> !m.startsWith("static initializer") || method.equals(root.toString())) // Only root points to static init
                    .filter(m -> !method.equals(root.toString()) || m.startsWith("static initializers") || m.startsWith(mainActivityConstructor))
                    .collect(Collectors.toSet());

            calledMethods.forEach(t -> {
                CallTreeVertex vertex = new CallTreeVertex(t);
                graph.addVertex(vertex);
                graph.addEdge(methodVertex, vertex);
            });
        }
    }

    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPath(String target) {
        return getShortestPath(new CallTreeVertex(target));
    }

    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPath(CallTreeVertex target) {
        return getShortestPath(root, target);
    }

    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPath(CallTreeVertex source, CallTreeVertex target) {
        Stream.of(source, target).forEach(vertex -> {
            if (graph.addVertex(vertex)) {
                LOGGER.warn("Graph did not contain vertex '" + vertex.toString() + "'");
            }
        });
        return Optional.ofNullable(BFSShortestPath.findPathBetween(graph, source, target));
    }

    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPathWithStops(List<CallTreeVertex> stops) {
        return getShortestPathWithStops(root, stops);
    }

    public Optional<GraphPath<CallTreeVertex, CallTreeEdge>> getShortestPathWithStops(CallTreeVertex startVertex, List<CallTreeVertex> stops) {
        CallTreeVertex start = startVertex;
        List<GraphPath<CallTreeVertex, CallTreeEdge>> paths = new LinkedList<>();

        for (CallTreeVertex end : stops) {
            var path = getShortestPath(start, end);

            if (path.isEmpty()) {
                return Optional.empty();
            } else {
                paths.add(path.get());
            }

            start = end;
        }

        List<CallTreeEdge> edges = paths.stream().map(GraphPath::getEdgeList).flatMap(Collection::stream).collect(Collectors.toList());
        double weight = paths.stream().mapToDouble(GraphPath::getWeight).sum();

        return Optional.of(new GraphWalk<>(graph, startVertex, start, edges, weight));
    }

    public Set<CallTreeVertex> getVertices() {
        return graph.vertexSet();
    }

    private Optional<String> getMethodName(Instruction instruction) {
        if (instruction.getOpcode().referenceType == ReferenceType.METHOD) {
            return Optional.of(((ReferenceInstruction) instruction).getReference().toString());
        } else {
            return Optional.empty();
        }
    }

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

    public void toDot(File output) {
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

        exporter.exportGraph(graph, output);
    }
}
