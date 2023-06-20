package de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.CFGVertex;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.Statement;

import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Represents a post-dominator tree.
 */
public class PostDominatorTree extends BaseCFG {

    /**
     * Creates a post-dominator tree from the given control flow graph.
     *
     * @param cfg The given control flow graph.
     */
    public PostDominatorTree(final BaseCFG cfg) {
        super(cfg.getMethodName() + "-PDT", cfg.getEntry(), cfg.getExit());
        final Map<CFGVertex, Set<CFGVertex>> postDominators = computePostDominators(cfg.reverseGraph());
        buildDominanceTree(cfg, postDominators);
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

        // decompose trace into class, method  and instruction index
        String[] tokens = trace.split("->");

        // class + method + entry|exit|instruction-index
        assert tokens.length == 3;

        // retrieve fully qualified method name (class name + method name)
        String method = tokens[0] + "->" + tokens[1];

        // check whether trace refers to this graph
        if (!method.equals(getMethodName())) {
            throw new IllegalArgumentException("Given trace refers to a different method, thus to a different graph!");
        }

        if (tokens[2].equals("entry")) {
            return getEntry();
        } else if (tokens[2].equals("exit")) {
            return getExit();
        } else {
            // brute force search
            int instructionIndex = Integer.parseInt(tokens[2]);

            for (CFGVertex vertex : getVertices()) {

                if (vertex.isEntryVertex() || vertex.isExitVertex()) {
                    continue;
                }

                Statement statement = vertex.getStatement();

                if (statement.getType() == Statement.StatementType.BASIC_STATEMENT) {
                    // no basic blocks
                    BasicStatement basicStmt = (BasicStatement) statement;
                    if (basicStmt.getInstructionIndex() == instructionIndex) {
                        return vertex;
                    }
                } else if (statement.getType() == Statement.StatementType.BLOCK_STATEMENT) {
                    // basic blocks
                    BlockStatement blockStmt = (BlockStatement) statement;

                    // check if index is in range [firstStmt,lastStmt]
                    BasicStatement firstStmt = (BasicStatement) blockStmt.getFirstStatement();
                    BasicStatement lastStmt = (BasicStatement) blockStmt.getLastStatement();

                    if (firstStmt.getInstructionIndex() <= instructionIndex &&
                            instructionIndex <= lastStmt.getInstructionIndex()) {
                        return vertex;
                    }
                }
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
