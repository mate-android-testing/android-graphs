package de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.*;
import org.jgrapht.traverse.BreadthFirstIterator;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Represents a post-dominator tree.
 */
public class PDT extends BaseCFG {

    /**
     * Maintains a reference to the individual intra CFGs.
     * NOTE: Only a reference to the entry and exit vertex is hold!
     */
    private final Map<String, BaseCFG> intraCFGs;

    /**
     * Creates a post-dominator tree from the given CFG.
     *
     * @param cfg The given CFG.
     */
    public PDT(final BaseCFG cfg) {
        super(cfg.getMethodName() + "-PDT", cfg.getExit(), cfg.getEntry()); // entry and exit are reversed
        final Map<CFGVertex, Set<CFGVertex>> postDominators = computePostDominators(cfg.reverseGraph());
        buildDominanceTree(cfg, postDominators);
        if (cfg instanceof InterCFG) {
            intraCFGs = ((InterCFG) cfg).getIntraCFGs();
        } else { // intra
            intraCFGs = new HashMap<>();
            intraCFGs.put(cfg.getMethodName(), new DummyCFG(cfg));
        }
    }

    /**
     * Computes the post-dominators on the reversed control flow graph.
     *
     * @param reversedCFG The control flow graph with reversed edges.
     * @return Returns a mapping that describes which vertex post-dominates which other vertices.
     */
    private Map<CFGVertex, Set<CFGVertex>> computePostDominators(final BaseCFG reversedCFG) {

        final CFGVertex entry = reversedCFG.getExit(); // Entry is the exit in the original CFG.
        final Set<CFGVertex> vertices = reversedCFG.getVertices();
        final Set<CFGVertex> nodesWithoutEntry = Sets.newHashSet(reversedCFG.getVertices());
        nodesWithoutEntry.remove(entry);

        // Maps a vertex to its dominators (the vertices that dominate it).
        final Map<CFGVertex, Set<CFGVertex>> dominatorsOfVertex = Maps.newHashMap();

        // The entry dominates itself.
        dominatorsOfVertex.put(entry, Sets.newHashSet(entry));

        // Initial coarse approximation: Every vertex is dominated by every other vertex.
        for (CFGVertex n : nodesWithoutEntry) {
            dominatorsOfVertex.put(n, Sets.newHashSet(vertices));
        }

        // Compute dominators iteratively until we find a fixed-point.
        boolean changed = true;
        while (changed) {
            changed = false;

            for (CFGVertex vertex : nodesWithoutEntry) {
                Set<CFGVertex> currentDominators = dominatorsOfVertex.get(vertex);

                // Refinement: Compute intersection over dominators of all immediate predecessors.
                Set<CFGVertex> newDominators = Sets.newHashSet(vertices);
                for (CFGVertex pre : reversedCFG.getPredecessors(vertex)) {
                    newDominators.retainAll(dominatorsOfVertex.get(pre));
                }
                newDominators.add(vertex); // Every vertex dominates itself.

                // Check if fixed-point reached.
                if (!newDominators.containsAll(currentDominators)) {
                    dominatorsOfVertex.put(vertex, newDominators);
                    changed = true;
                }
            }
        }

        // Compute strict dominators -> no reflexivity.
        for (CFGVertex node : vertices) {
            dominatorsOfVertex.get(node).remove(node);
        }

        return dominatorsOfVertex;
    }

    /**
     * Constructs the post-dominator tree (PDT) from the given post-dominator relations.
     *
     * @param cfg The original control flow graph.
     * @param postDominators The post-dominator relations.
     */
    private void buildDominanceTree(BaseCFG cfg, Map<CFGVertex, Set<CFGVertex>> postDominators) {

        // Add all vertices from CFG.
        for (CFGVertex vertex : cfg.getVertices()) {
            graph.addVertex(vertex);
        }

        final Queue<CFGVertex> queue = Queues.newArrayDeque();
        queue.add(cfg.getExit()); // Start with exit which is not dominated by any other vertex.

        while (!queue.isEmpty()) {
            CFGVertex m = queue.poll();

            // Check which vertices m dominates.
            for (CFGVertex n : cfg.getVertices()) {
                final Set<CFGVertex> dominators = postDominators.get(n);
                if (dominators.contains(m)) {
                    dominators.remove(m);
                    if (dominators.isEmpty()) {
                        graph.addEdge(m, n);
                        queue.add(n);
                    }
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

        } else {
            throw new IllegalArgumentException("Unrecognized trace: " + trace);
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
        return GraphType.PDT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BaseCFG copy() {
        return super.clone();
    }
}
