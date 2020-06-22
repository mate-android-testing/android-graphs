package de.uni_passau.fim.auermich.graphs.cfg;

import com.google.common.collect.Lists;
import com.rits.cloning.Cloner;
import de.uni_passau.fim.auermich.graphs.Edge;
import de.uni_passau.fim.auermich.graphs.GraphType;
import de.uni_passau.fim.auermich.graphs.Vertex;
import de.uni_passau.fim.auermich.statement.BasicStatement;
import de.uni_passau.fim.auermich.statement.Statement;
import de.uni_passau.fim.auermich.utility.Utility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.analysis.ClassPath;
import org.jf.dexlib2.analysis.DexClassProvider;
import org.jf.dexlib2.analysis.MethodAnalyzer;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;

import java.util.*;
import java.util.stream.Collectors;

public class IntraCFG extends BaseCFG implements Cloneable {

    private static final Logger LOGGER = LogManager.getLogger(IntraCFG.class);
    private static final GraphType GRAPH_TYPE = GraphType.INTRACFG;

    public IntraCFG(String methodName, DexFile dexFile, boolean useBasicBlocks) {
        super(methodName);
        Optional<Method> targetMethod = Utility.searchForTargetMethod(dexFile, methodName);

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

            List<AnalyzedInstruction> analyzedInstructions = Utility.getAnalyzedInstructions(dexFile, targetMethod);
            List<Vertex> vertices = new ArrayList<>();

            // pre-create a vertex for each single instruction
            for (int index = 0; index < analyzedInstructions.size(); index++) {
                Statement stmt = new BasicStatement(getMethodName(), analyzedInstructions.get(index));
                Vertex vertex = new Vertex(stmt);
                addVertex(vertex);
                vertices.add(vertex);
            }

            // connect vertices by inspecting successors and predecessors of instructions
            for (int index = 0; index < analyzedInstructions.size(); index++) {

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
     * Computes the intra-procedural CFG for a given method. Uses basic blocks to reduce
     * the number of vertices. The underlying algorithm computes first the leader statements
     * and then groups statements between two leaders within a basic block.
     *
     * @param dexFile      The dex file containing the target method.
     * @param targetMethod The method for which we want to generate the CFG.
     */
    private void constructCFGWithBasicBlocks(DexFile dexFile, Method targetMethod) {

        LOGGER.debug("Constructing Intra-CFG with BasicBlocks for method: " + targetMethod);

        if (targetMethod.getImplementation() != null) {

            List<AnalyzedInstruction> analyzedInstructions = Utility.getAnalyzedInstructions(dexFile, targetMethod);
            List<Integer> leaders = computeLeaders(targetMethod, analyzedInstructions);


        } else {
            // no method implementation found -> construct dummy CFG
            addEdge(getEntry(), getExit());
        }

    }

    /**
     * Computes the leader instructions according to the traditional basic block construction algorithm. We
     * consider the first instruction, any target of a branch or goto instruction as leaders. Additionally, we
     * denote the first instruction of a catch block as a leader instruction.
     *
     * @param method The method for which we want to compute the leader instructions.
     * @param analyzedInstructions The instructions of belonging to the given method.
     * @return Returns the instruction indices of leader instructions.
     */
    private List<Integer> computeLeaders(Method method, List<AnalyzedInstruction> analyzedInstructions) {

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

            if (analyzedInstruction.isBeginningInstruction()) {
                // any 'first' instruction is a leader instruction
                leaders.add(analyzedInstruction.getInstructionIndex());
            } else if (Utility.isJumpInstruction(analyzedInstruction)) {
                // any successor (target or due to exceptional flow) is a leader instruction
                leaders.addAll(analyzedInstruction.getSuccessors().stream()
                        .map(AnalyzedInstruction::getInstructionIndex).collect(Collectors.toSet()));
            } else if (analyzedInstruction.getSuccessors().size() > 1) {
                // TODO: remove when it's guaranteed that we don't miss any leader instructions
                //  These instructions should be the first instructions of a catch block only. (covered by above procedure)
                // any non-direct successor (exceptional flow) is a leader instruction
                for (AnalyzedInstruction successor : analyzedInstruction.getSuccessors()) {
                    if (successor.getInstructionIndex() != analyzedInstruction.getInstructionIndex() + 1) {
                        LOGGER.debug("Exceptional flow Leader: " + successor.getInstructionIndex());
                        leaders.add(successor.getInstructionIndex());
                    }
                }
            }
        }

        LOGGER.debug("Leader Instructions: " + leaders);
        return new ArrayList<>(leaders);
    }

    @Override
    public GraphType getGraphType() {
        return GRAPH_TYPE;
    }

    // TODO: check if this method will be ever used
    @Override
    public BaseCFG copy() {
        BaseCFG clone = new IntraProceduralCFG(getMethodName());

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
    public IntraProceduralCFG clone() {
        IntraProceduralCFG cloneCFG = (IntraProceduralCFG) super.clone();
        return cloneCFG;
    }
}
