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

public class PostDominatorTree extends BaseCFG {

    public PostDominatorTree(BaseCFG cfg) {
        super(cfg.getMethodName() + "-Post-Dominator-Tree", cfg.getEntry(), cfg.getExit());
        Map<CFGVertex, Set<CFGVertex>> postDominators = this.computePostDominators(cfg.reverseGraph());
        this.buildDominanceTree(cfg, postDominators);
    }

    private Map<CFGVertex, Set<CFGVertex>> computePostDominators(BaseCFG reversedCFG) {
        final CFGVertex entry = reversedCFG.getExit();      // Fetch exit as entry due to reversed CFG.
        final Set<CFGVertex> vertices = reversedCFG.getVertices();
        final Set<CFGVertex> nodesWithoutEntry = Sets.newHashSet(reversedCFG.getVertices());
        nodesWithoutEntry.remove(entry);

        // Maps a node to its dominators (the nodes that dominate it).
        final Map<CFGVertex, Set<CFGVertex>> dominatorsOfNode = Maps.newHashMap();

        // The entry dominates itself.
        dominatorsOfNode.put(entry, Sets.newHashSet(entry));

        // Initial coarse approximation: every node is dominated by every other node.
        for (CFGVertex n : nodesWithoutEntry) {
            dominatorsOfNode.put(n, Sets.newHashSet(vertices));
        }

        // Compute dominators iteratively until we find a fixed-point.
        boolean changed = true;
        while (changed) {
            changed = false;

            for (CFGVertex node : nodesWithoutEntry) {
                Set<CFGVertex> currentDominators = dominatorsOfNode.get(node);

                // Refinement: compute intersection over dominators of all immediate predecessors.
                Set<CFGVertex> newDominators = Sets.newHashSet(vertices);
                for (CFGVertex pre : reversedCFG.getPredecessors(node)) {
                    newDominators.retainAll(dominatorsOfNode.get(pre));
                }
                newDominators.add(node); // Every node dominates itself.

                // Check if fixed-point reached.
                if (!newDominators.containsAll(currentDominators)) {
                    dominatorsOfNode.put(node, newDominators);
                    changed = true;
                }
            }
        }

        // Compute strict dominators -> no reflexivity.
        for (CFGVertex node : vertices) {
            dominatorsOfNode.get(node).remove(node);
        }

        return dominatorsOfNode;
    }

    private void buildDominanceTree(BaseCFG cfg, Map<CFGVertex, Set<CFGVertex>> postDominators) {

        // Add all nodes from CFG
        for (CFGVertex vertex : cfg.getVertices()) {
            graph.addVertex(vertex);
        }

        Queue<CFGVertex> queue = Queues.newArrayDeque();
        queue.add(cfg.getExit());           // Start with exit which is not dominated by any other node
        while (!queue.isEmpty()) {
            CFGVertex m = queue.poll();

            // Check which nodes m dominates
            for (CFGVertex n : cfg.getVertices()) {
                Set<CFGVertex> dominators = postDominators.get(n);
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

    @Override
    public CFGVertex lookUpVertex(String trace) {

        // TODO: Copy from IntraCFG --> Improve Architecture

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

    @Override
    public GraphType getGraphType() {
        return GraphType.PDT;
    }

    @Override
    public BaseCFG copy() {
        return super.clone();
    }
}
