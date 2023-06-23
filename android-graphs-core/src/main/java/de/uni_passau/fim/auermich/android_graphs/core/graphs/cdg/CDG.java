package de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg;

import com.google.common.collect.Sets;
import com.google.errorprone.annotations.Var;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.*;
import org.jgrapht.traverse.BreadthFirstIterator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents a control dependence graph (CDG).
 */
public class CDG extends BaseCFG {

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
        super(cfg.getMethodName() + "-CDG", cfg.getEntry(), cfg.getExit());
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
    }

    /**
     * Searches for the vertex described by the given trace in the graph.
     *
     * @param trace The trace describing the vertex, i.e. className->methodName->(entry|exit|instructionIndex).
     * @return Returns the vertex corresponding to the given trace.
     */
    @Override
    public CFGVertex lookUpVertex(String trace) {

        // Decompose trace into class, method  and instruction index.
        String[] tokens = trace.split("->");

        // Retrieve fully qualified method name (class name + method name).
        String method = tokens[0] + "->" + tokens[1];

        if (tokens[2].equals("entry")) {
            return intraCFGs.get(method).getEntry();
        } else if (tokens[2].equals("exit")) {
            return intraCFGs.get(method).getExit();
        } else {
            int instructionIndex = Integer.parseInt(tokens[2]);
            return lookUpVertex(method, instructionIndex, getEntry());
        }
    }

    /**
     * Performs a breadth first search for looking up the vertex.
     *
     * @param method The method describing the vertex.
     * @param instructionIndex The instruction index of the vertex (the wrapped instruction).
     * @param entry The entry vertex of the graph.
     * @return Returns the vertex described by the given method and the instruction index, otherwise
     *         a {@link IllegalArgumentException} is thrown.
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
