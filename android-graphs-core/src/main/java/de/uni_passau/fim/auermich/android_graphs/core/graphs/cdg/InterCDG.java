package de.uni_passau.fim.auermich.android_graphs.core.graphs.cdg;

import com.google.common.collect.Sets;
import com.google.errorprone.annotations.Var;

import java.util.Set;

import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.BaseCFG;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg.CFGVertex;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.Statement;

// TODO: Even though the CDG originates from a CFG and has all the same nodes as the CFG,
//  we should think about changing the architecture.
public class InterCDG extends BaseCFG {

    public InterCDG(BaseCFG cfg) {
        super(cfg.getMethodName() + "-CDG", cfg.getEntry(), cfg.getExit());
        PostDominatorTree pdt = new PostDominatorTree(cfg);
        this.buildCDG(cfg, pdt);
    }

    /**
     * Generates the CDG from the supplied cfg and pdt.
     *
     * @param cfg based on which the cdg will be built.
     * @param pdt based on which the cdg will be built.
     */
    private void buildCDG(BaseCFG cfg, PostDominatorTree pdt) {
        final Set<CFGVertex> nodes = cfg.getVertices();

        // Add all nodes from CFG
        for (CFGVertex vertex : nodes) {
            graph.addVertex(vertex);
        }

        // Find a set of edges, such that tgt is not an ancestor of src in the post-dominator tree.
        Set<Edge> edges = Sets.newHashSet();
        for (CFGVertex src : nodes) {
            for (CFGVertex tgt : cfg.getSuccessors(src)) {
                if (!pdt.getTransitiveSuccessors(tgt).contains(src)) {
                    edges.add(new Edge(src, tgt));
                }
            }
        }

        // Mark nodes in the PDT and construct edges for them.
        for (Edge edge : edges) {
            final CFGVertex lca = pdt.getLeastCommonAncestor(edge.src, edge.tgt);

            // Starting at tgt, traverse backwards in the post-dominator tree until we arrive at the lca.
            @Var CFGVertex current = edge.tgt;
            while (!current.equals(lca)) {
                graph.addEdge(edge.src, current); // Current node is control dependent on src.
                current = pdt.getPredecessors(current).iterator().next();
            }

            // Check if lca is control-dependent on itself.
            if (lca == edge.src) {
                graph.addEdge(edge.src, lca); // Create loop in the CDG.
            }
        }

    }


    @Override
    public CFGVertex lookUpVertex(String trace) {
        // decompose trace into class, method  and instruction index
        String[] tokens = trace.split("->");

        if (tokens[2].equals("entry")) {
            return getEntry();
        } else if (tokens[2].equals("exit")) {
            return getExit();
        } else {
            int instructionIndex;
            if (tokens.length == 3) {       // branch lookup
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

    @Override
    public GraphType getGraphType() {
        return GraphType.INTER_CDG;
    }

    @Override
    public BaseCFG copy() {
        return super.clone();
    }

    private static class Edge {
        final CFGVertex src;
        final CFGVertex tgt;

        Edge(CFGVertex src, CFGVertex tgt) {
            this.src = src;
            this.tgt = tgt;
        }
    }
}
