package de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg;

import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.Vertex;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.Statement;
import de.uni_passau.fim.auermich.android_graphs.core.utility.InstructionUtils;
import de.uni_passau.fim.auermich.android_graphs.core.utility.MethodUtils;
import de.uni_passau.fim.auermich.android_graphs.core.utility.Utility;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.Edge;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.iface.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.util.*;
import java.util.stream.Collectors;

public class IntraCFG extends BaseCFG implements Cloneable {

    private static final Logger LOGGER = LogManager.getLogger(IntraCFG.class);
    private static final GraphType GRAPH_TYPE = GraphType.INTRACFG;

    public IntraCFG(String methodName) {
        super(methodName);
    }

    public IntraCFG(String methodName, DexFile dexFile, boolean useBasicBlocks) {
        super(methodName);
        Optional<Method> targetMethod = MethodUtils.searchForTargetMethod(dexFile, methodName);

        if (!targetMethod.isPresent()) {
            throw new IllegalStateException("Target method not present in dex files!");
        }

        // TODO: can we parallelize the construction?
        constructCFG(dexFile, targetMethod.get(), useBasicBlocks);
    }

    /**
     * Computes the intra-procedural CFG for a given method.
     *
     * @param dexFile        The dex file containing the target method.
     * @param targetMethod   The method for which we want to generate the CFG.
     * @param useBasicBlocks Whether to use basic blocks in the construction of the CFG or not.
     */
    private void constructCFG(DexFile dexFile, Method targetMethod, boolean useBasicBlocks) {
        if (useBasicBlocks) {
            constructCFGWithBasicBlocks(dexFile, targetMethod);
        } else {
            constructCFG(dexFile, targetMethod);
        }
    }

    /**
     * Computes the intra-procedural CFG for a given method. Doesn't use basic blocks.
     *
     * @param dexFile      The dex file containing the target method.
     * @param targetMethod The method for which we want to generate the CFG.
     */
    private void constructCFG(DexFile dexFile, Method targetMethod) {

        LOGGER.debug("Constructing Intra-CFG for method: " + targetMethod);

        MethodImplementation methodImplementation = targetMethod.getImplementation();

        if (methodImplementation != null) {

            List<AnalyzedInstruction> analyzedInstructions = MethodUtils.getAnalyzedInstructions(dexFile, targetMethod);
            List<Vertex> vertices = new ArrayList<>();

            // pre-create a vertex for each single instruction
            for (int index = 0; index < analyzedInstructions.size(); index++) {

                // ignore parse-switch and packed-switch payload instructions
                if (InstructionUtils.isSwitchPayloadInstruction(analyzedInstructions.get(index))) {
                    continue;
                }

                Statement stmt = new BasicStatement(getMethodName(), analyzedInstructions.get(index));
                Vertex vertex = new Vertex(stmt);

                // keep track of invoke vertices
                if (InstructionUtils.isInvokeInstruction(analyzedInstructions.get(index))) {
                    addInvokeVertex(vertex);
                }

                addVertex(vertex);
                vertices.add(vertex);
            }

            // connect vertices by inspecting successors and predecessors of instructions
            for (int index = 0; index < analyzedInstructions.size(); index++) {

                // ignore parse-switch and packed-switch payload instructions
                if (InstructionUtils.isSwitchPayloadInstruction(analyzedInstructions.get(index))) {
                    continue;
                }

                Vertex vertex = vertices.get(index);
                AnalyzedInstruction analyzedInstruction = analyzedInstructions.get(index);

                // connect entry vertex with 'first' instruction (there might be multiple due to exceptional flow)
                if (analyzedInstruction.isBeginningInstruction()) {
                    addEdge(getEntry(), vertex);
                }

                // add for each predecessor an incoming edge to the current vertex
                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();
                Iterator<AnalyzedInstruction> iterator = predecessors.iterator();

                while (iterator.hasNext()) {

                    AnalyzedInstruction predecessor = iterator.next();

                    // ignore the fake 'startOfMethod' instruction located at index -1
                    if (predecessor.getInstructionIndex() != -1) {
                        Vertex src = vertices.get(predecessor.getInstructionIndex());
                        LOGGER.debug("Edge: " + predecessor.getInstructionIndex() + "->" + analyzedInstruction.getInstructionIndex());
                        addEdge(src, vertex);
                    }
                }

                // add for each successor an outgoing edge to the current vertex + handle return/throw instructions
                List<AnalyzedInstruction> successors = analyzedInstruction.getSuccessors();

                // FIXME: There can be unreachable instructions after the return statement, ignoring them right now.
                if (successors.isEmpty()) {

                    LOGGER.debug("Terminator Instruction: " + analyzedInstruction.getOriginalInstruction().getOpcode()
                        + "(" + analyzedInstruction.getInstructionIndex() + ")");

                    /*
                    * An instruction, which does not define any successor, is either a return
                    * or a throw statement. Thus, such an instruction signals the end of the method.
                     */
                    addEdge(vertex, getExit());
                } else {
                    for (AnalyzedInstruction successor : successors) {
                        Vertex dest = vertices.get(successor.getInstructionIndex());
                        LOGGER.debug("Edge: " + analyzedInstruction.getInstructionIndex() + "->" + successor.getInstructionIndex());
                        addEdge(vertex, dest);
                    }
                }
            }
        } else {
            // no method implementation found -> construct dummy CFG
            addEdge(getEntry(), getExit());
        }
    }

    /**
     * Computes the intra-procedural CFG for a given method using basic blocks. Initially,
     * the leader instructions are computed, which separate basic blocks from each other. Then,
     * based on this information statements are grouped in basic blocks and mapped to a vertex.
     * Finally, the edges between the basic blocks are inserted.
     *
     * @param dexFile      The dex file containing the target method.
     * @param targetMethod The method for which we want to generate the CFG.
     */
    private void constructCFGWithBasicBlocks(DexFile dexFile, Method targetMethod) {

        LOGGER.debug("Constructing Intra-CFG with BasicBlocks for method: " + targetMethod);

        if (targetMethod.getImplementation() != null) {

            List<AnalyzedInstruction> analyzedInstructions = MethodUtils.getAnalyzedInstructions(dexFile, targetMethod);
            Set<Integer> leaders = computeLeaders(targetMethod, analyzedInstructions);

            String method = targetMethod.toString();

            // save for each vertex/basic block the instruction id of the last statement
            Map<Integer, Vertex> vertices = new HashMap<>();

            BlockStatement basicBlock = new BlockStatement(method);
            basicBlock.addStatement(new BasicStatement(method, analyzedInstructions.get(0)));

            // assign the instructions to basic blocks
            for (int index = 1; index < analyzedInstructions.size(); index++) {
                AnalyzedInstruction analyzedInstruction = analyzedInstructions.get(index);

                // ignore parse-switch and packed-switch payload instructions
                if (InstructionUtils.isSwitchPayloadInstruction(analyzedInstruction)) {
                    continue;
                }

                if (!leaders.contains(analyzedInstruction.getInstructionIndex())) {
                    // instruction belongs to current basic block
                    basicBlock.addStatement(new BasicStatement(method, analyzedInstruction));
                } else {
                    // end of basic block
                    createBasicBlockVertex(basicBlock, vertices);

                    // reset basic block
                    basicBlock = new BlockStatement(method);

                    // current instruction belongs to next basic block
                    basicBlock.addStatement(new BasicStatement(method, analyzedInstruction));
                }
            }

            // add the last basic block separately
            createBasicBlockVertex(basicBlock, vertices);
        } else {
            // no method implementation found -> construct dummy CFG
            addEdge(getEntry(), getExit());
        }
    }

    /**
     * Creates a new vertex in the graph for a given basic block. Also adds edges between the
     * basic block and previously created basic blocks. Likewise, an edge between the
     * entry vertex and the basic block or the basic block and the exit vertex is inserted if necessary.
     *
     * @param basicBlock The basic block wrapping the statements.
     * @param vertices A map storing each vertex/basic block by the instruction id of the last statement.
     */
    private void createBasicBlockVertex(BlockStatement basicBlock, Map<Integer, Vertex> vertices) {

        Vertex vertex = new Vertex(basicBlock);
        addVertex(vertex);

        // keep track of invoke vertices
        if (containsInvoke(basicBlock)) {
            addInvokeVertex(vertex);
        }

        // save vertex/basic block by index of last statement
        BasicStatement lastStmt = (BasicStatement) basicBlock.getLastStatement();
        vertices.put(lastStmt.getInstructionIndex(), vertex);

        // check if we need an edge between entry node and the basic block
        BasicStatement firstStmt = (BasicStatement) basicBlock.getFirstStatement();

        if (firstStmt.getInstruction().isBeginningInstruction()) {
            addEdge(getEntry(), vertex);
        }

        // check if we need an edge between the basic block and the exit node
        if (InstructionUtils.isTerminationStatement(lastStmt.getInstruction())) {
            addEdge(vertex, getExit());
        }

        // check for incoming edges of previously created basic blocks
        for (AnalyzedInstruction predecessor : firstStmt.getInstruction().getPredecessors()) {
            if (vertices.containsKey(predecessor.getInstructionIndex())) {
                addEdge(vertices.get(predecessor.getInstructionIndex()), vertex);
            }
        }
    }

    /**
     * Checks whether a block statement contains an invoke instruction.
     *
     * @param blockStatement The block statement to be checked.
     * @return Returns {@code true} if the block statement contains an
     * invoke instruction, otherwise {@code false} is returned.
     */
    private boolean containsInvoke(final BlockStatement blockStatement) {

        for (Statement statement : blockStatement.getStatements()) {
            if (statement instanceof BasicStatement) {
                if (InstructionUtils.isInvokeInstruction(((BasicStatement) statement).getInstruction())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Computes the leader instructions according to the traditional basic block construction algorithm. We
     * consider the first instruction, any target of a branch or goto instruction as leaders. Additionally, we
     * denote the first instruction of a catch block as a leader instruction.
     *
     * @param method The method for which we want to compute the leader instructions.
     * @param analyzedInstructions The instructions of belonging to the given method.
     * @return Returns the sorted instruction indices of leader instructions.
     */
    private Set<Integer> computeLeaders(Method method, List<AnalyzedInstruction> analyzedInstructions) {

        // maintains the leaders sorted
        Set<Integer> leaders = new TreeSet<>();

        // each entry refers to the code address of the first instruction within a catch block
        Set<Integer> catchBlocks = new HashSet<>();

        // retrieve the position of catch blocks
        List<? extends TryBlock<? extends ExceptionHandler>> tryBlocks = method.getImplementation().getTryBlocks();
        tryBlocks.forEach(tryBlock -> {
            tryBlock.getExceptionHandlers().forEach(exceptionHandler -> {
                // the code address denotes the absolute position of the catch block within the method
                catchBlocks.add(exceptionHandler.getHandlerCodeAddress());
            });
        });

        LOGGER.debug("Catch Blocks located at code addresses: " + catchBlocks);

        int consumedCodeUnits = 0;

        for (AnalyzedInstruction analyzedInstruction : analyzedInstructions) {

            if (!catchBlocks.isEmpty()) {
                if (catchBlocks.contains(consumedCodeUnits)) {
                    // first instruction of a catch block is a leader instruction
                    LOGGER.debug("First instruction within catch block at pos: " + analyzedInstruction.getInstructionIndex());
                    leaders.add(analyzedInstruction.getInstructionIndex());
                }
                consumedCodeUnits += analyzedInstruction.getInstruction().getCodeUnits();
            }

            // TODO: every instruction after a return/throw statement should be a leader instruction?

            if (analyzedInstruction.isBeginningInstruction()) {
                // any 'first' instruction is a leader instruction
                leaders.add(analyzedInstruction.getInstructionIndex());
            }

            if (InstructionUtils.isJumpInstruction(analyzedInstruction)) {
                // any successor (target or due to exceptional flow) is a leader instruction
                leaders.addAll(analyzedInstruction.getSuccessors().stream()
                        .map(AnalyzedInstruction::getInstructionIndex).collect(Collectors.toSet()));
            }

            if (analyzedInstruction.getSuccessors().size() > 1) {
                // any non-direct successor (exceptional flow) is a leader instruction
                for (AnalyzedInstruction successor : analyzedInstruction.getSuccessors()) {
                        LOGGER.debug("Exceptional flow Leader: " + successor.getInstructionIndex());
                        leaders.add(successor.getInstructionIndex());
                }
            }
        }

        LOGGER.debug("Leader Instructions: " + leaders);
        return leaders;
    }

    @Override
    public GraphType getGraphType() {
        return GRAPH_TYPE;
    }

    // TODO: check if deep copy of vertices and edges is necessary
    @Override
    @SuppressWarnings("unused")
    public BaseCFG copy() {
        BaseCFG clone = new IntraCFG(getMethodName());

        Graph<Vertex, Edge> graphClone = GraphTypeBuilder
                .<Vertex, DefaultEdge>directed().allowingMultipleEdges(true).allowingSelfLoops(true)
                .edgeClass(Edge.class).buildGraph();

        Set<Vertex> vertices = graph.vertexSet();
        Set<Edge> edges = graph.edgeSet();

        Cloner cloner = new Cloner();

        for (Vertex vertex : vertices) {
            graphClone.addVertex(cloner.deepClone(vertex));
        }

        for (Edge edge : edges) {
            Vertex src = cloner.deepClone(edge.getSource());
            Vertex dest = cloner.deepClone(edge.getTarget());
            graphClone.addEdge(src, dest);
        }

        clone.graph = graphClone;
        return clone;
    }

    @Override
    public IntraCFG clone() {
        IntraCFG cloneCFG = (IntraCFG) super.clone();
        return cloneCFG;
    }

    /**
     * Searches for the vertex described by the given trace in the graph.
     * Performs a brute force search if an instruction index is given.
     *
     * Don't use this method when dealing with an interCFG. The intra CFGs
     * stored by the interCFG are the original, non-connected graphs, i.e.
     * no virtual return vertices have been introduced.
     *
     * @param trace The trace describing the vertex, i.e. className->methodName->(entry|exit|instructionIndex).
     * @return Returns the vertex corresponding to the given trace.
     */
    @Override
    public Vertex lookUpVertex(String trace) {

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

            for (Vertex vertex : getVertices()) {

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
}
