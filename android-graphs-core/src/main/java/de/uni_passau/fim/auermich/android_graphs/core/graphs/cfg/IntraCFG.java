package de.uni_passau.fim.auermich.android_graphs.core.graphs.cfg;

import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.*;
import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.android_graphs.core.graphs.GraphType;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BasicStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.BlockStatement;
import de.uni_passau.fim.auermich.android_graphs.core.statements.Statement;
import de.uni_passau.fim.auermich.android_graphs.core.utility.InstructionUtils;
import de.uni_passau.fim.auermich.android_graphs.core.utility.MethodUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents an intra procedural control flow graph.
 */
public class IntraCFG extends BaseCFG implements Cloneable {

    private static final Logger LOGGER = LogManager.getLogger(IntraCFG.class);
    private static final GraphType GRAPH_TYPE = GraphType.INTRACFG;

    // copy constructor
    public IntraCFG(String methodName) {
        super(methodName);
    }

    public IntraCFG(Method method, DexFile dexFile, boolean useBasicBlocks) {
        super(MethodUtils.deriveMethodSignature(method));

        // TODO: can we parallelize the construction?
        constructCFG(dexFile, method, useBasicBlocks);
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

        MethodImplementation methodImplementation = targetMethod.getImplementation();

        if (methodImplementation != null) {

            List<AnalyzedInstruction> analyzedInstructions = MethodUtils.getAnalyzedInstructions(dexFile, targetMethod);
            List<CFGVertex> vertices = new ArrayList<>();

            // pre-create a vertex for each single instruction
            for (int index = 0; index < analyzedInstructions.size(); index++) {

                AnalyzedInstruction analyzedInstruction = analyzedInstructions.get(index);

                // ignore parse-switch, packed-switch and array payload instructions as well as nop at the end
                if (InstructionUtils.isPayloadInstruction(analyzedInstruction)
                    || (InstructionUtils.isNOPInstruction(analyzedInstruction)
                        && analyzedInstruction.getSuccessors().isEmpty())) {
                    continue;
                }

                Statement stmt = new BasicStatement(getMethodName(), analyzedInstruction);
                CFGVertex vertex = new CFGVertex(stmt);

                // keep track of invoke vertices
                if (InstructionUtils.isInvokeInstruction(analyzedInstruction)) {
                    addInvokeVertex(vertex);
                }

                addVertex(vertex);
                vertices.add(vertex);
            }

            // connect vertices by inspecting successors and predecessors of instructions
            for (int index = 0; index < analyzedInstructions.size(); index++) {

                AnalyzedInstruction analyzedInstruction = analyzedInstructions.get(index);

                // ignore parse-switch, packed-switch and array payload instructions as well as nop at the end
                if (InstructionUtils.isPayloadInstruction(analyzedInstruction)
                        || (InstructionUtils.isNOPInstruction(analyzedInstruction)
                        && analyzedInstruction.getSuccessors().isEmpty())) {
                    continue;
                }

                CFGVertex vertex = vertices.get(index);

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
                        CFGVertex src = vertices.get(predecessor.getInstructionIndex());
                        LOGGER.debug("Edge: " + predecessor.getInstructionIndex() + "->" + analyzedInstruction.getInstructionIndex());
                        addEdge(src, vertex);
                    }
                }

                // add for each successor an outgoing edge to the current vertex + handle return/throw instructions
                List<AnalyzedInstruction> successors = analyzedInstruction.getSuccessors();

                /*
                 * An instruction without a successor is either a return, throw, one of those payload instructions
                 * or a NOP instruction at the end of the method.
                 * We ignore here the latter types of instructions, see the previous check
                 * InstructionUtils.isPayloadInstruction().
                 */
                if (successors.isEmpty()) {

                    LOGGER.debug("Terminator Instruction: " + analyzedInstruction.getOriginalInstruction().getOpcode()
                        + "(" + analyzedInstruction.getInstructionIndex() + ")");

                    // a return or throw instruction defines an edge to the virtual exit vertex
                    addEdge(vertex, getExit());
                } else {
                    for (AnalyzedInstruction successor : successors) {
                        CFGVertex dest = vertices.get(successor.getInstructionIndex());
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

        if (targetMethod.getImplementation() != null) {

            List<AnalyzedInstruction> analyzedInstructions = MethodUtils.getAnalyzedInstructions(dexFile, targetMethod);
            Set<Integer> leaders = computeLeaders(targetMethod, analyzedInstructions);

            String method = targetMethod.toString();

            // save for each basic block vertex the instruction id of the first and last statement
            Map<Integer, CFGVertex> basicBlocks = new HashMap<>();

            BlockStatement basicBlock = new BlockStatement(method);
            basicBlock.addStatement(new BasicStatement(method, analyzedInstructions.get(0)));

            // assign the instructions to basic blocks
            for (int index = 1; index < analyzedInstructions.size(); index++) {
                AnalyzedInstruction analyzedInstruction = analyzedInstructions.get(index);

                // ignore parse-switch, packed-switch and array payload instructions as well as nop at the end
                if (InstructionUtils.isPayloadInstruction(analyzedInstruction)
                        || (InstructionUtils.isNOPInstruction(analyzedInstruction)
                        && analyzedInstruction.getSuccessors().isEmpty())) {
                    LOGGER.debug("Ignoring instruction: " + analyzedInstruction.getInstruction().getOpcode()
                            + "(" + analyzedInstruction.getInstructionIndex() + ")");
                    continue;
                }

                if (!leaders.contains(analyzedInstruction.getInstructionIndex())) {
                    // instruction belongs to current basic block
                    basicBlock.addStatement(new BasicStatement(method, analyzedInstruction));
                } else {
                    // end of basic block
                    createBasicBlockVertex(basicBlock, basicBlocks);

                    // reset basic block
                    basicBlock = new BlockStatement(method);

                    // current instruction belongs to next basic block
                    basicBlock.addStatement(new BasicStatement(method, analyzedInstruction));
                }
            }

            // add the last basic block separately
            createBasicBlockVertex(basicBlock, basicBlocks);

            /*
            * A method may contain an endless loop in which case there is no return statement (even not in the bytecode)
            * and the virtual exit is isolated. Since we need to avoid isolated vertices, we attach a virtual edge from
            * the loop header to the virtual exit. Note that the detected loop header is not unique, this primarily
            * depends in which order the successors of a vertex are inserted into the stack, but since the virtual exit
            * is theoretically not reachable at all, we ignore this fact for now.
             */
            if (getPredecessors(getExit()).isEmpty()) {

                // TODO: Find an algorithm that can discover the loop header of the endless loop.

                final Set<CFGVertex> visited = new HashSet<>();
                final Deque<CFGVertex> stack = new LinkedList<>();
                CFGVertex current = getEntry();

                while (!visited.contains(current) && current != null) {
                    visited.add(current);
                    for (CFGVertex successors : getSuccessors(current)) {
                        if (!stack.contains(successors)) {
                            stack.push(successors);
                        }
                    }
                    current = stack.pop();
                }
                addEdge(current, getExit()); // add edge from loop header to virtual exit
            }
        } else {
            // no method implementation found -> construct dummy CFG
            LOGGER.warn("No implementation present for method: " + targetMethod + "! Using dummy CFG.");
            addEdge(getEntry(), getExit());
        }
    }

    /**
     * Creates a new vertex in the graph for a given basic block. Also adds edges between the
     * basic block and previously created basic blocks. Likewise, an edge between the
     * entry vertex and the basic block or the basic block and the exit vertex is inserted if necessary.
     *
     * @param basicBlock The basic block wrapping the statements.
     * @param basicBlocks A map storing for each basic block vertex the first and last instruction index.
     */
    private void createBasicBlockVertex(BlockStatement basicBlock, Map<Integer, CFGVertex> basicBlocks) {

        CFGVertex vertex = new CFGVertex(basicBlock);
        addVertex(vertex);

        // keep track of invoke vertices
        if (containsInvoke(basicBlock)) {
            addInvokeVertex(vertex);
        }

        // save basic block by index of first and last statement
        BasicStatement firstStmt = (BasicStatement) basicBlock.getFirstStatement();
        BasicStatement lastStmt = (BasicStatement) basicBlock.getLastStatement();
        basicBlocks.put(firstStmt.getInstructionIndex(), vertex);
        basicBlocks.put(lastStmt.getInstructionIndex(), vertex);

        // check if we need an edge between entry node and the basic block
        if (firstStmt.getInstruction().isBeginningInstruction()) {
            addEdge(getEntry(), vertex);
        }

        // check if we need an edge between the basic block and the exit node
        if (InstructionUtils.isTerminationStatement(lastStmt.getInstruction())) {
            addEdge(vertex, getExit());
        }

        // check for incoming edges of previously created basic blocks
        for (AnalyzedInstruction predecessor : firstStmt.getInstruction().getPredecessors()) {
            if (basicBlocks.containsKey(predecessor.getInstructionIndex())) {
                addEdge(basicBlocks.get(predecessor.getInstructionIndex()), vertex);
            }
        }

        // check for outgoing edges to previously created basic blocks
        for (AnalyzedInstruction successor : lastStmt.getInstruction().getSuccessors()) {
            if (basicBlocks.containsKey(successor.getInstructionIndex())) {
                addEdge(vertex, basicBlocks.get(successor.getInstructionIndex()));
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

        int consumedCodeUnits = 0;

        for (AnalyzedInstruction analyzedInstruction : analyzedInstructions) {

            if (!catchBlocks.isEmpty()) {
                if (catchBlocks.contains(consumedCodeUnits)) {
                    // first instruction of a catch block is a leader instruction
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
                        leaders.add(successor.getInstructionIndex());
                }
            }
        }

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

        Graph<CFGVertex, CFGEdge> graphClone = GraphTypeBuilder
                .<CFGVertex, DefaultEdge>directed().allowingMultipleEdges(true).allowingSelfLoops(true)
                .edgeClass(CFGEdge.class).buildGraph();

        Set<CFGVertex> vertices = graph.vertexSet();
        Set<CFGEdge> edges = graph.edgeSet();

        Cloner cloner = new Cloner();

        for (CFGVertex vertex : vertices) {
            graphClone.addVertex(cloner.deepClone(vertex));
        }

        for (CFGEdge edge : edges) {
            CFGVertex src = cloner.deepClone(edge.getSource());
            CFGVertex dest = cloner.deepClone(edge.getTarget());
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
    public CFGVertex lookUpVertex(String trace) {

        // TODO: May enable lookup of 'if' or 'switch' traces.

        // decompose trace into class, method  and instruction index
        String[] tokens = trace.split("->");

        // retrieve fully qualified method name (class name + method name)
        final String method = tokens[0] + "->" + tokens[1];

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

            // instruction index is always at same position for both branch and basic block traces
            final int instructionIndex = Integer.parseInt(tokens[2]);

            for (CFGVertex vertex : getVertices()) {

                if (vertex.isEntryVertex() || vertex.isExitVertex()) {
                    continue;
                }

                final Statement statement = vertex.getStatement();

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
