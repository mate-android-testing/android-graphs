package de.uni_passau.fim.auermich.graphs.cfg;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.graphs.Edge;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;
import de.uni_passau.fim.auermich.statement.BasicStatement;
import de.uni_passau.fim.auermich.statement.BlockStatement;
import de.uni_passau.fim.auermich.statement.Statement;
import de.uni_passau.fim.auermich.utility.Utility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.analysis.ClassPath;
import org.jf.dexlib2.analysis.DexClassProvider;
import org.jf.dexlib2.analysis.MethodAnalyzer;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OffsetInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction21t;
import org.jf.dexlib2.iface.instruction.formats.Instruction22t;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;
import org.jf.dexlib2.iface.instruction.formats.Instruction3rc;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.nio.file.LinkOption;
import java.util.*;
import java.util.stream.Collectors;

public class IntraProceduralCFG extends BaseCFG implements Cloneable {

    private static final Logger LOGGER = LogManager.getLogger(IntraProceduralCFG.class);

    private static final GraphType GRAPH_TYPE = GraphType.INTRACFG;

    public IntraProceduralCFG(String methodName) {
        super(methodName);
    }

    public IntraProceduralCFG(String methodName, DexFile dexFile, boolean useBasicBlocks) {
        super(methodName);
        Optional<Method> targetMethod = Utility.searchForTargetMethod(dexFile, methodName);

        if (!targetMethod.isPresent()) {
            throw new IllegalStateException("Target method not present in dex files!");
        }

        constructCFG(dexFile, targetMethod.get(), useBasicBlocks);
    }

    public IntraProceduralCFG clone() {
        IntraProceduralCFG cloneCFG = (IntraProceduralCFG) super.clone();
        return cloneCFG;
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
     * Computes the intra-procedural CFG for a given method. Uses basic blocks to reduce
     * the number of vertices. The underlying algorithm computes first the leader statements
     * and then groups statements between two leaders within a basic block.
     *
     * @param dexFile      The dex file containing the target method.
     * @param targetMethod The method for which we want to generate the CFG.
     */
    private void constructCFGWithBasicBlocks(DexFile dexFile, Method targetMethod) {

        MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                true, ClassPath.NOT_ART), targetMethod,
                null, false);
        List<AnalyzedInstruction> analyzedInstructions = analyzer.getAnalyzedInstructions();

        // stores the edge mapping between basic blocks based on the instruction id
        Multimap<Integer, Integer> basicBlockEdges = TreeMultimap.create();

        // keeps track of the instruction indices of return statements
        List<Integer> returnStmtIndices = new ArrayList<>();

        // computes the leader instructions, as a byproduct also computes the edges between the basic blocks + return indices
        List<AnalyzedInstruction> leaders = computeLeaders(analyzedInstructions, basicBlockEdges, returnStmtIndices);

        LOGGER.debug("Leaders: " + leaders.stream().map(instruction -> instruction.getInstructionIndex()).collect(Collectors.toList()));
        LOGGER.debug("Basic Block Edges: " + basicBlockEdges);

        // maps to each vertex the instruction id of the first and last statement
        Map<Integer, Vertex> vertexMap = new HashMap<>();

        // construct the basic blocks
        Set<List<AnalyzedInstruction>> basicBlocks = constructBasicBlocks(analyzedInstructions,
                leaders, vertexMap, getMethodName());

        LOGGER.debug("Number of BasicBlocks: " + basicBlocks.size());
        LOGGER.debug("Basic Blocks: " + basicBlocks.stream()
                .sorted((b1, b2) -> Integer.compare(b1.get(0).getInstructionIndex(), b2.get(0).getInstructionIndex()))
                .map(list -> list.stream()
                        .map(elem -> String.valueOf(elem.getInstructionIndex())).collect(Collectors.joining("-", "[", "]")))
                .collect(Collectors.joining(", ")));

        // connect the basic blocks
        for (Integer srcIndex : basicBlockEdges.keySet()) {

            LOGGER.debug("Source: " + srcIndex);
            Vertex src = vertexMap.get(srcIndex);

            Collection<Integer> targets = basicBlockEdges.get(srcIndex);

            for (Integer target : targets) {
                Vertex dest = vertexMap.get(target);
                LOGGER.debug("Target: " + target);
                graph.addEdge(src, dest);
            }
            LOGGER.debug(System.lineSeparator());
        }

        // connect entry vertex with first basic block
        addEdge(getEntry(), vertexMap.get(0));

        // connect each return statement with exit vertex
        for (Integer returnIndex : returnStmtIndices) {
            addEdge(vertexMap.get(returnIndex), getExit());
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

            List<Instruction> instructions = Lists.newArrayList(methodImplementation.getInstructions());

            MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                    true, ClassPath.NOT_ART), targetMethod,
                    null, false);

            List<AnalyzedInstruction> analyzedInstructions = analyzer.getAnalyzedInstructions();

            List<Vertex> vertices = new ArrayList<>();

            // pre-create vertices for each single instruction
            for (int index = 0; index < instructions.size(); index++) {
                Statement stmt = new BasicStatement(getMethodName(), analyzedInstructions.get(index));
                Vertex vertex = new Vertex(stmt);
                addVertex(vertex);
                vertices.add(vertex);
            }

            for (int index = 0; index < instructions.size(); index++) {

                LOGGER.debug("Instruction: " + vertices.get(index));

                AnalyzedInstruction analyzedInstruction = analyzedInstructions.get(index);

                // the current instruction represented as vertex
                Vertex vertex = vertices.get(index);

                // special treatment for first instruction (virtual entry node as predecessor)
                if (analyzedInstruction.isBeginningInstruction()) {
                    addEdge(getEntry(), vertex);
                }

                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();
                Iterator<AnalyzedInstruction> iterator = predecessors.iterator();

                // add for each predecessor an incoming edge to the current vertex
                while (iterator.hasNext()) {
                    AnalyzedInstruction predecessor = iterator.next();

                    if (predecessor.getInstructionIndex() != -1) {
                        // not entry vertex
                        Vertex src = vertices.get(predecessor.getInstructionIndex());
                        // Vertex src = new Vertex(predecessor.getInstructionIndex(), predecessor.getInstruction());
                        LOGGER.debug("Predecessor: " + src);
                        addEdge(src, vertex);
                    }
                }

                List<AnalyzedInstruction> successors = analyzedInstruction.getSuccessors();

                if (successors.isEmpty()) {
                    // must be a return statement, thus we need to insert an edge to the exit vertex
                    addEdge(vertex, getExit());
                } else {
                    // add for each successor an outgoing each from the current vertex
                    for (AnalyzedInstruction successor : successors) {
                        Vertex dest = vertices.get(successor.getInstructionIndex());
                        // Vertex dest = new Vertex(successor.getInstructionIndex(), successor.getInstruction());
                        LOGGER.debug("Successor: " + dest);
                        addEdge(vertex, dest);
                    }
                }
            }
        } else {
            // no method implementation found -> dummy CFG
            addEdge(getEntry(), getExit());
        }
    }

    /**
     * Computes the leader instructions. We consider branch targets, return statements, jump targets and invoke
     * instructions as leaders. As a side product, the indices of return instructions are tracked as well as
     * the edges between basic blocks are computed.
     *
     * @param analyzedInstructions The set of instructions for a given method.
     * @param basicBlockEdges      Contains the edges between basic blocks. Initially empty.
     * @param returnStmtIndices    Contains the instruction indices of return statements. Initially empty.
     * @return Returns a sorted list of leader instructions.
     */
    private List<AnalyzedInstruction> computeLeaders(List<AnalyzedInstruction> analyzedInstructions,
                                                            Multimap<Integer, Integer> basicBlockEdges,
                                                            List<Integer> returnStmtIndices) {

        Set<AnalyzedInstruction> leaderInstructions = new HashSet<>();

        // the first instruction is also a leader
        leaderInstructions.add(analyzedInstructions.get(0));

        for (AnalyzedInstruction analyzedInstruction : analyzedInstructions) {

            Instruction instruction = analyzedInstruction.getInstruction();

            if (instruction instanceof Instruction35c
                    || instruction instanceof Instruction3rc) {
                // invoke instruction
                leaderInstructions.add(analyzedInstruction);

                // each predecessor should be the last statement of a basic block
                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();

                for (AnalyzedInstruction predecessor : predecessors) {
                    if (predecessor.getInstructionIndex() != -1) {
                        // there is a dummy instruction located at pos -1 which is the predecessor of the first instruction
                        basicBlockEdges.put(predecessor.getInstructionIndex(), analyzedInstruction.getInstructionIndex());
                    }
                }
            } else if (instruction.getOpcode() == Opcode.RETURN
                    || instruction.getOpcode() == Opcode.RETURN_OBJECT
                    || instruction.getOpcode() == Opcode.RETURN_VOID
                    || instruction.getOpcode() == Opcode.RETURN_VOID_BARRIER
                    || instruction.getOpcode() == Opcode.RETURN_VOID_NO_BARRIER
                    || instruction.getOpcode() == Opcode.RETURN_WIDE) {
                // return instruction
                leaderInstructions.add(analyzedInstruction);

                // save instruction index
                returnStmtIndices.add(analyzedInstruction.getInstructionIndex());

                // each predecessor should be the last statement of a basic block
                Set<AnalyzedInstruction> predecessors = analyzedInstruction.getPredecessors();

                for (AnalyzedInstruction predecessor : predecessors) {
                    if (predecessor.getInstructionIndex() != -1) {
                        // there is a dummy instruction located at pos -1 which is the predecessor of the first instruction
                        basicBlockEdges.put(predecessor.getInstructionIndex(), analyzedInstruction.getInstructionIndex());
                    }
                }
            } else if (instruction instanceof OffsetInstruction) {

                if (instruction instanceof Instruction21t
                        || instruction instanceof Instruction22t) {
                    // if instruction
                    List<AnalyzedInstruction> successors = analyzedInstruction.getSuccessors();
                    leaderInstructions.addAll(successors);

                    // there is an edge from the if instruction to each successor
                    for (AnalyzedInstruction successor : successors) {
                        basicBlockEdges.put(analyzedInstruction.getInstructionIndex(), successor.getInstructionIndex());
                    }
                } else {
                    // some jump instruction, goto packed-switch, sparse-switch
                    List<AnalyzedInstruction> successors = analyzedInstruction.getSuccessors();
                    leaderInstructions.addAll(successors);

                    // there is an edge from the jump instruction to each successor (there should be only one)
                    for (AnalyzedInstruction successor : successors) {
                        basicBlockEdges.put(analyzedInstruction.getInstructionIndex(), successor.getInstructionIndex());
                    }
                }
            }
        }

        List<AnalyzedInstruction> leaders = leaderInstructions.stream()
                .sorted((i1, i2) -> Integer.compare(i1.getInstructionIndex(), i2.getInstructionIndex())).collect(Collectors.toList());

        return leaders;
    }

    /**
     * Constructs the basic blocks for a given method and adds them to the given CFG.
     *
     * @param analyzedInstructions The set of instructions of a given method.
     * @param leaders              The set of leader instructions previously identified.
     * @param vertexMap            A map that stores for each basic block (a vertex) the instruction id of
     *                             the first and last statement. So we have two entries per vertex.
     * @param methodName           The name of the method for which we want to construct the basic blocks.
     * @return Returns the basic blocks each as a list of instructions.
     */
    private Set<List<AnalyzedInstruction>> constructBasicBlocks(List<AnalyzedInstruction> analyzedInstructions,
                                                                       List<AnalyzedInstruction> leaders, Map<Integer, Vertex> vertexMap,
                                                                       String methodName) {
        // stores all the basic blocks
        Set<List<AnalyzedInstruction>> basicBlocks = new HashSet<>();

        // the next leader index, not the leader at the first instruction!
        int nextLeaderIndex = 1;

        // the next leader
        AnalyzedInstruction nextLeader = leaders.get(nextLeaderIndex);

        // a single basic block
        List<AnalyzedInstruction> basicBlock = new ArrayList<>();

        // construct basic blocks and build graph
        for (AnalyzedInstruction analyzedInstruction : analyzedInstructions) {

            // while we haven't found the next leader
            if (analyzedInstruction.getInstructionIndex() != nextLeader.getInstructionIndex()) {
                basicBlock.add(analyzedInstruction);
            } else {
                // we reached the next leader

                // update basic blocks
                List<AnalyzedInstruction> instructionsOfBasicBlock = new ArrayList<>(basicBlock);
                basicBlocks.add(instructionsOfBasicBlock);

                // construct a basic statement for each instruction
                List<Statement> stmts = instructionsOfBasicBlock.stream().map(i -> new BasicStatement(methodName, i)).collect(Collectors.toList());

                // construct the block statement
                Statement blockStmt = new BlockStatement(methodName, stmts);

                // each basic block is represented by a vertex
                Vertex vertex = new Vertex(blockStmt);

                int firstStmtIndex = basicBlock.get(0).getInstructionIndex();
                int lastStmtIndex = basicBlock.get(basicBlock.size() - 1).getInstructionIndex();
                vertexMap.put(firstStmtIndex, vertex);
                vertexMap.put(lastStmtIndex, vertex);

                addVertex(vertex);

                // reset basic block
                basicBlock.clear();

                // the leader we reached belongs to the next basic block
                basicBlock.add(nextLeader);

                // update the next leader
                nextLeaderIndex++;
                if (nextLeaderIndex >= leaders.size()) {
                    // add the last leader as basic block
                    basicBlocks.add(basicBlock);

                    // construct a basic statement for each instruction
                    stmts = basicBlock.stream().map(i -> new BasicStatement(methodName, i)).collect(Collectors.toList());

                    // construct the block statement
                    blockStmt = new BlockStatement(methodName, stmts);

                    // each basic block is represented by a vertex
                    vertex = new Vertex(blockStmt);

                    // identify each basic block by its first and last statement
                    firstStmtIndex = basicBlock.get(0).getInstructionIndex();
                    lastStmtIndex = basicBlock.get(basicBlock.size() - 1).getInstructionIndex();
                    vertexMap.put(firstStmtIndex, vertex);
                    vertexMap.put(lastStmtIndex, vertex);

                    addVertex(vertex);

                    break;
                } else {
                    nextLeader = leaders.get(nextLeaderIndex);
                }
            }
        }
        return basicBlocks;
    }


    public BaseCFG copy() {

        BaseCFG clone = new IntraProceduralCFG(getMethodName());

        Graph<Vertex, Edge> graphClone = GraphTypeBuilder
                .<Vertex, DefaultEdge> directed().allowingMultipleEdges(true).allowingSelfLoops(true)
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
}
