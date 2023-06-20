package de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg;

import com.google.common.collect.Sets;
import com.google.errorprone.annotations.Var;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.CFGVertex;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.Statement;

import java.util.Set;

/**
 * Represents an inter-procedural control dependence graph (CDG).
 */
public class InterCDG extends BaseCFG {

    /**
     * Constructs an inter-procedural CDG from the inter-procedural CFG.
     *
     * @param cfg The inter-procedural CFG.
     */
    public InterCDG(BaseCFG cfg) {
        super(cfg.getMethodName() + "-CDG", cfg.getEntry(), cfg.getExit());
        final PostDominatorTree pdt = new PostDominatorTree(cfg);
        buildCDG(cfg, pdt);
    }

    /**
     * Builds the CDG from the given CFG and PDT.
     *
     * @param cfg The given inter-procedural CFG.
     * @param pdt The given PDT.
     */
    private void buildCDG(final BaseCFG cfg, final PostDominatorTree pdt) {

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

        if (tokens[2].equals("entry")) {
            return getEntry();
        } else if (tokens[2].equals("exit")) {
            return getExit();
        } else {
            int instructionIndex;
            if (tokens.length == 3) { // branch lookup
                instructionIndex = Integer.parseInt(tokens[2]);
            } else {
                instructionIndex = Integer.parseInt(tokens[3]);
            }

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
                    for (Statement stmt : blockStmt.getStatements()) {
                        if (stmt instanceof BasicStatement) {
                            BasicStatement basicStatement = (BasicStatement) stmt;
                            if (basicStatement.getInstructionIndex() == instructionIndex) {
                                return vertex;
                            }
                        }
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
