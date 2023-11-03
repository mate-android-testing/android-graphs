package de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg;

import com.google.common.collect.Sets;
import com.google.errorprone.annotations.Var;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.traverse.BreadthFirstIterator;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a control dependence graph (CDG).
 */
public class CDG extends BaseCFG {

    private static final Logger LOGGER = LogManager.getLogger(BaseCFG.class);

    /**
     * Maintains a reference to the individual intra CFGs.
     * NOTE: Only a reference to the entry and exit vertex is hold!
     */
    private final Map<String, BaseCFG> intraCFGs;

    /**
     * Constructs a CDG from the given CFG.
     *
     * @param cfg The given CFG.
     */
    public CDG(BaseCFG cfg) {
        super(cfg.getMethodName(), cfg.getEntry(), cfg.getExit());
        final PDT pdt = new PDT(cfg);
        buildCDG(cfg, pdt);

        if (cfg instanceof InterCFG) {
            intraCFGs = ((InterCFG) cfg).getIntraCFGs();
        } else { // intra
            intraCFGs = new HashMap<>();
            intraCFGs.put(cfg.getMethodName(), new DummyCFG(cfg));
        }
    }

    /**
     * Builds the CDG from the given CFG and PDT.
     *
     * @param cfg The given inter-procedural CFG.
     * @param pdt The given PDT.
     */
    private void buildCDG(final BaseCFG cfg, final PDT pdt) {

        final Set<CFGVertex> vertices = cfg.getVertices();

        // Add all vertices from CFG.
        for (CFGVertex vertex : vertices) {
            graph.addVertex(vertex);
        }

        // Find a set of edges, such that the successor is not an ancestor of a given vertex in the PDT.
        final Set<Edge> edges = Sets.newHashSet();
        for (CFGVertex vertex : vertices) {
            for (CFGVertex successor : cfg.getSuccessors(vertex)) {
                if (!pdt.getTransitiveSuccessors(successor).contains(vertex)) {
                    edges.add(new Edge(vertex, successor));
                }
            }
        }

        // Mark vertices in the PDT and construct edges for them.
        for (Edge edge : edges) {

            final CFGVertex lca = pdt.getLeastCommonAncestor(edge.source, edge.target);

            // Starting at target, traverse backwards in the PDT until we arrive at the LCA.
            @Var CFGVertex current = edge.target;
            while (!current.equals(lca)) {
                graph.addEdge(edge.source, current); // Current vertex is control dependent on source of edge.
                current = pdt.getPredecessors(current).iterator().next();
            }

            // Check if LCA is control-dependent on itself.
            if (lca == edge.source) {
                graph.addEdge(edge.source, lca); // Create loop in the CDG.
            }
        }

        // Connect all non control-dependent vertices with the entry.
        for (CFGVertex vertex : graph.vertexSet()) {
            if (!vertex.equals(getEntry()) && graph.incomingEdgesOf(vertex).isEmpty()) {
                graph.addEdge(getEntry(), vertex);
            }
        }

        findAndAddDisconnectedVertices(cfg);
    }

    /**
     * Find disconnected vertices by traversing the CFG starting from the entry in a breadth-first manner. If we find a
     * vertex that is not connect to the CDG entry, we add it manually to the CDG.
     *
     * @param cfg The {@link BaseCFG} from which the CDG is being generated.
     */
    private void findAndAddDisconnectedVertices(BaseCFG cfg) {
        CFGVertex current = cfg.getEntry();
        Set<CFGVertex> visited = new HashSet<>();
        Queue<CFGVertex> queue = new ArrayDeque<>();
        // Iterate over all vertices in a breadth-first manner.
        while (current != null) {
            visited.add(current);
            queue.addAll(cfg.getSuccessors(current).stream().filter(s -> !visited.contains(s)).collect(Collectors.toSet()));
            // Check if the vertex is connected to the CDG entry.
            if (!current.equals(getEntry()) && !getTransitiveSuccessors(getEntry()).contains(current)) {
                LOGGER.warn("Adding disconnected vertex: " + current);
                addDisconnectedVertex(cfg, current);
            }
            current = queue.poll();
        }
    }

    /**
     * Add a disconnected vertex to the CDG.
     * If the vertex's CFG parent is a control-dependency add the vertex to the CDG as a successor of the CFG parent,
     * otherwise add it to the CDG as a successor of the the CFG grandparents.
     *
     * @param cfg                The {@link BaseCFG} from which the CDG is being generated.
     * @param disconnectedVertex The vertex to be added to the CDG.
     */
    private void addDisconnectedVertex(BaseCFG cfg, CFGVertex disconnectedVertex) {
        Set<CFGVertex> entryConnected = (Set<CFGVertex>) getTransitiveSuccessors(getEntry());
        Set<CFGVertex> cfgParents = cfg.getPredecessors(disconnectedVertex);
        for (CFGVertex parent : cfgParents) {

            if (!entryConnected.contains(parent)) {
                continue;
            }

            // If the current parent is a branch, the disconnected vertex is dependent on the branch
            // and we add an edge from the branch to the disconnected vertex.
            if (parent.isSwitchVertex() || parent.isIfVertex()) {
                addEdge(parent, disconnectedVertex);
            } else {
                // Otherwise, if the current parent is not a branch, the disconnected vertex is dependent
                // on all CDG parents of the current parent.
                Set<CFGVertex> cfgGrandParents = cfg.getPredecessors(parent);
                for (CFGVertex grandParent : cfgGrandParents) {
                    if (!entryConnected.contains(parent)) {
                        continue;
                    }
                    addEdge(grandParent, disconnectedVertex);
                }
            }
        }
    }

    /**
     * Searches for the vertex described by the given trace in the graph.
     *
     * @param trace The trace describing the vertex, i.e. className->methodName->(entry|exit|instructionIndex).
     * @return Returns the vertex corresponding to the given trace.
     */
    @Override
    public CFGVertex lookUpVertex(String trace) {

        /*
         * A trace has the following form:
         *   className -> methodName -> ([entry|exit|if|switch])? -> (index)?
         *
         * The first two components are always fixed, while the instruction type and the instruction index
         * are optional, but not both at the same time:
         *
         * Making the instruction type optional allows to search (by index) for a custom instruction, e.g. a branch.
         * Making the index optional allows to look up virtual entry and exit vertices as well as if and switch vertices.
         */
        String[] tokens = trace.split("->");

        // Retrieve fully qualified method name (class name + method name).
        final String method = tokens[0] + "->" + tokens[1];

        // check whether method belongs to graph
        if (!intraCFGs.containsKey(method)) {
            throw new IllegalArgumentException("Given trace refers to a method not part of the graph: " + method);
        }

        if (tokens.length == 3) {

            if (tokens[2].equals("entry")) {
                return intraCFGs.get(method).getEntry();
            } else if (tokens[2].equals("exit")) {
                return intraCFGs.get(method).getExit();
            } else {
                // lookup of a branch
                int instructionIndex = Integer.parseInt(tokens[2]);
                return lookUpVertex(method, instructionIndex, getEntry());
            }

        } else if (tokens.length == 4) { // if or switch statement

            int instructionIndex = Integer.parseInt(tokens[3]);
            return lookUpVertex(method, instructionIndex, getEntry());

        } else if (tokens.length == 5) { // basic block coverage trace

            int instructionIndex = Integer.parseInt(tokens[2]);
            CFGVertex entry = intraCFGs.get(method).getEntry();
            return lookUpVertex(method, instructionIndex, entry);

        } else {
            throw new IllegalArgumentException("Unrecognized trace: " + trace);
        }
    }

    /**
     * Performs a breadth first search for looking up the vertex.
     *
     * @param method           The method describing the vertex.
     * @param instructionIndex The instruction index of the vertex (the wrapped instruction).
     * @param entry            The entry vertex of the graph.
     * @return Returns the vertex described by the given method and the instruction index, otherwise
     * a {@link IllegalArgumentException} is thrown.
     */
    private CFGVertex lookUpVertex(String method, int instructionIndex, CFGVertex entry) {

        BreadthFirstIterator<CFGVertex, CFGEdge> bfs = new BreadthFirstIterator<>(graph, entry);

        while (bfs.hasNext()) {
            CFGVertex vertex = bfs.next();
            if (vertex.containsInstruction(method, instructionIndex)) {
                return vertex;
            }
        }

        throw new IllegalArgumentException("Given trace refers to no vertex in graph!");
    }

    /**
     * Retrieves the graph type.
     *
     * @return Returns the graph type.
     */
    @Override
    public GraphType getGraphType() {
        return GraphType.INTERCDG;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BaseCFG copy() {
        return super.clone();
    }

    /**
     * A simple edge class consisting of a source and target vertex.
     */
    private static class Edge {

        final CFGVertex source;
        final CFGVertex target;

        Edge(CFGVertex source, CFGVertex target) {
            this.source = source;
            this.target = target;
        }
    }
}
